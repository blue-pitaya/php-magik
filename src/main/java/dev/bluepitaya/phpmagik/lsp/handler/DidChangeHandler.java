package dev.bluepitaya.phpmagik.lsp.handler;

import dev.bluepitaya.phpmagik.index.*;
import dev.bluepitaya.phpmagik.lsp.Logger;
import dev.bluepitaya.phpmagik.lsp.dto.*;
import org.jspecify.annotations.NullMarked;

import java.nio.charset.StandardCharsets;
import java.util.List;

@NullMarked
public final class DidChangeHandler {

    private final Workspace app;
    private final Logger log;

    public DidChangeHandler(Workspace app, Logger log) {
        this.app = app;
        this.log = log;
    }

    public void handle(DidChangeParams params) {
        TextDocumentIdentifier td = params.textDocument();
        String uri = td == null ? null : td.uri();
        List<ContentChange> changes = params.contentChanges();
        ContentChange change = changes == null || changes.isEmpty() ? null : changes.get(0);
        String text = change == null ? null : change.text();
        if (uri == null || text == null) return;
        byte[] content = text.getBytes(StandardCharsets.UTF_8);
        log.log("didChange: " + uri + " (" + content.length + " bytes)");

        PhpFile file = app.findFile(uri);
        if (file == null) {
            log.log("didChange: " + uri + " not in index, skipping reparse");
            return;
        }

        /* textDocumentSync is Full (1): `text` is always the whole document */
        app.reparse(file, content);
    }
}
