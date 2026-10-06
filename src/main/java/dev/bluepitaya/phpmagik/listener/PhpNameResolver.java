package dev.bluepitaya.phpmagik.listener;

import dev.bluepitaya.phpmagik.CompleteIndexer;
import dev.bluepitaya.phpmagik.PhpDoc;
import dev.bluepitaya.phpmagik.phpdoc.PhpDocTypes;
import dev.bluepitaya.phpmagik.phpsymbol.PhpType;
import dev.bluepitaya.phpmagik.ts.Node;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

@NullMarked
public final class PhpNameResolver implements Listener {

    private static final Set<String> UNMAPPED = Set.of(
            "void", "never", "false", "true", "resource", "scalar", "numeric", "number");

    private final Map<String, String> imports = new HashMap<>();
    private @Nullable String namespace;

    public @Nullable String namespace() {
        return namespace;
    }

    public @Nullable PhpType type(@Nullable String text) {
        return type(text, List.of());
    }

    public @Nullable PhpType type(@Nullable String text, List<PhpDoc.Template> templates) {
        String member = text == null ? null : onlyMember(text);
        if (member == null) {
            return null;
        }
        if (member.endsWith("[]")) {
            return arrayOf(type(member.substring(0, member.length() - 2), templates));
        }

        String name = PhpDocTypes.withoutArguments(member);
        if (name.isEmpty() || name.contains("-") || UNMAPPED.contains(name)
                || templates.stream().anyMatch(template -> template.name().equals(name))) {
            return null;
        }
        if ("$this".equals(name)) {
            return PhpType.named("static");
        }
        if ("array".equals(name) || "list".equals(name)) {
            return arrayOf(elementOf(member.substring(name.length()).strip(), templates));
        }
        PhpType builtin = PhpType.of(name);
        return builtin != null ? builtin : PhpType.named(resolve(name));
    }

    private @Nullable PhpType elementOf(String arguments, List<PhpDoc.Template> templates) {
        if (!arguments.startsWith("<") || !arguments.endsWith(">")) {
            return null;
        }

        List<String> parts = PhpDocTypes.split(arguments.substring(1, arguments.length() - 1), ',');
        return type(parts.getLast(), templates);
    }

    private static PhpType arrayOf(@Nullable PhpType element) {
        return element == null ? PhpType.Builtin.Array : PhpType.arrayOf(element);
    }

    private static @Nullable String onlyMember(String text) {
        List<String> members = new ArrayList<>();
        for (String member : PhpDocTypes.split(text, '|')) {
            String stripped = member.strip();
            members.add(stripped.startsWith("?") ? stripped.substring(1) : stripped);
        }
        if (members.size() > 1) {
            members.removeIf("null"::equalsIgnoreCase);
        }
        if (members.size() != 1 || PhpDocTypes.split(members.getFirst(), '&').size() != 1) {
            return null;
        }
        return members.getFirst();
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
                    Node nameNode = parameter.getChildByFieldName("name");
                    if (nameNode == null) {
                        continue;
                    }
                    String name = nameNode.getContent();
                    if (parameter.isType("variadic_parameter")) {
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
            case "named_type" -> resolve(type.getContent());
            case "optional_type" -> "?" + joined(type, "");
            case "union_type", "disjunctive_normal_form_type" -> joined(type, "|");
            case "intersection_type" -> joined(type, "&");
            case null, default -> type.getContent();
        };
    }

    private String joined(Node type, String separator) {
        List<String> parts = new ArrayList<>();
        for (int i = 0; i < type.getNamedChildCount(); i++) {
            Node child = type.getNamedChild(i);
            String part = typeText(child);
            boolean grouped = "|".equals(separator) && child.isType("intersection_type");
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
            if (child.isType("name") || child.isType("qualified_name")) {
                imported = child;
            }
        }
        if (imported == null) {
            return null;
        }
        String text = imported.getContent();
        if (text.startsWith("\\")) {
            text = text.substring(1);
        }

        @Nullable Node group = clause.getParent();
        @Nullable Node declaration = group == null ? null : group.getParent();
        if (group == null || declaration == null || !group.isType("namespace_use_group")) {
            return text;
        }
        for (int i = 0; i < declaration.getNamedChildCount(); i++) {
            Node child = declaration.getNamedChild(i);
            if (child.isType("namespace_name")) {
                return child.getContent() + "\\" + text;
            }
        }
        return text;
    }

    public boolean importsClass(Node clause) {
        if (clause.getChildByFieldName("type") != null) {
            return false;
        }

        Node declaration = clause.getParent();
        if (declaration != null && declaration.isType("namespace_use_group")) {
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
                Node parent = ctx.parent();
                if (parent != null && parent.isType("namespace_definition")) {
                    namespace = node.getContent();
                }
            }
            case "namespace_use_clause" -> {
                String imported = importedName(node);
                if (imported != null && importsClass(node)) {
                    Node alias = node.getChildByFieldName("alias");
                    String key = alias != null ? alias.getContent() : imported.substring(imported.lastIndexOf('\\') + 1);
                    imports.put(key, imported);
                }
            }
            case null, default -> {
            }
        }
    }

    public void exit(CompleteIndexer.Ctx ctx, Node node) {
        if (node.isType("namespace_definition") && node.getChildByFieldName("body") != null) {
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
