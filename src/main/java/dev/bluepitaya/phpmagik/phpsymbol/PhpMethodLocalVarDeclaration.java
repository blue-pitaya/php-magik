package dev.bluepitaya.phpmagik.phpsymbol;

import dev.bluepitaya.phpmagik.PhpFile;
import dev.bluepitaya.phpmagik.ts.Range;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

@NullMarked
public final class PhpMethodLocalVarDeclaration implements PhpSymbol {

    private final PhpFile file;

    private @Nullable PhpMethodDeclaration owner;
    private @Nullable String name;
    private @Nullable Range range;

    public PhpMethodLocalVarDeclaration(PhpFile file) {
        this.file = file;
    }

    @Override
    public @Nullable String hover() {
        if (name == null) return null;

        String ownerName = owner == null ? null : owner.name();

        return PhpSymbol.code(ownerName == null ? name : ownerName + "(): " + name);
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
