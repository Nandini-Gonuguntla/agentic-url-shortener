package com.agentic.orchestrator.workspace;

import com.agentic.orchestrator.artifact.ValidationReport;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

/**
 * Builds and tests a workspace through its Maven wrapper and reads the Surefire XML reports,
 * so pass/fail comes from the real tool rather than from an agent's claim.
 */
public class MavenRunner {

    private final ProcessRunner processes;
    private final boolean preferOffline;

    public MavenRunner(ProcessRunner processes, boolean preferOffline) {
        this.processes = processes;
        this.preferOffline = preferOffline;
    }

    public ValidationReport test(Path project, Duration timeout) {
        ValidationReport report = run(project, timeout, preferOffline);
        if (preferOffline && !report.passed() && report.testsRun() == 0 && looksLikeMissingDependency(report.outputTail())) {
            report = run(project, timeout, false);
        }
        return report;
    }

    private ValidationReport run(Path project, Duration timeout, boolean offline) {
        deleteReports(project.resolve("target/surefire-reports"));
        List<String> command = new ArrayList<>(wrapperCommand());
        command.addAll(List.of("-B", "-ntp"));
        if (offline) {
            command.add("-o");
        }
        command.add("test");
        // The child JVM uses the same JDK as the orchestrator, independent of the caller's JAVA_HOME.
        Map<String, String> env = Map.of("JAVA_HOME", System.getProperty("java.home"));
        ProcessRunner.Result result = processes.run(command, project, timeout, env);
        Totals totals = readReports(project.resolve("target/surefire-reports"));
        List<ValidationReport.TestFailure> failures = new ArrayList<>(totals.failures());
        if (!result.ok() && totals.tests() == 0) {
            failures.add(new ValidationReport.TestFailure("build", result.timedOut() ? "timeout" : "compile",
                    buildErrors(result.output())));
        }
        boolean passed = result.ok() && totals.tests() > 0 && totals.failed() == 0 && totals.errors() == 0;
        return new ValidationReport(passed, totals.tests(), totals.failed(), totals.errors() + (totals.tests() == 0 && !result.ok() ? 1 : 0),
                totals.skipped(), failures, result.durationMs(), String.join(" ", command), result.tail(40));
    }

    private static List<String> wrapperCommand() {
        boolean windows = System.getProperty("os.name").toLowerCase().contains("win");
        // Explicit relative path: cmd.exe may not search the working directory, and a relative
        // path avoids quoting the (possibly space-containing) absolute workspace path.
        return windows ? List.of("cmd.exe", "/c", ".\\mvnw.cmd") : List.of("sh", "./mvnw");
    }

    private static boolean looksLikeMissingDependency(String output) {
        return output.contains("offline mode") || output.contains("Could not resolve") || output.contains("Cannot access");
    }

    /** Compiler errors and Maven ERROR lines are what an implementation agent needs to fix the code. */
    private static String buildErrors(String output) {
        List<String> errors = output.lines()
                .filter(l -> l.contains("[ERROR]") && !l.contains("-> [Help") && !l.contains("Re-run Maven")
                        && !l.contains("For more information") && !l.contains("http://cwiki"))
                .limit(25)
                .toList();
        return errors.isEmpty() ? "Build failed without test reports" : String.join("\n", errors);
    }

    private static Totals readReports(Path dir) {
        if (!Files.isDirectory(dir)) {
            return new Totals(0, 0, 0, 0, List.of());
        }
        int tests = 0, failed = 0, errors = 0, skipped = 0;
        List<ValidationReport.TestFailure> failures = new ArrayList<>();
        try (Stream<Path> files = Files.list(dir)) {
            for (Path file : files.filter(f -> f.getFileName().toString().matches("TEST-.*\\.xml")).sorted().toList()) {
                Document doc = parser().parse(file.toFile());
                Element suite = doc.getDocumentElement();
                tests += intAttr(suite, "tests");
                failed += intAttr(suite, "failures");
                errors += intAttr(suite, "errors");
                skipped += intAttr(suite, "skipped");
                NodeList cases = suite.getElementsByTagName("testcase");
                for (int i = 0; i < cases.getLength(); i++) {
                    Element testCase = (Element) cases.item(i);
                    for (String tag : List.of("failure", "error")) {
                        NodeList problems = testCase.getElementsByTagName(tag);
                        if (problems.getLength() > 0) {
                            Element problem = (Element) problems.item(0);
                            String message = problem.getAttribute("message");
                            if (message.isBlank()) {
                                message = problem.getTextContent().lines().limit(3).reduce("", (a, b) -> a + b + " ");
                            }
                            failures.add(new ValidationReport.TestFailure(testCase.getAttribute("classname"),
                                    testCase.getAttribute("name"), truncate(message, 600)));
                        }
                    }
                }
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        } catch (Exception e) {
            throw new IllegalStateException("Cannot parse surefire reports", e);
        }
        return new Totals(tests, failed, errors, skipped, failures);
    }

    private static DocumentBuilder parser() throws Exception {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
        factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
        return factory.newDocumentBuilder();
    }

    private static int intAttr(Element element, String name) {
        String value = element.getAttribute(name);
        return value.isBlank() ? 0 : (int) Double.parseDouble(value);
    }

    private static String truncate(String value, int max) {
        return value.length() <= max ? value : value.substring(0, max) + "...";
    }

    private static void deleteReports(Path dir) {
        if (!Files.isDirectory(dir)) {
            return;
        }
        try (Stream<Path> walk = Files.walk(dir)) {
            walk.sorted(Comparator.reverseOrder()).forEach(p -> p.toFile().delete());
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private record Totals(int tests, int failed, int errors, int skipped, List<ValidationReport.TestFailure> failures) {
    }
}
