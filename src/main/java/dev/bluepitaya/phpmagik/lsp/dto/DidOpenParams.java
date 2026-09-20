package dev.bluepitaya.phpmagik.lsp.dto;

import org.jspecify.annotations.Nullable;

public record DidOpenParams(@Nullable TextDocumentItem textDocument) {
}
