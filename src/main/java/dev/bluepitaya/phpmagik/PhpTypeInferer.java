package dev.bluepitaya.phpmagik;

import dev.bluepitaya.phpmagik.phpsymbol.PhpClassDeclaration;
import dev.bluepitaya.phpmagik.phpsymbol.PhpMethodDeclaration;
import dev.bluepitaya.phpmagik.phpsymbol.PhpMethodLocalVarDeclaration;
import dev.bluepitaya.phpmagik.phpsymbol.PhpParameterDeclaration;
import dev.bluepitaya.phpmagik.phpsymbol.PhpPropertyDeclaration;
import dev.bluepitaya.phpmagik.phpsymbol.PhpSymbolCollection;
import dev.bluepitaya.phpmagik.phpsymbol.PhpSymbolOwner;
import dev.bluepitaya.phpmagik.phpsymbol.PhpType;
import dev.bluepitaya.phpmagik.ts.Node;
import dev.bluepitaya.phpmagik.ts.Nodes;
import dev.bluepitaya.phpmagik.ts.Range;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

@NullMarked
public final class PhpTypeInferer {

    public void infer(PhpSymbolCollection symbols, Node root) {
        inferLocals(symbols, root);
        inferMethodReturns(symbols, root);
    }

    private void inferLocals(PhpSymbolCollection symbols, Node root) {
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
            if (assignment == null || !"assignment_expression".equals(assignment.getType())) {
                continue;
            }
            PhpType type = typeOf(assignment.getChildByFieldName("right"), local.owner(), symbols);
            if (type != null) {
                local.type(type);
            }
        }
    }

    private void inferMethodReturns(PhpSymbolCollection symbols, Node root) {
        for (PhpMethodDeclaration method : symbols.methodDeclarations()) {
            if (method.returnType() != null) {
                continue;
            }
            Range range = method.range();
            if (range == null) {
                continue;
            }
            Node name = root.getDescendant(range.start(), range.end());
            Node declaration = climbTo(name, "method_declaration");
            if (declaration == null) {
                continue;
            }
            Node returned = returnedExpression(declaration.getChildByFieldName("body"));
            PhpType type = typeOf(returned, method, symbols);
            if (type != null) {
                method.returnType(type);
            }
        }
    }

    private @Nullable PhpType typeOf(
            @Nullable Node expr, @Nullable PhpSymbolOwner owner, PhpSymbolCollection symbols
    ) {
        if (expr == null) {
            return null;
        }
        PhpType builtin = PhpType.of(Nodes.type(expr));
        if (builtin != null) {
            return builtin;
        }
        return switch (Nodes.type(expr)) {
            case "variable_name" -> variableType(Nodes.text(expr), owner, symbols);
            case "member_access_expression" -> memberType(expr, owner, symbols);
            case "object_creation_expression" -> createdType(expr);
            case "parenthesized_expression" -> typeOf(firstNamedChild(expr), owner, symbols);
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
                String className = method.owner().name();
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
        String member = Nodes.text(expr.getChildByFieldName("name"));
        if (objectType == null || member == null) {
            return null;
        }
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

    private static @Nullable PhpType createdType(Node expr) {
        int count = expr.getNamedChildCount();
        for (int i = 0; i < count; i++) {
            Node child = expr.getNamedChild(i);
            switch (Nodes.type(child)) {
                case "name", "qualified_name" -> {
                    String text = Nodes.text(child);
                    return text == null ? null : PhpType.named(text);
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
            if (classType.name().equals(declared.name())) {
                return declared;
            }
        }
        return null;
    }

    private static @Nullable Node returnedExpression(@Nullable Node body) {
        if (body == null) {
            return null;
        }
        for (Node child : body.getChildren()) {
            if ("return_statement".equals(child.getType())) {
                return firstNamedChild(child);
            }
        }
        return null;
    }

    private static @Nullable Node climbTo(@Nullable Node node, String type) {
        Node current = node;
        while (current != null && !type.equals(current.getType())) {
            current = current.getParent();
        }
        return current;
    }

    private static @Nullable Node firstNamedChild(Node node) {
        return node.getNamedChildCount() > 0 ? node.getNamedChild(0) : null;
    }

    private static @Nullable String withoutDollar(@Nullable String name) {
        if (name == null) {
            return null;
        }
        return name.startsWith("$") ? name.substring(1) : name;
    }
}
