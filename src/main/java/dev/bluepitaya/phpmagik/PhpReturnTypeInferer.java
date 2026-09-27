package dev.bluepitaya.phpmagik;

import dev.bluepitaya.phpmagik.listener.PhpNameResolver;
import dev.bluepitaya.phpmagik.phpdoc.PhpDoc;
import dev.bluepitaya.phpmagik.phpsymbol.PhpFunctionLike;
import dev.bluepitaya.phpmagik.phpsymbol.PhpSymbolCollection;
import dev.bluepitaya.phpmagik.phpsymbol.PhpType;
import dev.bluepitaya.phpmagik.ts.Node;
import dev.bluepitaya.phpmagik.ts.Nodes;
import dev.bluepitaya.phpmagik.ts.Range;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

@NullMarked
public final class PhpReturnTypeInferer {

    private final PhpTypeInferer expressions = new PhpTypeInferer();

    public static @Nullable PhpType declared(@Nullable Node hint, @Nullable PhpDoc doc, PhpNameResolver names) {
        if (hint != null) {
            return names.type(Nodes.text(hint));
        }
        if (doc == null || doc.returns() == null) {
            return null;
        }
        return names.type(doc.returns().type(), doc.templates());
    }

    public void infer(PhpSymbolCollection symbols, Node root) {
        for (PhpFunctionLike function : symbols.functionLikes()) {
            Range scope = function.scope();
            if (scope == null || hasDeclaredReturn(function)) {
                continue;
            }

            Node declaration = root.getDescendant(scope.start(), scope.end());
            Node body = declaration == null ? null : declaration.getChildByFieldName("body");
            Node statement = body == null ? null : lastReturn(body);
            if (statement == null) {
                continue;
            }

            PhpType type = expressions.typeOf(Nodes.namedChild(statement, 0), function, symbols);
            if (type != null) {
                function.returnType(type);
            }
        }
    }

    private static boolean hasDeclaredReturn(PhpFunctionLike function) {
        PhpDoc doc = function.doc();
        return function.declaredReturnType() != null || (doc != null && doc.returns() != null);
    }

    private static @Nullable Node lastReturn(Node node) {
        for (Node child : node.getNamedChildren().reversed()) {
            Node found = switch (child.getType()) {
                case "return_statement" -> child;
                case "anonymous_function", "arrow_function", "function_definition", "class_declaration",
                     "anonymous_class" -> null;
                case null, default -> lastReturn(child);
            };
            if (found != null) {
                return found;
            }
        }
        return null;
    }
}
