package dev.bluepitaya.phpmagik.phpsymbol;

import dev.bluepitaya.phpmagik.PhpFile;
import dev.bluepitaya.phpmagik.ts.Range;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

@NullMarked
public final class PhpMethodVarUsage implements PhpSymbol {

    private final PhpFile file;
    private final PhpSymbolOwner owner;
    private final String name;
    private final Range range;

    private @Nullable PhpSymbol definition;
    private @Nullable PhpType type;

    public PhpMethodVarUsage(PhpFile file, PhpSymbolOwner owner, String name, Range range) {
        this.file = file;
        this.owner = owner;
        this.name = name;
        this.range = range;
    }

    @Override
    public String hover() {
        return PhpSymbol.code(PhpType.declared(type, name));
    }

    public PhpSymbolOwner owner() {
        return owner;
    }

    public @Nullable PhpSymbol definition() {
        return definition;
    }

    public void definition(PhpSymbol definition) {
        this.definition = definition;
    }

    public @Nullable PhpType type() {
        return type;
    }

    public void type(PhpType type) {
        this.type = type;
    }

    public String name() {
        return name;
    }

    @Override
    public Range range() {
        return range;
    }

    @Override
    public PhpFile file() {
        return file;
    }
}
