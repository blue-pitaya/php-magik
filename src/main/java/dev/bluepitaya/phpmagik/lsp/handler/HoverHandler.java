package dev.bluepitaya.phpmagik.lsp.handler;

import dev.bluepitaya.phpmagik.AppException;
import dev.bluepitaya.phpmagik.PhpFile;
import dev.bluepitaya.phpmagik.SymbolFinder;
import dev.bluepitaya.phpmagik.Workspace;
import dev.bluepitaya.phpmagik.lsp.Logger;
import dev.bluepitaya.phpmagik.lsp.dto.Hover;
import dev.bluepitaya.phpmagik.lsp.dto.MarkupContent;
import dev.bluepitaya.phpmagik.lsp.dto.TextDocumentPosition;
import dev.bluepitaya.phpmagik.phpsymbol.PhpMethodDeclaration;
import dev.bluepitaya.phpmagik.phpsymbol.PhpSymbol;
import dev.bluepitaya.phpmagik.resolver.PhpMethodDeclarationResolver;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;


@NullMarked
public final class HoverHandler {

    private final Workspace workspace;
    private final SymbolFinder symbols;

    private final Logger log;

    public HoverHandler(Workspace workspace, SymbolFinder symbols,
                        PhpMethodDeclarationResolver methodDeclarations, Logger log) {
        this.workspace = workspace;
        this.symbols = symbols;
        this.log = log;
    }

    public @Nullable Hover handle(TextDocumentPosition params) {
        String uri = params.textDocument().uri();
        int line = params.position().line();
        int col = params.position().character();

        log.log("hover: " + uri + " " + line + ":" + col);

        PhpFile file = workspace.findFile(uri);
        if (file == null) {
            throw new AppException("file not found: " + uri);
        }

        PhpSymbol sym = symbols.resolveAt(file, line, col);
        if (sym == null) {
            return null;
        }

        @Nullable String text = switch (sym) {
            case PhpMethodDeclaration method -> method.hover();
            default -> null;
        };
        if (text == null) return null;

        return new Hover(new MarkupContent("markdown", text));
    }
}
