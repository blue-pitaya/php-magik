package dev.bluepitaya.phpmagik.resolver;

import dev.bluepitaya.phpmagik.PhpFile;
import dev.bluepitaya.phpmagik.PhpNodes;
import dev.bluepitaya.phpmagik.Workspace;
import dev.bluepitaya.phpmagik.phpsymbol.PhpFunctionDefinition;
import dev.bluepitaya.phpmagik.phpsymbol.PhpFunctionUsage;
import dev.bluepitaya.phpmagik.phpsymbol.PhpMethodDefinition;
import dev.bluepitaya.phpmagik.phpsymbol.PhpMethodUsage;
import dev.bluepitaya.phpmagik.phpsymbol.PhpPropertyDefinition;
import dev.bluepitaya.phpmagik.phpsymbol.PhpPropertyUsage;
import dev.bluepitaya.phpmagik.phpsymbol.PhpSymbol;
import dev.bluepitaya.phpmagik.phpsymbol.PhpVarDefinition;
import dev.bluepitaya.phpmagik.phpsymbol.PhpVarUsage;
import dev.bluepitaya.phpmagik.ts.Node;
import dev.bluepitaya.phpmagik.ts.Nodes;
import dev.bluepitaya.phpmagik.ts.Point;
import dev.bluepitaya.phpmagik.ts.Range;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

@NullMarked
public final class TypeInference {

    private static final Set<String> BOOL_OPS = Set.of(
            "==", "!=", "===", "!==", "<", ">", "<=", ">=", "<=>",
            "&&", "||", "and", "or", "xor", "instanceof");

    private static final Set<String> INT_OPS = Set.of("%", "<<", ">>", "&", "|", "^");

    private static final Set<String> ARITHMETIC_OPS = Set.of("+", "-", "*", "/", "**");

    private final Workspace workspace;
    private final FunctionResolver functions;

    public TypeInference(Workspace workspace, FunctionResolver functions) {
        this.workspace = workspace;
        this.functions = functions;
    }

    public @Nullable String typeOf(PhpVarUsage usage) {
        return typeOfVariable(usage.file(), usage.functionName(), usage.name(),
                usage.range().start(), new HashSet<>());
    }

    public @Nullable String typeOf(PhpVarDefinition def) {
        return typeOf(def, new HashSet<>());
    }

    public @Nullable String typeOf(PhpPropertyUsage usage) {
        return typeOfSymbol(usage, new HashSet<>());
    }

    public @Nullable String typeOf(PhpPropertyDefinition def) {
        return def.type();
    }

    public @Nullable String returnTypeOf(PhpFunctionDefinition func) {
        return returnType(func.returnType(), func.file(), func.returnSource(), new HashSet<>());
    }

    public @Nullable String returnTypeOf(PhpMethodDefinition method) {
        return returnType(method.returnType(), method.file(), method.returnSource(),
                new HashSet<>());
    }

    public @Nullable PhpPropertyDefinition declarationOf(PhpPropertyUsage usage) {
        return declarationOf(usage, new HashSet<>());
    }

    public @Nullable PhpMethodDefinition declarationOf(PhpMethodUsage usage) {
        return declarationOf(usage, new HashSet<>());
    }

    public @Nullable String classOf(PhpPropertyUsage usage) {
        return classOf(usage.className(), usage.file(), usage.objectSource(), new HashSet<>());
    }

    public @Nullable String classOf(PhpMethodUsage usage) {
        return classOf(usage.className(), usage.file(), usage.objectSource(), new HashSet<>());
    }

    private @Nullable PhpPropertyDefinition declarationOf(PhpPropertyUsage usage,
                                                          Set<PhpSymbol> visiting) {
        String cls = classOf(usage.className(), usage.file(), usage.objectSource(), visiting);
        if (cls == null) return null;

        for (PhpPropertyDefinition declared : workspace.symbols().properties()) {
            if (declared.name().equals(usage.name()) && declared.owner().name().equals(cls)) {
                return declared;
            }
        }
        return null;
    }

    private @Nullable PhpMethodDefinition declarationOf(PhpMethodUsage usage,
                                                        Set<PhpSymbol> visiting) {
        String cls = classOf(usage.className(), usage.file(), usage.objectSource(), visiting);
        if (cls == null) return null;

        for (PhpMethodDefinition declared : workspace.symbols().methods()) {
            if (declared.name().equals(usage.name()) && declared.owner().name().equals(cls)) {
                return declared;
            }
        }
        return null;
    }

    private @Nullable String classOf(@Nullable String className, PhpFile file,
                                     @Nullable Range objectSource, Set<PhpSymbol> visiting) {
        if (className != null) return className;

        String type = sourceType(file, objectSource, visiting);
        return type == null ? null : simpleClassName(type);
    }

    private static String simpleClassName(String type) {
        int last = type.lastIndexOf('\\');
        String name = last < 0 ? type : type.substring(last + 1);
        return name.startsWith("?") ? name.substring(1) : name;
    }

    private @Nullable String typeOfVariable(PhpFile file, @Nullable String functionName, String name,
                                            Point at, Set<PhpSymbol> visiting) {
        var candidates = new ArrayList<PhpVarDefinition>();
        for (PhpVarDefinition def : workspace.symbols().varDefinitions()) {
            if (def.file() != file || !def.name().equals(name)) continue;
            if (!Objects.equals(def.functionName(), functionName)) continue;
            if (def.range().start().compareTo(at) > 0) continue;
            candidates.add(def);
        }
        candidates.sort(Comparator.comparing((PhpVarDefinition def) -> def.range().start())
                .reversed());

        for (PhpVarDefinition def : candidates) {
            String type = typeOf(def, visiting);
            if (type != null) return type;
        }
        return null;
    }

