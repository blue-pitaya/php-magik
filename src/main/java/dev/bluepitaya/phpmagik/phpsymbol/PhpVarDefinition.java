package dev.bluepitaya.phpmagik.phpsymbol;

import dev.bluepitaya.phpmagik.PhpFile;
import dev.bluepitaya.phpmagik.ts.Range;

public final class PhpVarDefinition implements PhpSymbol {

    private final String name;
    private final String ns;
    private final String functionName;
    private final String type;
    private final Range valueSource;
    private final Range range;
    private final PhpFile file;

    public PhpVarDefinition(
            String name,
            String ns,
            String functionName,
            String type,
            Range valueSource,
            Range range,
            PhpFile file) {
        this.name = name;
        this.ns = ns;
        this.functionName = functionName;
        this.type = type;
        this.valueSource = valueSource;
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

    public String type() {
        return type;
    }

    public Range valueSource() {
        return valueSource;
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
