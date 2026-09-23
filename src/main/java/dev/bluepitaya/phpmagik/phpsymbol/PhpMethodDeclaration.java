package dev.bluepitaya.phpmagik.phpsymbol;

import dev.bluepitaya.phpmagik.PhpFile;
import dev.bluepitaya.phpmagik.ts.Range;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

@NullMarked
public final class PhpMethodDeclaration implements PhpSymbol {

    private final PhpFile file;
    private final int depth;

    private @Nullable PhpClassDeclaration owner;
    private @Nullable String name;
    private @Nullable Range range;

    public PhpMethodDeclaration(PhpFile file, int depth) {
        this.file = file;
        this.depth = depth;
    }

    public int depth() {
        return depth;
    }

    public @Nullable PhpClassDeclaration owner() {
        return owner;
    }

    public void owner(PhpClassDeclaration owner) {
        this.owner = owner;
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
