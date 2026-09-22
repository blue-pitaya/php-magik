package dev.bluepitaya.phpmagik.phpsymbol;

import dev.bluepitaya.phpmagik.PhpFile;
import dev.bluepitaya.phpmagik.ts.Range;

public record PhpVarUsage(
        String name,
        String ns,
        String functionName,
        Range range,
        PhpFile file) implements PhpSymbol {
}
