package dev.bluepitaya.phpmagik;

import dev.bluepitaya.phpmagik.phpsymbol.PhpClass;
import dev.bluepitaya.phpmagik.phpsymbol.PhpFunctionDefinition;
import dev.bluepitaya.phpmagik.phpsymbol.PhpMethodDefinition;
import dev.bluepitaya.phpmagik.phpsymbol.PhpSymbol;
import dev.bluepitaya.phpmagik.phpsymbol.PhpUseStatement;
import dev.bluepitaya.phpmagik.phpsymbol.UseKind;
import dev.bluepitaya.phpmagik.ts.Point;
import dev.bluepitaya.phpmagik.ts.Range;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Answers what is written where: the symbol at a position, the declaration
 * enclosing one, and what a type name written there refers to.
 *
 * <p>Pairing a usage with its declaration is a resolver's job, in the
 * {@code resolver} package.
 */
@NullMarked
public final class SymbolFinder {

    /** Never namespaced, so they must survive {@link #resolveType} untouched. */
    private static final Set<String> RESERVED_TYPES = Set.of(
            "int", "float", "string", "bool", "array", "object", "mixed", "callable",
            "iterable", "void", "null", "never", "false", "true", "self", "static",
            "parent");

    private final Workspace workspace;

    public SymbolFinder(Workspace ws) {
        this.workspace = ws;
    }

    /**
     * The symbol written at that position, or {@code null} if nothing indexed
     * covers it. Ranges nest - a use clause spans the name inside it - so the
     * innermost match is the one under the cursor.
     *
     * <p>Purely an index lookup: the syntax tree is the indexer's business, and
     * a symbol that is not in the index cannot be found here however well the
     * tree describes it.
     */
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
        found.addAll(file.uses());
        return found;
    }

    private static void collectIn(PhpFile file, List<? extends PhpSymbol> symbols,
                                  List<PhpSymbol> into) {
        for (PhpSymbol symbol : symbols) {
            if (symbol.fileId() == file.fileId()) into.add(symbol);
        }
    }

    /**
     * The function or method whose declaration encloses {@code at} - the scope a
     * variable written there belongs to - or {@code null} at file scope. A name
     * rather than a symbol, since that is what the variable index is keyed by,
     * and the two kinds of declaration are indexed apart.
     */
    public @Nullable String enclosingFunctionName(int fileId, Point at) {
        Range innermost = null;
        String name = null;

        for (PhpFunctionDefinition f : workspace.functions()) {
            if (f.fileId() != fileId || !f.scope().contains(at)) continue;
            if (innermost == null || f.scope().isWithin(innermost)) {
                innermost = f.scope();
                name = f.name();
            }
        }
        for (PhpMethodDefinition m : workspace.methods()) {
            if (m.fileId() != fileId || !m.scope().contains(at)) continue;
            if (innermost == null || m.scope().isWithin(innermost)) {
                innermost = m.scope();
                name = m.name();
            }
        }
        return name;
    }

    /** The class, interface, trait or enum whose declaration encloses {@code at}. */
    public @Nullable PhpClass enclosingClass(int fileId, Point at) {
        PhpClass found = null;
        for (PhpClass c : workspace.classes()) {
            if (c.fileId() != fileId || !c.scope().contains(at)) continue;
            if (found == null || c.scope().isWithin(found.scope())) found = c;
        }
        return found;
    }

    /**
     * A type as written, resolved to a fully qualified name: {@code A} becomes
     * {@code App\NSA\A} in a file that says {@code use App\NSA\A;}, and an alias
     * expands to what it aliases. A leading {@code \} means it already is
     * qualified, a reserved name is left alone, and an unimported name takes the
     * file's own namespace only when that names a class the index has seen -
     * otherwise it stays as written rather than becoming a plausible lie.
     *
     * <p>{@code ?} is preserved, so nullability still shows in a hover.
     */
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
        for (PhpClass declared : workspace.classes()) {
            if (declared.fqn().equals(fqn)) return true;
        }
        return false;
    }

    /** {@code ?Foo}, {@code \Foo} become {@code Foo}. */
    public static @Nullable String stripNs(@Nullable String t) {
        if (t == null) return null;
        var i = 0;
        while (i < t.length() && (t.charAt(i) == '?' || t.charAt(i) == '\\')) {
            i++;
        }
        return t.substring(i);
    }
}
