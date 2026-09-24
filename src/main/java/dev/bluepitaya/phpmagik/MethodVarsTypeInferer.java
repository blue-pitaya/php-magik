package dev.bluepitaya.phpmagik;

import dev.bluepitaya.phpmagik.phpsymbol.PhpMethodVarUsage;
import dev.bluepitaya.phpmagik.phpsymbol.PhpParameterDeclaration;
import dev.bluepitaya.phpmagik.phpsymbol.PhpSymbolCollection;
import dev.bluepitaya.phpmagik.phpsymbol.PhpType;
import org.jspecify.annotations.NullMarked;

@NullMarked
public final class MethodVarsTypeInferer {

    public void infer(PhpSymbolCollection symbols) {
        for (PhpMethodVarUsage usage : symbols.varUsages()) {
            if (!(usage.definition() instanceof PhpParameterDeclaration parameter)) {
                continue;
            }

            PhpType phpType = parameter.phpType();
            if (phpType != null) {
                usage.phpType(phpType);
            }

            String namedTypeName = parameter.namedTypeName();
            if (namedTypeName != null) {
                usage.namedTypeName(namedTypeName);
            }
        }
    }
}
