package com.agentic.shortener.api;

import com.agentic.shortener.service.AliasAlreadyExistsException;
import com.agentic.shortener.service.InvalidAliasException;
import com.agentic.shortener.service.InvalidUrlException;
import com.agentic.shortener.service.LinkExpiredException;
import com.agentic.shortener.service.LinkNotFoundException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.net.URI;
import java.util.stream.Collectors;

/** Maps domain errors to RFC 9457 problem details. Internal errors never leak stack traces. */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(LinkNotFoundException.class)
    ProblemDetail notFound(LinkNotFoundException e) {
        return problem(HttpStatus.NOT_FOUND, "link-not-found", e.getMessage());
    }

    /** 410 rather than 404: the link existed, which is useful for clients and support. */
    @ExceptionHandler(LinkExpiredException.class)
    ProblemDetail expired(LinkExpiredException e) {
        return problem(HttpStatus.GONE, "link-expired", e.getMessage());
    }

    @ExceptionHandler(AliasAlreadyExistsException.class)
    ProblemDetail aliasTaken(AliasAlreadyExistsException e) {
        return problem(HttpStatus.CONFLICT, "alias-taken", e.getMessage());
    }

    @ExceptionHandler({InvalidUrlException.class, InvalidAliasException.class})
    ProblemDetail invalidInput(RuntimeException e) {
        String type = e instanceof InvalidUrlException ? "invalid-url" : "invalid-alias";
        return problem(HttpStatus.BAD_REQUEST, type, e.getMessage());
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    ProblemDetail invalidRequest(MethodArgumentNotValidException e) {
        String detail = e.getBindingResult().getFieldErrors().stream()
                .map(error -> error.getField() + " " + error.getDefaultMessage())
                .collect(Collectors.joining("; "));
        return problem(HttpStatus.BAD_REQUEST, "invalid-request", detail);
    }

    @ExceptionHandler(IllegalStateException.class)
    ProblemDetail unavailable(IllegalStateException e) {
        log.error("Request failed", e);
        return problem(HttpStatus.SERVICE_UNAVAILABLE, "temporarily-unavailable", "Please retry shortly");
    }

    private static ProblemDetail problem(HttpStatus status, String type, String detail) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, detail);
        problem.setType(URI.create("urn:problem:" + type));
        return problem;
    }
}
