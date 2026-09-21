package dev.bluepitaya.phpmagik.phpsymbol;

import dev.bluepitaya.phpmagik.ts.Range;

/**
 * One call through an object: {@code $this->foo()} or {@code $obj->foo()}.
 *
 * <p>Only the call itself, not what it resolves to - pair it with its
 * {@link PhpMethodDefinition} through {@code MethodResolver}.
 *
 * @param className the class the indexer resolved the object to: the enclosing
 * class for {@code $this}, else the variable's known type. A call whose object
 * has no known class is not indexed at all, so this is never empty
 * @param range spans the name alone, without the arguments
 */
public record PhpMethodUsage(
        String name,
        String className,
        Range range,
        int fileId) implements PhpSymbol {
}
