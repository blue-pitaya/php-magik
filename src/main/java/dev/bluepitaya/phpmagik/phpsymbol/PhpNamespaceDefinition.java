package dev.bluepitaya.phpmagik.phpsymbol;

import dev.bluepitaya.phpmagik.PhpFile;
import dev.bluepitaya.phpmagik.ts.Range;

public final class PhpNamespaceDefinition implements PhpSymbol, PhpSymbolOwner {

    private final PhpFile file;
    private final int depth;

    private String name;
    private Range range;
    private Range scope;

    public PhpNamespaceDefinition(PhpFile file, int depth) {
        this.file = file;
        this.depth = depth;
    }

    @Override
    public PhpFile file() {
        return file;
    }

    public int depth() {
        return depth;
    }

    public String name() {
        return name;
    }

    public void name(String name) {
        this.name = name;
    }

    @Override
    public Range range() {
        return range;
    }

    public void range(Range range) {
        this.range = range;
    }

    public Range scope() {
        return scope;
    }

    public void scope(Range scope) {
        this.scope = scope;
    }

    @Override
    public String hover() {
        return name == null ? null : PhpSymbol.code("namespace " + name);
    }
}
