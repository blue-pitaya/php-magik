package dev.bluepitaya.phpmagik;

import dev.bluepitaya.phpmagik.phpsymbol.PhpSymbol;
import dev.bluepitaya.phpmagik.ts.Point;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

@NullMarked
public final class SymbolFinder {

    private static final Set<String> RESERVED_TYPES = Set.of(
            "int", "float", "string", "bool", "array", "object", "mixed", "callable",
            "iterable", "void", "null", "never", "false", "true", "self", "static",
            "parent");

    private final Workspace workspace;

    public SymbolFinder(Workspace ws) {
        this.workspace = ws;
    }

    public @Nullable PhpSymbol resolveAt(PhpFile file, int line, int character) {
        Point at = new Point(line, character);

        PhpSymbol found = null;
        for (PhpSymbol sym : symbolsIn(file)) {
            if (!sym.range().contains(at)) continue;
            if (found == null || sym.range().isWithin(found.range())) found = sym;
        }
        return found;
    }

    private List<PhpSymbol> symbolsIn(PhpFile file) {
        var found = new ArrayList<PhpSymbol>();
        for (List<? extends PhpSymbol> symbols : workspace.symbols().lists()) {
            for (PhpSymbol symbol : symbols) {
                if (symbol.file() == file) found.add(symbol);
            }
        }
        return found;
    }
}
