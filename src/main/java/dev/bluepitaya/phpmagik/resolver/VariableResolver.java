package dev.bluepitaya.phpmagik.resolver;

import dev.bluepitaya.phpmagik.PhpFile;
import dev.bluepitaya.phpmagik.Workspace;
import dev.bluepitaya.phpmagik.phpsymbol.PhpSymbol;
import dev.bluepitaya.phpmagik.phpsymbol.PhpVarDefinition;
import dev.bluepitaya.phpmagik.phpsymbol.PhpVarUsage;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

@NullMarked
public final class VariableResolver {

    private final Workspace workspace;

    public VariableResolver(Workspace workspace) {
        this.workspace = workspace;
    }

    public @Nullable PhpVarDefinition definitionOf(PhpVarUsage usage) {
        return firstDefinition(usage.file(), usage.functionName(), usage.name());
    }

    public PhpSymbol anchorOf(PhpVarDefinition def) {
        return anchor(def.file(), def.functionName(), def.name(), def);
    }

    public PhpSymbol anchorOf(PhpVarUsage usage) {
        return anchor(usage.file(), usage.functionName(), usage.name(), usage);
    }

    public List<PhpSymbol> occurrencesOf(PhpVarDefinition def, boolean includeDecl) {
        return occurrences(def.file(), def.functionName(), def.name(), def, includeDecl);
    }

    public List<PhpSymbol> occurrencesOf(PhpVarUsage usage, boolean includeDecl) {
        return occurrences(usage.file(), usage.functionName(), usage.name(), usage, includeDecl);
    }

    private PhpSymbol anchor(PhpFile file, @Nullable String functionName, String name,
                             PhpSymbol fallback) {
        PhpVarDefinition defined = firstDefinition(file, functionName, name);
        if (defined != null) return defined;

        List<PhpSymbol> all = occurrences(file, functionName, name, null, true);
        return all.isEmpty() ? fallback : all.get(0);
    }

    private @Nullable PhpVarDefinition firstDefinition(PhpFile file, @Nullable String functionName,
                                                       String name) {
        PhpVarDefinition first = null;
        for (PhpVarDefinition def : workspace.symbols().varDefinitions()) {
            if (!def.name().equals(name)) continue;
            if (!inSameScope(file, functionName, def.file(), def.functionName())) continue;
            if (first == null || def.range().start().compareTo(first.range().start()) < 0) {
                first = def;
            }
        }
        return first;
    }

    private List<PhpSymbol> occurrences(PhpFile file, @Nullable String functionName, String name,
                                        @Nullable PhpSymbol asked, boolean includeDecl) {
        var found = new ArrayList<PhpSymbol>();
        for (PhpVarDefinition def : workspace.symbols().varDefinitions()) {
            if (def.name().equals(name)
                    && inSameScope(file, functionName, def.file(), def.functionName())) {
                found.add(def);
            }
        }
        for (PhpVarUsage usage : workspace.symbols().varUsages()) {
            if (usage.name().equals(name)
                    && inSameScope(file, functionName, usage.file(), usage.functionName())) {
                found.add(usage);
            }
        }
        /* the two lists are each in source order but interleave, and a read can
         * come before the first write */
        found.sort(Comparator.comparing(symbol -> symbol.range().start()));

        if (!includeDecl && asked != null) {
            found.remove(anchor(file, functionName, name, asked));
        }
        return found;
    }

    private static boolean inSameScope(PhpFile file, @Nullable String functionName,
                                       PhpFile otherFile, @Nullable String otherFunctionName) {
        return file == otherFile && Objects.equals(functionName, otherFunctionName);
    }
}
