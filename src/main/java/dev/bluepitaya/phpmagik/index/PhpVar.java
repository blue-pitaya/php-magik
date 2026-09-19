package dev.bluepitaya.phpmagik.index;

/**
 * One indexed occurrence of a variable: a property, a parameter, a plain local,
 * or a {@code $this->}/{@code $obj->} property access.
 *
 * @param name the leading {@code $} is kept, so {@code "$foo"}; accesses through
 * an object store a synthetic {@code $}-prefixed name to match against the
 * property declaration, even though the source text has no {@code $} there
 * @param ns enclosing namespace, {@code null} if not in one
 * @param className owning class, {@code null} outside one
 * @param functionName enclosing function, {@code null} at file scope
 * @param type resolved or declared type, {@code null} if unknown
 * @param line 0-based
 * @param col 0-based, a UTF-8 byte offset into the line
 */
public record PhpVar(
        String name,
        String ns,
        String className,
        String functionName,
        String type,
        VarKind kind,
        int line,
        int col,
        int fileId) {
}
