package dev.bluepitaya.phpmagik;

import dev.bluepitaya.phpmagik.phpsymbol.PhpClassDeclaration;
import dev.bluepitaya.phpmagik.phpsymbol.PhpMethodDeclaration;
import dev.bluepitaya.phpmagik.phpsymbol.PhpMethodLocalVarDeclaration;
import dev.bluepitaya.phpmagik.phpsymbol.PhpParameterDeclaration;
import dev.bluepitaya.phpmagik.phpsymbol.PhpPropertyDeclaration;
import dev.bluepitaya.phpmagik.phpsymbol.PhpClassReference;
import dev.bluepitaya.phpmagik.phpsymbol.PhpReference;
import dev.bluepitaya.phpmagik.phpsymbol.PhpSymbolCollection;
import dev.bluepitaya.phpmagik.phpsymbol.PhpSymbolOwner;
import dev.bluepitaya.phpmagik.phpsymbol.PhpType;
import dev.bluepitaya.phpmagik.ts.Node;
import dev.bluepitaya.phpmagik.ts.Range;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

@NullMarked
public final class PhpTypeInferer {

    public void infer(PhpSymbolCollection symbols, Node root) {
        for (PhpMethodLocalVarDeclaration local : symbols.localVarDeclarations()) {
            if (local.type() != null) {
                continue;
            }
            Range range = local.range();
            if (range == null) {
                continue;
            }
            Node node = root.getDescendant(range.start(), range.end());
            Node assignment = node == null ? null : node.getParent();
            if (assignment == null || !assignment.isType("assignment_expression")) {
                continue;
            }
            PhpType type = typeOf(assignment.getChildByFieldName("right"), local.owner(), symbols);
            if (type != null) {
                local.type(type);
            }
        }
    }

    @Nullable PhpType typeOf(
            @Nullable Node expr, @Nullable PhpSymbolOwner owner, PhpSymbolCollection symbols
    ) {
        if (expr == null) {
            return null;
        }
        PhpType builtin = PhpType.of(expr.getType());
        if (builtin != null) {
            return builtin;
        }
        return switch (expr.getType()) {
            case "variable_name" -> variableType(expr.getContent(), owner, symbols);
            case "member_access_expression" -> memberType(expr, owner, symbols);
            case "object_creation_expression" -> createdType(expr, symbols);
            case "parenthesized_expression" -> typeOf(Node.namedChild(expr, 0), owner, symbols);
            case null, default -> null;
        };
    }

    private @Nullable PhpType variableType(
            @Nullable String name, @Nullable PhpSymbolOwner owner, PhpSymbolCollection symbols
    ) {
        if (name == null) {
            return null;
        }
        if ("$this".equals(name)) {
            if (owner instanceof PhpMethodDeclaration method && method.owner() != null) {
                String className = method.owner().fqn();
                return className == null ? null : PhpType.named(className);
            }
            return null;
        }
        for (PhpParameterDeclaration param : symbols.parameterDeclarations()) {
            if (param.owner() == owner && name.equals(param.name())) {
                return param.type();
            }
        }
        for (PhpMethodLocalVarDeclaration local : symbols.localVarDeclarations()) {
            if (local.owner() == owner && name.equals(local.name())) {
                return local.type();
            }
        }
        return null;
    }

    private @Nullable PhpType memberType(
            Node expr, @Nullable PhpSymbolOwner owner, PhpSymbolCollection symbols
    ) {
        PhpType objectType = typeOf(expr.getChildByFieldName("object"), owner, symbols);
        Node name = expr.getChildByFieldName("name");
        if (objectType == null || name == null) {
            return null;
        }
        String member = name.getContent();
        PhpClassDeclaration owningClass = classNamed(objectType, symbols);
        if (owningClass == null) {
            return null;
        }
        for (PhpPropertyDeclaration property : symbols.propertyDeclarations()) {
            if (property.owner() == owningClass && member.equals(withoutDollar(property.name()))) {
                return property.type();
            }
        }
        return null;
    }

    private static @Nullable PhpType createdType(Node expr, PhpSymbolCollection symbols) {
        int count = expr.getNamedChildCount();
        for (int i = 0; i < count; i++) {
            Node child = expr.getNamedChild(i);
            switch (child.getType()) {
                case "name", "qualified_name", "relative_name" -> {
                    Range range = child.getRange();
                    for (PhpReference reference : symbols.references()) {
                        if (reference instanceof PhpClassReference classReference
                                && range.equals(classReference.range())) {
                            return PhpType.named(classReference.name());
                        }
                    }
                    return null;
                }
                default -> {
                }
            }
        }
        return null;
    }

    private static @Nullable PhpClassDeclaration classNamed(PhpType type, PhpSymbolCollection symbols) {
        if (!(type instanceof PhpType.ClassType classType)) {
            return null;
        }
        for (PhpClassDeclaration declared : symbols.classDeclarations()) {
            if (classType.fqn().equals(declared.fqn())) {
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
