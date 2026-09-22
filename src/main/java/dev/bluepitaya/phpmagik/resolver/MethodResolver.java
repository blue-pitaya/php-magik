package dev.bluepitaya.phpmagik.resolver;

import dev.bluepitaya.phpmagik.Workspace;
import dev.bluepitaya.phpmagik.phpsymbol.PhpMethodDefinition;
import dev.bluepitaya.phpmagik.phpsymbol.PhpMethodUsage;
import dev.bluepitaya.phpmagik.phpsymbol.PhpSymbol;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

@NullMarked
public final class MethodResolver {

    private final Workspace workspace;
    private final TypeInference types;

    public MethodResolver(Workspace workspace, TypeInference types) {
        this.workspace = workspace;
        this.types = types;
    }

    public @Nullable PhpMethodDefinition definitionOf(PhpMethodUsage usage) {
        return types.declarationOf(usage);
    }

    public List<PhpSymbol> usagesOf(PhpMethodDefinition def, boolean includeDecl) {
        var found = new ArrayList<PhpSymbol>();
        if (includeDecl) found.add(def);
        for (PhpMethodUsage usage : workspace.symbols().methodUsages()) {
            /* the name first: it rules out nearly everything, and what the object
             * is takes real work to answer */
            if (usage.name().equals(def.name())
                    && def.owner().name().equals(types.classOf(usage))) {
                found.add(usage);
            }
        }
        return found;
    }
}
