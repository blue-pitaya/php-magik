package dev.bluepitaya.phpmagik.phpsymbol;

import dev.bluepitaya.phpmagik.PhpFile;
import dev.bluepitaya.phpmagik.ts.Range;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

@NullMarked
public final class PhpClassDeclaration implements PhpSymbol, PhpSymbolOwner {

    private final PhpFile file;
    private final int depth;

    private String $modifier = "";
    private @Nullable String name;
    private @Nullable Range range;
    private @Nullable Range scope;

    public PhpClassDeclaration(PhpFile file, int depth) {
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

    public @Nullable Range scope() {
        return scope;
    }

    public void scope(Range scope) {
        this.scope = scope;
    }

    @Override
    public PhpFile file() {
        return file;
    }
}
