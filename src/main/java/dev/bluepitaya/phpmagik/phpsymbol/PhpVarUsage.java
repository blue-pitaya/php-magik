package dev.bluepitaya.phpmagik.phpsymbol;

import dev.bluepitaya.phpmagik.PhpFile;
import dev.bluepitaya.phpmagik.ts.Range;

public final class PhpVarUsage implements PhpSymbol {

    private final String name;
    private final String ns;
    private final String functionName;
    private final Range range;
    private final PhpFile file;

    public PhpVarUsage(
            String name,
            String ns,
            String functionName,
            Range range,
            PhpFile file) {
        this.name = name;
        this.ns = ns;
        this.functionName = functionName;
        this.range = range;
        this.file = file;
    }

    public String name() {
        return name;
    }

    public String ns() {
        return ns;
    }

    public String functionName() {
        return functionName;
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
