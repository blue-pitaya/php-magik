package dev.bluepitaya.phpmagik.phpsymbol;

import dev.bluepitaya.phpmagik.PhpFile;
import dev.bluepitaya.phpmagik.ts.Range;

public final class PhpPropertyDefinition implements PhpSymbol {

    private final String name;
    private final PhpClassDefinition owner;
    private final String type;
    private final Range range;
    private final PhpFile file;

    public PhpPropertyDefinition(
            String name,
            PhpClassDefinition owner,
            String type,
            Range range,
            PhpFile file) {
        this.name = name;
        this.owner = owner;
        this.type = type;
        this.range = range;
        this.file = file;
    }

    public String qualifiedName() {
        return owner.fqn() + "::" + name;
    }

    public String name() {
        return name;
    }

    public PhpClassDefinition owner() {
        return owner;
    }

    public String type() {
        return type;
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
