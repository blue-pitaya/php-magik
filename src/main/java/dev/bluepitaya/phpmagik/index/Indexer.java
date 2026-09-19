package dev.bluepitaya.phpmagik.index;

import dev.bluepitaya.phpmagik.ts.Node;
import dev.bluepitaya.phpmagik.ts.Nodes;
import dev.bluepitaya.phpmagik.ts.Point;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Walks one file's syntax tree and records every variable and function it finds,
 * declarations and usages alike, along with whatever type could be inferred.
 *
 * <p>One instance indexes one file: the namespace, class and function fields
 * track where the walk currently is, so entries can be attributed to their
 * enclosing scope as they are pushed.
 */
public final class Indexer {

    private static final String BOOL_OPS =
            "== != === !== < > <= >= <=> && || and or xor instanceof";

    private final int fileId;
    private final List<PhpVar> vars = new ArrayList<>();
    private final List<PhpFunction> funcs = new ArrayList<>();

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

    /** {@code root} is the {@code (program)} node. */
    public void parseProgram(Node root) {
        int count = root.getNamedChildCount();
        for (int i = 0; i < count; i++) {
            Node node = root.getNamedChild(i);
            switch (node.getType()) {
                case "function_definition" -> parseFunctionLike(node);
                case "class_declaration", "interface_declaration", "trait_declaration",
                        "enum_declaration" -> parseClassDeclaration(node);
                case "namespace_definition" -> parseNamespaceDefinition(node);
                default -> collectVariables(node);
            }
        }
    }

    private void parseNamespaceDefinition(Node root) {
        int count = root.getNamedChildCount();
        for (int i = 0; i < count; i++) {
            Node node = root.getNamedChild(i);
            switch (node.getType()) {
                case "namespace_name" -> ns = node.getContent();
                case "compound_statement" -> parseProgram(node);
                default -> {
                }
            }
        }
    }

    private void parseClassDeclaration(Node root) {
        int count = root.getNamedChildCount();
        for (int i = 0; i < count; i++) {
            Node node = root.getNamedChild(i);
            switch (node.getType()) {
                case "name" -> className = node.getContent();
                case "declaration_list", "enum_declaration_list" -> parseDeclarationList(node);
                default -> {
                }
            }
        }
        className = null;
    }

    private void parseDeclarationList(Node root) {
        int count = root.getNamedChildCount();
        for (int i = 0; i < count; i++) {
            Node node = root.getNamedChild(i);
            switch (node.getType()) {
                case "method_declaration" -> parseFunctionLike(node);
                case "property_declaration" -> collectVariables(node);
                default -> {
                }
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
        pushFunction(functionName, inferReturnType(root), FuncKind.DEF, name);
        functionName = null;
    }

    /** Records every {@code (variable_name)} in the subtree: declarations and usages. */
    private void collectVariables(Node root) {
        String t = root.getType();

        if (t.equals("variable_name")) {
            addVar(root);
            return;
        }
        if (t.equals("member_access_expression")) {
            Node obj = root.getChildByFieldName("object");
            Node name = root.getChildByFieldName("name");
            if (isVariableAccess(obj, name)) {
                String o = obj.getContent();
                String cls = objectClass(o);
                if (cls != null) {
                    addObjProp(name, cls, o.equals("$this") ? VarKind.THIS : VarKind.OBJ);
                }
            }
            /* fall through: recurse for the object var itself */
        }
        if (t.equals("function_call_expression")) {
            Node fn = root.getChildByFieldName("function");
            String ft = Nodes.type(fn);
            if ("name".equals(ft) || "qualified_name".equals(ft)) {
                pushFunction(fn.getContent(), null, FuncKind.CALL, fn);
            }
            /* fall through: recurse for arguments */
        }
        if (t.equals("member_call_expression")) {
            Node obj = root.getChildByFieldName("object");
            Node name = root.getChildByFieldName("name");
            if (isVariableAccess(obj, name)) {
                String cls = objectClass(obj.getContent());
                if (cls != null) {
                    addMethodCall(name, cls);
                }
            }
            /* fall through: recurse for object and arguments */
        }

        int count = root.getNamedChildCount();
        for (int i = 0; i < count; i++) {
            collectVariables(root.getNamedChild(i));
        }
    }

    private static boolean isVariableAccess(Node obj, Node name) {
        return "variable_name".equals(Nodes.type(obj)) && "name".equals(Nodes.type(name));
    }

    private void addVar(Node node) {
        Node parent = node.getParent();
        String ptype = Nodes.type(parent);
        VarKind kind;
        if ("property_element".equals(ptype)) {
            kind = VarKind.PROPERTY;
        } else if ("simple_parameter".equals(ptype) || "variadic_parameter".equals(ptype)
                || "property_promotion_parameter".equals(ptype)) {
            kind = VarKind.PARAM;
        } else {
            kind = VarKind.USE;
        }
        pushVar(node.getContent(), inferType(node), kind, node);
    }

    /** {@code $obj->foo} becomes a property usage {@code "$foo"} on class {@code cls}. */
    private void addObjProp(Node nameNode, String cls, VarKind kind) {
        String name = "$" + nameNode.getContent();
        String type = classPropType(cls, name);
        String saved = className;
        className = cls; /* attribute entry to owning class */
        pushVar(name, type, kind, nameNode);
        className = saved;
    }

    /** {@code $obj->foo(...)} becomes a method usage on class {@code cls}. */
    private void addMethodCall(Node nameNode, String cls) {
        String saved = className;
        className = cls; /* attribute call to owning class */
        pushFunction(nameNode.getContent(), null, FuncKind.METHOD, nameNode);
        className = saved;
    }

    private void pushVar(String name, String type, VarKind kind, Node node) {
        Point p = node.getStartPoint();
        vars.add(new PhpVar(name, ns, className, functionName, type, kind,
                p.getRow(), p.getColumn(), fileId));
    }

    private void pushFunction(String name, String returnType, FuncKind kind, Node node) {
        Point p = node.getStartPoint();
        funcs.add(new PhpFunction(name, ns, className, null, kind, returnType,
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
        if (op.equals("%") || op.equals("<<") || op.equals(">>")
                || op.equals("&") || op.equals("|") || op.equals("^")) {
            return "int";
        }
        if (BOOL_OPS.contains(op)) return "bool";

        if (op.equals("+") || op.equals("-") || op.equals("*") || op.equals("/")
                || op.equals("**")) {
            String left = inferExprType(expr.getChildByFieldName("left"));
            String right = inferExprType(expr.getChildByFieldName("right"));
            if (left == null || right == null) return null;
            boolean isFloat = left.equals("float") || right.equals("float") || op.equals("/");
            return isFloat ? "float" : "int";
        }
        return null;
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

        int count = root.getNamedChildCount();
        for (int i = 0; i < count; i++) {
            String r = inferFromReturns(root.getNamedChild(i));
            if (r != null) return r;
        }
        return null;
    }

    /** Latest known type of variable {@code name} in the current function scope. */
    private String scopeVarType(String name) {
        for (int i = vars.size() - 1; i >= 0; i--) {
            PhpVar v = vars.get(i);
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
