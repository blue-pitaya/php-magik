package dev.bluepitaya.phpmagik.index;

import dev.bluepitaya.phpmagik.ts.Node;
import dev.bluepitaya.phpmagik.ts.Nodes;
import dev.bluepitaya.phpmagik.ts.Point;
import org.jspecify.annotations.NullMarked;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Set;

@NullMarked
public final class Indexer {

    private static final Set<String> BOOL_OPS = Set.of(
            "==", "!=", "===", "!==", "<", ">", "<=", ">=", "<=>",
            "&&", "||", "and", "or", "xor", "instanceof");

    /** Integer whatever the operands are, unlike the arithmetic ops. */
    private static final Set<String> INT_OPS = Set.of("%", "<<", ">>", "&", "|", "^");

    private static final Set<String> ARITHMETIC_OPS = Set.of("+", "-", "*", "/", "**");

    private static final Set<String> PARAMETER_TYPES = Set.of(
            "simple_parameter", "variadic_parameter", "property_promotion_parameter");

    private final int fileId;
    private final List<PhpVar> vars = new ArrayList<>();
    private final List<PhpFunction> funcs = new ArrayList<>();
    private final List<PhpUseStatement> uses = new ArrayList<>();

    private String ns;
    private String className;
    private String functionName;

    public Indexer(int fileId) {
        this.fileId = fileId;
    }

    public List<PhpVar> vars() {
        return vars;
    }

    public List<PhpFunction> funcs() {
        return funcs;
    }

    public List<PhpUseStatement> uses() {
        return uses;
    }

    public void parseProgram(Node root) {
        for (Node node : root.getNamedChildren()) {
            switch (node.getType()) {
                case "function_definition" -> parseFunctionLike(node);
                case "class_declaration", "interface_declaration", "trait_declaration",
                     "enum_declaration" -> parseClassDeclaration(node);
                case "namespace_definition" -> parseNamespaceDefinition(node);
                case "namespace_use_declaration" -> parseUseDeclaration(node);
                default -> collectVariables(node);
            }
        }
    }

    /**
     * {@code use A\B, C\D;} lists its clauses directly; {@code use A\{B, C};}
     * puts them in a group under a {@code namespace_name} prefix.
     */
    private void parseUseDeclaration(Node root) {
        UseKind declared = useKind(root);
        String prefix = null;
        for (Node node : root.getNamedChildren()) {
            switch (node.getType()) {
                case "namespace_name" -> prefix = node.getContent();
                case "namespace_use_clause" -> addUse(node, prefix, declared);
            }
        }

        Node group = root.getChildByFieldName("body");
        if (group == null) return;
        for (Node node : group.getNamedChildren()) {
            if ("namespace_use_clause".equals(node.getType())) {
                addUse(node, prefix, declared);
            }
        }
    }

    private void addUse(Node clause, String prefix, UseKind declared) {
        /* in "use A as B" the alias is a name node too, so it has to be told
         * apart from the path rather than taken by position */
        Node alias = clause.getChildByFieldName("alias");
        Node path = null;
        for (Node node : clause.getNamedChildren()) {
            String type = node.getType();
            boolean isPath = "name".equals(type) || "qualified_name".equals(type);
            if (isPath && (alias == null || !node.equals(alias))) {
                path = node;
                break;
            }
        }
        if (path == null) return;

        /* a clause may narrow the declaration's kind inside a group */
        UseKind kind = clause.getChildByFieldName("type") != null ? useKind(clause) : declared;
        String fqn = stripLeadingSeparator(
                prefix == null ? path.getContent() : prefix + "\\" + path.getContent());
        String name = alias != null ? alias.getContent() : lastSegment(fqn);

        uses.add(new PhpUseStatement(name, fqn, kind));
    }

    private static UseKind useKind(Node node) {
        String type = Nodes.text(node.getChildByFieldName("type"));
        if ("function".equals(type)) return UseKind.FUNCTION;
        if ("const".equals(type)) return UseKind.CONST;
        return UseKind.CLASS;
    }

    private static String stripLeadingSeparator(String fqn) {
        return fqn.startsWith("\\") ? fqn.substring(1) : fqn;
    }

    private static String lastSegment(String fqn) {
        int last = fqn.lastIndexOf('\\');
        return last < 0 ? fqn : fqn.substring(last + 1);
    }

    private void parseNamespaceDefinition(Node root) {
        for (Node node : root.getNamedChildren()) {
            switch (node.getType()) {
                case "namespace_name" -> ns = node.getContent();
                case "compound_statement" -> parseProgram(node);
            }
        }
    }

