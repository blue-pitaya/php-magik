package dev.bluepitaya.phpmagik.phpsymbol;

import dev.bluepitaya.phpmagik.ts.Range;

/**
 * One place a class is named rather than declared: a parameter, property,
 * return or {@code catch} type, {@code new Foo}, an {@code extends} or
 * {@code implements} clause, {@code instanceof Foo}, a {@code Foo::} access, a
 * trait {@code use}, or an attribute.
 *
 * <p>Only the reference itself, not what it resolves to - pair it with its
 * {@link PhpClassDefinition} through {@code ClassResolver}.
 *
 * <p>An import is a {@link PhpUseStatement} instead: {@code use App\Foo;} names
 * a class to bring the name into the file, not to use it there.
 *
 * @param name as written, so a bare name, an alias or a qualified one, any
 * leading {@code \} kept
 * @param ns the namespace it was written in, {@code null} at global scope;
 * with {@code fileId} it is what a bare name has to be resolved against
 * @param range spans the name as written
 */
public record PhpClassUsage(
        String name,
        String ns,
        Range range,
        int fileId) implements PhpSymbol {
}
