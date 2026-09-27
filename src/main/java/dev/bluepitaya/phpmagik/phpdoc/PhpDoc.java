package dev.bluepitaya.phpmagik.phpdoc;

import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

import java.util.List;

@NullMarked
public record PhpDoc(
        String summary,
        String description,
        List<Template> templates,
        List<Param> params,
        @Nullable Return returns
) {

    public record Template(String name, @Nullable String bound) {
    }

    public record Param(@Nullable String type, String name, String description) {
    }

    public record Return(String type, String description) {
    }
}
