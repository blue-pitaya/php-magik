package dev.bluepitaya.phpmagik.phpsymbol;

import dev.bluepitaya.phpmagik.PhpFile;
import dev.bluepitaya.phpmagik.ts.Range;

public record PhpNamespaceDefinition(
        String name,
        Range range,
        PhpFile file
) implements PhpSymbol {
}
