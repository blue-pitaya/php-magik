package dev.bluepitaya.phpmagik.phpsymbol;

import dev.bluepitaya.phpmagik.PhpFile;
import dev.bluepitaya.phpmagik.ts.Range;

public record PhpMethodDefinition(
        String name,
        PhpClassDefinition owner,
        String returnType,
        Range returnSource,
        String signature,
        String doc,
        Range range,
        Range scope,
        PhpFile file) implements PhpSymbol {

    public String qualifiedName() {
        return owner.fqn() + "::" + name;
    }
}
