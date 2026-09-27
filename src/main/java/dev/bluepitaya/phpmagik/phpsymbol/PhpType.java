package dev.bluepitaya.phpmagik.phpsymbol;

import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

@NullMarked
public sealed interface PhpType permits PhpType.Builtin, PhpType.ClassType {

    String php();

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

    static String declared(@Nullable PhpType type, String name) {
        return switch (type) {
            case ClassType(String fqn) -> fqn + " " + name;
            case null -> name;
            default -> type.php() + " " + name;
        };
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
        Mixed("mixed");

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
    }
}
