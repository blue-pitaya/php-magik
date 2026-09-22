package dev.bluepitaya.phpmagik.phpsymbol;

import dev.bluepitaya.phpmagik.PhpFile;
import dev.bluepitaya.phpmagik.ts.Range;

public sealed interface PhpSymbol permits
        PhpVarDefinition,
        PhpVarUsage,
        PhpFunctionDefinition,
        PhpFunctionUsage,
        PhpMethodDefinition,
        PhpMethodUsage,
        PhpPropertyDefinition,
        PhpPropertyUsage,
        PhpClassDefinition,
        PhpClassUsage,
        PhpUseStatement,
        PhpNamespaceDefinition {

    Range range();

    PhpFile file();
}
