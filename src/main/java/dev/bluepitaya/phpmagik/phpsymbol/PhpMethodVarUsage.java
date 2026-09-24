package dev.bluepitaya.phpmagik.phpsymbol;

import dev.bluepitaya.phpmagik.PhpFile;
import dev.bluepitaya.phpmagik.ts.Range;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

@NullMarked
public final class PhpMethodVarUsage implements PhpSymbol {

    private final PhpFile file;

    private @Nullable PhpSymbolOwner owner;
    private @Nullable PhpSymbol definition;
    private @Nullable PhpType phpType;
    private @Nullable String namedTypeName;
    private @Nullable String name;
    private @Nullable Range range;

    public PhpMethodVarUsage(PhpFile file) {
        this.file = file;
    }

    @Override
    public @Nullable String hover() {
        if (name == null) return null;

        String typeStr = phpType != null ? phpType.php() : namedTypeName;
        String declared = typeStr == null ? name : typeStr + " " + name;
        String ownerName = ownerName();

        return PhpSymbol.code(ownerName == null ? declared : ownerName + "(): " + declared);
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

    public @Nullable PhpSymbol definition() {
        return definition;
    }

    public void definition(PhpSymbol definition) {
        this.definition = definition;
    }

    public @Nullable PhpType phpType() {
        return phpType;
    }

    public void phpType(PhpType phpType) {
        this.phpType = phpType;
    }

    public @Nullable String namedTypeName() {
        return namedTypeName;
    }

    public void namedTypeName(String namedTypeName) {
        this.namedTypeName = namedTypeName;
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
