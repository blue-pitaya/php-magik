package dev.bluepitaya.phpmagik.phpsymbol;

import dev.bluepitaya.phpmagik.PhpFile;
import dev.bluepitaya.phpmagik.ts.Range;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

/**
 * Filled in as the walk uncovers its parts, so it is only whole once the
 * element it stands for closes. Nothing reaches a collection before that.
 * The depth is where the walk opened it, which is what tells the parts one
 * level below it apart from those of anything nested deeper.
 */
@NullMarked
public final class PhpPropertyDeclaration implements PhpSymbol {

    private final PhpFile file;
    private final int depth;

    private String $modifier = "";
    private @Nullable String type;
    private @Nullable String name;
    private @Nullable Range range;

    public PhpPropertyDeclaration(PhpFile file, int depth) {
        this.file = file;
        this.depth = depth;
    }

    public int depth() {
        return depth;
    }

    public String $modifier() {
        return $modifier;
    }

    public void $modifier(String $modifier) {
        this.$modifier = $modifier;
    }

    public @Nullable String type() {
        return type;
    }

    public void type(String type) {
        this.type = type;
    }

    public @Nullable String name() {
        return name;
    }

    public void name(String name) {
        this.name = name;
    }

    @Override
    public @Nullable Range range() {
        return range;
    }

    public void range(Range range) {
        this.range = range;
    }

    @Override
    public PhpFile file() {
        return file;
    }
}
