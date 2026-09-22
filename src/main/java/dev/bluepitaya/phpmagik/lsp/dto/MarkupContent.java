package dev.bluepitaya.phpmagik.lsp.dto;

import org.jspecify.annotations.NullMarked;

@NullMarked
public record MarkupContent(String kind, String value) {
}
