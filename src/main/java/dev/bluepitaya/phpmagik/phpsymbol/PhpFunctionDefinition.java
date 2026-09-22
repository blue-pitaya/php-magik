package dev.bluepitaya.phpmagik.phpsymbol;

import dev.bluepitaya.phpmagik.PhpFile;
import dev.bluepitaya.phpmagik.ts.Range;

public record PhpFunctionDefinition(
        String name,
        String ns,
        String returnType,
        Range returnSource,
        String signature,
        String doc,
        Range range,
        Range scope,
        PhpFile file) implements PhpSymbol {

    public String qualifiedName() {
        return ns == null ? name : ns + "\\" + name;
    }
}
