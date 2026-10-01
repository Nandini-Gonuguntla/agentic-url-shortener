package com.agentic.orchestrator.workspace;

import com.agentic.orchestrator.artifact.RepoInventory;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Deterministic static inventory of a Spring codebase: the facts the analysis agent reasons over. */
public class CodebaseScanner {

    private static final Pattern CLASS_MAPPING = Pattern.compile("@RequestMapping\\(\\s*\"([^\"]*)\"");
    private static final Pattern METHOD_MAPPING = Pattern.compile(
            "@(Get|Post|Put|Delete|Patch)Mapping(?:\\(\\s*(?:value\\s*=\\s*)?\"([^\"]*)\")?[^\\n]*\\n\\s*(?:public\\s+)?[\\w<>,\\s?]+?\\s(\\w+)\\(");
    private static final Set<String> STOP_WORDS = Set.of("should", "would", "could", "their", "there", "which",
            "about", "these", "those", "other", "under", "every", "users", "make", "into", "with", "that", "this",
            "from", "have", "when", "where", "what", "while", "after", "before", "links", "service", "support");

    public RepoInventory scan(Workspace workspace, String requirement) {
        List<String> files = workspace.listFiles();
        List<String> sources = files.stream().filter(f -> f.startsWith("src/main/java/") && f.endsWith(".java")).toList();
        List<String> tests = files.stream().filter(f -> f.startsWith("src/test/java/") && f.endsWith(".java")).toList();
        List<String> migrations = files.stream().filter(f -> f.contains("db/migration/")).toList();
        List<String> configs = files.stream().filter(f -> f.matches("src/main/resources/application.*\\.(yml|yaml|properties)")
                || f.equals("pom.xml")).toList();
        List<RepoInventory.Endpoint> endpoints = new ArrayList<>();
        List<String> entities = new ArrayList<>();
        Set<String> keywords = keywords(requirement);
        List<String> hits = new ArrayList<>();
        for (String file : sources) {
            String content = workspace.read(file).orElse("");
            String className = file.substring(file.lastIndexOf('/') + 1).replace(".java", "");
            if (content.contains("@Entity")) {
                entities.add(className);
            }
            String prefix = "";
            Matcher classMapping = CLASS_MAPPING.matcher(content);
            if (classMapping.find()) {
                prefix = classMapping.group(1);
            }
            Matcher method = METHOD_MAPPING.matcher(content);
            while (method.find()) {
                String path = prefix + (method.group(2) == null ? "" : method.group(2));
                endpoints.add(new RepoInventory.Endpoint(method.group(1).toUpperCase(Locale.ROOT),
                        path.isEmpty() ? "/" : path, className + "." + method.group(3)));
            }
            String lower = content.toLowerCase(Locale.ROOT);
            List<String> matched = keywords.stream().filter(lower::contains).toList();
            if (!matched.isEmpty()) {
                hits.add(file + " " + matched);
            }
        }
        return new RepoInventory(files.size(), sources, tests, endpoints, entities, migrations, configs, hits);
    }

    static Set<String> keywords(String requirement) {
        Set<String> words = new LinkedHashSet<>();
        for (String word : requirement.toLowerCase(Locale.ROOT).split("[^a-z]+")) {
            if (word.length() >= 5 && !STOP_WORDS.contains(word)) {
                words.add(word.endsWith("s") ? word.substring(0, word.length() - 1) : word);
            }
        }
        return words;
    }
}
