package dev.bluepitaya.phpmagik.resolver;

import dev.bluepitaya.phpmagik.SymbolFinder;
import dev.bluepitaya.phpmagik.Workspace;
import dev.bluepitaya.phpmagik.phpsymbol.PhpClassDefinition;
import dev.bluepitaya.phpmagik.phpsymbol.PhpClassUsage;
import dev.bluepitaya.phpmagik.phpsymbol.PhpSymbol;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * Pairs references to a class with the declaration they name.
 *
 * <p>A reference records the name as written, which may be an alias or a
 * qualified name, so it is put through the file's {@code use} statements first
 * and matched on the fully qualified name - which is what
 * {@link PhpClassDefinition#fqn()} is for. A name that resolves to nothing, an
 * unimported class in another namespace, falls back to matching the simple name,
 * and then two same-named classes resolve to whichever the workspace indexed
 * first.
 */
@NullMarked
public final class ClassResolver {

    private final Workspace workspace;
    private final SymbolFinder symbols;

    public ClassResolver(Workspace workspace, SymbolFinder symbols) {
        this.workspace = workspace;
        this.symbols = symbols;
    }

    /** The declaration {@code usage} names, or {@code null} if nothing indexed declares it. */
    public @Nullable PhpClassDefinition definitionOf(PhpClassUsage usage) {
        String fqn = fqnOf(usage);
        String simple = simpleName(fqn);

        PhpClassDefinition sameName = null;
        for (PhpClassDefinition declared : workspace.classes()) {
            if (declared.fqn().equals(fqn)) return declared;
            if (sameName == null && declared.name().equals(simple)) sameName = declared;
        }
        return sameName;
    }

    /**
     * Every reference {@link #definitionOf} would resolve to {@code def}, from
     * anywhere in the workspace and in index order, with the declaration itself
     * first when {@code includeDecl}.
     */
    public List<PhpSymbol> usagesOf(PhpClassDefinition def, boolean includeDecl) {
        var found = new ArrayList<PhpSymbol>();
        if (includeDecl) found.add(def);
        for (PhpClassUsage usage : workspace.classUsages()) {
            /* asked the same way round as a jump would ask it, so that what the
             * references list holds is exactly what resolves here */
            if (def.equals(definitionOf(usage))) found.add(usage);
        }
        return found;
    }

    private String fqnOf(PhpClassUsage usage) {
        String resolved = symbols.resolveType(usage.fileId(), usage.ns(), usage.name());
        return resolved == null ? usage.name() : resolved;
    }

    private static String simpleName(String fqn) {
        int last = fqn.lastIndexOf('\\');
        return last < 0 ? fqn : fqn.substring(last + 1);
    }
}
