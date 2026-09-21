package dev.bluepitaya.phpmagik;

import dev.bluepitaya.phpmagik.phpsymbol.ClassKind;
import dev.bluepitaya.phpmagik.phpsymbol.PhpClassDefinition;
import dev.bluepitaya.phpmagik.phpsymbol.PhpClassUsage;
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
import org.jspecify.annotations.Nullable;

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

    /** A class or function named outright, as opposed to one named by a variable. */
    private static final Set<String> NAME_TYPES = Set.of("name", "qualified_name");

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
    private final List<PhpClassDefinition> classes = new ArrayList<>();
    private final List<PhpClassUsage> classUsages = new ArrayList<>();

    private @Nullable String ns;

    /** source must be the bytes the tree was parsed from: nodes index into them. */
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

    public List<PhpClassDefinition> classes() {
        return classes;
    }

    public List<PhpClassUsage> classUsages() {
        return classUsages;
    }

    public void index(Node root) {
        visit(root, new Scope(null, null, new Returns()));
    }

    private record Scope(@Nullable PhpClassDefinition cls, @Nullable String function,
                         Returns returns) {

        Scope inClass(@Nullable PhpClassDefinition declared) {
            return new Scope(declared, function, returns);
        }

        /** A function-like body: its own returns, and the scope its locals belong to. */
        Scope inBody(@Nullable String function) {
            return new Scope(cls, function, new Returns());
        }
    }

    private static final class Returns {

        private @Nullable String type;
        private @Nullable Range source;

        void record(@Nullable String returnType, @Nullable Range valueSource) {
            if (type == null) type = returnType;
            if (source == null) source = valueSource;
        }
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
            /* function-like as well, and their returns are their own; a closure's
             * locals stay in the scope around it, since nothing names it */
            case "anonymous_function", "arrow_function", "property_hook" ->
                    visitChildren(node, scope.inBody(scope.function()));

            case "simple_parameter", "variadic_parameter", "property_promotion_parameter" ->
                    visitParameter(node, scope);
            case "assignment_expression", "reference_assignment_expression" -> visitAssignment(node, scope);
            case "static_variable_declaration" -> visitStaticVariable(node, scope);
            case "catch_clause" -> visitCatchClause(node, scope);
            case "foreach_statement" -> visitForeach(node, scope);
            case "global_declaration" -> visitGlobalDeclaration(node, scope);
            case "return_statement" -> visitReturn(node, scope);

            /* the places a class is named rather than declared */
            case "named_type" -> addClassUsage(Nodes.namedChild(node, 0));
            case "base_clause", "class_interface_clause", "use_declaration" ->
                    visitClassNames(node, scope);
            case "object_creation_expression", "class_constant_access_expression", "attribute" -> {
                addClassUsage(Nodes.namedChild(node, 0));
                visitChildren(node, scope);
            }
            case "scoped_call_expression", "scoped_property_access_expression" -> {
                addClassUsage(node.getChildByFieldName("scope"));
                visitChildren(node, scope);
            }
            case "binary_expression" -> visitBinary(node, scope);

            /* nothing above bound it, so it is read here */
            case "variable_name" -> varUsages.add(new PhpVarUsage(node.getContent(), ns,
                    scope.function(), Range.of(node), fileId));
            case "member_access_expression", "nullsafe_member_access_expression" -> {
                addPropertyUsage(node, scope);
                visitChildren(node, scope);
            }
            case "member_call_expression", "nullsafe_member_call_expression" -> {
                addMethodUsage(node, scope);
                visitChildren(node, scope);
            }
            case "function_call_expression" -> {
                addFunctionUsage(node);
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

    private void visitNamespace(Node node, Scope scope) {
        String outer = ns;
        ns = Nodes.text(node.getChildByFieldName("name"));

        Node body = node.getChildByFieldName("body");
        if (body == null) return;
        visit(body, scope);
        ns = outer;
    }

    private void visitUseDeclaration(Node node) {
        UseKind declared = useKind(node);

        Node group = node.getChildByFieldName("body");
        if (group == null) {
            for (Node clause : node.getNamedChildren()) {
                addUse(clause, null, declared);
            }
            return;
        }
        String prefix = Nodes.text(Nodes.namedChild(node, 0));
        for (Node clause : group.getNamedChildren()) {
            addUse(clause, prefix, declared);
        }
    }

    private void addUse(Node clause, @Nullable String prefix, UseKind declared) {
        /* "path" or "path as alias": the path comes first, and the alias is the
         * only part with a field */
        Node path = Nodes.namedChild(clause, 0);
        if (path == null) return;

        Node alias = clause.getChildByFieldName("alias");
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

    private void visitClassDeclaration(Node node, Scope scope) {
        Node name = node.getChildByFieldName("name");
        PhpClassDefinition declared = null;
        if (name != null) {
            String simple = name.getContent();
            declared = new PhpClassDefinition(simple, ns, ns == null ? simple : ns + "\\" + simple,
                    classKind(node.getType()), Range.of(name), Range.of(node), fileId);
            classes.add(declared);
        }
        visitChildren(node, scope.inClass(declared));
    }

    private static ClassKind classKind(String nodeType) {
        return switch (nodeType) {
            case "interface_declaration" -> ClassKind.INTERFACE;
            case "trait_declaration" -> ClassKind.TRAIT;
            case "enum_declaration" -> ClassKind.ENUM;
            default -> ClassKind.CLASS;
        };
    }

    /** One {@code public int $a, $b;} declares a property per element, all of that type. */
    private void visitPropertyDeclaration(Node node, Scope scope) {
        String type = Nodes.text(node.getChildByFieldName("type"));
        for (Node child : node.getNamedChildren()) {
            if (!"property_element".equals(child.getType())) {
                visit(child, scope);
                continue;
            }
            Node name = child.getChildByFieldName("name");
            addProperty(name, scope.cls(), type);
            visitChildrenExcept(child, name, scope);
        }
    }

    private void visitFunction(Node node, Scope scope) {
        Node name = node.getChildByFieldName("name");
        Returns returns = visitDeclarationBody(node, scope, name);
        if (name == null) return;

        String returnType = returnType(node, returns);
        functions.add(new PhpFunctionDefinition(name.getContent(), ns, returnType, returns.source,
                signature(node, returnType), docComment(node), Range.of(name), Range.of(node),
                fileId));
    }

    private void visitMethod(Node node, Scope scope) {
        Node name = node.getChildByFieldName("name");
        Returns returns = visitDeclarationBody(node, scope, name);
        PhpClassDefinition owner = scope.cls();
        /* a method of an anonymous class belongs to no class a call site could
         * name, so there is nothing to record it under */
        if (name == null || owner == null) return;

        String returnType = returnType(node, returns);
        methods.add(new PhpMethodDefinition(name.getContent(), owner, returnType, returns.source,
                signature(node, returnType), docComment(node), Range.of(name), Range.of(node),
                fileId));
    }

    /**
     * Walks the declaration as its own scope - parameters and body both, which is
     * where its locals come from - and hands back what its returns said. The
     * declaration itself is recorded afterwards: it is only then that its return
     * type is known.
     */
    private Returns visitDeclarationBody(Node node, Scope scope, @Nullable Node name) {
        Scope inside = scope.inBody(Nodes.text(name));
        visitChildren(node, inside);
        return inside.returns();
    }

    private static @Nullable String returnType(Node declaration, Returns returns) {
        String declared = Nodes.text(declaration.getChildByFieldName("return_type"));
        return declared != null ? declared : returns.type;
    }

    private void visitReturn(Node node, Scope scope) {
        /* "return" takes one unfielded expression, or none at all */
        Node value = Nodes.namedChild(node, 0);
        scope.returns().record(value == null ? "void" : inferExprType(value, scope),
                valueSource(value));
        visitChildren(node, scope);
    }

    /**
     * A parameter names its variable and may type it. A
     * {@code property_promotion_parameter} - {@code private Engine $engine} in a
     * constructor - declares the property of that name as well.
     */
    private void visitParameter(Node node, Scope scope) {
        Node declared = node.getChildByFieldName("name");
        /* a by-reference parameter wraps its variable: "&$x" */
        Node name = "by_ref".equals(Nodes.type(declared))
                ? Nodes.namedChild(declared, 0)
                : declared;

        if ("variable_name".equals(Nodes.type(name))) {
            String type = Nodes.text(node.getChildByFieldName("type"));
            addVarDefinition(name, type, null, scope);
            if ("property_promotion_parameter".equals(node.getType())) {
                addProperty(name, scope.cls(), type);
            }
            visitChildrenExcept(node, declared, scope);
            return;
        }
        visitChildren(node, scope);
    }

    /**
     * {@code $x = ...} is what gives {@code $x} a value. Assigning to anything
     * else - a property, an array element, a list destructuring - only reads the
     * variables it names, so those go through the walk as usual.
     */
    private void visitAssignment(Node node, Scope scope) {
        Node left = node.getChildByFieldName("left");
        if (!"variable_name".equals(Nodes.type(left))) {
            visitChildren(node, scope);
            return;
        }
        addAssigned(left, node.getChildByFieldName("right"), scope);
        visitChildrenExcept(node, left, scope);
    }

    /** {@code static $x = 1;} keeps its value between calls, and is a local like any other. */
    private void visitStaticVariable(Node node, Scope scope) {
        Node name = node.getChildByFieldName("name");
        if (name != null) addAssigned(name, node.getChildByFieldName("value"), scope);
        visitChildrenExcept(node, name, scope);
    }

    /** {@code catch (RuntimeException $e)} binds {@code $e} and says what it is. */
    private void visitCatchClause(Node node, Scope scope) {
        Node name = node.getChildByFieldName("name");
        if (name != null) {
            /* one clause can list several classes, and then the type is that list
             * as written - no single class to match it to */
            addVarDefinition(name, Nodes.text(node.getChildByFieldName("type")), null, scope);
        }
        visitChildrenExcept(node, name, scope);
    }

    /**
     * {@code foreach (expr as $v)} binds what it takes out of the collection. The
     * grammar fields only the body, leaving the collection first and the binding
     * second - a variable, a {@code &$ref}, a list destructuring, or a
     * {@code pair} of key and value. What an element holds is unknown, so a
     * binding is typeless.
     */
    private void visitForeach(Node node, Scope scope) {
        Node bound = Nodes.namedChild(node, 1);
        bindForeachValue(bound, scope);
        visitChildrenExcept(node, bound, scope);
    }

    private void bindForeachValue(@Nullable Node node, Scope scope) {
        if (node == null) return;
        switch (Nodes.type(node)) {
            case "variable_name" -> addVarDefinition(node, null, null, scope);
            case "by_ref" -> bindForeachValue(Nodes.namedChild(node, 0), scope);
            case "pair" -> {
                bindForeachValue(Nodes.namedChild(node, 0), scope);
                bindForeachValue(Nodes.namedChild(node, 1), scope);
            }
            /* a list destructuring, or a key that is an expression rather than a
             * name: nothing this binds, but still something to walk */
            default -> visit(node, scope);
        }
    }

    /** {@code global $a, $b;} brings each name into the function's scope. */
    private void visitGlobalDeclaration(Node node, Scope scope) {
        for (Node child : node.getNamedChildren()) {
            if ("variable_name".equals(child.getType())) {
                addVarDefinition(child, null, null, scope);
            } else {
                visit(child, scope);
            }
        }
    }

    private void addVarDefinition(Node name, @Nullable String type, @Nullable Range valueSource,
                                  Scope scope) {
        varDefinitions.add(new PhpVarDefinition(name.getContent(), ns, scope.function(), type,
                valueSource, Range.of(name), fileId));
    }

    /** A variable given whatever an expression evaluates to. */
    private void addAssigned(Node name, @Nullable Node value, Scope scope) {
        addVarDefinition(name, inferExprType(value, scope), valueSource(value), scope);
    }

    private void addProperty(@Nullable Node name, @Nullable PhpClassDefinition owner,
                             @Nullable String type) {
        if (name == null || owner == null) return;
        properties.add(new PhpPropertyDefinition(name.getContent(), owner, type, Range.of(name),
                fileId));
    }

    /**
     * {@code $obj->foo} is a property of whichever class the object turns out to
     * be, and which one that is, is not settled here: only {@code $this} can be
     * answered from one file walked in one direction. Every other object is
     * recorded as where it came from, for the type inference to follow.
     */
    private void addPropertyUsage(Node node, Scope scope) {
        Node name = memberName(node);
        if (name == null) return;

        /* "$"-prefixed to match the declaration, though the source after -> has
         * no "$" */
        propertyUsages.add(new PhpPropertyUsage("$" + name.getContent(), thisClass(node, scope),
                objectSource(node), Range.of(name), fileId));
    }

    /** {@code $obj->foo(...)} is a method of that same class, on the same terms. */
    private void addMethodUsage(Node node, Scope scope) {
        Node name = memberName(node);
        if (name == null) return;

        methodUsages.add(new PhpMethodUsage(name.getContent(), thisClass(node, scope),
                objectSource(node), Range.of(name), fileId));
    }

    /** The class {@code $this} stands for, and {@code null} for any other object. */
    private @Nullable String thisClass(Node access, Scope scope) {
        Node object = access.getChildByFieldName("object");
        boolean isThis = "variable_name".equals(Nodes.type(object))
                && "$this".equals(object.getContent());
        if (!isThis || scope.cls() == null) return null;
        return scope.cls().name();
    }

    /** An object is worth typing on the same terms as any other value. */
    private static @Nullable Range objectSource(Node access) {
        return valueSource(access.getChildByFieldName("object"));
    }

    /** {@code $x instanceof Foo} is the one operator with a class for an operand. */
    private void visitBinary(Node node, Scope scope) {
        if ("instanceof".equals(Nodes.type(node.getChildByFieldName("operator")))) {
            addClassUsage(node.getChildByFieldName("right"));
        }
        visitChildren(node, scope);
    }

    /** {@code extends A, B}, {@code implements A, B} and a trait {@code use T} name classes. */
    private void visitClassNames(Node node, Scope scope) {
        for (Node child : node.getNamedChildren()) {
            if (NAME_TYPES.contains(child.getType())) {
                addClassUsage(child);
            } else {
                visit(child, scope);
            }
        }
    }

    /**
     * A name where the grammar means a class. {@code self}, {@code static} and
     * {@code $cls::} put something else there, and none of those is a name a
     * declaration can be found by.
     */
    private void addClassUsage(@Nullable Node name) {
        String type = Nodes.type(name);
        if (type == null || !NAME_TYPES.contains(type)) return;

        classUsages.add(new PhpClassUsage(name.getContent(), ns, Range.of(name), fileId));
    }

    /** A call says nothing about the function beyond the name it calls. */
    private void addFunctionUsage(Node node) {
        Node called = node.getChildByFieldName("function");
        String type = Nodes.type(called);
        if (type != null && NAME_TYPES.contains(type)) {
            functionUsages.add(new PhpFunctionUsage(called.getContent(), ns, Range.of(called),
                    fileId));
        }
    }

    /**
     * The member as a plain name. {@code $obj->$name} and {@code $obj->{expr}}
     * name theirs at runtime, which is nothing the index can match.
     */
    private static @Nullable Node memberName(Node access) {
        Node name = access.getChildByFieldName("name");
        return "name".equals(Nodes.type(name)) ? name : null;
    }

    /**
     * The class behind {@code $var->}: the one being declared for {@code $this},
     * otherwise whatever the variable was last seen holding.
     */
    private @Nullable String objectClass(Node access, Scope scope) {
        Node object = access.getChildByFieldName("object");
        if (!"variable_name".equals(Nodes.type(object))) return null;

        String name = object.getContent();
        if ("$this".equals(name)) return scope.cls() == null ? null : scope.cls().name();
        return stripType(scopeVarType(name, scope));
    }

    /**
     * The type of an expression, as far as reading that one expression goes:
     * a literal, a {@code new X}, an operator, or a name this file has already
     * recorded a type for.
     */
    private @Nullable String inferExprType(@Nullable Node expr, Scope scope) {
        if (expr == null) return null;
        String type = expr.getType();

        String literal = literalType(type);
        if (literal != null) return literal;

        return switch (type) {
            case "object_creation_expression" -> createdClass(expr);
            case "variable_name" -> scopeVarType(expr.getContent(), scope);
            case "member_access_expression", "nullsafe_member_access_expression" -> memberAccessType(expr, scope);
            case "parenthesized_expression" -> inferExprType(Nodes.namedChild(expr, 0), scope);
            case "unary_op_expression" -> unaryType(expr, scope);
            case "binary_expression" -> binaryType(expr, scope);
            default -> null;
        };
    }

    private static @Nullable String literalType(String nodeType) {
        return switch (nodeType) {
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

    /**
     * {@code new Foo} names its class outright. {@code new $cls}, {@code new
     * (expr)} and {@code new class { }} put something else where the name would
     * be, and none of those is a type.
     */
    private static @Nullable String createdClass(Node expr) {
        Node reference = Nodes.namedChild(expr, 0);
        String type = Nodes.type(reference);
        return type != null && NAME_TYPES.contains(type) ? reference.getContent() : null;
    }

    private @Nullable String memberAccessType(Node expr, Scope scope) {
        Node name = memberName(expr);
        String cls = objectClass(expr, scope);
        if (name == null || cls == null) return null;
        return classPropType(cls, "$" + name.getContent());
    }

    /** {@code -1}, {@code +$x}, {@code !$b}, {@code ~$n} */
    private @Nullable String unaryType(Node expr, Scope scope) {
        String op = Nodes.type(expr.getChildByFieldName("operator"));
        if ("!".equals(op)) return "bool";
        if ("~".equals(op)) return "int";
        return inferExprType(expr.getChildByFieldName("argument"), scope);
    }

    private @Nullable String binaryType(Node expr, Scope scope) {
        String op = Nodes.type(expr.getChildByFieldName("operator"));
        if (op == null) return null;

        if (op.equals(".")) return "string";
        if (INT_OPS.contains(op)) return "int";
        if (BOOL_OPS.contains(op)) return "bool";
        if (!ARITHMETIC_OPS.contains(op)) return null;

        String left = inferExprType(expr.getChildByFieldName("left"), scope);
        String right = inferExprType(expr.getChildByFieldName("right"), scope);
        if (left == null || right == null) return null;

        boolean isFloat = op.equals("/") || left.equals("float") || right.equals("float");
        return isFloat ? "float" : "int";
    }

    /**
     * The range the index recorded for whatever this expression names - a
     * variable, a property read, a call - so the type inference can look that
     * usage up by it later, and {@code null} for anything that names nothing.
     */
    private static @Nullable Range valueSource(@Nullable Node expr) {
        if (expr == null) return null;

        return switch (expr.getType()) {
            case "variable_name" -> Range.of(expr);
            case "function_call_expression" -> {
                Node called = expr.getChildByFieldName("function");
                String type = Nodes.type(called);
                yield type != null && NAME_TYPES.contains(type) ? Range.of(called) : null;
            }
            case "member_access_expression", "nullsafe_member_access_expression",
                 "member_call_expression", "nullsafe_member_call_expression" -> {
                /* whatever the object is: the access was recorded either way, and
                 * which class it is on is settled when it is resolved */
                Node name = memberName(expr);
                yield name == null ? null : Range.of(name);
            }
            case "parenthesized_expression" -> valueSource(Nodes.namedChild(expr, 0));
            default -> null;
        };
    }

    /** Latest type this file has recorded for {@code name} in the scope being walked. */
    private @Nullable String scopeVarType(String name, Scope scope) {
        for (PhpVarDefinition def : varDefinitions.reversed()) {
            if (def.type() != null && def.name().equals(name)
                    && Objects.equals(def.functionName(), scope.function())) {
                return def.type();
            }
        }
        return null;
    }

    /** Declared type of property {@code prop} ({@code "$x"}) on already-recorded class {@code cls}. */
    private @Nullable String classPropType(String cls, String prop) {
        for (PhpPropertyDefinition p : properties) {
            if (p.owner().name().equals(cls) && p.name().equals(prop)) return p.type();
        }
        return null;
    }

    /**
     * The declaration as written, from its modifiers up to but not including the
     * body, which the grammar fields on everything that has one. Sliced out of the
     * source rather than rebuilt, so types and defaults read exactly as the author
     * wrote them - except that {@code returnType} is appended when the source
     * declares none, which is the whole point of showing this on a hover.
     */
    private @Nullable String signature(Node declaration, @Nullable String returnType) {
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
     *
     * <p>The block is the previous sibling because a comment is an extra, and it
     * is the declaration's own previous sibling even when attributes are in
     * between: the grammar makes those the first thing inside the declaration.
     */
    private static @Nullable String docComment(Node declaration) {
        Node prev = declaration.getPrevSibling();
        if (prev == null || !"comment".equals(prev.getType())) return null;

        String text = prev.getContent();
        /* "/*" alone is an ordinary block comment, not a doc block */
        if (text == null || !text.startsWith("/**")) return null;
        return stripDocMarkers(text);
    }

    private static @Nullable String stripDocMarkers(String comment) {
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

    /** {@code ?Foo}, {@code \Foo} become {@code Foo}. */
    private static @Nullable String stripType(@Nullable String t) {
        if (t == null) return null;
        int i = 0;
        while (i < t.length() && (t.charAt(i) == '?' || t.charAt(i) == '\\')) {
            i++;
        }
        return t.substring(i);
    }
}
