package dev.bluepitaya.phpmagik.phpsymbol;

import dev.bluepitaya.phpmagik.PhpFile;
import dev.bluepitaya.phpmagik.ts.Range;

public record PhpClassUsage(
        String name,
        String ns,
        Range range,
        PhpFile file) implements PhpSymbol {
}
