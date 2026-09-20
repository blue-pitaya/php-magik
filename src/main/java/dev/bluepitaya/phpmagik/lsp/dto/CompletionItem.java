package dev.bluepitaya.phpmagik.lsp.dto;

import org.jspecify.annotations.Nullable;

public record CompletionItem(String label, int kind, @Nullable String detail) {
}
