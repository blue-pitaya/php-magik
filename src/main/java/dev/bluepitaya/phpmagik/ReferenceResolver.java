package dev.bluepitaya.phpmagik;

import dev.bluepitaya.phpmagik.phpsymbol.PhpClassDeclaration;
import dev.bluepitaya.phpmagik.phpsymbol.PhpClassReference;
import dev.bluepitaya.phpmagik.phpsymbol.PhpFunctionCall;
import dev.bluepitaya.phpmagik.phpsymbol.PhpFunctionDefinition;
import dev.bluepitaya.phpmagik.phpsymbol.PhpMemberReference;
import dev.bluepitaya.phpmagik.phpsymbol.PhpMethodCall;
import dev.bluepitaya.phpmagik.phpsymbol.PhpMethodDeclaration;
import dev.bluepitaya.phpmagik.phpsymbol.PhpMethodLocalVarDeclaration;
import dev.bluepitaya.phpmagik.phpsymbol.PhpParameterDeclaration;
import dev.bluepitaya.phpmagik.phpsymbol.PhpPropertyAccess;
import dev.bluepitaya.phpmagik.phpsymbol.PhpPropertyDeclaration;
import dev.bluepitaya.phpmagik.phpsymbol.PhpReference;
import dev.bluepitaya.phpmagik.phpsymbol.PhpStaticCall;
import dev.bluepitaya.phpmagik.phpsymbol.PhpSymbolCollection;
import dev.bluepitaya.phpmagik.phpsymbol.PhpSymbolOwner;
import dev.bluepitaya.phpmagik.phpsymbol.PhpType;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

@NullMarked
public final class ReferenceResolver {

    private final PhpSymbolCollection symbols;

    public ReferenceResolver(PhpSymbolCollection symbols) {
        this.symbols = symbols;
    }

    public void resolve() {
        Index index = new Index(symbols);
        for (PhpReference reference : symbols.references()) {
            switch (reference) {
                case PhpClassReference r -> r.definition(index.classes.get(r.name()));
                case PhpFunctionCall r -> r.definition(index.functions.get(r.name()));
                case PhpPropertyAccess r -> r.definition(propertyOf(receiverClass(r, index), r.name(), index));
                case PhpMethodCall r -> r.definition(methodOf(receiverClass(r, index), r.name(), index));
                case PhpStaticCall r -> r.definition(methodOf(scopeClass(r.owner(), r.scope(), index), r.name(), index));
            }
        }
        for (PhpMethodLocalVarDeclaration local : symbols.localVarDeclarations()) {
            PhpMemberReference source = local.source();
            if (source != null) {
                local.type(sourceType(local, source, index));
            }
        }
    }

    private @Nullable PhpType sourceType(PhpMethodLocalVarDeclaration local, PhpMemberReference source, Index index) {
        PhpType type = memberType(source, index);
        return local.elementOfSource() ? PhpType.elementOf(type) : type;
    }

    private @Nullable PhpPropertyDeclaration propertyOf(
            @Nullable PhpClassDeclaration owner, String name, Index index
    ) {
        if (owner == null) {
            return null;
        }
        Map<String, PhpPropertyDeclaration> byName = index.properties.get(owner);
        return byName == null ? null : byName.get(name);
    }

    private @Nullable PhpMethodDeclaration methodOf(
            @Nullable PhpClassDeclaration owner, String name, Index index
    ) {
        if (owner == null) {
            return null;
        }
        Map<String, PhpMethodDeclaration> byName = index.methods.get(owner);
        return byName == null ? null : byName.get(name);
    }

    private @Nullable PhpClassDeclaration receiverClass(PhpMemberReference member, Index index) {
        PhpMemberReference receiver = member.receiver();
        if (receiver != null) {
            return classOf(memberType(receiver, index), index);
        }

        String variable = member.variable();
        if (variable == null) {
            return null;
        }
        return "$this".equals(variable)
                ? enclosingClass(member.owner())
                : classOf(variableType(variable, member.owner(), index), index);
    }

    private @Nullable PhpType memberType(PhpMemberReference member, Index index) {
        PhpClassDeclaration owner = receiverClass(member, index);
        if (owner == null) {
            return null;
        }

        PhpType type = switch (member) {
            case PhpPropertyAccess access -> {
                PhpPropertyDeclaration property = propertyOf(owner, access.name(), index);
                yield property == null ? null : property.type();
            }
            case PhpMethodCall call -> {
                PhpMethodDeclaration method = methodOf(owner, call.name(), index);
                yield method == null ? null : method.returnType();
            }
        };
        String fqn = owner.fqn();
        String typeName = classTypeName(type);
        boolean self = "self".equalsIgnoreCase(typeName) || "static".equalsIgnoreCase(typeName);
        return self && fqn != null ? PhpType.named(fqn) : type;
    }

    private @Nullable PhpClassDeclaration classOf(@Nullable PhpType type, Index index) {
        String fqn = classTypeName(type);
        return fqn == null ? null : index.classes.get(fqn);
    }

    private static @Nullable PhpClassDeclaration scopeClass(@Nullable PhpSymbolOwner owner, String scope, Index index) {
        if ("self".equalsIgnoreCase(scope) || "static".equalsIgnoreCase(scope)) {
            return enclosingClass(owner);
        }
        return index.classes.get(scope);
    }

    private static @Nullable PhpClassDeclaration enclosingClass(@Nullable PhpSymbolOwner owner) {
        return owner instanceof PhpMethodDeclaration method ? method.owner() : null;
    }

    private @Nullable PhpType variableType(
            String variable, @Nullable PhpSymbolOwner owner, Index index
    ) {
        PhpParameterDeclaration parameter = index.parameters.get(new Scoped(owner, variable));
        if (parameter != null) {
            return parameter.type();
        }
        PhpMethodLocalVarDeclaration local = index.locals.get(new Scoped(owner, variable));
        if (local == null) {
            return null;
        }
        PhpMemberReference source = local.source();
        if (source == null || !index.resolving.add(local)) {
            return local.type();
        }
        try {
            return sourceType(local, source, index);
        } finally {
            index.resolving.remove(local);
        }
    }

    private @Nullable String classTypeName(@Nullable PhpType type) {
        return PhpType.classFqn(type);
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
        final Set<PhpMethodLocalVarDeclaration> resolving = new HashSet<>();

        Index(PhpSymbolCollection symbols) {
            for (PhpFunctionDefinition declared : symbols.functionDefinitions()) {
                String name = declared.name();
                if (name != null) {
                    functions.putIfAbsent(name, declared);
                }
            }
            for (PhpClassDeclaration declared : symbols.classDeclarations()) {
                String fqn = declared.fqn();
                if (fqn != null) {
                    classes.putIfAbsent(fqn, declared);
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
