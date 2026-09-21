package dev.bluepitaya.phpmagik.phpsymbol;

import dev.bluepitaya.phpmagik.ts.Range;

/**
 * One read of a plain variable.
 *
 * <p>Pair it with its {@link PhpVarDefinition} through {@code VariableResolver}.
 * Some of these are the closest thing to a declaration the index has: nothing
 * writes {@code $this}, a {@code foreach} value or a {@code catch} variable,
 * yet each of those is where a variable begins.
 *
 * @param name the leading {@code $} is kept, so {@code "$foo"}
 * @param ns enclosing namespace, {@code null} if not in one
 * @param functionName enclosing function, {@code null} at file scope; with
 * {@code fileId} it is the scope the definition is looked up in
 */
public record PhpVarUsage(
        String name,
        String ns,
        String functionName,
        Range range,
        int fileId) implements PhpSymbol {
}
