package com.agentic.shortener.api;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CreateLinkRequest(
        @NotBlank @Size(max = 8192) String url,
        @Size(max = 32) String customAlias) {
}
