package com.agentic.orchestrator.api;

import com.agentic.orchestrator.engine.EngineException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class ApiExceptionHandler {

    @ExceptionHandler(EngineException.class)
    ProblemDetail engine(EngineException e) {
        HttpStatus status = switch (e.reason()) {
            case NOT_FOUND -> HttpStatus.NOT_FOUND;
            case CONFLICT -> HttpStatus.CONFLICT;
            case INVALID -> HttpStatus.BAD_REQUEST;
        };
        return ProblemDetail.forStatusAndDetail(status, e.getMessage());
    }
}
