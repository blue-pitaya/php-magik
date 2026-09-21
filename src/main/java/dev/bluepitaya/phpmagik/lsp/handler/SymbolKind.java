package dev.bluepitaya.phpmagik.lsp.handler;

/** LSP SymbolKind, as far as the PHP declarations this server indexes need it. */
final class SymbolKind {

    static final int NAMESPACE = 3;
    static final int CLASS = 5;
    static final int METHOD = 6;
    static final int PROPERTY = 7;
    static final int CONSTRUCTOR = 9;
    static final int ENUM = 10;
    static final int INTERFACE = 11;
    static final int FUNCTION = 12;
    static final int ENUM_MEMBER = 22;

    private SymbolKind() {
    }
}
