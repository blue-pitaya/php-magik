package dev.bluepitaya.phpmagik.phpsymbol;

import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

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

    static PhpType named(String fqn) {
        return new ClassType(fqn);
    }

    static PhpType arrayOf(PhpType element) {
        return new ArrayType(element);
    }

    static PhpType union(List<PhpType> types) {
        List<PhpType> distinct = types.stream().distinct().toList();
        return distinct.size() == 1 ? distinct.getFirst() : new UnionType(distinct);
    }

    static @Nullable String classFqn(@Nullable PhpType type) {
        return withoutNull(type) instanceof ClassType(String fqn) ? fqn : null;
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

    record ClassType(String fqn) implements PhpType {

        @Override
        public String php() {
            return fqn.substring(fqn.lastIndexOf('\\') + 1);
        }

        @Override
        public String qualified() {
            return fqn;
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
