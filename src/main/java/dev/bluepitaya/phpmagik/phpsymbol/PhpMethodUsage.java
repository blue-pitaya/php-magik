package dev.bluepitaya.phpmagik.phpsymbol;

import dev.bluepitaya.phpmagik.ts.Range;

/**
 * One call through an object: {@code $this->foo()} or {@code $obj->foo()}.
 *
 * <p>Only the call itself, not what it resolves to - pair it with its
 * {@link PhpMethodDefinition} through {@code MethodResolver}.
 *
 * @param className set for {@code $this} alone, where the enclosing class
 * answers it outright and nothing has to be typed; {@code null} for every other
 * object, whose class is worked out from {@code objectSource} when the call is
 * resolved
 * @param objectSource where the object came from - a variable, a property read,
 * a call - as the range of the usage the index recorded there, and {@code null}
 * when the object is something no usage names, as in {@code (new Foo)->x()}
 * @param range spans the name alone, without the arguments
 */
public record PhpMethodUsage(
        String name,
        String className,
        Range objectSource,
        Range range,
        int fileId) implements PhpSymbol {
}
