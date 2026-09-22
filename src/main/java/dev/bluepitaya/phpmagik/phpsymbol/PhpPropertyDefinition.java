package dev.bluepitaya.phpmagik.phpsymbol;

import dev.bluepitaya.phpmagik.PhpFile;
import dev.bluepitaya.phpmagik.ts.Range;

public record PhpPropertyDefinition(
        String name,
        PhpClassDefinition owner,
        String type,
        Range range,
        PhpFile file) implements PhpSymbol {

    public String qualifiedName() {
        return owner.fqn() + "::" + name;
    }
}
