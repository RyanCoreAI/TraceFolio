package com.yanxai.tracefolio.jd;

import jakarta.validation.constraints.NotBlank;

public record JdCompileRequest(@NotBlank String jdText) {
}
