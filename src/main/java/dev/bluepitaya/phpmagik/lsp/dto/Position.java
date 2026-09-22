package dev.bluepitaya.phpmagik.lsp.dto;

import org.jspecify.annotations.NullMarked;

@NullMarked
public record Position(int line, int character) {
}
