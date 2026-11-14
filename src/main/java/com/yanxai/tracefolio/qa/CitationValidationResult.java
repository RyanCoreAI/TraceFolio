package com.yanxai.tracefolio.qa;

import java.util.List;

public record CitationValidationResult(boolean passed, List<String> errors) {
}
