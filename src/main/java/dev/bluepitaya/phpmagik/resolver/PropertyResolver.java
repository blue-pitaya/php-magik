package dev.bluepitaya.phpmagik.resolver;

import dev.bluepitaya.phpmagik.Workspace;
import dev.bluepitaya.phpmagik.phpsymbol.PhpPropertyDefinition;
import dev.bluepitaya.phpmagik.phpsymbol.PhpPropertyUsage;
import dev.bluepitaya.phpmagik.phpsymbol.PhpSymbol;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * Pairs property accesses with the declarations they read.
 *
 * <p>The index records the two apart - {@link PhpPropertyUsage} knows a name and
 * where its object came from, {@link PhpPropertyDefinition} knows the class that
 * declares it - and this is what joins them, so a hover can show the declared
 * type and go-to-definition can jump to the declaration.
 *
 * <p>Which class an access is on is {@link TypeInference}'s answer, since
 * reaching it can mean following the object back through other definitions and
 * other files. The join itself is by simple class name, not by fully qualified
 * name, because that is all an access has to go on: two same-named classes in
 * different namespaces still resolve to whichever the workspace indexed first.
 */
@NullMarked
public final class PropertyResolver {

    private final Workspace workspace;
    private final TypeInference types;

    public PropertyResolver(Workspace workspace, TypeInference types) {
        this.workspace = workspace;
        this.types = types;
    }

    /**
     * The declaration {@code usage} reads, or {@code null} when its object has no
     * class that can be named, or that class declares no such property - an
     * inherited one, or a class the index has not seen.
     */
    public @Nullable PhpPropertyDefinition definitionOf(PhpPropertyUsage usage) {
        return types.declarationOf(usage);
    }

    /**
     * Every usage {@link #definitionOf} would resolve to {@code def}, from
     * anywhere in the workspace and in index order, with the declaration itself
     * first when {@code includeDecl}.
     */
    public List<PhpSymbol> usagesOf(PhpPropertyDefinition def, boolean includeDecl) {
        var found = new ArrayList<PhpSymbol>();
        if (includeDecl) found.add(def);
        for (PhpPropertyUsage usage : workspace.propertyUsages()) {
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
