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
 * the class the object resolved to, {@link PhpPropertyDefinition} knows the
 * class that declares it - and this is what joins them, so a hover can show the
 * declared type and go-to-definition can jump to the declaration.
 *
 * <p>The join is by simple class name, not by fully qualified name, because that
 * is all a call site records: two same-named classes in different namespaces
 * still resolve to whichever the workspace indexed first.
 */
@NullMarked
public final class PropertyResolver {

    private final Workspace workspace;

    public PropertyResolver(Workspace workspace) {
        this.workspace = workspace;
    }

    /**
     * The declaration {@code usage} reads, or {@code null} when the class it was
     * read from declares no such property - an inherited or promoted one, or a
     * class the index has not seen.
     */
    public @Nullable PhpPropertyDefinition definitionOf(PhpPropertyUsage usage) {
        for (PhpPropertyDefinition declared : workspace.properties()) {
            if (declared.name().equals(usage.name())
                    && declared.owner().name().equals(usage.className())) {
                return declared;
            }
        }
        return null;
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
            if (usage.name().equals(def.name())
                    && usage.className().equals(def.owner().name())) {
                found.add(usage);
            }
        }
        return found;
    }

    /** Every property {@code cls} declares, from anywhere in the workspace. */
    public List<PhpPropertyDefinition> declaredIn(String cls) {
        var found = new ArrayList<PhpPropertyDefinition>();
        for (PhpPropertyDefinition declared : workspace.properties()) {
            if (declared.owner().name().equals(cls)) found.add(declared);
        }
        return found;
    }
}
