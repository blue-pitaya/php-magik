package dev.bluepitaya.phpmagik.index;

/**
 * One indexed occurrence of a function: a declaration, a plain call, or a method
 * call through an object.
 *
 * @param className owning class for methods, {@code null} for plain functions
 * @param returnType declarations only, {@code null} if unknown
 * @param line 0-based
 * @param col 0-based, a UTF-8 byte offset into the line
 */
public record PhpFunction(
        String name,
        String ns,
        String className,
        String functionName,
        FuncKind kind,
        String returnType,
        int line,
        int col,
        int fileId) implements PhpSymbol {
}
