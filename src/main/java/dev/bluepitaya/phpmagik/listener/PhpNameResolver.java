package dev.bluepitaya.phpmagik.listener;

import dev.bluepitaya.phpmagik.CompleteIndexer;
import dev.bluepitaya.phpmagik.phpsymbol.PhpType;
import dev.bluepitaya.phpmagik.ts.Node;
import dev.bluepitaya.phpmagik.ts.Nodes;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@NullMarked
public final class PhpNameResolver implements Listener {

    private final Map<String, String> imports = new HashMap<>();
    private @Nullable String namespace;

    public @Nullable String namespace() {
        return namespace;
    }

    public @Nullable PhpType type(@Nullable String text) {
        if (text == null) {
            return null;
        }
        if (text.startsWith("?")) {
            text = text.substring(1);
        }
        PhpType builtin = PhpType.of(text);
        return builtin != null ? builtin : PhpType.named(resolve(text));
    }

    public String resolve(String name) {
        if (name.startsWith("\\")) {
            return name.substring(1);
        }
        if (name.regionMatches(true, 0, "namespace\\", 0, 10)) {
            return qualify(name.substring(10));
        }
        if ("self".equalsIgnoreCase(name) || "static".equalsIgnoreCase(name) || "parent".equalsIgnoreCase(name)) {
            return name;
        }

        int slash = name.indexOf('\\');
        String imported = imports.get(slash < 0 ? name : name.substring(0, slash));
        if (imported != null) {
            return slash < 0 ? imported : imported + name.substring(slash);
        }
        return qualify(name);
    }

    public String parametersText(@Nullable Node parameters) {
        if (parameters == null) {
            return "";
        }

        List<String> parts = new ArrayList<>();
        for (int i = 0; i < parameters.getNamedChildCount(); i++) {
            Node parameter = parameters.getNamedChild(i);
            switch (parameter.getType()) {
                case "simple_parameter", "variadic_parameter", "property_promotion_parameter" -> {
                    String name = Nodes.text(parameter.getChildByFieldName("name"));
                    if (name == null) {
                        continue;
                    }
                    if ("variadic_parameter".equals(parameter.getType())) {
                        name = "..." + name;
                    }
                    Node type = parameter.getChildByFieldName("type");
                    parts.add(type == null ? name : typeText(type) + " " + name);
                }
                case null, default -> {
                }
            }
        }
        return String.join(", ", parts);
    }

    public String typeText(Node type) {
        return switch (type.getType()) {
            case "named_type" -> {
                String text = Nodes.text(type);
                yield text == null ? "" : resolve(text);
            }
            case "optional_type" -> "?" + joined(type, "");
            case "union_type", "disjunctive_normal_form_type" -> joined(type, "|");
            case "intersection_type" -> joined(type, "&");
            case null, default -> {
                String text = Nodes.text(type);
                yield text == null ? "" : text;
            }
        };
    }

    private String joined(Node type, String separator) {
        List<String> parts = new ArrayList<>();
        for (int i = 0; i < type.getNamedChildCount(); i++) {
            Node child = type.getNamedChild(i);
            String part = typeText(child);
            boolean grouped = "|".equals(separator) && "intersection_type".equals(child.getType());
            parts.add(grouped ? "(" + part + ")" : part);
        }
        return String.join(separator, parts);
    }

    private String qualify(String name) {
        return namespace == null ? name : namespace + "\\" + name;
    }

    public @Nullable String importedName(Node clause) {
        @Nullable Node imported = null;
        for (int i = 0; i < clause.getNamedChildCount() && imported == null; i++) {
            Node child = clause.getNamedChild(i);
            if ("name".equals(child.getType()) || "qualified_name".equals(child.getType())) {
                imported = child;
            }
        }
        String text = Nodes.text(imported);
        if (text == null) {
            return null;
        }
        if (text.startsWith("\\")) {
            text = text.substring(1);
        }

        @Nullable Node group = clause.getParent();
        @Nullable Node declaration = group == null ? null : group.getParent();
        if (declaration == null || !"namespace_use_group".equals(Nodes.type(group))) {
            return text;
        }
        for (int i = 0; i < declaration.getNamedChildCount(); i++) {
            Node child = declaration.getNamedChild(i);
            if ("namespace_name".equals(child.getType())) {
                return Nodes.text(child) + "\\" + text;
            }
        }
        return text;
    }

    public boolean importsClass(Node clause) {
        if (clause.getChildByFieldName("type") != null) {
            return false;
        }

        Node declaration = clause.getParent();
        if (declaration != null && "namespace_use_group".equals(declaration.getType())) {
            declaration = declaration.getParent();
        }
        return declaration == null || declaration.getChildByFieldName("type") == null;
    }

    public void enter(CompleteIndexer.Ctx ctx, Node node) {
        switch (node.getType()) {
            case "namespace_definition" -> {
                namespace = null;
                imports.clear();
            }
            case "namespace_name" -> {
                if ("namespace_definition".equals(Nodes.type(ctx.parent()))) {
                    namespace = Nodes.text(node);
                }
            }
            case "namespace_use_clause" -> {
                String imported = importedName(node);
                if (imported != null && importsClass(node)) {
                    Node alias = node.getChildByFieldName("alias");
                    String key = alias != null ? Nodes.text(alias) : imported.substring(imported.lastIndexOf('\\') + 1);
                    imports.put(key, imported);
                }
            }
            case null, default -> {
            }
        }
    }

    public void exit(CompleteIndexer.Ctx ctx, Node node) {
        if ("namespace_definition".equals(node.getType()) && node.getChildByFieldName("body") != null) {
            namespace = null;
            imports.clear();
        }
    }

    public void leaf(CompleteIndexer.Ctx ctx, Node node) {
    }

    public void token(CompleteIndexer.Ctx ctx, Node node, String field) {
    }

    public void extra(CompleteIndexer.Ctx ctx, Node node) {
    }

    public void error(CompleteIndexer.Ctx ctx, Node node) {
    }

    public void unexpected(CompleteIndexer.Ctx ctx, Node parent, Node child, String field) {
    }

    public void unknown(CompleteIndexer.Ctx ctx, Node node) {
    }
}
