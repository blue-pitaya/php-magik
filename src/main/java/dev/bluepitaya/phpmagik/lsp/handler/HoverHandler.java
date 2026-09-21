package dev.bluepitaya.phpmagik.lsp.handler;

import dev.bluepitaya.phpmagik.index.*;
import dev.bluepitaya.phpmagik.lsp.Logger;
import dev.bluepitaya.phpmagik.lsp.dto.*;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

@NullMarked
public final class HoverHandler {

    private final Workspace app;
    private final SymbolFinder symbols;
    private final Logger log;

    public HoverHandler(Workspace app, SymbolFinder symbols, Logger log) {
        this.app = app;
        this.symbols = symbols;
        this.log = log;
    }

    public @Nullable Hover handle(TextDocumentPosition params) {
        TextDocumentIdentifier td = params.textDocument();
        Position pos = params.position();
        String uri = td.uri();
        int line = pos.line();
        int col = pos.character();
        log.log("hover: " + uri + " " + line + ":" + col);

        PhpFile file = app.findFile(uri);
        if (file == null) return null;

        PhpSymbol sym = symbols.resolveAt(file, line, col);
        String text = null;
        if (sym instanceof PhpVar var) {
            String type = var.type() != null ? var.type() : symbols.latestVarType(var);
            /* $this and any unresolved local have no type to show */
            text = type != null
                    ? "```php\n" + var.name() + ": " + type + "\n```"
                    : "```php\n" + var.name() + "\n```";
        } else if (sym instanceof PhpFunction func) {
            PhpFunction def = symbols.findFuncDef(func);
            String ret = func.returnType();
            if (ret == null && def != null) {
                ret = def.returnType();
            }
            text = ret != null
                    ? "```php\nfunction " + func.name() + "(): " + ret + "\n```"
                    : "```php\nfunction " + func.name() + "()\n```";

            String doc = def != null ? symbols.docComment(def) : null;
            if (doc != null) {
                text += "\n\n---\n\n" + doc.replace("\n", "  \n");
            }
        }
        if (text == null) return null;

        return new Hover(new MarkupContent("markdown", text));
    }
}
