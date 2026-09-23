package dev.bluepitaya.phpmagik.phpsymbol;

import dev.bluepitaya.phpmagik.PhpFile;
import dev.bluepitaya.phpmagik.ts.Range;
import org.jspecify.annotations.NullMarked;

@NullMarked
public record PhpClassDeclaration(
        String $modifier,
        String name,
        Range range,
        Range scope,
        PhpFile file
) implements PhpSymbol {
}
