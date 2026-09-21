package dev.bluepitaya.phpmagik.phpsymbol;

import dev.bluepitaya.phpmagik.ts.Range;

/**
 * One property declared in a class, interface, trait or enum.
 *
 * <p>A declaration rather than a usage: {@code $this->x} and {@code $obj->x}
 * are {@link PhpPropertyUsage}s, since an access knows nothing but the name and
 * the class it was read from.
 *
 * <p>A constructor-promoted property is not one of these either - the source
 * writes it as a parameter, and that is how it is indexed.
 *
 * @param name the leading {@code $} is kept, so {@code "$x"}, which is also how
 * an access spells it once it has been matched to this declaration
 * @param owner the declaring class, which is also where the namespace and the
 * qualified name come from
 * @param type as written on the declaration, {@code null} when untyped
 * @param range spans the name, the {@code $} included
 */
public record PhpPropertyDefinition(
        String name,
        PhpClass owner,
        String type,
        Range range,
        int fileId) implements PhpSymbol {

    /** {@code App\NSA\A::$x}. */
    public String qualifiedName() {
        return owner.fqn() + "::" + name;
    }
}
