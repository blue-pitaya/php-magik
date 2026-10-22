package dev.bluepitaya.phpmagik.phpsymbol;

import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

@NullMarked
public sealed interface PhpType permits PhpType.Builtin, PhpType.ClassType, PhpType.ArrayType, PhpType.UnionType {

    String php();

    default String qualified() {
        return php();
    }

    static @Nullable PhpType of(@Nullable String node) {
        return switch (node) {
            case "int", "integer" -> Builtin.Integer;
            case "float" -> Builtin.Float;
            case "string", "encapsed_string", "heredoc", "nowdoc" -> Builtin.String;
            case "bool", "boolean" -> Builtin.Boolean;
            case "array", "array_creation_expression" -> Builtin.Array;
            case "null" -> Builtin.Null;
            case "callable" -> Builtin.Callable;
            case "iterable" -> Builtin.Iterable;
            case "object" -> Builtin.Object;
            case "mixed" -> Builtin.Mixed;
            case null, default -> null;
        };
    }

    static @Nullable PhpType parse(@Nullable String text, PhpNameScope scope) {
        return parse(text, scope, List.of());
    }

    static @Nullable PhpType parse(@Nullable String text, PhpNameScope scope, List<PhpDoc.Template> templates) {
        if (text == null) {
            return null;
        }

        List<PhpType> types = new ArrayList<>();
        for (String member : members(text)) {
            PhpType type = parseMember(member, scope, templates);
            if (type == null) {
                return null;
            }
            types.add(type);
        }
        return types.isEmpty() ? null : union(types);
    }

    private static List<String> members(String text) {
        List<String> members = new ArrayList<>();
        for (String member : PhpDoc.split(text, '|')) {
            String stripped = member.strip();
            if (stripped.startsWith("?")) {
                members.add(stripped.substring(1).strip());
                members.add("null");
            } else {
                members.add(stripped);
            }
        }
        return members;
    }

    private static @Nullable PhpType parseMember(String member, PhpNameScope scope, List<PhpDoc.Template> templates) {
        if (PhpDoc.split(member, '&').size() != 1) {
            return null;
        }
        if (member.endsWith("[]")) {
            return arrayOrPlain(parse(member.substring(0, member.length() - 2), scope, templates));
        }

        String name = PhpDoc.withoutArguments(member);
        if (name.isEmpty() || name.contains("-") || isUnmapped(name)
                || templates.stream().anyMatch(template -> template.name().equals(name))) {
            return null;
        }
        if ("$this".equals(name)) {
            return named("static");
        }
        if ("null".equalsIgnoreCase(name)) {
            return Builtin.Null;
        }
        if ("array".equals(name) || "list".equals(name)) {
            return arrayOrPlain(parseElement(member.substring(name.length()).strip(), scope, templates));
        }
        PhpType builtin = of(name);
        return builtin != null ? builtin : new ClassType(name, scope);
    }

    private static boolean isUnmapped(String name) {
        return switch (name) {
            case "void", "never", "false", "true", "resource", "scalar", "numeric", "number" -> true;
            default -> false;
        };
    }

    private static @Nullable PhpType parseElement(String arguments, PhpNameScope scope, List<PhpDoc.Template> templates) {
        if (!arguments.startsWith("<") || !arguments.endsWith(">")) {
            return null;
        }

        List<String> parts = PhpDoc.split(arguments.substring(1, arguments.length() - 1), ',');
        return parse(parts.getLast(), scope, templates);
    }

    private static PhpType arrayOrPlain(@Nullable PhpType element) {
        return element == null ? Builtin.Array : arrayOf(element);
    }

    static PhpType named(String fqn) {
        return new ClassType(fqn, PhpNameScope.GLOBAL);
    }

    static PhpType arrayOf(PhpType element) {
        return new ArrayType(element);
    }

    static PhpType union(List<PhpType> types) {
        List<PhpType> distinct = types.stream().distinct().toList();
        return distinct.size() == 1 ? distinct.getFirst() : new UnionType(distinct);
    }

    static @Nullable String classFqn(@Nullable PhpType type) {
        return withoutNull(type) instanceof ClassType classType ? classType.fqn() : null;
    }

    static @Nullable PhpType elementOf(@Nullable PhpType type) {
        return withoutNull(type) instanceof ArrayType(PhpType element) ? element : null;
    }

    private static @Nullable PhpType withoutNull(@Nullable PhpType type) {
        if (!(type instanceof UnionType(List<PhpType> types))) {
            return type;
        }
        List<PhpType> present = types.stream().filter(member -> member != Builtin.Null).toList();
        return present.size() == 1 ? present.getFirst() : type;
    }

    static PhpType orMixed(@Nullable PhpType type) {
        return type == null ? Builtin.Mixed : type;
    }

    static String declared(@Nullable PhpType type, String name) {
        return orMixed(type).qualified() + " " + name;
    }

    enum Builtin implements PhpType {
        Integer("int"),
        Float("float"),
        String("string"),
        Boolean("bool"),
        Array("array"),
        Null("null"),
        Callable("callable"),
        Iterable("iterable"),
        Object("object"),
        Mixed("mixed"),
        Void("void");

        private final String php;

        Builtin(String php) {
            this.php = php;
        }

        @Override
        public String php() {
            return php;
        }
    }

    record ClassType(String name, PhpNameScope scope) implements PhpType {

        public String fqn() {
            return scope.fqn(name);
        }

        @Override
        public String php() {
            String fqn = fqn();
            return fqn.substring(fqn.lastIndexOf('\\') + 1);
        }

        @Override
        public String qualified() {
            return fqn();
        }

        @Override
        public boolean equals(@Nullable Object other) {
            return other instanceof ClassType classType && fqn().equals(classType.fqn());
        }

        @Override
        public int hashCode() {
            return fqn().hashCode();
        }
    }

    record ArrayType(PhpType element) implements PhpType {

        @Override
        public String php() {
            return "array<" + element.php() + ">";
        }

        @Override
        public String qualified() {
            return "array<" + element.qualified() + ">";
        }
    }

    record UnionType(List<PhpType> types) implements PhpType {

        @Override
        public String php() {
            return types.stream().map(PhpType::php).collect(Collectors.joining("|"));
        }

        @Override
        public String qualified() {
            return types.stream().map(PhpType::qualified).collect(Collectors.joining("|"));
        }
    }
}
