package dev.bluepitaya.phpmagik.phpdoc;

import dev.bluepitaya.phpmagik.ts.Node;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@NullMarked
public final class PhpDocParser {

    private static final Pattern WHITESPACE = Pattern.compile("\\s+");

    private PhpDocParser() {
    }

    public static @Nullable PhpDoc of(Node declaration) {
        Node previous = declaration.getPrevSibling();
        return previous != null && previous.isType("comment") ? parse(previous.getContent()) : null;
    }

    public static @Nullable PhpDoc parse(@Nullable String comment) {
        if (comment == null || comment.length() < 5 || !comment.startsWith("/**") || !comment.endsWith("*/")) {
            return null;
        }

        List<String> text = new ArrayList<>();
        List<String> tags = new ArrayList<>();
        for (String line : lines(comment.substring(3, comment.length() - 2))) {
            if (line.startsWith("@")) {
                tags.add(line);
            } else if (!tags.isEmpty()) {
                tags.set(tags.size() - 1, tags.getLast() + "\n" + line);
            } else {
                text.add(line);
            }
        }

        List<PhpDoc.Template> templates = new ArrayList<>();
        List<PhpDoc.Param> params = new ArrayList<>();
        PhpDoc.@Nullable Return returns = null;
        for (String tag : tags) {
            int nameEnd = wordEnd(tag);
            String body = tag.substring(nameEnd).strip();
            switch (tag.substring(1, nameEnd)) {
                case "param" -> {
                    PhpDoc.Param param = param(body);
                    if (param != null) {
                        params.add(param);
                    }
                }
                case "return" -> returns = returns(body);
                case "template", "template-covariant", "template-contravariant" -> {
                    PhpDoc.Template template = template(body);
                    if (template != null) {
                        templates.add(template);
                    }
                }
            }
        }

        List<List<String>> paragraphs = paragraphs(text);
        String summary = paragraphs.isEmpty() ? "" : String.join(" ", paragraphs.getFirst());
        String description = paragraphs.stream()
                .skip(1)
                .map(paragraph -> String.join("\n", paragraph))
                .collect(Collectors.joining("\n\n"));
        return new PhpDoc(summary, description, List.copyOf(templates), List.copyOf(params), returns);
    }

    private static List<String> lines(String body) {
        return body.lines()
                .map(String::strip)
                .map(line -> line.startsWith("*") ? line.substring(1).strip() : line)
                .toList();
    }

    private static List<List<String>> paragraphs(List<String> text) {
        List<List<String>> paragraphs = new ArrayList<>();
        List<String> current = new ArrayList<>();
        for (String line : text) {
            if (!line.isEmpty()) {
                current.add(line);
            } else if (!current.isEmpty()) {
                paragraphs.add(current);
                current = new ArrayList<>();
            }
        }
        if (!current.isEmpty()) {
            paragraphs.add(current);
        }
        return paragraphs;
    }

    private static PhpDoc.@Nullable Param param(String body) {
        @Nullable String type = null;
        String rest = body;
        if (!PhpDocTypes.isVariableAt(rest, 0)) {
            Typed typed = typed(rest);
            type = typed.type();
            rest = typed.rest();
        }
        if (!PhpDocTypes.isVariableAt(rest, 0)) {
            return null;
        }

        int nameEnd = wordEnd(rest);
        return new PhpDoc.Param(type, rest.substring(0, nameEnd), rest.substring(nameEnd).strip());
    }

    private static PhpDoc.@Nullable Return returns(String body) {
        if (body.isEmpty()) {
            return null;
        }

        Typed typed = typed(body);
        return new PhpDoc.Return(typed.type(), typed.rest());
    }

    private static PhpDoc.@Nullable Template template(String body) {
        if (body.isEmpty()) {
            return null;
        }

        String[] parts = WHITESPACE.split(body, 3);
        boolean bounded = parts.length == 3 && ("of".equals(parts[1]) || "as".equals(parts[1]));
        return new PhpDoc.Template(parts[0], bounded ? typed(parts[2]).type() : null);
    }

    private record Typed(String type, String rest) {
    }

    private static Typed typed(String body) {
        int end = PhpDocTypes.end(body);
        String type = WHITESPACE.matcher(body.substring(0, end).strip()).replaceAll(" ");
        return new Typed(type, body.substring(end).strip());
    }

    private static int wordEnd(String text) {
        int end = 0;
        while (end < text.length() && !Character.isWhitespace(text.charAt(end))) {
            end++;
        }
        return end;
    }
}
