package dev.bluepitaya.phpmagik.phpsymbol;

import dev.bluepitaya.phpmagik.PhpFile;
import dev.bluepitaya.phpmagik.ts.Range;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

@NullMarked
public final class PhpClassReference implements PhpReference {

    private final PhpFile file;
    private final String name;
    private final Range range;

    private @Nullable PhpClassDeclaration definition;

    public PhpClassReference(PhpFile file, String name, Range range) {
        this.file = file;
        this.name = name;
        this.range = range;
    }

    @Override
    public String name() {
        return name;
    }

    @Override
    public @Nullable PhpClassDeclaration definition() {
        return definition;
    }

    public void definition(@Nullable PhpClassDeclaration definition) {
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
