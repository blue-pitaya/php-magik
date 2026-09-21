package dev.bluepitaya.phpmagik.phpsymbol;

import dev.bluepitaya.phpmagik.ts.Range;

/**
 * One method declared in a class, interface, trait or enum.
 *
 * <p>A declaration rather than a usage: {@code $obj->foo()} is a
 * {@link PhpMethodUsage}, since a call site knows nothing but the name and the
 * class it was called on.
 *
 * @param owner the declaring class, which is also where the namespace and the
 * qualified name come from - a method has none of its own
 * @param returnType declared, or inferred from the first {@code return};
 * {@code null} when neither is known
 * @param signature the declaration as written, from its modifiers up to but not
 * including the body, with an inferred return type appended when the source
 * declares none
 * @param doc the PHPDoc block above the declaration, markers stripped and tags
 * left as written; {@code null} if there is none
 * @param range spans the name alone, not the declaration
 * @param scope spans the whole declaration, body included
 * @param fileId always the file {@code owner} is declared in: a method cannot
 * be written outside its class
 */
public record PhpMethodDefinition(
        String name,
        PhpClass owner,
        String returnType,
        String signature,
        String doc,
        Range range,
        Range scope,
        int fileId) implements PhpSymbol {

    /** {@code App\NSA\A::get}, the name a hover shows. */
    public String qualifiedName() {
        return owner.fqn() + "::" + name;
    }
}
