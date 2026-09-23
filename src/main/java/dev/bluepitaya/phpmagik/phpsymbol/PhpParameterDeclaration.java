package dev.bluepitaya.phpmagik.phpsymbol;

import dev.bluepitaya.phpmagik.PhpFile;
import dev.bluepitaya.phpmagik.ts.Range;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

@NullMarked
public final class PhpParameterDeclaration implements PhpSymbol {

    private final PhpFile file;
    private final int depth;

    private @Nullable PhpMethodDeclaration owner;
    private @Nullable String name;
    private @Nullable Range range;

    public PhpParameterDeclaration(PhpFile file, int depth) {
        this.file = file;
        this.depth = depth;
    }

    public int depth() {
        return depth;
    }

    @Override
    public @Nullable String hover() {
        if (name == null) return null;

        String ownerName = owner == null ? null : owner.name();

        return PhpSymbol.code(ownerName == null ? name : ownerName + "(" + name + ")");
    }

    public @Nullable PhpMethodDeclaration owner() {
        return owner;
    }

    public void owner(PhpMethodDeclaration owner) {
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
