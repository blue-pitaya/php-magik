package dev.bluepitaya.phpmagik.lsp.dto;

/** Shared by hover and definition. */
public record TextDocumentPosition(TextDocumentIdentifier textDocument, Position position) {
}
