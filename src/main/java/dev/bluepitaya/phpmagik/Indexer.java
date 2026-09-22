package dev.bluepitaya.phpmagik;

import dev.bluepitaya.phpmagik.phpsymbol.PhpClassDefinition;
import dev.bluepitaya.phpmagik.phpsymbol.PhpSymbolCollection;
import dev.bluepitaya.phpmagik.phpsymbol.UseKind;
import dev.bluepitaya.phpmagik.ts.Node;
import dev.bluepitaya.phpmagik.ts.Nodes;
import dev.bluepitaya.phpmagik.ts.Range;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

@NullMarked
public final class Indexer {

    private final CtxPhpSymbolAdder adder;

    private @Nullable Range returnSource;

    public Indexer(PhpFile file) {
        this.adder = new CtxPhpSymbolAdder(file);
    }

    public PhpSymbolCollection symbols() {
        return adder.symbols();
    }

    public void index(Node root) {
        visit(root, new Scope(null, null));
    }

    private void recordReturn(@Nullable Range valueSource) {
        if (returnSource == null) returnSource = valueSource;
    }

    private void visit(Node node, Scope scope) {
        switch (node.getType()) {
            case "namespace_definition" -> visitNamespace(node, scope);
            case "namespace_use_declaration" -> visitUseDeclaration(node);

            case "class_declaration", "interface_declaration", "trait_declaration",
                 "enum_declaration" -> visitClassDeclaration(node, scope);
            case "anonymous_class" -> visitChildren(node, scope.inClass(null));
            case "property_declaration" -> visitPropertyDeclaration(node, scope);
            case "function_definition" -> visitFunction(node, scope);
            case "method_declaration" -> visitMethod(node, scope);

            case "anonymous_function", "arrow_function", "property_hook" -> visitBody(node, scope);

            case "simple_parameter", "variadic_parameter", "property_promotion_parameter" ->
                    visitParameter(node, scope);
            case "assignment_expression", "reference_assignment_expression" -> visitAssignment(node, scope);
            case "static_variable_declaration" -> visitStaticVariable(node, scope);
            case "catch_clause" -> visitCatchClause(node, scope);
            case "foreach_statement" -> visitForeach(node, scope);
            case "global_declaration" -> visitGlobalDeclaration(node, scope);
            case "return_statement" -> visitReturn(node, scope);

            case "named_type" -> adder.addClassUsage(Nodes.namedChild(node, 0));
            case "base_clause", "class_interface_clause", "use_declaration" -> visitClassNames(node, scope);
            case "object_creation_expression", "class_constant_access_expression", "attribute" -> {
                adder.addClassUsage(Nodes.namedChild(node, 0));
                visitChildren(node, scope);
            }
            case "scoped_call_expression", "scoped_property_access_expression" -> {
                adder.addClassUsage(node.getChildByFieldName("scope"));
                visitChildren(node, scope);
            }
            case "binary_expression" -> visitBinary(node, scope);

            case "variable_name" -> adder.addVarUsage(node, scope);
            case "member_access_expression", "nullsafe_member_access_expression" -> {
                adder.addPropertyUsage(node, scope);
                visitChildren(node, scope);
            }
            case "member_call_expression", "nullsafe_member_call_expression" -> {
                adder.addMethodUsage(node, scope);
                visitChildren(node, scope);
            }
            case "function_call_expression" -> {
                adder.addFunctionUsage(node);
                visitChildren(node, scope);
            }

            default -> visitChildren(node, scope);
        }
    }

    private void visitChildren(Node node, Scope scope) {
        visitChildrenExcept(node, null, scope);
    }

    private void visitChildrenExcept(Node node, @Nullable Node handled, Scope scope) {
        for (Node child : node.getNamedChildren()) {
            if (!child.equals(handled)) visit(child, scope);
        }
    }

    /** namespace App; namespace App { } */
    private void visitNamespace(Node node, Scope scope) {
        String outer = adder.ns();
        adder.ns(Nodes.text(node.getChildByFieldName("name")));

        Node body = node.getChildByFieldName("body");
        if (body == null) return;
        visit(body, scope);
        adder.ns(outer);
    }

    /** use App\Foo; use function App\bar; use App\{A, B}; */
    private void visitUseDeclaration(Node node) {
        UseKind declared = CtxPhpSymbolAdder.useKind(node);

        Node group = node.getChildByFieldName("body");
        if (group == null) {
            for (Node clause : node.getNamedChildren()) {
                adder.addUse(clause, null, declared);
            }
            return;
        }
        String prefix = Nodes.text(Nodes.namedChild(node, 0));
        for (Node clause : group.getNamedChildren()) {
            adder.addUse(clause, prefix, declared);
        }
    }

    /** class Foo { } interface I { } trait T { } enum E { } */
    private void visitClassDeclaration(Node node, Scope scope) {
        PhpClassDefinition declared = adder.addClass(node);
        visitChildren(node, scope.inClass(declared));
    }

    /** public int $a, $b; */
    private void visitPropertyDeclaration(Node node, Scope scope) {
        String type = Nodes.text(node.getChildByFieldName("type"));
        for (Node child : node.getNamedChildren()) {
            if (!"property_element".equals(child.getType())) {
                visit(child, scope);
                continue;
            }
            Node name = child.getChildByFieldName("name");
            adder.addProperty(name, scope.cls(), type);
            visitChildrenExcept(child, name, scope);
        }
    }

