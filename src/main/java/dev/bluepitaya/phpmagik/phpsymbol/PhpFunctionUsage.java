package dev.bluepitaya.phpmagik.phpsymbol;

import dev.bluepitaya.phpmagik.ts.Range;

/**
 * One call to a function by name: {@code foo()}, not {@code $obj->foo()}.
 *
 * <p>Pair it with its {@link PhpFunctionDefinition} through
 * {@code FunctionResolver}.
 *
 * @param ns the namespace the call was written in, {@code null} at global
 * scope - which is not necessarily the namespace of what it calls
 * @param range spans the name alone, without the arguments
 */
public record PhpFunctionUsage(
        String name,
        String ns,
        Range range,
        int fileId) implements PhpSymbol {
}
