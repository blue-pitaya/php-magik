package dev.bluepitaya.phpmagik.resolver;

import dev.bluepitaya.phpmagik.Workspace;
import dev.bluepitaya.phpmagik.phpsymbol.PhpMethodDefinition;
import dev.bluepitaya.phpmagik.phpsymbol.PhpMethodUsage;
import dev.bluepitaya.phpmagik.phpsymbol.PhpSymbol;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * Pairs method calls with the declarations they invoke.
 *
 * <p>The index records the two apart - {@link PhpMethodUsage} knows a name and
 * the class the object resolved to, {@link PhpMethodDefinition} knows the class
 * that declares it - and this is what joins them, so a hover can show the
 * signature and its doc block and go-to-definition can jump to the declaration.
 *
 * <p>The join is by simple class name, not by fully qualified name, because that
 * is all a call site records: two same-named classes in different namespaces
 * still resolve to whichever the workspace indexed first.
 */
@NullMarked
public final class MethodResolver {

    private final Workspace workspace;

    public MethodResolver(Workspace workspace) {
        this.workspace = workspace;
    }

    /**
     * The declaration {@code usage} calls, or {@code null} when the class it was
     * called on declares no such method - an inherited or magic one, or a class
     * the index has not seen.
     */
    public @Nullable PhpMethodDefinition definitionOf(PhpMethodUsage usage) {
        for (PhpMethodDefinition declared : workspace.methods()) {
            if (declared.name().equals(usage.name())
                    && declared.owner().name().equals(usage.className())) {
                return declared;
            }
        }
        return null;
    }

    /**
     * Every call {@link #definitionOf} would resolve to {@code def}, from
     * anywhere in the workspace and in index order, with the declaration itself
     * first when {@code includeDecl}.
     */
    public List<PhpSymbol> usagesOf(PhpMethodDefinition def, boolean includeDecl) {
        var found = new ArrayList<PhpSymbol>();
        if (includeDecl) found.add(def);
        for (PhpMethodUsage usage : workspace.methodUsages()) {
            if (usage.name().equals(def.name())
                    && usage.className().equals(def.owner().name())) {
                found.add(usage);
            }
        }
        return found;
    }

    /** Every method {@code cls} declares, from anywhere in the workspace. */
    public List<PhpMethodDefinition> declaredIn(String cls) {
        var found = new ArrayList<PhpMethodDefinition>();
        for (PhpMethodDefinition declared : workspace.methods()) {
            if (declared.owner().name().equals(cls)) found.add(declared);
        }
        return found;
    }
}
