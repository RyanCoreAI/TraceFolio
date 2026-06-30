package com.yanxai.tracefolio.document;

import java.util.UUID;

public record IngestedDocument(UUID id, int chunkCount) {
}
