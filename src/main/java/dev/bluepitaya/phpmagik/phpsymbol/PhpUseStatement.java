package dev.bluepitaya.phpmagik.phpsymbol;

import dev.bluepitaya.phpmagik.PhpFile;
import dev.bluepitaya.phpmagik.ts.Range;

public record PhpUseStatement(String alias, String fqn, UseKind kind, Range range, PhpFile file)
        implements PhpSymbol {
}
