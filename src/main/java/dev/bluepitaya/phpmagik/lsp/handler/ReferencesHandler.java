package dev.bluepitaya.phpmagik.lsp.handler;

import dev.bluepitaya.phpmagik.*;
import dev.bluepitaya.phpmagik.phpsymbol.*;
import dev.bluepitaya.phpmagik.resolver.*;
import dev.bluepitaya.phpmagik.lsp.Json;
import dev.bluepitaya.phpmagik.lsp.Logger;
import dev.bluepitaya.phpmagik.lsp.dto.*;
import org.jspecify.annotations.NullMarked;
import tools.jackson.databind.node.ArrayNode;

import java.util.List;

@NullMarked
public final class ReferencesHandler {

    private final Workspace app;
    private final SymbolFinder symbols;
    private final VariableResolver variables;
    private final FunctionResolver functions;
    private final MethodResolver methods;
    private final PropertyResolver properties;
    private final ClassResolver classes;
    private final Logger log;

    public ReferencesHandler(Workspace app, SymbolFinder symbols, VariableResolver variables,
                             FunctionResolver functions, MethodResolver methods,
                             PropertyResolver properties, ClassResolver classes, Logger log) {
        this.app = app;
        this.symbols = symbols;
        this.variables = variables;
        this.functions = functions;
        this.methods = methods;
        this.properties = properties;
        this.classes = classes;
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

        if (sym instanceof PhpMethodDefinition method) {
            collect(methods.usagesOf(method, includeDecl), locs);
        } else if (sym instanceof PhpMethodUsage usage) {
            PhpMethodDefinition declared = methods.definitionOf(usage);
            if (declared != null) collect(methods.usagesOf(declared, includeDecl), locs);
        } else if (sym instanceof PhpFunctionDefinition func) {
            collect(functions.usagesOf(func, includeDecl), locs);
        } else if (sym instanceof PhpFunctionUsage usage) {
            PhpFunctionDefinition declared = functions.definitionOf(usage);
            if (declared != null) collect(functions.usagesOf(declared, includeDecl), locs);
        } else if (sym instanceof PhpPropertyDefinition property) {
            collect(properties.usagesOf(property, includeDecl), locs);
        } else if (sym instanceof PhpPropertyUsage usage) {
            PhpPropertyDefinition declared = properties.definitionOf(usage);
            if (declared != null) collect(properties.usagesOf(declared, includeDecl), locs);
        } else if (sym instanceof PhpClassDefinition cls) {
            collect(classes.usagesOf(cls, includeDecl), locs);
        } else if (sym instanceof PhpClassUsage usage) {
            PhpClassDefinition declared = classes.definitionOf(usage);
            if (declared != null) collect(classes.usagesOf(declared, includeDecl), locs);
        } else if (sym instanceof PhpVarDefinition def) {
            collect(variables.occurrencesOf(def, includeDecl), locs);
        } else if (sym instanceof PhpVarUsage usage) {
            collect(variables.occurrencesOf(usage, includeDecl), locs);
        }

        return locs;
    }

    private static void collect(List<? extends PhpSymbol> refs, ArrayNode locs) {
        for (PhpSymbol ref : refs) {
            locs.add(Json.location(ref.file().uri(), ref.range()));
        }
    }
}