    /** function foo(int $a): int { } */
    private void visitFunction(Node node, Scope scope) {
        Node name = node.getChildByFieldName("name");
        Range returned = visitBody(node, scope.inFunction(Nodes.text(name)));
        if (name == null) return;

        adder.addFunction(node, name, returnType(node), returned);
    }

    /** public function foo(int $a): int { } */
    private void visitMethod(Node node, Scope scope) {
        Node name = node.getChildByFieldName("name");
        Range returned = visitBody(node, scope.inFunction(Nodes.text(name)));
        PhpClassDefinition owner = scope.cls();
        if (name == null || owner == null) {
            return;
        }

        adder.addMethod(node, name, owner, returnType(node), returned);
    }

    /** function () { } fn() => $x; get { } */
    private @Nullable Range visitBody(Node node, Scope scope) {
        @Nullable Range enclosing = returnSource;
        returnSource = null;

        visitChildren(node, scope);

        @Nullable Range body = returnSource;
        returnSource = enclosing;
        return body;
    }

    private @Nullable String returnType(Node declaration) {
        return Nodes.text(declaration.getChildByFieldName("return_type"));
    }

    /** return $x; return; */
    private void visitReturn(Node node, Scope scope) {
        Node value = Nodes.namedChild(node, 0);
        @Nullable Range range;
        if (value == null) {
            range = Range.of(node);
        } else {
            range = PhpNodes.valueSource(value);
        }

        recordReturn(range);
        visitChildren(node, scope);
    }

    /** int $a, &$a, ...$rest, private Engine $engine */
    private void visitParameter(Node node, Scope scope) {
        Node declared = node.getChildByFieldName("name");
        /* a by-reference parameter wraps its variable: "&$x" */
        Node name = "by_ref".equals(Nodes.type(declared))
                ? Nodes.namedChild(declared, 0)
                : declared;

        if ("variable_name".equals(Nodes.type(name))) {
            String type = Nodes.text(node.getChildByFieldName("type"));
            adder.addVarDefinition(name, type, null, scope);
            if ("property_promotion_parameter".equals(node.getType())) {
                adder.addProperty(name, scope.cls(), type);
            }
            visitChildrenExcept(node, declared, scope);
            return;
        }
        visitChildren(node, scope);
    }

    /** $x = 1; $x =& $y; */
    private void visitAssignment(Node node, Scope scope) {
        Node left = node.getChildByFieldName("left");
        if (!"variable_name".equals(Nodes.type(left))) {
            visitChildren(node, scope);
            return;
        }
        bindAssigned(left, node.getChildByFieldName("right"), scope);
        visitChildrenExcept(node, left, scope);
    }

    /** static $x = 1; */
    private void visitStaticVariable(Node node, Scope scope) {
        Node name = node.getChildByFieldName("name");
        if (name != null) bindAssigned(name, node.getChildByFieldName("value"), scope);
        visitChildrenExcept(node, name, scope);
    }

    /** catch (RuntimeException $e) */
    private void visitCatchClause(Node node, Scope scope) {
        Node name = node.getChildByFieldName("name");
        if (name != null) {
            adder.addVarDefinition(name, Nodes.text(node.getChildByFieldName("type")), null, scope);
        }
        visitChildrenExcept(node, name, scope);
    }

    /** foreach ($items as $k => $v) */
    private void visitForeach(Node node, Scope scope) {
        Node bound = Nodes.namedChild(node, 1);
        bindForeachValue(bound, scope);
        visitChildrenExcept(node, bound, scope);
    }

    private void bindForeachValue(@Nullable Node node, Scope scope) {
        if (node == null) return;
        switch (Nodes.type(node)) {
            case "variable_name" -> adder.addVarDefinition(node, null, null, scope);
            case "by_ref" -> bindForeachValue(Nodes.namedChild(node, 0), scope);
            case "pair" -> {
                bindForeachValue(Nodes.namedChild(node, 0), scope);
                bindForeachValue(Nodes.namedChild(node, 1), scope);
            }
            default -> visit(node, scope);
        }
    }

    /** global $a, $b; */
    private void visitGlobalDeclaration(Node node, Scope scope) {
        for (Node child : node.getNamedChildren()) {
            if ("variable_name".equals(child.getType())) {
                adder.addVarDefinition(child, null, null, scope);
            } else {
                visit(child, scope);
            }
        }
    }

    /** $x instanceof Foo */
    private void visitBinary(Node node, Scope scope) {
        if ("instanceof".equals(Nodes.type(node.getChildByFieldName("operator")))) {
            adder.addClassUsage(node.getChildByFieldName("right"));
        }
        visitChildren(node, scope);
    }

    /** extends A, implements B, use T; */
    private void visitClassNames(Node node, Scope scope) {
        for (Node child : node.getNamedChildren()) {
            if (PhpNodes.NAME_TYPES.contains(child.getType())) {
                adder.addClassUsage(child);
            } else {
                visit(child, scope);
            }
        }
    }

    private void bindAssigned(Node name, @Nullable Node value, Scope scope) {
        adder.addVarDefinition(name, null, PhpNodes.valueSource(value), scope);
    }
}
