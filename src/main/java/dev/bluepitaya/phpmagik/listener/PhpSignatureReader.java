package dev.bluepitaya.phpmagik.listener;

import dev.bluepitaya.phpmagik.PhpReturnTypeInferer;
import dev.bluepitaya.phpmagik.phpsymbol.PhpDoc;
import dev.bluepitaya.phpmagik.phpsymbol.PhpFunctionLike;
import dev.bluepitaya.phpmagik.phpsymbol.PhpNameScope;
import dev.bluepitaya.phpmagik.phpsymbol.PhpType;
import dev.bluepitaya.phpmagik.ts.Node;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

@NullMarked
final class PhpSignatureReader {

    private PhpSignatureReader() {
    }

    static void read(PhpFunctionLike function, Node declaration, PhpNameScope scope) {
        function.scope(declaration.getRange());
        function.parameters(parametersText(declaration.getChildByFieldName("parameters"), scope));

        PhpDoc doc = PhpDoc.of(declaration);
        if (doc != null) {
            function.doc(doc);
        }

        Node hint = declaration.getChildByFieldName("return_type");
        if (hint != null) {
            function.declaredReturnType(typeText(hint, scope));
        }
        PhpType returnType = PhpReturnTypeInferer.declared(hint, doc, scope);
        if (returnType != null) {
            function.returnType(returnType);
        }
    }

    private static String parametersText(@Nullable Node parameters, PhpNameScope scope) {
        if (parameters == null) {
            return "";
        }

        List<String> parts = new ArrayList<>();
        for (int i = 0; i < parameters.getNamedChildCount(); i++) {
            Node parameter = parameters.getNamedChild(i);
            switch (parameter.getType()) {
                case "simple_parameter", "variadic_parameter", "property_promotion_parameter" -> {
                    Node nameNode = parameter.getChildByFieldName("name");
                    if (nameNode == null) {
                        continue;
                    }
                    String name = nameNode.getContent();
                    if (parameter.isType("variadic_parameter")) {
                        name = "..." + name;
                    }
                    Node type = parameter.getChildByFieldName("type");
                    parts.add((type == null ? PhpType.Builtin.Mixed.php() : typeText(type, scope)) + " " + name);
                }
                case null, default -> {
                }
            }
        }
        return String.join(", ", parts);
    }

    private static String typeText(Node type, PhpNameScope scope) {
        return switch (type.getType()) {
            case "named_type" -> scope.fqn(type.getContent());
            case "optional_type" -> "?" + joined(type, "", scope);
            case "union_type", "disjunctive_normal_form_type" -> joined(type, "|", scope);
            case "intersection_type" -> joined(type, "&", scope);
            case null, default -> type.getContent();
        };
    }

    private static String joined(Node type, String separator, PhpNameScope scope) {
        List<String> parts = new ArrayList<>();
        for (int i = 0; i < type.getNamedChildCount(); i++) {
            Node child = type.getNamedChild(i);
            String part = typeText(child, scope);
            boolean grouped = "|".equals(separator) && child.isType("intersection_type");
            parts.add(grouped ? "(" + part + ")" : part);
        }
        return String.join(separator, parts);
    }
}
