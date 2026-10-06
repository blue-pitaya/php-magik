package dev.bluepitaya.phpmagik.phpsymbol;

import dev.bluepitaya.phpmagik.PhpFile;
import dev.bluepitaya.phpmagik.ts.Range;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

@NullMarked
public sealed interface PhpSymbolOwner permits
        PhpNamespaceDefinition,
        PhpClassDeclaration,
        PhpFunctionLike {

    PhpFile file();

    @Nullable Range scope();
}
