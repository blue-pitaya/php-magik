package dev.bluepitaya.phpmagik.lsp.dto;

import org.jspecify.annotations.Nullable;

import java.util.List;

public record DidChangeParams(@Nullable TextDocumentIdentifier textDocument,
                              @Nullable List<ContentChange> contentChanges) {
}
