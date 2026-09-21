package dev.bluepitaya.phpmagik.lsp.dto;

/** Shared by hover, definition and completion. */
public record TextDocumentPosition(TextDocumentIdentifier textDocument, Position position) {
}
