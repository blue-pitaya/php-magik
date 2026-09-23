package dev.bluepitaya.phpmagik.phpsymbol;

import dev.bluepitaya.phpmagik.PhpFile;
import dev.bluepitaya.phpmagik.ts.Range;

public final class PhpFunctionUsage implements PhpSymbol {

    private final String name;
    private final String ns;
    private final Range range;
    private final PhpFile file;

    public PhpFunctionUsage(
            String name,
            String ns,
            Range range,
            PhpFile file) {
        this.name = name;
        this.ns = ns;
        this.range = range;
        this.file = file;
    }

    public String name() {
        return name;
    }

    public String ns() {
        return ns;
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
