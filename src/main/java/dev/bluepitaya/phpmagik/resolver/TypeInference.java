package dev.bluepitaya.phpmagik.resolver;

import dev.bluepitaya.phpmagik.Workspace;
import dev.bluepitaya.phpmagik.phpsymbol.PhpFunctionDefinition;
import dev.bluepitaya.phpmagik.phpsymbol.PhpFunctionUsage;
import dev.bluepitaya.phpmagik.phpsymbol.PhpMethodDefinition;
import dev.bluepitaya.phpmagik.phpsymbol.PhpMethodUsage;
import dev.bluepitaya.phpmagik.phpsymbol.PhpPropertyDefinition;
import dev.bluepitaya.phpmagik.phpsymbol.PhpPropertyUsage;
import dev.bluepitaya.phpmagik.phpsymbol.PhpSymbol;
import dev.bluepitaya.phpmagik.phpsymbol.PhpVarDefinition;
import dev.bluepitaya.phpmagik.phpsymbol.PhpVarUsage;
import dev.bluepitaya.phpmagik.ts.Point;
import dev.bluepitaya.phpmagik.ts.Range;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * What type a thing holds, worked out by following the index.
 *
 * <p>The indexer names the types it can see outright: a declared parameter or
 * property type, a literal, a {@code new X}, the result of an operator. What is
 * left over is a chain, and this walks it - a read to the definitions that give
 * it a value, a definition to whatever it was assigned from, a call to the
 * declaration it calls, a declaration to what it returns - until something along
 * the way names a type or the chain runs out.
 *
 * <p>Which class {@code $obj->foo} is on is the same question asked of the
 * object, so it is answered here too, and {@link #declarationOf} is what a
 * member access resolves through. That is what lets an access reach a
 * declaration in another file: the indexer only ever sees one.
 *
 * <p>Types come back as the source wrote them, so {@code A} rather than
 * {@code App\NSA\A}. Turning one into a qualified name needs the file's
 * {@code use} statements, which is {@code SymbolFinder.resolveType}'s job.
 *
 * <p>Nothing is cached. Every answer is only as good as the index it was read
 * from, and that is rebuilt for a file on each keystroke; a cache would have to
 * be invalidated exactly as often as it would be used.
 */
@NullMarked
public final class TypeInference {

    private final Workspace workspace;
    private final FunctionResolver functions;

    public TypeInference(Workspace workspace, FunctionResolver functions) {
        this.workspace = workspace;
        this.functions = functions;
    }

    /** The type a read holds: whatever the definitions before it work out to. */
    public @Nullable String typeOf(PhpVarUsage usage) {
        return typeOfVariable(usage.fileId(), usage.functionName(), usage.name(),
                usage.range().start(), new HashSet<>());
    }

    /** The type a parameter or assignment gives its variable. */
    public @Nullable String typeOf(PhpVarDefinition def) {
        return typeOf(def, new HashSet<>());
    }

    /** The declared type of the property this access reads. */
    public @Nullable String typeOf(PhpPropertyUsage usage) {
        return typeOfSymbol(usage, new HashSet<>());
    }

    public @Nullable String typeOf(PhpPropertyDefinition def) {
        return def.type();
    }

    /** What a call to this gets back: the declared type, or what it returns. */
    public @Nullable String returnTypeOf(PhpFunctionDefinition func) {
        return returnType(func.returnType(), func.fileId(), func.returnSource(), new HashSet<>());
    }

    public @Nullable String returnTypeOf(PhpMethodDefinition method) {
        return returnType(method.returnType(), method.fileId(), method.returnSource(),
                new HashSet<>());
    }

    /**
     * The property this access reads, wherever in the workspace it is declared,
     * or {@code null} when the object's class cannot be named or declares no such
     * property - an inherited one, or a class nothing indexed.
     */
    public @Nullable PhpPropertyDefinition declarationOf(PhpPropertyUsage usage) {
        return declarationOf(usage, new HashSet<>());
    }

    /** The method this call invokes, on the same terms. */
    public @Nullable PhpMethodDefinition declarationOf(PhpMethodUsage usage) {
        return declarationOf(usage, new HashSet<>());
    }

    /** The class an access is on, by the simple name a declaration goes by. */
    public @Nullable String classOf(PhpPropertyUsage usage) {
        return classOf(usage.className(), usage.fileId(), usage.objectSource(), new HashSet<>());
    }

    public @Nullable String classOf(PhpMethodUsage usage) {
        return classOf(usage.className(), usage.fileId(), usage.objectSource(), new HashSet<>());
    }

    private @Nullable PhpPropertyDefinition declarationOf(PhpPropertyUsage usage,
                                                          Set<PhpSymbol> visiting) {
        String cls = classOf(usage.className(), usage.fileId(), usage.objectSource(), visiting);
        if (cls == null) return null;

        for (PhpPropertyDefinition declared : workspace.properties()) {
            if (declared.name().equals(usage.name()) && declared.owner().name().equals(cls)) {
                return declared;
            }
        }
        return null;
    }

    private @Nullable PhpMethodDefinition declarationOf(PhpMethodUsage usage,
                                                       Set<PhpSymbol> visiting) {
        String cls = classOf(usage.className(), usage.fileId(), usage.objectSource(), visiting);
        if (cls == null) return null;

        for (PhpMethodDefinition declared : workspace.methods()) {
            if (declared.name().equals(usage.name()) && declared.owner().name().equals(cls)) {
                return declared;
            }
        }
        return null;
    }

    /**
     * {@code $this} was settled when the file was indexed; any other object is
     * whatever its own type works out to.
     */
    private @Nullable String classOf(@Nullable String className, int fileId,
                                     @Nullable Range objectSource, Set<PhpSymbol> visiting) {
        if (className != null) return className;

        String type = sourceType(fileId, objectSource, visiting);
        return type == null ? null : simpleClassName(type);
    }

    /**
     * {@code ?App\NSA\A} is the class {@code A}: a declaration is joined by the
     * name it was written under, and that is all an access has to go on.
     */
    private static String simpleClassName(String type) {
        int last = type.lastIndexOf('\\');
        String name = last < 0 ? type : type.substring(last + 1);
        return name.startsWith("?") ? name.substring(1) : name;
    }

    /**
     * The type the name holds at that point, which is what the latest definition
     * at or before it works out to - so a reassignment retypes what follows it.
     * Earlier definitions are tried when a later one works out to nothing.
     */
    private @Nullable String typeOfVariable(int fileId, @Nullable String functionName, String name,
                                            Point at, Set<PhpSymbol> visiting) {
        var candidates = new ArrayList<PhpVarDefinition>();
        for (PhpVarDefinition def : workspace.varDefinitions()) {
            if (def.fileId() != fileId || !def.name().equals(name)) continue;
            if (!Objects.equals(def.functionName(), functionName)) continue;
            if (def.range().start().compareTo(at) > 0) continue;
            candidates.add(def);
        }
        candidates.sort(Comparator.comparing((PhpVarDefinition def) -> def.range().start())
                .reversed());

        for (PhpVarDefinition def : candidates) {
            String type = typeOf(def, visiting);
            if (type != null) return type;
        }
        return null;
    }

    private @Nullable String typeOf(PhpVarDefinition def, Set<PhpSymbol> visiting) {
        if (!visiting.add(def)) return null;
        if (def.type() != null) return def.type();
        return sourceType(def.fileId(), def.valueSource(), visiting);
    }

    /**
     * The type of whatever the index recorded at that range - the far end of one
     * link in the chain.
     */
    private @Nullable String sourceType(int fileId, @Nullable Range source,
                                        Set<PhpSymbol> visiting) {
        if (source == null) return null;
        PhpSymbol symbol = symbolAt(fileId, source);
        return symbol == null ? null : typeOfSymbol(symbol, visiting);
    }

    private @Nullable String typeOfSymbol(PhpSymbol symbol, Set<PhpSymbol> visiting) {
        if (symbol instanceof PhpVarDefinition def) {
            return typeOf(def, visiting);
        }
        if (!visiting.add(symbol)) return null;

        if (symbol instanceof PhpVarUsage usage) {
            return typeOfVariable(usage.fileId(), usage.functionName(), usage.name(),
                    usage.range().start(), visiting);
        }
        if (symbol instanceof PhpPropertyUsage usage) {
            PhpPropertyDefinition declared = declarationOf(usage, visiting);
            return declared == null ? null : declared.type();
        }
        if (symbol instanceof PhpFunctionUsage usage) {
            PhpFunctionDefinition declared = functions.definitionOf(usage);
            return declared == null ? null : returnType(declared.returnType(), declared.fileId(),
                    declared.returnSource(), visiting);
        }
        if (symbol instanceof PhpMethodUsage usage) {
            PhpMethodDefinition declared = declarationOf(usage, visiting);
            return declared == null ? null : returnType(declared.returnType(), declared.fileId(),
                    declared.returnSource(), visiting);
        }
        return null;
    }

    private @Nullable String returnType(@Nullable String declared, int fileId,
                                       @Nullable Range source, Set<PhpSymbol> visiting) {
        return declared != null ? declared : sourceType(fileId, source, visiting);
    }

    /**
     * The usage recorded at exactly that range. Only usages can be the source of
     * a value, and no two of them share a range in one file, so this is the one
     * the indexer meant.
     */
    private @Nullable PhpSymbol symbolAt(int fileId, Range range) {
        PhpSymbol found = firstAt(workspace.varUsages(), fileId, range);
        if (found == null) found = firstAt(workspace.propertyUsages(), fileId, range);
        if (found == null) found = firstAt(workspace.functionUsages(), fileId, range);
        if (found == null) found = firstAt(workspace.methodUsages(), fileId, range);
        return found;
    }

    private static @Nullable PhpSymbol firstAt(List<? extends PhpSymbol> symbols, int fileId,
                                               Range range) {
        for (PhpSymbol symbol : symbols) {
            if (symbol.fileId() == fileId && symbol.range().equals(range)) return symbol;
        }
        return null;
    }
}
