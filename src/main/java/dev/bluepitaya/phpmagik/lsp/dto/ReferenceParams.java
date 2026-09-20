package dev.bluepitaya.phpmagik.lsp.dto;

import org.jspecify.annotations.Nullable;

public record ReferenceParams(@Nullable TextDocumentIdentifier textDocument,
                              @Nullable Position position,
                              @Nullable ReferenceContext context) {
}
