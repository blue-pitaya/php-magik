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

@NullMarked
public final class ReferenceResolver {

    public void resolve(PhpSymbolCollection symbols) {
        for (PhpReference reference : symbols.references()) {
            PhpSymbol definition = definitionOf(reference, symbols);
            if (definition != null) {
                reference.definition(definition);
            }
        }
    }

    private static @Nullable PhpSymbol definitionOf(
            PhpReference reference, PhpSymbolCollection symbols
    ) {
        String name = reference.name();
        if (name == null) {
            return null;
        }

        return switch (reference.kind()) {
            case FUNCTION -> functionNamed(name, symbols);
            case PROPERTY -> propertyOf(receiverClass(reference, symbols), name, symbols);
            case METHOD -> methodOf(receiverClass(reference, symbols), name, symbols);
        };
    }

    private static @Nullable PhpFunctionDefinition functionNamed(
            String name, PhpSymbolCollection symbols
    ) {
        for (PhpFunctionDefinition declared : symbols.functionDefinitions()) {
            if (name.equals(declared.name())) {
                return declared;
            }
        }
        return null;
    }

    private static @Nullable PhpPropertyDeclaration propertyOf(
            @Nullable PhpClassDeclaration owner, String name, PhpSymbolCollection symbols
    ) {
        if (owner == null) {
            return null;
        }
        for (PhpPropertyDeclaration declared : symbols.propertyDeclarations()) {
            if (declared.owner() == owner && name.equals(withoutDollar(declared.name()))) {
                return declared;
            }
        }
        return null;
    }

    private static @Nullable PhpMethodDeclaration methodOf(
            @Nullable PhpClassDeclaration owner, String name, PhpSymbolCollection symbols
    ) {
        if (owner == null) {
            return null;
        }
        for (PhpMethodDeclaration declared : symbols.methodDeclarations()) {
            if (declared.owner() == owner && name.equals(declared.name())) {
                return declared;
            }
        }
        return null;
    }

    private static @Nullable PhpClassDeclaration receiverClass(
            PhpReference reference, PhpSymbolCollection symbols
    ) {
        String receiver = reference.receiverVar();
        PhpSymbolOwner owner = reference.owner();
        if (receiver == null) {
            return null;
        }

        if ("$this".equals(receiver)) {
            return owner instanceof PhpMethodDeclaration method ? method.owner() : null;
        }

        String typeName = receiverTypeName(receiver, owner, symbols);
        return typeName == null ? null : classNamed(typeName, symbols);
    }

    private static @Nullable String receiverTypeName(
            String receiver, @Nullable PhpSymbolOwner owner, PhpSymbolCollection symbols
    ) {
        for (PhpParameterDeclaration declared : symbols.parameterDeclarations()) {
            if (declared.owner() == owner && receiver.equals(declared.name())) {
                return classTypeName(declared.type());
            }
        }
        for (PhpMethodLocalVarDeclaration declared : symbols.localVarDeclarations()) {
            if (declared.owner() == owner && receiver.equals(declared.name())) {
                return classTypeName(declared.type());
            }
        }
        return null;
    }

    private static @Nullable String classTypeName(@Nullable PhpType type) {
        return type instanceof PhpType.ClassType classType ? classType.name() : null;
    }

    private static @Nullable PhpClassDeclaration classNamed(
            String name, PhpSymbolCollection symbols
    ) {
        for (PhpClassDeclaration declared : symbols.classDeclarations()) {
            if (name.equals(declared.name())) {
                return declared;
            }
        }
        return null;
    }

    private static @Nullable String withoutDollar(@Nullable String name) {
        if (name == null) {
            return null;
        }
        return name.startsWith("$") ? name.substring(1) : name;
    }
}
