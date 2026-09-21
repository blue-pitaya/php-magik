package dev.bluepitaya.phpmagik.lsp.handler;

import dev.bluepitaya.phpmagik.index.*;
import dev.bluepitaya.phpmagik.lsp.Logger;
import dev.bluepitaya.phpmagik.lsp.dto.*;
import org.jspecify.annotations.NullMarked;

import java.nio.charset.StandardCharsets;

@NullMarked
public final class DidOpenHandler {

    private final Workspace app;
    private final Logger log;

    public DidOpenHandler(Workspace app, Logger log) {
        this.app = app;
        this.log = log;
    }

    public void handle(DidOpenParams params) {
        TextDocumentItem td = params.textDocument();
        String uri = td == null ? null : td.uri();
        String text = td == null ? null : td.text();
        if (uri == null || text == null) return;
        byte[] content = text.getBytes(StandardCharsets.UTF_8);
        log.log("didOpen: " + uri + " (" + content.length + " bytes)");

        if (!app.reparseFile(uri, content)) {
            log.log("didOpen: " + uri + " not in index, skipping reparse");
        }
    }
}
