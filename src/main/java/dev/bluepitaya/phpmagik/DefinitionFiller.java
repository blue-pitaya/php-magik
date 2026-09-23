package dev.bluepitaya.phpmagik;

import dev.bluepitaya.phpmagik.phpsymbol.PhpMethodLocalVarDeclaration;
import dev.bluepitaya.phpmagik.phpsymbol.PhpMethodVarUsage;
import dev.bluepitaya.phpmagik.phpsymbol.PhpParameterDeclaration;
import dev.bluepitaya.phpmagik.phpsymbol.PhpSymbol;
import dev.bluepitaya.phpmagik.phpsymbol.PhpSymbolCollection;
import dev.bluepitaya.phpmagik.ts.Point;
import dev.bluepitaya.phpmagik.ts.Range;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

@NullMarked
public final class DefinitionFiller {

    public void fill(PhpSymbolCollection symbols) {
        for (PhpMethodVarUsage usage : symbols.varUsages()) {
            PhpSymbol definition = definitionOf(usage, symbols);
            if (definition != null) {
                usage.definition(definition);
            }
        }
    }

    private static @Nullable PhpSymbol definitionOf(
            PhpMethodVarUsage usage, PhpSymbolCollection symbols
    ) {
        PhpMethodLocalVarDeclaration assigned = assignedAbove(usage, symbols);

        return assigned != null ? assigned : parameterOf(usage, symbols);
    }

    private static @Nullable PhpParameterDeclaration parameterOf(
            PhpMethodVarUsage usage, PhpSymbolCollection symbols
    ) {
        String name = usage.name();
        if (name == null || usage.owner() == null) {
            return null;
        }

        for (PhpParameterDeclaration declared : symbols.parameterDeclarations()) {
            if (declared.owner() == usage.owner() && name.equals(declared.name())) {
                return declared;
            }
        }

        return null;
    }

    private static @Nullable PhpMethodLocalVarDeclaration assignedAbove(
            PhpMethodVarUsage usage, PhpSymbolCollection symbols
    ) {
        Range range = usage.range();
        String name = usage.name();
        if (range == null || name == null || usage.owner() == null) {
            return null;
        }

        PhpMethodLocalVarDeclaration found = null;
        Point at = null;
        for (PhpMethodLocalVarDeclaration declared : symbols.localVarDeclarations()) {
            Range declaredRange = declared.range();
            if (declared.owner() != usage.owner() || !name.equals(declared.name())
                    || declaredRange == null) {
                continue;
            }

            Point start = declaredRange.start();
            /* the nearest assignment above wins: a later one has not run yet */
            if (start.compareTo(range.start()) > 0) continue;
            if (at == null || start.compareTo(at) > 0) {
                found = declared;
                at = start;
            }
        }

        return found;
    }
}
