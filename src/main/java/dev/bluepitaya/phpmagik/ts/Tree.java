package dev.bluepitaya.phpmagik.ts;

import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

import java.nio.charset.StandardCharsets;

@NullMarked
public final class Tree {

    static final int TYPE = 0;
    static final int FIELD = 1;
    static final int FLAGS = 2;
    static final int PARENT = 3;
    static final int NEXT = 4;
    static final int START_BYTE = 5;
    static final int END_BYTE = 6;
    static final int START_ROW = 7;
    static final int START_COLUMN = 8;
    static final int END_ROW = 9;
    static final int END_COLUMN = 10;
    static final int STRIDE = 11;

    static final int NAMED = 1;
    static final int EXTRA = 2;
    static final int MISSING = 4;
    static final int ERROR = 8;

    static final int NONE = -1;

    static {
        System.loadLibrary("tsjni");
    }

    private static final String[] TYPES = types();
    private static final @Nullable String[] FIELDS = fields();

    private final byte[] source;
    private final int[] nodes;

    private Tree(byte[] source, int[] nodes) {
        this.source = source;
        this.nodes = nodes;
    }

    public static Tree parse(byte[] source) {
        return new Tree(source, flatten(source));
    }

    private static native String[] types();

    private static native @Nullable String[] fields();

    private static native int[] flatten(byte[] source);

    public Node getRootNode() {
        return new Node(this, 0);
    }

    int get(int node, int column) {
        return nodes[node * STRIDE + column];
    }

    boolean is(int node, int flag) {
        return (get(node, FLAGS) & flag) != 0;
    }

    int firstChild(int node) {
        int child = node + 1;
        return child * STRIDE < nodes.length && get(child, PARENT) == node ? child : NONE;
    }

    String type(int node) {
        return is(node, ERROR) ? "ERROR" : TYPES[get(node, TYPE)];
    }

    @Nullable String field(int node) {
        return FIELDS[get(node, FIELD)];
    }

    Point start(int node) {
        return new Point(get(node, START_ROW), get(node, START_COLUMN));
    }

    Point end(int node) {
        return new Point(get(node, END_ROW), get(node, END_COLUMN));
    }

    String content(int node) {
        int start = get(node, START_BYTE);
        return new String(source, start, get(node, END_BYTE) - start, StandardCharsets.UTF_8);
    }
}
