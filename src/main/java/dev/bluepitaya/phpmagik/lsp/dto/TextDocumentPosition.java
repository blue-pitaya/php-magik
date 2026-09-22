package dev.bluepitaya.phpmagik.lsp.dto;

import org.jspecify.annotations.NullMarked;

@NullMarked
public record TextDocumentPosition(TextDocumentIdentifier textDocument, Position position) {
}
