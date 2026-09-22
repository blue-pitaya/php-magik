package dev.bluepitaya.phpmagik.lsp.dto;

import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

@NullMarked
public record ContentChange(@Nullable String text) {
}
