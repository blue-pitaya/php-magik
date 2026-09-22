package dev.bluepitaya.phpmagik.lsp.dto;

import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

@NullMarked
public record ReferenceParams(@Nullable TextDocumentIdentifier textDocument,
                              @Nullable Position position,
                              @Nullable ReferenceContext context) {
}
