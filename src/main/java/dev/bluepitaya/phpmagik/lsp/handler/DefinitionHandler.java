package dev.bluepitaya.phpmagik.lsp.handler;

import dev.bluepitaya.phpmagik.AppException;
import dev.bluepitaya.phpmagik.PhpFile;
import dev.bluepitaya.phpmagik.SymbolFinder;
import dev.bluepitaya.phpmagik.Workspace;
import dev.bluepitaya.phpmagik.lsp.Json;
import dev.bluepitaya.phpmagik.lsp.Logger;
import dev.bluepitaya.phpmagik.lsp.dto.TextDocumentPosition;
import dev.bluepitaya.phpmagik.phpsymbol.PhpMethodVarUsage;
import dev.bluepitaya.phpmagik.phpsymbol.PhpSymbol;
import dev.bluepitaya.phpmagik.ts.Range;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;
import tools.jackson.databind.node.ObjectNode;


@NullMarked
public final class DefinitionHandler {

    private final Workspace workspace;
    private final SymbolFinder symbols;

    private final Logger log;

    public DefinitionHandler(Workspace workspace, SymbolFinder symbols, Logger log) {
        this.workspace = workspace;
        this.symbols = symbols;
        this.log = log;
    }

    public @Nullable ObjectNode handle(TextDocumentPosition params) {
        String uri = params.textDocument().uri();
        int line = params.position().line();
        int col = params.position().character();

        log.log("definition: " + uri + " " + line + ":" + col);

        PhpFile file = workspace.findFile(uri);
        if (file == null) {
            throw new AppException("file not found: " + uri);
        }

        PhpSymbol sym = symbols.resolveAt(file, line, col);
        if (!(sym instanceof PhpMethodVarUsage usage)) {
            return null;
        }

        PhpSymbol definition = usage.definition();
        if (definition == null) {
            return null;
        }

        Range range = definition.range();
        if (range == null) {
            return null;
        }

        return Json.location(definition.file().uri(), range);
    }
}
