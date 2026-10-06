package dev.bluepitaya.phpmagik.phpsymbol;

import dev.bluepitaya.phpmagik.PhpFile;
import dev.bluepitaya.phpmagik.ts.Range;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

@NullMarked
public final class PhpStaticCall implements PhpReference {

    private final PhpFile file;
    private final @Nullable PhpSymbolOwner owner;
    private final String name;
    private final Range range;
    private final String scope;

    private @Nullable PhpMethodDeclaration definition;

    public PhpStaticCall(PhpFile file, @Nullable PhpSymbolOwner owner, String name, Range range, String scope) {
        this.file = file;
        this.owner = owner;
        this.name = name;
        this.range = range;
        this.scope = scope;
    }

    public @Nullable PhpSymbolOwner owner() {
        return owner;
    }

    @Override
    public String name() {
        return name;
    }

    public String scope() {
        return scope;
    }

    @Override
    public @Nullable PhpMethodDeclaration definition() {
        return definition;
    }

    public void definition(@Nullable PhpMethodDeclaration definition) {
        this.definition = definition;
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
