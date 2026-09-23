package dev.bluepitaya.phpmagik.phpsymbol;

import dev.bluepitaya.phpmagik.PhpFile;
import dev.bluepitaya.phpmagik.ts.Range;

public final class PhpClassDefinition implements PhpSymbol {

    private final String name;
    private final String ns;
    private final String fqn;
    private final ClassKind kind;
    private final Range range;
    private final Range scope;
    private final PhpFile file;

    public PhpClassDefinition(
            String name,
            String ns,
            String fqn,
            ClassKind kind,
            Range range,
            Range scope,
            PhpFile file) {
        this.name = name;
        this.ns = ns;
        this.fqn = fqn;
        this.kind = kind;
        this.range = range;
        this.scope = scope;
        this.file = file;
    }

    public String name() {
        return name;
    }

    public String ns() {
        return ns;
    }

    public String fqn() {
        return fqn;
    }

    public ClassKind kind() {
        return kind;
    }

    @Override
    public Range range() {
        return range;
    }

    public Range scope() {
        return scope;
    }

    @Override
    public PhpFile file() {
        return file;
    }
}
