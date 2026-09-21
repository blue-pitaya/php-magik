package dev.bluepitaya.phpmagik.lsp.handler;

import dev.bluepitaya.phpmagik.index.*;
import dev.bluepitaya.phpmagik.lsp.Json;
import dev.bluepitaya.phpmagik.lsp.Logger;
import dev.bluepitaya.phpmagik.lsp.dto.*;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;
import tools.jackson.databind.node.ObjectNode;

@NullMarked
public final class DefinitionHandler {

    private final Workspace app;
    private final SymbolFinder symbols;
    private final Logger log;

    public DefinitionHandler(Workspace app, SymbolFinder symbols, Logger log) {
        this.app = app;
        this.symbols = symbols;
        this.log = log;
    }

    public @Nullable ObjectNode handle(TextDocumentPosition params) {
        TextDocumentIdentifier td = params.textDocument();
        Position pos = params.position();
        String uri = td.uri();
        int line = pos.line();
        int col = pos.character();
        log.log("definition: " + uri + " " + line + ":" + col);

        PhpFile file = app.findFile(uri);
        if (file == null) return null;

        PhpSymbol sym = symbols.resolveAt(file, line, col);

        if (sym instanceof PhpFunction func) {
            PhpFunction def = symbols.findFuncDef(func);
            if (def != null) {
                PhpFile target = app.file(def.fileId());
                return Json.location(target.uri(), def.line(), def.col(),
                        Json.byteLength(def.name()));
            }
        } else if (sym instanceof PhpVar var) {
            PhpVar def = symbols.findVarDef(var);
            if (def != null) {
                PhpFile target = app.file(def.fileId());
                return Json.location(target.uri(), def.line(), def.col(),
                        Json.byteLength(def.name()));
            }
        }

        return null;
    }
}
