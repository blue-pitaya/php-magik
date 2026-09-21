package dev.bluepitaya.phpmagik;

import dev.bluepitaya.phpmagik.phpsymbol.PhpClassDefinition;
import dev.bluepitaya.phpmagik.phpsymbol.PhpSymbol;
import dev.bluepitaya.phpmagik.phpsymbol.PhpUseStatement;
import dev.bluepitaya.phpmagik.phpsymbol.UseKind;
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
        collectIn(file, workspace.varDefinitions(), found);
        collectIn(file, workspace.varUsages(), found);
        collectIn(file, workspace.functions(), found);
        collectIn(file, workspace.functionUsages(), found);
        collectIn(file, workspace.methods(), found);
        collectIn(file, workspace.methodUsages(), found);
        collectIn(file, workspace.properties(), found);
        collectIn(file, workspace.propertyUsages(), found);
        collectIn(file, workspace.classes(), found);
        collectIn(file, workspace.classUsages(), found);
        found.addAll(file.uses());
        return found;
    }

    private static void collectIn(PhpFile file, List<? extends PhpSymbol> symbols,
                                  List<PhpSymbol> into) {
        for (PhpSymbol symbol : symbols) {
            if (symbol.fileId() == file.fileId()) into.add(symbol);
        }
    }

    public @Nullable String resolveType(int fileId, @Nullable String ns, @Nullable String type) {
        if (type == null || type.isEmpty()) return type;

        String prefix = type.startsWith("?") ? "?" : "";
        String name = type.substring(prefix.length());
        if (name.startsWith("\\")) return prefix + name.substring(1);
        if (RESERVED_TYPES.contains(name.toLowerCase())) return type;

        /* only the first segment can be an alias: "NS\A" with "use App\NS" */
        int sep = name.indexOf('\\');
        String head = sep < 0 ? name : name.substring(0, sep);
        String tail = sep < 0 ? "" : name.substring(sep);
        for (PhpUseStatement use : workspace.file(fileId).uses()) {
            if (use.kind() == UseKind.CLASS && use.alias().equals(head)) {
                return prefix + use.fqn() + tail;
            }
        }

        if (ns == null) return type;
        String qualified = ns + "\\" + name;
        return declaresClass(qualified) ? prefix + qualified : type;
    }

    private boolean declaresClass(String fqn) {
        for (PhpClassDefinition declared : workspace.classes()) {
            if (declared.fqn().equals(fqn)) return true;
        }
        return false;
    }
}
