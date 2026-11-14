package com.yanxai.tracefolio.source;

import jakarta.validation.constraints.NotBlank;

public record BlogIngestRequest(@NotBlank String url) {
}
