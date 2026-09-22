package dev.bluepitaya.phpmagik.phpsymbol;

import dev.bluepitaya.phpmagik.PhpFile;
import dev.bluepitaya.phpmagik.ts.Range;

public record PhpPropertyUsage(
        String name,
        String className,
        Range objectSource,
        Range range,
        PhpFile file) implements PhpSymbol {
}
