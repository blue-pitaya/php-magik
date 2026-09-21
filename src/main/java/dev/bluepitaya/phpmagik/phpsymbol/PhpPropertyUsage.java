package dev.bluepitaya.phpmagik.phpsymbol;

import dev.bluepitaya.phpmagik.ts.Range;

/**
 * One read of a property through an object: {@code $this->x} or {@code $obj->x}.
 *
 * <p>Only the access itself, not what it resolves to - pair it with its
 * {@link PhpPropertyDefinition} through {@code PropertyResolver}.
 *
 * @param name spelled as the declaration spells it, with the leading {@code $},
 * even though the source text after {@code ->} has none
 * @param className the class the indexer resolved the object to: the enclosing
 * class for {@code $this}, else the variable's known type. An access whose
 * object has no known class is not indexed at all, so this is never empty
 * @param range spans the name as written, so without the {@code $}
 */
public record PhpPropertyUsage(
        String name,
        String className,
        Range range,
        int fileId) implements PhpSymbol {
}