    private void parseClassDeclaration(Node root) {
        for (Node node : root.getNamedChildren()) {
            switch (node.getType()) {
                case "name" -> className = node.getContent();
                case "declaration_list", "enum_declaration_list" -> parseDeclarationList(node);
            }
        }
        className = null;
    }

    private void parseDeclarationList(Node root) {
        for (Node node : root.getNamedChildren()) {
            switch (node.getType()) {
                case "method_declaration" -> parseFunctionLike(node);
                case "property_declaration" -> collectVariables(node);
            }
        }
    }

    private void parseFunctionLike(Node root) {
        Node name = root.getChildByFieldName("name");
        if (name == null) return;

        functionName = name.getContent();
        collectVariables(root);
        /* position the def at its own name, not the declaration's start, so
         * hovering/go-to-def on the name lines up like every other symbol */
        pushFunction(functionName, inferReturnType(root), FuncKind.DEF, name, className);
        functionName = null;
    }

    /** Records every {@code (variable_name)} in the subtree: declarations and usages. */
    private void collectVariables(Node root) {
        switch (root.getType()) {
            case "variable_name" -> {
                addVar(root);
                return;
            }
            case "member_access_expression" -> collectPropertyAccess(root);
            case "function_call_expression" -> collectFunctionCall(root);
            case "member_call_expression" -> collectMethodCall(root);
        }

        /* the cases above record the access itself; their object and arguments
         * hold variables of their own, so every node type still recurses */
        for (Node child : root.getNamedChildren()) {
            collectVariables(child);
        }
    }

    /** {@code $obj->foo} becomes a property usage on the object's class. */
    private void collectPropertyAccess(Node node) {
        Node obj = node.getChildByFieldName("object");
        Node name = node.getChildByFieldName("name");
        if (!isVariableAccess(obj, name)) return;

        String object = obj.getContent();
        String cls = objectClass(object);
        if (cls == null) return;

        /* "$"-prefixed to match the property declaration, though the source
         * text after -> has no "$" */
        String prop = "$" + name.getContent();
        VarKind kind = object.equals("$this") ? VarKind.THIS : VarKind.OBJ;
        pushVar(prop, classPropType(cls, prop), kind, name, cls);
    }

    private void collectFunctionCall(Node node) {
        Node fn = node.getChildByFieldName("function");
        String type = Nodes.type(fn);
        if ("name".equals(type) || "qualified_name".equals(type)) {
            pushFunction(fn.getContent(), null, FuncKind.CALL, fn, className);
        }
    }

    /** {@code $obj->foo(...)} becomes a method usage on the object's class. */
    private void collectMethodCall(Node node) {
        Node obj = node.getChildByFieldName("object");
        Node name = node.getChildByFieldName("name");
        if (!isVariableAccess(obj, name)) return;

        String cls = objectClass(obj.getContent());
        if (cls == null) return;

        pushFunction(name.getContent(), null, FuncKind.METHOD, name, cls);
    }

    private static boolean isVariableAccess(Node obj, Node name) {
        return "variable_name".equals(Nodes.type(obj)) && "name".equals(Nodes.type(name));
    }

    private void addVar(Node node) {
        String parentType = Nodes.type(node.getParent());
        VarKind kind;
        if ("property_element".equals(parentType)) {
            kind = VarKind.PROPERTY;
        } else if (parentType != null && PARAMETER_TYPES.contains(parentType)) {
            kind = VarKind.PARAM;
        } else {
            kind = VarKind.USE;
        }
        pushVar(node.getContent(), inferType(node), kind, node, className);
    }

    private void pushVar(String name, String type, VarKind kind, Node node, String owner) {
        Point p = node.getStartPoint();
        vars.add(new PhpVar(name, ns, owner, functionName, type, kind,
                p.getRow(), p.getColumn(), fileId));
    }

    private void pushFunction(String name, String returnType, FuncKind kind, Node node,
                              String owner) {
        Point p = node.getStartPoint();
        funcs.add(new PhpFunction(name, ns, owner, null, kind, returnType,
                p.getRow(), p.getColumn(), fileId));
    }

    private String inferType(Node node) {
        Node parent = node.getParent();
        if (parent == null) return null;
        String pt = parent.getType();

        /* declared type on parameter (incl. variadic/promoted) */
        if (pt.contains("parameter")) {
            return Nodes.text(parent.getChildByFieldName("type"));
        }
        /* typed property: type field sits on property_declaration */
        if (pt.equals("property_element")) {
            Node declaration = parent.getParent();
            if (declaration == null) return null;
            return Nodes.text(declaration.getChildByFieldName("type"));
        }
        /* $x = <rhs>, only when we are the left side */
        if (pt.equals("assignment_expression")) {
            Node left = parent.getChildByFieldName("left");
            if (left != null && left.equals(node)) {
                return inferExprType(parent.getChildByFieldName("right"));
            }
        }
        return null;
    }

