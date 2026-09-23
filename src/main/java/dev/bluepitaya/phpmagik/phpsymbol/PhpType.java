package dev.bluepitaya.phpmagik.phpsymbol;

import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

@NullMarked
public enum PhpType {
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

    PhpType(String php) {
        this.php = php;
    }

    public String php() {
        return php;
    }

    public static @Nullable PhpType of(@Nullable String node) {
        return switch (node) {
            case "int", "integer" -> Integer;
            case "float" -> Float;
            case "string", "encapsed_string", "heredoc", "nowdoc" -> String;
            case "bool", "boolean" -> Boolean;
            case "array", "array_creation_expression" -> Array;
            case "null" -> Null;
            case "callable" -> Callable;
            case "iterable" -> Iterable;
            case "object" -> Object;
            case "mixed" -> Mixed;
            case null, default -> null;
        };
    }
}
