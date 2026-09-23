package dev.bluepitaya.phpmagik.phpsymbol;

import dev.bluepitaya.phpmagik.PhpFile;
import dev.bluepitaya.phpmagik.ts.Range;

public final class PhpUseStatement implements PhpSymbol {

    private final String alias;
    private final String fqn;
    private final UseKind kind;
    private final Range range;
    private final PhpFile file;

    public PhpUseStatement(String alias, String fqn, UseKind kind, Range range, PhpFile file) {
        this.alias = alias;
        this.fqn = fqn;
        this.kind = kind;
        this.range = range;
        this.file = file;
    }

    public String alias() {
        return alias;
    }

    public String fqn() {
        return fqn;
    }

    public UseKind kind() {
        return kind;
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
