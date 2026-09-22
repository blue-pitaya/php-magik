package dev.bluepitaya.phpmagik.phpsymbol;

import dev.bluepitaya.phpmagik.PhpFile;
import dev.bluepitaya.phpmagik.ts.Range;

public record PhpClassDefinition(
        String name,
        String ns,
        String fqn,
        ClassKind kind,
        Range range,
        Range scope,
        PhpFile file) implements PhpSymbol {
}
