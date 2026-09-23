package dev.bluepitaya.phpmagik.phpsymbol;

import dev.bluepitaya.phpmagik.PhpFile;
import dev.bluepitaya.phpmagik.ts.Range;

public sealed interface PhpSymbol permits
        PhpNamespaceDefinition,
        PhpClassDeclaration,
        PhpPropertyDeclaration,
        PhpMethodDeclaration,
        PhpParameterDeclaration {

    Range range();

    PhpFile file();
}
