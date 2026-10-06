package dev.bluepitaya.phpmagik.phpsymbol;

import dev.bluepitaya.phpmagik.PhpFile;
import dev.bluepitaya.phpmagik.ts.Range;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

@NullMarked
public final class PhpMethodLocalVarDeclaration implements PhpSymbol {

    private final PhpFile file;

    private @Nullable PhpSymbolOwner owner;
    private @Nullable String name;
    private @Nullable PhpType type;
    private @Nullable PhpMemberReference source;
    private @Nullable Range range;

    public PhpMethodLocalVarDeclaration(PhpFile file) {
        this.file = file;
    }

    @Override
    public @Nullable String hover() {
        if (name == null) return null;

        return PhpSymbol.code(PhpType.declared(type, name));
    }

    public @Nullable PhpSymbolOwner owner() {
        return owner;
    }

    public void owner(PhpSymbolOwner owner) {
        this.owner = owner;
    }

    public @Nullable String name() {
        return name;
    }

    public void name(String name) {
        this.name = name;
    }

    public @Nullable PhpType type() {
        return type;
    }

    public void type(@Nullable PhpType type) {
        this.type = type;
    }

    public @Nullable PhpMemberReference source() {
        return source;
    }

    public void source(PhpMemberReference source) {
        this.source = source;
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
