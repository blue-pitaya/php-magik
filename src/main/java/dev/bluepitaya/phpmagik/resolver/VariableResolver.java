package dev.bluepitaya.phpmagik.resolver;

import dev.bluepitaya.phpmagik.Workspace;
import dev.bluepitaya.phpmagik.phpsymbol.PhpSymbol;
import dev.bluepitaya.phpmagik.phpsymbol.PhpVarDefinition;
import dev.bluepitaya.phpmagik.phpsymbol.PhpVarUsage;
import dev.bluepitaya.phpmagik.ts.Point;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;

/**
 * Pairs variable reads with the parameters and assignments that give them a
 * value, and answers what type a name holds at a point.
 *
 * <p>A scope here is a file plus an enclosing function - PHP has no block
 * scope, and a local in one function says nothing about a same-named local in
 * another.
 *
 * <p>Not everything that binds a variable is indexed as a definition: a
 * {@code foreach} value, a {@code catch} variable and a {@code global} are all
 * recorded as reads. For those, the earliest occurrence stands in for a
 * declaration, which is the best the index can do and what the editor wants
 * anyway - somewhere to jump to.
 */
@NullMarked
public final class VariableResolver {

    private final Workspace workspace;

    public VariableResolver(Workspace workspace) {
        this.workspace = workspace;
    }

    /**
     * The parameter or first assignment that gives {@code usage}'s variable a
     * value, or {@code null} for one nothing ever writes - {@code $this} among
     * them.
     */
    public @Nullable PhpVarDefinition definitionOf(PhpVarUsage usage) {
        return firstDefinition(usage.fileId(), usage.functionName(), usage.name());
    }

    /**
     * What a request about this variable anchors on, so that asking from a
     * reassignment or from the third read all answer the same: its definition,
     * or where it first appears when nothing defines it.
     */
    public PhpSymbol anchorOf(PhpVarDefinition def) {
        return anchor(def.fileId(), def.functionName(), def.name(), def);
    }

    public PhpSymbol anchorOf(PhpVarUsage usage) {
        return anchor(usage.fileId(), usage.functionName(), usage.name(), usage);
    }

    /**
     * Every occurrence of that variable in its scope, in source order. The one
     * {@link #anchorOf} picks stands for the declaration and is included only
     * when {@code includeDecl}; a later assignment is an occurrence like any
     * other, since a reference request asks where a variable is used rather
     * than where it is written.
     */
    public List<PhpSymbol> occurrencesOf(PhpVarDefinition def, boolean includeDecl) {
        return occurrences(def.fileId(), def.functionName(), def.name(), def, includeDecl);
    }

    public List<PhpSymbol> occurrencesOf(PhpVarUsage usage, boolean includeDecl) {
        return occurrences(usage.fileId(), usage.functionName(), usage.name(), usage, includeDecl);
    }

    /**
     * The type {@code name} holds at {@code at}: whatever the latest definition
     * at or before it says, {@code null} if none of them says anything.
     */
    public @Nullable String typeAt(int fileId, @Nullable String functionName, String name,
                                   Point at) {
        PhpVarDefinition latest = null;
        for (PhpVarDefinition def : workspace.varDefinitions()) {
            if (def.type() == null || !def.name().equals(name)) continue;
            if (!inSameScope(fileId, functionName, def.fileId(), def.functionName())) continue;
            if (def.range().start().compareTo(at) > 0) continue;
            if (latest == null || def.range().start().compareTo(latest.range().start()) > 0) {
                latest = def;
            }
        }
        return latest == null ? null : latest.type();
    }

    /** Whatever type is known for {@code name} anywhere in that scope. */
    public @Nullable String typeAnywhere(int fileId, @Nullable String functionName, String name) {
        return typeAt(fileId, functionName, name,
                new Point(Integer.MAX_VALUE, Integer.MAX_VALUE));
    }

    /**
     * Every variable that scope gives a value to, deduped by name, keeping the
     * first. A variable only ever read is not one of them - nothing says what it
     * would hold.
     */
    public List<PhpVarDefinition> inScope(int fileId, @Nullable String functionName) {
        var seen = new LinkedHashSet<String>();
        var found = new ArrayList<PhpVarDefinition>();
        for (PhpVarDefinition def : workspace.varDefinitions()) {
            if (!inSameScope(fileId, functionName, def.fileId(), def.functionName())) continue;
            if (seen.add(def.name())) found.add(def);
        }
        return found;
    }

    private PhpSymbol anchor(int fileId, @Nullable String functionName, String name,
                             PhpSymbol fallback) {
        PhpVarDefinition defined = firstDefinition(fileId, functionName, name);
        if (defined != null) return defined;

        List<PhpSymbol> all = occurrences(fileId, functionName, name, null, true);
        return all.isEmpty() ? fallback : all.get(0);
    }

    private @Nullable PhpVarDefinition firstDefinition(int fileId, @Nullable String functionName,
                                                       String name) {
        PhpVarDefinition first = null;
        for (PhpVarDefinition def : workspace.varDefinitions()) {
            if (!def.name().equals(name)) continue;
            if (!inSameScope(fileId, functionName, def.fileId(), def.functionName())) continue;
            if (first == null || def.range().start().compareTo(first.range().start()) < 0) {
                first = def;
            }
        }
        return first;
    }

    private List<PhpSymbol> occurrences(int fileId, @Nullable String functionName, String name,
                                        @Nullable PhpSymbol asked, boolean includeDecl) {
        var found = new ArrayList<PhpSymbol>();
        for (PhpVarDefinition def : workspace.varDefinitions()) {
            if (def.name().equals(name)
                    && inSameScope(fileId, functionName, def.fileId(), def.functionName())) {
                found.add(def);
            }
        }
        for (PhpVarUsage usage : workspace.varUsages()) {
            if (usage.name().equals(name)
                    && inSameScope(fileId, functionName, usage.fileId(), usage.functionName())) {
                found.add(usage);
            }
        }
        /* the two lists are each in source order but interleave, and a read can
         * come before the first write */
        found.sort(Comparator.comparing(symbol -> symbol.range().start()));

        if (!includeDecl && asked != null) {
            found.remove(anchor(fileId, functionName, name, asked));
        }
        return found;
    }

    private static boolean inSameScope(int fileId, @Nullable String functionName,
                                       int otherFileId, @Nullable String otherFunctionName) {
        return fileId == otherFileId && Objects.equals(functionName, otherFunctionName);
    }
}
