package dev.bluepitaya.phpmagik;

import dev.bluepitaya.phpmagik.phpsymbol.PhpMethodLocalVarDeclaration;
import dev.bluepitaya.phpmagik.phpsymbol.PhpMethodVarUsage;
import dev.bluepitaya.phpmagik.phpsymbol.PhpParameterDeclaration;
import dev.bluepitaya.phpmagik.phpsymbol.PhpSymbolCollection;
import dev.bluepitaya.phpmagik.phpsymbol.PhpType;
import org.jspecify.annotations.NullMarked;

@NullMarked
public final class VarUsageTypePropagator {

    public void propagate(PhpSymbolCollection symbols) {
        for (PhpMethodVarUsage usage : symbols.varUsages()) {
            PhpType type = switch (usage.definition()) {
                case PhpParameterDeclaration parameter -> parameter.type();
                case PhpMethodLocalVarDeclaration local -> local.type();
                case null, default -> null;
            };
            if (type != null) {
                usage.type(type);
            }
        }
    }
}
