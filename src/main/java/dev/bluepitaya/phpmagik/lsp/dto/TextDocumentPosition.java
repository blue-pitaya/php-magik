package dev.bluepitaya.phpmagik.lsp.dto;

import org.jspecify.annotations.Nullable;

/** Shared by hover, definition and completion. */
public record TextDocumentPosition(@Nullable TextDocumentIdentifier textDocument,
                                   @Nullable Position position) {
}
