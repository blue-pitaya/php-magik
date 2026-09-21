package dev.bluepitaya.phpmagik.phpsymbol;

import dev.bluepitaya.phpmagik.ts.Range;

/**
 * One declared plain function. A method is a {@link PhpMethodDefinition}.
 *
 * <p>Everything the declaration says about itself is read off the tree while it
 * is being indexed, so answering a query never has to go back to the tree.
 *
 * @param ns enclosing namespace, {@code null} at global scope
 * @param returnType declared, or inferred from the first {@code return};
 * {@code null} when neither is known
 * @param signature the declaration as written, up to but not including the
 * body, with an inferred return type appended when the source declares none
 * @param doc the PHPDoc block above it, markers stripped and tags left as
 * written; {@code null} if there is none
 * @param range spans the name alone, not the declaration
 * @param scope spans the whole declaration, body included, which is what makes
 * a point resolvable to the function containing it
 */
public record PhpFunctionDefinition(
        String name,
        String ns,
        String returnType,
        String signature,
        String doc,
        Range range,
        Range scope,
        int fileId) implements PhpSymbol {

    /** {@code App\Example\run}, the name a hover shows. */
    public String qualifiedName() {
        return ns == null ? name : ns + "\\" + name;
    }
}
