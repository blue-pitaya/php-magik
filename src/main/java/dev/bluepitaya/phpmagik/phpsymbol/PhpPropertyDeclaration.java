package dev.bluepitaya.phpmagik.phpsymbol;

import dev.bluepitaya.phpmagik.PhpFile;
import dev.bluepitaya.phpmagik.ts.Range;
import org.jspecify.annotations.Nullable;

import java.util.Optional;

public final class PhpPropertyDeclaration implements PhpSymbol {

    private final PhpFile file;
    private final String $modifier;
    private final String name;
    private final Range range;

    private @Nullable PhpClassDeclaration owner;
    private @Nullable PhpType type;

    public PhpPropertyDeclaration(PhpFile file, String $modifier, String name, Range range) {
        this.file = file;
        this.$modifier = $modifier;
        this.name = name;
        this.range = range;
    }

    @Override
    public String hover() {
        String declared = PhpType.declared(type, name);
        return PhpSymbol.code($modifier.isEmpty() ? declared : $modifier + " " + declared);
    }

    public String $modifier() {
        return $modifier;
    }

    public Optional<PhpClassDeclaration> owner() {
        return Optional.ofNullable(owner);
    }

    public void owner(PhpClassDeclaration owner) {
        this.owner = owner;
    }

    public String name() {
        return name;
    }

    public Optional<PhpType> type() {
        return Optional.ofNullable(type);
    }

    public void type(PhpType type) {
        this.type = type;
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
