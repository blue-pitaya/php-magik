package dev.bluepitaya.phpmagik.phpsymbol;

import dev.bluepitaya.phpmagik.PhpFile;
import dev.bluepitaya.phpmagik.ts.Range;

public record PhpVarDefinition(
        String name,
        String ns,
        String functionName,
        String type,
        Range valueSource,
        Range range,
        PhpFile file) implements PhpSymbol {
}
