package dev.bluepitaya.phpmagik.phpsymbol;

import dev.bluepitaya.phpmagik.PhpFile;
import dev.bluepitaya.phpmagik.ts.Range;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

@NullMarked
public final class PhpParameterDeclaration implements PhpSymbol {

    private final PhpFile file;
    private final int depth;

    private @Nullable PhpSymbolOwner owner;
    private @Nullable PhpType type;
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

        String typeStr = type != null ? type.php() : null;
        String declared = typeStr == null ? name : typeStr + " " + name;
        String ownerName = ownerName();

        return PhpSymbol.code(ownerName == null ? declared : ownerName + "(" + declared + ")");
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

    public @Nullable PhpType type() {
        return type;
    }

    public void type(PhpType type) {
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
