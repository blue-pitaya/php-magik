package dev.bluepitaya.phpmagik;

import dev.bluepitaya.phpmagik.phpsymbol.ClassKind;
import dev.bluepitaya.phpmagik.phpsymbol.PhpClass;
import dev.bluepitaya.phpmagik.phpsymbol.PhpFunctionDefinition;
import dev.bluepitaya.phpmagik.phpsymbol.PhpFunctionUsage;
import dev.bluepitaya.phpmagik.phpsymbol.PhpMethodDefinition;
import dev.bluepitaya.phpmagik.phpsymbol.PhpMethodUsage;
import dev.bluepitaya.phpmagik.phpsymbol.PhpPropertyDefinition;
import dev.bluepitaya.phpmagik.phpsymbol.PhpPropertyUsage;
import dev.bluepitaya.phpmagik.phpsymbol.PhpUseStatement;
import dev.bluepitaya.phpmagik.phpsymbol.PhpVarDefinition;
import dev.bluepitaya.phpmagik.phpsymbol.PhpVarUsage;
import dev.bluepitaya.phpmagik.phpsymbol.UseKind;
import dev.bluepitaya.phpmagik.ts.Node;
import dev.bluepitaya.phpmagik.ts.Nodes;
import dev.bluepitaya.phpmagik.ts.Range;
import org.jspecify.annotations.NullMarked;

