package dev.bluepitaya.phpmagik.phpsymbol;

/** What a {@code use} imports: {@code use function f}, {@code use const C}, else a class-like. */
public enum UseKind {
    CLASS,
    FUNCTION,
    CONST
}
