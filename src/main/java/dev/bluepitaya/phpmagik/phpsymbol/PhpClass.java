package dev.bluepitaya.phpmagik.phpsymbol;

import dev.bluepitaya.phpmagik.ts.Range;

/**
 * One declared class, interface, trait or enum.
 *
 * @param name as declared, with no namespace
 * @param ns enclosing namespace, {@code null} at global scope
 * @param fqn {@code ns\name}, or just {@code name} at global scope; no leading
 * {@code \}, so it compares directly against a resolved {@link PhpUseStatement}
 * @param range spans the declared name rather than the whole declaration
 * @param scope spans the whole declaration, body included, which is what makes
 * a point resolvable to the class containing it
 */
public record PhpClass(
        String name,
        String ns,
        String fqn,
        ClassKind kind,
        Range range,
        Range scope,
        int fileId) implements PhpSymbol {
}
