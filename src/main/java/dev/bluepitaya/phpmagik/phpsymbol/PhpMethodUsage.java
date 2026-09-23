package dev.bluepitaya.phpmagik.phpsymbol;

import dev.bluepitaya.phpmagik.PhpFile;
import dev.bluepitaya.phpmagik.ts.Range;

public final class PhpMethodUsage implements PhpSymbol {

    private final String name;
    private final String className;
    private final Range objectSource;
    private final Range range;
    private final PhpFile file;

    public PhpMethodUsage(
            String name,
            String className,
            Range objectSource,
            Range range,
            PhpFile file) {
        this.name = name;
        this.className = className;
        this.objectSource = objectSource;
        this.range = range;
        this.file = file;
    }

    public String name() {
        return name;
    }

    public String className() {
        return className;
    }

    public Range objectSource() {
        return objectSource;
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
