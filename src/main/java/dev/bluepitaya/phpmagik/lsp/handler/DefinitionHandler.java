package dev.bluepitaya.phpmagik.lsp.handler;

import dev.bluepitaya.phpmagik.*;
import dev.bluepitaya.phpmagik.phpsymbol.*;
import dev.bluepitaya.phpmagik.resolver.*;
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
    private final VariableResolver variables;
    private final FunctionResolver functions;
    private final MethodResolver methods;
    private final PropertyResolver properties;
    private final Logger log;

    public DefinitionHandler(Workspace app, SymbolFinder symbols, VariableResolver variables,
                             FunctionResolver functions, MethodResolver methods,
                             PropertyResolver properties, Logger log) {
        this.app = app;
        this.symbols = symbols;
        this.variables = variables;
        this.functions = functions;
        this.methods = methods;
        this.properties = properties;
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

        /* a declaration is its own definition: jumping from one is a no-op that
         * still tells the editor it found something */
        if (sym instanceof PhpMethodDefinition method) {
            return locationOf(method);
        } else if (sym instanceof PhpMethodUsage usage) {
            PhpMethodDefinition declared = methods.definitionOf(usage);
            if (declared != null) return locationOf(declared);
        } else if (sym instanceof PhpFunctionDefinition func) {
            return locationOf(func);
        } else if (sym instanceof PhpFunctionUsage usage) {
            PhpFunctionDefinition declared = functions.definitionOf(usage);
            if (declared != null) return locationOf(declared);
        } else if (sym instanceof PhpPropertyDefinition property) {
            return locationOf(property);
        } else if (sym instanceof PhpPropertyUsage usage) {
            PhpPropertyDefinition declared = properties.definitionOf(usage);
            if (declared != null) return locationOf(declared);
        } else if (sym instanceof PhpVarDefinition def) {
            /* from a reassignment, back to where the variable starts */
            return locationOf(variables.anchorOf(def));
        } else if (sym instanceof PhpVarUsage usage) {
            PhpVarDefinition declared = variables.definitionOf(usage);
            /* nothing writes $this, a foreach value or a catch variable, so the
             * best a jump can do for those is where the variable first appears */
            return locationOf(declared != null ? declared : variables.anchorOf(usage));
        }

        return null;
    }

    private ObjectNode locationOf(PhpSymbol sym) {
        return Json.location(app.file(sym.fileId()).uri(), sym.range());
    }
}
