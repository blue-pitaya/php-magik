package dev.bluepitaya.phpmagik.ts;

/**
 * Bounds-safe child accessors.
 *
 * <p>C's {@code ts_node_child} returns a null node when the index is out of
 * range, which callers test with {@code ts_node_is_null}. The binding instead
 * throws {@link IndexOutOfBoundsException}, so ported code that relied on the
 * C behaviour goes through these.
 */
public final class Nodes {

    private Nodes() {
    }

    public static Node namedChild(Node node, int index) {
        if (node == null || index < 0 || index >= node.getNamedChildCount()) return null;
        return node.getNamedChild(index);
    }

    public static Node child(Node node, int index) {
        if (node == null || index < 0 || index >= node.getChildCount()) return null;
        return node.getChild(index);
    }

    public static String type(Node node) {
        return node == null ? null : node.getType();
    }

    public static String text(Node node) {
        return node == null ? null : node.getContent();
    }
}
