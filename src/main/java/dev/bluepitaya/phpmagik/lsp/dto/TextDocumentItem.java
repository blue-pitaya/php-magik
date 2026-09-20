package dev.bluepitaya.phpmagik.lsp.dto;

import org.jspecify.annotations.Nullable;

public record TextDocumentItem(@Nullable String uri, @Nullable String text) {
}