import java.nio.charset.StandardCharsets;
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
    private final byte[] source;
    private final List<PhpVarDefinition> varDefinitions = new ArrayList<>();
    private final List<PhpVarUsage> varUsages = new ArrayList<>();
    private final List<PhpFunctionDefinition> functions = new ArrayList<>();
    private final List<PhpFunctionUsage> functionUsages = new ArrayList<>();
    private final List<PhpMethodDefinition> methods = new ArrayList<>();
    private final List<PhpMethodUsage> methodUsages = new ArrayList<>();
    private final List<PhpPropertyDefinition> properties = new ArrayList<>();
    private final List<PhpPropertyUsage> propertyUsages = new ArrayList<>();
    private final List<PhpUseStatement> uses = new ArrayList<>();
    private final List<PhpClass> classes = new ArrayList<>();

    private String ns;
    private PhpClass currentClass;
    private String functionName;

    /** {@code source} must be the bytes the tree was parsed from: nodes index into them. */
    public Indexer(int fileId, byte[] source) {
        this.fileId = fileId;
        this.source = source;
    }

    public List<PhpVarDefinition> varDefinitions() {
        return varDefinitions;
    }

    public List<PhpVarUsage> varUsages() {
        return varUsages;
    }

    public List<PhpFunctionDefinition> functions() {
        return functions;
    }

    public List<PhpFunctionUsage> functionUsages() {
        return functionUsages;
    }

    public List<PhpMethodDefinition> methods() {
        return methods;
    }

    public List<PhpMethodUsage> methodUsages() {
        return methodUsages;
    }

    public List<PhpPropertyDefinition> properties() {
        return properties;
    }

    public List<PhpPropertyUsage> propertyUsages() {
        return propertyUsages;
    }

    public List<PhpUseStatement> uses() {
        return uses;
    }

    public List<PhpClass> classes() {
        return classes;
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

        uses.add(new PhpUseStatement(name, fqn, kind, Range.of(clause), fileId));
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
        ClassKind kind = classKind(root.getType());
        for (Node node : root.getNamedChildren()) {
            switch (node.getType()) {
                /* the only bare name child: extends and implements keep theirs
                 * inside a base_clause / class_interface_clause; it also comes
                 * before the body, so members already have their owner */
                case "name" -> currentClass = pushClass(node.getContent(), kind, node, root);
                case "declaration_list", "enum_declaration_list" -> parseDeclarationList(node);
            }
        }
        currentClass = null;
    }

    private static ClassKind classKind(String nodeType) {
        return switch (nodeType) {
            case "interface_declaration" -> ClassKind.INTERFACE;
            case "trait_declaration" -> ClassKind.TRAIT;
            case "enum_declaration" -> ClassKind.ENUM;
            default -> ClassKind.CLASS;
        };
    }

    private PhpClass pushClass(String name, ClassKind kind, Node nameNode, Node declaration) {
        String fqn = ns == null ? name : ns + "\\" + name;
        PhpClass declared = new PhpClass(name, ns, fqn, kind, Range.of(nameNode),
                Range.of(declaration), fileId);
        classes.add(declared);
        return declared;
    }

    /** The class being parsed, as the string the usage indexes store. */
    private String className() {
        return currentClass == null ? null : currentClass.name();
    }

    private void parseDeclarationList(Node root) {
        for (Node node : root.getNamedChildren()) {
            switch (node.getType()) {
                case "method_declaration" -> parseFunctionLike(node);
                case "property_declaration" -> parsePropertyDeclaration(node);
            }
        }
    }

    /** One {@code public int $a, $b;} declares a property per element, all of that type. */
    private void parsePropertyDeclaration(Node root) {
        if (currentClass == null) return;

        String type = Nodes.text(root.getChildByFieldName("type"));
        for (Node node : root.getNamedChildren()) {
            if (!"property_element".equals(node.getType())) continue;
            Node name = node.getChildByFieldName("name");
            if (name == null) continue;
            properties.add(new PhpPropertyDefinition(name.getContent(), currentClass, type,
                    Range.of(name), fileId));
        }
    }

    private void parseFunctionLike(Node root) {
        Node name = root.getChildByFieldName("name");
        if (name == null) return;

        functionName = name.getContent();
        collectVariables(root);

        String returnType = inferReturnType(root);
        String signature = signature(root, returnType);
        String doc = docComment(root);
        /* position the declaration at its own name, not the declaration's
         * start, so hovering/go-to-def on the name lines up like every other
         * symbol */
        Range range = Range.of(name);
        Range scope = Range.of(root);

        if (currentClass == null) {
            functions.add(new PhpFunctionDefinition(functionName, ns, returnType,
                    signature, doc, range, scope, fileId));
        } else {
            methods.add(new PhpMethodDefinition(functionName, currentClass, returnType,
                    signature, doc, range, scope, fileId));
        }
        functionName = null;
    }

    /**
     * The declaration as written, from its modifiers up to but not including the
     * body. Sliced out of the source rather than rebuilt, so types and defaults
     * read exactly as the author wrote them - except that {@code returnType} is
     * appended when the source declares none, which is the whole point of
     * showing this on a hover.
     */
    private String signature(Node declaration, String returnType) {
        Node body = declaration.getChildByFieldName("body");
        int start = declaration.getStartByte();
        int end = body != null ? body.getStartByte() : declaration.getEndByte();
        if (start < 0 || end > source.length || end <= start) return null;

        String text = new String(source, start, end - start, StandardCharsets.UTF_8).strip();
        /* an abstract or interface method ends in ";" where a body would be */
        if (text.endsWith(";")) {
            text = text.substring(0, text.length() - 1).strip();
        }
        if (declaration.getChildByFieldName("return_type") == null && returnType != null) {
            text = text + ": " + returnType;
        }
        return text;
    }

    /**
     * The PHPDoc block above {@code declaration}, its {@code /**} and leading
     * {@code *} markers stripped, or {@code null} if there is none. Tags are left
     * exactly as written - this locates the block, it does not parse PHPDoc.
     */
    private static String docComment(Node declaration) {
        Node prev = declaration.getPrevSibling();
        /* attributes sit between the doc block and the declaration */
        while (prev != null && "attribute_list".equals(prev.getType())) {
            prev = prev.getPrevSibling();
        }
        if (prev == null || !"comment".equals(prev.getType())) return null;

        String text = prev.getContent();
        /* "/*" alone is an ordinary block comment, not a doc block */
        if (text == null || !text.startsWith("/**")) return null;
        return stripDocMarkers(text);
    }

    private static String stripDocMarkers(String comment) {
        String body = comment.substring("/**".length());
        if (body.endsWith("*/")) {
            body = body.substring(0, body.length() - "*/".length());
        }

        var out = new StringBuilder();
        for (String line : body.split("\n", -1)) {
            String stripped = line.strip();
            if (stripped.startsWith("*")) {
                stripped = stripped.substring(1).strip();
            }
            if (out.isEmpty() && stripped.isEmpty()) continue;
            out.append(stripped).append('\n');
        }

        String doc = out.toString().strip();
        return doc.isEmpty() ? null : doc;
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

    /**
     * {@code $obj->foo} becomes a property usage on the object's class. An
     * object of unknown class is skipped: there would be nothing to match the
     * access against.
     */
    private void collectPropertyAccess(Node node) {
        Node obj = node.getChildByFieldName("object");
        Node name = node.getChildByFieldName("name");
        if (!isVariableAccess(obj, name)) return;

        String cls = objectClass(obj.getContent());
        if (cls == null) return;

        /* "$"-prefixed to match the property declaration, though the source
         * text after -> has no "$" */
        propertyUsages.add(new PhpPropertyUsage("$" + name.getContent(), cls,
                Range.of(name), fileId));
    }

    private void collectFunctionCall(Node node) {
        Node fn = node.getChildByFieldName("function");
        String type = Nodes.type(fn);
        if ("name".equals(type) || "qualified_name".equals(type)) {
            pushCall(fn.getContent(), fn);
        }
    }

    /**
     * {@code $obj->foo(...)} becomes a method usage on the object's class. An
     * object of unknown class is skipped: there would be nothing to match the
     * call against.
     */
    private void collectMethodCall(Node node) {
        Node obj = node.getChildByFieldName("object");
        Node name = node.getChildByFieldName("name");
        if (!isVariableAccess(obj, name)) return;

        String cls = objectClass(obj.getContent());
        if (cls == null) return;

        methodUsages.add(new PhpMethodUsage(name.getContent(), cls, Range.of(name), fileId));
    }

    private static boolean isVariableAccess(Node obj, Node name) {
        return "variable_name".equals(Nodes.type(obj)) && "name".equals(Nodes.type(name));
    }

    /**
     * A parameter or an assignment gives the variable a value, so it is a
     * definition; everything else only reads it.
     */
    private void addVar(Node node) {
        String parentType = Nodes.type(node.getParent());
        boolean parameter = parentType != null && PARAMETER_TYPES.contains(parentType);
        if (parameter || isAssignmentTarget(node)) {
            varDefinitions.add(new PhpVarDefinition(node.getContent(), ns, functionName,
                    inferType(node), Range.of(node), fileId));
        } else {
            varUsages.add(new PhpVarUsage(node.getContent(), ns, functionName, Range.of(node),
                    fileId));
        }
    }

    private static boolean isAssignmentTarget(Node node) {
        Node parent = node.getParent();
        if (parent == null || !"assignment_expression".equals(parent.getType())) return false;
        Node left = parent.getChildByFieldName("left");
        return left != null && left.equals(node);
    }

    /** A usage: the call site says nothing about the function beyond its name. */
    private void pushCall(String name, Node node) {
        functionUsages.add(new PhpFunctionUsage(name, ns, Range.of(node), fileId));
    }

    private String inferType(Node node) {
        Node parent = node.getParent();
        if (parent == null) return null;

        /* declared type on parameter (incl. variadic/promoted) */
        if (parent.getType().contains("parameter")) {
            return Nodes.text(parent.getChildByFieldName("type"));
        }
        return isAssignmentTarget(node)
                ? inferExprType(parent.getChildByFieldName("right"))
                : null;
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
        for (PhpVarDefinition def : varDefinitions.reversed()) {
            if (def.type() != null && def.name().equals(name)
                    && Objects.equals(def.functionName(), functionName)) {
                return def.type();
            }
        }
        return null;
    }

    /** Declared type of property {@code prop} ({@code "$x"}) on already-collected class {@code cls}. */
    private String classPropType(String cls, String prop) {
        for (PhpPropertyDefinition p : properties) {
            if (p.owner().name().equals(cls) && p.name().equals(prop)) return p.type();
        }
        return null;
    }

    /** Class behind {@code $var->}: the current class for {@code $this}, else a scope lookup. */
    private String objectClass(String obj) {
        return obj.equals("$this") ? className() : stripType(scopeVarType(obj));
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
