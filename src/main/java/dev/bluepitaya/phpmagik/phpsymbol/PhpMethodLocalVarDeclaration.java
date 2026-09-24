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
    private @Nullable Range range;

    public PhpMethodLocalVarDeclaration(PhpFile file) {
        this.file = file;
    }

    @Override
    public @Nullable String hover() {
        if (name == null) return null;

        String ownerName = ownerName();

        return PhpSymbol.code(ownerName == null ? name : ownerName + "(): " + name);
    }

    private @Nullable String ownerName() {
        return switch (owner) {
            case PhpMethodDeclaration m -> m.name();
            case PhpFunctionDefinition f -> f.name();
            case null, default -> null;
        };
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

    public void type(PhpType type) {
        this.type = type;
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
