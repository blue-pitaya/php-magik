package dev.bluepitaya.phpmagik.lsp.handler;

import dev.bluepitaya.phpmagik.lsp.Logger;
import dev.bluepitaya.phpmagik.lsp.dto.*;
import org.jspecify.annotations.NullMarked;

@NullMarked
public final class DidCloseHandler {

    private final Logger log;

    public DidCloseHandler(Logger log) {
        this.log = log;
    }

    public void handle(TextDocumentParams params) {
        TextDocumentIdentifier td = params.textDocument();
        log.log("didClose: " + (td == null ? null : td.uri()));
    }
}
