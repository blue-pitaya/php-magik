package dev.bluepitaya.phpmagik.resolver;

import dev.bluepitaya.phpmagik.Workspace;
import dev.bluepitaya.phpmagik.phpsymbol.PhpFunctionDefinition;
import dev.bluepitaya.phpmagik.phpsymbol.PhpFunctionUsage;
import dev.bluepitaya.phpmagik.phpsymbol.PhpSymbol;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * Pairs calls by name with the plain functions they call.
 *
 * <p>The join is by bare name: PHP resolves an unqualified call against the
 * current namespace and then falls back to the global one, and the index
 * records neither, so two same-named functions in different namespaces resolve
 * to whichever the workspace indexed first.
 */
@NullMarked
public final class FunctionResolver {

    private final Workspace workspace;

    public FunctionResolver(Workspace workspace) {
        this.workspace = workspace;
    }

    /** The declaration {@code usage} calls, or {@code null} if nothing indexed declares it. */
    public @Nullable PhpFunctionDefinition definitionOf(PhpFunctionUsage usage) {
        for (PhpFunctionDefinition declared : workspace.functions()) {
            if (declared.name().equals(usage.name())) return declared;
        }
        return null;
    }

    /**
     * Every call {@link #definitionOf} would resolve to {@code def}, from
     * anywhere in the workspace and in index order, with the declaration itself
     * first when {@code includeDecl}.
     */
    public List<PhpSymbol> usagesOf(PhpFunctionDefinition def, boolean includeDecl) {
        var found = new ArrayList<PhpSymbol>();
        if (includeDecl) found.add(def);
        for (PhpFunctionUsage usage : workspace.functionUsages()) {
            if (usage.name().equals(def.name())) found.add(usage);
        }
        return found;
    }
}
