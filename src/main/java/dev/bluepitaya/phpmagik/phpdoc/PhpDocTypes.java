package dev.bluepitaya.phpmagik.phpdoc;

import org.jspecify.annotations.NullMarked;

import java.util.ArrayList;
import java.util.List;

@NullMarked
public final class PhpDocTypes {

    private PhpDocTypes() {
    }

    public static List<String> split(String type, char separator) {
        List<String> parts = new ArrayList<>();
        int depth = 0;
        int start = 0;
        for (int i = 0; i < type.length(); i++) {
            char c = type.charAt(i);
            if (c == separator && depth == 0) {
                parts.add(type.substring(start, i));
                start = i + 1;
            }
            depth += nesting(c);
        }
        parts.add(type.substring(start));
        return parts;
    }

    public static String withoutArguments(String type) {
        for (int i = 0; i < type.length(); i++) {
            if (nesting(type.charAt(i)) > 0) {
                return type.substring(0, i).strip();
            }
        }
        return type;
    }

    static int end(String text) {
        int depth = 0;
        char quote = 0;
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (quote != 0) {
                if (c == quote) {
                    quote = 0;
                }
            } else if (c == '\'' || c == '"') {
                quote = c;
            } else if (depth == 0 && Character.isWhitespace(c)) {
                int next = whitespaceEnd(text, i);
                if (!joins(text, i - 1, next)) {
                    return i;
                }
                i = next - 1;
            } else {
                depth += nesting(c);
            }
        }
        return text.length();
    }

    static boolean isVariableAt(String text, int index) {
        return text.startsWith("$", index) || text.startsWith("&", index) || text.startsWith("...", index);
    }

    private static boolean joins(String text, int previous, int next) {
        if (next >= text.length()) {
            return false;
        }

        char before = previous < 0 ? ' ' : text.charAt(previous);
        char after = text.charAt(next);
        return before == '|' || before == '&' || before == ':'
                || after == '|' || after == '&' && !isVariableAt(text, next + 1);
    }

    private static int whitespaceEnd(String text, int from) {
        int end = from;
        while (end < text.length() && Character.isWhitespace(text.charAt(end))) {
            end++;
        }
        return end;
    }

    private static int nesting(char c) {
        return switch (c) {
            case '(', '<', '{', '[' -> 1;
            case ')', '>', '}', ']' -> -1;
            default -> 0;
        };
    }
}