    /** Type of an expression node: literal, {@code new X}, {@code $var}, {@code $obj->prop}. */
    private String inferExprType(Node expr) {
        if (expr == null) return null;
        String et = expr.getType();

        String literal = literalType(et);
        if (literal != null) return literal;

        return switch (et) {
            case "object_creation_expression" -> Nodes.text(Nodes.namedChild(expr, 0));
            case "variable_name" -> scopeVarType(expr.getContent());
            case "member_access_expression" -> memberAccessType(expr);
            case "parenthesized_expression" -> inferExprType(Nodes.namedChild(expr, 0));
            case "unary_op_expression" -> unaryType(expr);
            case "binary_expression" -> binaryType(expr);
            default -> null;
        };
    }

    private String memberAccessType(Node expr) {
        Node obj = expr.getChildByFieldName("object");
        Node name = expr.getChildByFieldName("name");
        if (!isVariableAccess(obj, name)) return null;
        String cls = objectClass(obj.getContent());
        return cls == null ? null : classPropType(cls, "$" + name.getContent());
    }

    /** {@code -1}, {@code +$x}, {@code !$b}, {@code ~$n} */
    private String unaryType(Node expr) {
        String op = Nodes.type(Nodes.child(expr, 0));
        if ("!".equals(op)) return "bool";
        if ("~".equals(op)) return "int";
        return inferExprType(Nodes.namedChild(expr, 0));
    }

    private String binaryType(Node expr) {
        String op = Nodes.type(expr.getChildByFieldName("operator"));
        if (op == null) return null;

        if (op.equals(".")) return "string";
        if (INT_OPS.contains(op)) return "int";
        if (BOOL_OPS.contains(op)) return "bool";
        if (!ARITHMETIC_OPS.contains(op)) return null;

        String left = inferExprType(expr.getChildByFieldName("left"));
        String right = inferExprType(expr.getChildByFieldName("right"));
        if (left == null || right == null) return null;

        boolean isFloat = op.equals("/") || left.equals("float") || right.equals("float");
        return isFloat ? "float" : "int";
    }

    private static String literalType(String t) {
        return switch (t) {
            case "integer" -> "int";
            case "float" -> "float";
            case "string", "encapsed_string", "heredoc", "nowdoc" -> "string";
            case "boolean" -> "bool";
            case "null" -> "null";
            case "array_creation_expression" -> "array";
            case "anonymous_function", "arrow_function" -> "Closure";
            default -> null;
        };
    }

    private String inferReturnType(Node func) {
        Node declared = func.getChildByFieldName("return_type");
        if (declared != null) return declared.getContent();
        return inferFromReturns(func.getChildByFieldName("body"));
    }

    /** First {@code return <expr>;} in the body, not descending into closures. */
    private String inferFromReturns(Node root) {
        if (root == null) return null;
        String t = root.getType();

        if (t.equals("anonymous_function") || t.equals("arrow_function")) return null;
        if (t.equals("return_statement")) {
            Node expr = Nodes.namedChild(root, 0);
            return expr == null ? "void" : inferExprType(expr);
        }

        for (Node child : root.getNamedChildren()) {
            String returnType = inferFromReturns(child);
            if (returnType != null) return returnType;
        }
        return null;
    }

    /** Latest known type of variable {@code name} in the current function scope. */
    private String scopeVarType(String name) {
        for (PhpVar v : vars.reversed()) {
            if (v.type() != null && v.name().equals(name)
                    && Objects.equals(v.functionName(), functionName)) {
                return v.type();
            }
        }
        return null;
    }

    /** Declared type of property {@code prop} ({@code "$x"}) on already-collected class {@code cls}. */
    private String classPropType(String cls, String prop) {
        for (PhpVar v : vars) {
            if (v.kind() == VarKind.PROPERTY && v.className() != null
                    && v.className().equals(cls) && v.name().equals(prop)) {
                return v.type();
            }
        }
        return null;
    }

    /** Class behind {@code $var->}: the current class for {@code $this}, else a scope lookup. */
    private String objectClass(String obj) {
        return obj.equals("$this") ? className : stripType(scopeVarType(obj));
    }

    /** {@code ?Foo}, {@code \Foo} become {@code Foo}. */
    static String stripType(String t) {
        if (t == null) return null;
        int i = 0;
        while (i < t.length() && (t.charAt(i) == '?' || t.charAt(i) == '\\')) {
            i++;
        }
        return t.substring(i);
    }
}
