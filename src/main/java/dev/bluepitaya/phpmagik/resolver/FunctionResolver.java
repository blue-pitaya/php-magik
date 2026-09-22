package dev.bluepitaya.phpmagik.resolver;

import dev.bluepitaya.phpmagik.Workspace;
import dev.bluepitaya.phpmagik.phpsymbol.PhpFunctionDefinition;
import dev.bluepitaya.phpmagik.phpsymbol.PhpFunctionUsage;
import dev.bluepitaya.phpmagik.phpsymbol.PhpSymbol;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

@NullMarked
public final class FunctionResolver {

    private final Workspace workspace;

    public FunctionResolver(Workspace workspace) {
        this.workspace = workspace;
    }

    public @Nullable PhpFunctionDefinition definitionOf(PhpFunctionUsage usage) {
        for (PhpFunctionDefinition declared : workspace.symbols().functions()) {
            if (declared.name().equals(usage.name())) return declared;
        }
        return null;
    }

    public List<PhpSymbol> usagesOf(PhpFunctionDefinition def, boolean includeDecl) {
        var found = new ArrayList<PhpSymbol>();
        if (includeDecl) found.add(def);
        for (PhpFunctionUsage usage : workspace.symbols().functionUsages()) {
            if (usage.name().equals(def.name())) found.add(usage);
        }
        return found;
    }
}
