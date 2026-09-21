package dev.bluepitaya.phpmagik.phpsymbol;

import dev.bluepitaya.phpmagik.ts.Range;

/**
 * Where a plain variable comes into being: a parameter, or an assignment to it.
 *
 * <p>PHP declares neither, so every assignment counts as one of these, not just
 * the first. The earliest is what go-to-definition jumps to; each later one may
 * retype the variable from that point on.
 *
 * @param name the leading {@code $} is kept, so {@code "$foo"}
 * @param ns enclosing namespace, {@code null} if not in one
 * @param functionName enclosing function, {@code null} at file scope; with
 * {@code fileId} it is the scope a name is unique within
 * @param type declared on a parameter, inferred from the right-hand side of an
 * assignment, {@code null} when neither says anything
 * @param range spans the name as written
 */
public record PhpVarDefinition(
        String name,
        String ns,
        String functionName,
        String type,
        Range range,
        int fileId) implements PhpSymbol {
}
