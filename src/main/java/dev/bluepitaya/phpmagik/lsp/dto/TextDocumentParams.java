package dev.bluepitaya.phpmagik.lsp.dto;

import org.jspecify.annotations.Nullable;

/** Shared by didClose and documentSymbol, which carry nothing but the document. */
public record TextDocumentParams(@Nullable TextDocumentIdentifier textDocument) {
}