    private @Nullable String typeOf(PhpVarDefinition def, Set<PhpSymbol> visiting) {
        /* a declared type is an answer on its own: nothing is followed to reach
         * it, so there is no path it could close a loop on */
        if (def.type() != null) return def.type();

        if (!visiting.add(def)) return null;
        try {
            return sourceType(def.file(), def.valueSource(), visiting);
        } finally {
            visiting.remove(def);
        }
    }

    private @Nullable String sourceType(PhpFile file, @Nullable Range source,
                                        Set<PhpSymbol> visiting) {
        if (source == null) return null;

        PhpSymbol symbol = symbolAt(file, source);
        if (symbol != null) return typeOfSymbol(symbol, visiting);

        return inferExprType(file, nodeAt(file, source), visiting);
    }

    private @Nullable String inferExprType(PhpFile file, @Nullable Node expr,
                                           Set<PhpSymbol> visiting) {
        if (expr == null) return null;
        String type = expr.getType();
        if (type == null) return null;

        String literal = literalType(type);
        if (literal != null) return literal;

        return switch (type) {
            case "object_creation_expression" -> createdClass(expr);
            /* the statement stands in for a "return;", which returns nothing */
            case "return_statement" -> "void";
            case "parenthesized_expression" -> subExprType(file, Nodes.namedChild(expr, 0), visiting);
            case "unary_op_expression" -> unaryType(file, expr, visiting);
            case "binary_expression" -> binaryType(file, expr, visiting);
            default -> null;
        };
    }

    private @Nullable String subExprType(PhpFile file, @Nullable Node expr,
                                         Set<PhpSymbol> visiting) {
        return expr == null ? null : sourceType(file, PhpNodes.valueSource(expr), visiting);
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

    private static @Nullable String createdClass(Node expr) {
        Node reference = Nodes.namedChild(expr, 0);
        String type = Nodes.type(reference);
        return type != null && PhpNodes.NAME_TYPES.contains(type) ? reference.getContent() : null;
    }

    private @Nullable String unaryType(PhpFile file, Node expr, Set<PhpSymbol> visiting) {
        String op = Nodes.type(expr.getChildByFieldName("operator"));
        if ("!".equals(op)) return "bool";
        if ("~".equals(op)) return "int";
        return subExprType(file, expr.getChildByFieldName("argument"), visiting);
    }

    private @Nullable String binaryType(PhpFile file, Node expr, Set<PhpSymbol> visiting) {
        String op = Nodes.type(expr.getChildByFieldName("operator"));
        if (op == null) return null;

        if (op.equals(".")) return "string";
        if (INT_OPS.contains(op)) return "int";
        if (BOOL_OPS.contains(op)) return "bool";
        if (!ARITHMETIC_OPS.contains(op)) return null;

        String left = subExprType(file, expr.getChildByFieldName("left"), visiting);
        String right = subExprType(file, expr.getChildByFieldName("right"), visiting);
        if (left == null || right == null) return null;

        boolean isFloat = op.equals("/") || left.equals("float") || right.equals("float");
        return isFloat ? "float" : "int";
    }

    private static @Nullable Node nodeAt(PhpFile file, Range range) {
        Node found = file.tree().getRootNode().getNamedDescendant(range.start(), range.end());
        return found == null || found.isNull() ? null : found;
    }

    private @Nullable String typeOfSymbol(PhpSymbol symbol, Set<PhpSymbol> visiting) {
        if (symbol instanceof PhpVarDefinition def) {
            return typeOf(def, visiting);
        }
        if (!visiting.add(symbol)) return null;
        try {
            if (symbol instanceof PhpVarUsage usage) {
                return typeOfVariable(usage.file(), usage.functionName(), usage.name(),
                        usage.range().start(), visiting);
            }
            if (symbol instanceof PhpPropertyUsage usage) {
                PhpPropertyDefinition declared = declarationOf(usage, visiting);
                return declared == null ? null : declared.type();
            }
            if (symbol instanceof PhpFunctionUsage usage) {
                PhpFunctionDefinition declared = functions.definitionOf(usage);
                return declared == null ? null : returnType(declared.returnType(), declared.file(),
                        declared.returnSource(), visiting);
            }
            if (symbol instanceof PhpMethodUsage usage) {
                PhpMethodDefinition declared = declarationOf(usage, visiting);
                return declared == null ? null : returnType(declared.returnType(), declared.file(),
                        declared.returnSource(), visiting);
            }
            return null;
        } finally {
            visiting.remove(symbol);
        }
    }

    private @Nullable String returnType(@Nullable String declared, PhpFile file,
                                        @Nullable Range source, Set<PhpSymbol> visiting) {
        return declared != null ? declared : sourceType(file, source, visiting);
    }

    private @Nullable PhpSymbol symbolAt(PhpFile file, Range range) {
        PhpSymbol found = firstAt(workspace.symbols().varUsages(), file, range);
        if (found == null) found = firstAt(workspace.symbols().propertyUsages(), file, range);
        if (found == null) found = firstAt(workspace.symbols().functionUsages(), file, range);
        if (found == null) found = firstAt(workspace.symbols().methodUsages(), file, range);
        return found;
    }

    private static @Nullable PhpSymbol firstAt(List<? extends PhpSymbol> symbols, PhpFile file,
                                               Range range) {
        for (PhpSymbol symbol : symbols) {
            if (symbol.file() == file && symbol.range().equals(range)) return symbol;
        }
        return null;
    }
}
