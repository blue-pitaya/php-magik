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

@NullMarked
public final class ClassResolver {

    private final Workspace workspace;
    private final SymbolFinder symbols;

    public ClassResolver(Workspace workspace, SymbolFinder symbols) {
        this.workspace = workspace;
        this.symbols = symbols;
    }

    public @Nullable PhpClassDefinition definitionOf(PhpClassUsage usage) {
        String fqn = fqnOf(usage);
        String simple = simpleName(fqn);

        PhpClassDefinition sameName = null;
        for (PhpClassDefinition declared : workspace.symbols().classes()) {
            if (declared.fqn().equals(fqn)) return declared;
            if (sameName == null && declared.name().equals(simple)) sameName = declared;
        }
        return sameName;
    }

    public List<PhpSymbol> usagesOf(PhpClassDefinition def, boolean includeDecl) {
        var found = new ArrayList<PhpSymbol>();
        if (includeDecl) found.add(def);
        for (PhpClassUsage usage : workspace.symbols().classUsages()) {
            /* asked the same way round as a jump would ask it, so that what the
             * references list holds is exactly what resolves here */
            if (def.equals(definitionOf(usage))) found.add(usage);
        }
        return found;
    }

    private String fqnOf(PhpClassUsage usage) {
        String resolved = symbols.resolveType(usage.file(), usage.ns(), usage.name());
        return resolved == null ? usage.name() : resolved;
    }

    private static String simpleName(String fqn) {
        int last = fqn.lastIndexOf('\\');
        return last < 0 ? fqn : fqn.substring(last + 1);
    }
}
