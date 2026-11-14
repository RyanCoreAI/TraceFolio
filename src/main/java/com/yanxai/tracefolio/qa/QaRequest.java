package com.yanxai.tracefolio.qa;

import jakarta.validation.constraints.NotBlank;

public record QaRequest(@NotBlank String question) {
}
