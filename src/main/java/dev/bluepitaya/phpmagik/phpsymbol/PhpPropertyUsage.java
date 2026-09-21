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
 * @param className set for {@code $this} alone, where the enclosing class
 * answers it outright and nothing has to be typed; {@code null} for every other
 * object, whose class is worked out from {@code objectSource} when the access is
 * resolved
 * @param objectSource where the object came from - a variable, a property read,
 * a call - as the range of the usage the index recorded there, and {@code null}
 * when the object is something no usage names, as in {@code (new Foo)->x}
 * @param range spans the name as written, so without the {@code $}
 */
public record PhpPropertyUsage(
        String name,
        String className,
        Range objectSource,
        Range range,
        int fileId) implements PhpSymbol {
}
