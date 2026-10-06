package dev.bluepitaya.phpmagik.phpsymbol;

import dev.bluepitaya.phpmagik.PhpFile;
import dev.bluepitaya.phpmagik.ts.Range;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

@NullMarked
public final class PhpPropertyAccess implements PhpMemberReference {

    private final PhpFile file;
    private final @Nullable PhpSymbolOwner owner;
    private final String name;
    private final Range range;
    private final @Nullable String variable;
    private final @Nullable PhpMemberReference receiver;

    private @Nullable PhpPropertyDeclaration definition;

    public PhpPropertyAccess(
            PhpFile file, @Nullable PhpSymbolOwner owner, String name, Range range,
            @Nullable String variable, @Nullable PhpMemberReference receiver
    ) {
        this.file = file;
        this.owner = owner;
        this.name = name;
        this.range = range;
        this.variable = variable;
        this.receiver = receiver;
    }

    @Override
    public @Nullable PhpSymbolOwner owner() {
        return owner;
    }

    @Override
    public String name() {
        return name;
    }

    @Override
    public @Nullable String variable() {
        return variable;
    }

    @Override
    public @Nullable PhpMemberReference receiver() {
        return receiver;
    }

    @Override
    public @Nullable PhpPropertyDeclaration definition() {
        return definition;
    }

    public void definition(@Nullable PhpPropertyDeclaration definition) {
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
