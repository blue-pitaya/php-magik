package dev.bluepitaya.phpmagik.lsp.handler;

import dev.bluepitaya.phpmagik.AppException;
import dev.bluepitaya.phpmagik.PhpFile;
import dev.bluepitaya.phpmagik.SymbolFinder;
import dev.bluepitaya.phpmagik.Workspace;
import dev.bluepitaya.phpmagik.lsp.Json;
import dev.bluepitaya.phpmagik.lsp.Logger;
import dev.bluepitaya.phpmagik.lsp.dto.Position;
import dev.bluepitaya.phpmagik.lsp.dto.ReferenceContext;
import dev.bluepitaya.phpmagik.lsp.dto.ReferenceParams;
import dev.bluepitaya.phpmagik.lsp.dto.TextDocumentIdentifier;
import dev.bluepitaya.phpmagik.phpsymbol.PhpMethodLocalVarDeclaration;
import dev.bluepitaya.phpmagik.phpsymbol.PhpMethodVarUsage;
import dev.bluepitaya.phpmagik.phpsymbol.PhpParameterDeclaration;
import dev.bluepitaya.phpmagik.phpsymbol.PhpSymbol;
import dev.bluepitaya.phpmagik.ts.Range;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;
import tools.jackson.databind.node.ArrayNode;


@NullMarked
public final class ReferencesHandler {

    private final Workspace workspace;
    private final SymbolFinder symbols;

    private final Logger log;

    public ReferencesHandler(Workspace workspace, SymbolFinder symbols, Logger log) {
        this.workspace = workspace;
        this.symbols = symbols;
        this.log = log;
    }

    public ArrayNode handle(ReferenceParams params) {
        ArrayNode locations = Json.array();

        TextDocumentIdentifier document = params.textDocument();
        Position position = params.position();
        if (document == null || position == null) {
            return locations;
        }

        String uri = document.uri();
        int line = position.line();
        int col = position.character();

        log.log("references: " + uri + " " + line + ":" + col);

        PhpFile file = workspace.findFile(uri);
        if (file == null) {
            throw new AppException("file not found: " + uri);
        }

        PhpSymbol definition = definitionAt(file, line, col);
        if (definition == null) {
            return locations;
        }

        ReferenceContext context = params.context();
        if (context != null && context.includeDeclaration()) {
            add(locations, definition);
        }

        for (PhpMethodVarUsage usage : workspace.symbols().varUsages()) {
            if (usage.definition() == definition) {
                add(locations, usage);
            }
        }

        return locations;
    }

    private @Nullable PhpSymbol definitionAt(PhpFile file, int line, int col) {
        PhpSymbol sym = symbols.resolveAt(file, line, col);

        return switch (sym) {
            case PhpMethodVarUsage usage -> usage.definition();
            case PhpMethodLocalVarDeclaration declaration -> declaration;
            case PhpParameterDeclaration declaration -> declaration;
            case null, default -> null;
        };
    }

    private static void add(ArrayNode locations, PhpSymbol symbol) {
        Range range = symbol.range();
        if (range == null) {
            return;
        }

        locations.add(Json.location(symbol.file().uri(), range));
    }
}
