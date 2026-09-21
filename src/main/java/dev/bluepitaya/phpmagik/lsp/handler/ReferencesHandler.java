package dev.bluepitaya.phpmagik.lsp.handler;

import dev.bluepitaya.phpmagik.index.*;
import dev.bluepitaya.phpmagik.lsp.Json;
import dev.bluepitaya.phpmagik.lsp.Logger;
import dev.bluepitaya.phpmagik.lsp.dto.*;
import org.jspecify.annotations.NullMarked;
import tools.jackson.databind.node.ArrayNode;

@NullMarked
public final class ReferencesHandler {

    private final Workspace app;
    private final SymbolFinder symbols;
    private final Logger log;

    public ReferencesHandler(Workspace app, SymbolFinder symbols, Logger log) {
        this.app = app;
        this.symbols = symbols;
        this.log = log;
    }

    public ArrayNode handle(ReferenceParams params) {
        TextDocumentIdentifier td = params.textDocument();
        Position pos = params.position();
        ReferenceContext rctx = params.context();
        ArrayNode locs = Json.array();
        if (td == null || pos == null) return locs;
        String uri = td.uri();
        int line = pos.line();
        int col = pos.character();
        boolean includeDecl = rctx != null && rctx.includeDeclaration();
        log.log("references: " + uri + " " + line + ":" + col);

        PhpFile file = app.findFile(uri);
        if (file == null) return locs;

        PhpSymbol sym = symbols.resolveAt(file, line, col);

        if (sym instanceof PhpFunction func) {
            PhpFunction def = symbols.findFuncDef(func);
            if (def != null) collectFuncRefs(def, includeDecl, locs);
        } else if (sym instanceof PhpVar var) {
            PhpVar def = symbols.findVarDef(var);
            if (def != null) collectVarRefs(def, includeDecl, locs);
        }

        return locs;
    }

    private void collectFuncRefs(PhpFunction def, boolean includeDecl, ArrayNode locs) {
        for (PhpFunction f : symbols.funcRefs(def, includeDecl)) {
            PhpFile file = app.file(f.fileId());
            locs.add(Json.location(file.uri(), f.line(), f.col(), Json.byteLength(f.name())));
        }
    }

    private void collectVarRefs(PhpVar def, boolean includeDecl, ArrayNode locs) {
        for (PhpVar v : symbols.varRefs(def, includeDecl)) {
            /* $this->prop / $obj->prop store a synthetic "$"-prefixed name (to
             * match against property declarations), but the source text at this
             * position is just the bare property name - there is no literal "$" */
            int len = Json.byteLength(v.name());
            if (v.kind() == VarKind.THIS || v.kind() == VarKind.OBJ) {
                len -= 1;
            }
            PhpFile file = app.file(v.fileId());
            locs.add(Json.location(file.uri(), v.line(), v.col(), len));
        }
    }
}
