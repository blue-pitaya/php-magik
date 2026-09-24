package dev.bluepitaya.phpmagik;

import dev.bluepitaya.phpmagik.phpsymbol.PhpClassDeclaration;
import dev.bluepitaya.phpmagik.phpsymbol.PhpFunctionDefinition;
import dev.bluepitaya.phpmagik.phpsymbol.PhpMethodDeclaration;
import dev.bluepitaya.phpmagik.phpsymbol.PhpMethodLocalVarDeclaration;
import dev.bluepitaya.phpmagik.phpsymbol.PhpParameterDeclaration;
import dev.bluepitaya.phpmagik.phpsymbol.PhpPropertyDeclaration;
import dev.bluepitaya.phpmagik.phpsymbol.PhpReference;
import dev.bluepitaya.phpmagik.phpsymbol.PhpSymbol;
import dev.bluepitaya.phpmagik.phpsymbol.PhpSymbolCollection;
import dev.bluepitaya.phpmagik.phpsymbol.PhpSymbolOwner;
import dev.bluepitaya.phpmagik.phpsymbol.PhpType;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

import java.util.HashMap;
import java.util.Map;

@NullMarked
public final class ReferenceResolver {

    public void resolve(PhpSymbolCollection symbols) {
        Index index = new Index(symbols);
        for (PhpReference reference : symbols.references()) {
            PhpSymbol definition = definitionOf(reference, index);
            if (definition != null) {
                reference.definition(definition);
            }
        }
    }

    private static @Nullable PhpSymbol definitionOf(PhpReference reference, Index index) {
        String name = reference.name();
        if (name == null) {
            return null;
        }

        return switch (reference.kind()) {
            case FUNCTION -> index.functions.get(name);
            case PROPERTY -> propertyOf(receiverClass(reference, index), name, index);
            case METHOD -> methodOf(receiverClass(reference, index), name, index);
        };
    }

    private static @Nullable PhpPropertyDeclaration propertyOf(
            @Nullable PhpClassDeclaration owner, String name, Index index
    ) {
        if (owner == null) {
            return null;
        }
        Map<String, PhpPropertyDeclaration> byName = index.properties.get(owner);
        return byName == null ? null : byName.get(name);
    }

    private static @Nullable PhpMethodDeclaration methodOf(
            @Nullable PhpClassDeclaration owner, String name, Index index
    ) {
        if (owner == null) {
            return null;
        }
        Map<String, PhpMethodDeclaration> byName = index.methods.get(owner);
        return byName == null ? null : byName.get(name);
    }

    private static @Nullable PhpClassDeclaration receiverClass(PhpReference reference, Index index) {
        String receiver = reference.receiverVar();
        PhpSymbolOwner owner = reference.owner();
        if (receiver == null) {
            return null;
        }

        if ("$this".equals(receiver)) {
            return owner instanceof PhpMethodDeclaration method ? method.owner() : null;
        }

        String typeName = receiverTypeName(receiver, owner, index);
        return typeName == null ? null : index.classes.get(typeName);
    }

    private static @Nullable String receiverTypeName(
            String receiver, @Nullable PhpSymbolOwner owner, Index index
    ) {
        PhpParameterDeclaration parameter = index.parameters.get(new Scoped(owner, receiver));
        if (parameter != null) {
            return classTypeName(parameter.type());
        }
        PhpMethodLocalVarDeclaration local = index.locals.get(new Scoped(owner, receiver));
        if (local != null) {
            return classTypeName(local.type());
        }
        return null;
    }

    private static @Nullable String classTypeName(@Nullable PhpType type) {
        return type instanceof PhpType.ClassType classType ? classType.name() : null;
    }

    private static @Nullable String withoutDollar(@Nullable String name) {
        if (name == null) {
            return null;
        }
        return name.startsWith("$") ? name.substring(1) : name;
    }

    private record Scoped(@Nullable PhpSymbolOwner owner, String name) {
    }

    private static final class Index {

        final Map<String, PhpFunctionDefinition> functions = new HashMap<>();
        final Map<String, PhpClassDeclaration> classes = new HashMap<>();
        final Map<PhpClassDeclaration, Map<String, PhpMethodDeclaration>> methods = new HashMap<>();
        final Map<PhpClassDeclaration, Map<String, PhpPropertyDeclaration>> properties = new HashMap<>();
        final Map<Scoped, PhpParameterDeclaration> parameters = new HashMap<>();
        final Map<Scoped, PhpMethodLocalVarDeclaration> locals = new HashMap<>();

        Index(PhpSymbolCollection symbols) {
            for (PhpFunctionDefinition declared : symbols.functionDefinitions()) {
                String name = declared.name();
                if (name != null) {
                    functions.putIfAbsent(name, declared);
                }
            }
            for (PhpClassDeclaration declared : symbols.classDeclarations()) {
                String name = declared.name();
                if (name != null) {
                    classes.putIfAbsent(name, declared);
                }
            }
            for (PhpMethodDeclaration declared : symbols.methodDeclarations()) {
                PhpClassDeclaration owner = declared.owner();
                String name = declared.name();
                if (owner != null && name != null) {
                    methods.computeIfAbsent(owner, key -> new HashMap<>()).putIfAbsent(name, declared);
                }
            }
            for (PhpPropertyDeclaration declared : symbols.propertyDeclarations()) {
                PhpClassDeclaration owner = declared.owner();
                String name = withoutDollar(declared.name());
                if (owner != null && name != null) {
                    properties.computeIfAbsent(owner, key -> new HashMap<>()).putIfAbsent(name, declared);
                }
            }
            for (PhpParameterDeclaration declared : symbols.parameterDeclarations()) {
                String name = declared.name();
                if (name != null) {
                    parameters.putIfAbsent(new Scoped(declared.owner(), name), declared);
                }
            }
            for (PhpMethodLocalVarDeclaration declared : symbols.localVarDeclarations()) {
                String name = declared.name();
                if (name != null) {
                    locals.putIfAbsent(new Scoped(declared.owner(), name), declared);
                }
            }
        }
    }
}
