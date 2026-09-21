package dev.bluepitaya.phpmagik.ts;


import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

import java.util.ArrayDeque;
import java.util.Collections;
import java.util.Deque;
import java.util.Iterator;
import java.util.List;
import java.util.NoSuchElementException;

/**
 * A single node within a syntax {@link Tree}, and a port of the by-value struct:
 *
 * <pre>
 * typedef struct TSNode {
 *   uint32_t context[4];
 *   const void *id;
 *   const TSTree *tree;
 * } TSNode;
 * </pre>
 *
 * C passes it by value, so an instance owns nothing: every native call copies
 * these fields back onto the C stack, and a resulting node is copied into a new
 * instance. The context words are opaque to Java and only have to round-trip,
 * which they do even though Java's ints are signed.
 *
 * <p>The tree is the one field not copied verbatim: Java holds the {@link Tree}
 * object and C reads the pointer back out of it, so a reachable node keeps the
 * tree it points into alive.
 */
@NullMarked
public class Node implements Iterable<Node> {

    static {
        LibraryLoader.load();
    }

    private final int context0;
    private final int context1;
    private final int context2;
    private final int context3;

    private final long id;

    private final @Nullable Tree tree;

    Node(int context0, int context1, int context2, int context3, long id, @Nullable Tree tree) {
        this.context0 = context0;
        this.context1 = context1;
        this.context2 = context2;
        this.context3 = context3;
        this.id = id;
        this.tree = tree;
    }

    /**
     * @throws IndexOutOfBoundsException if the index is negative, or greater than
     * or equal to the total number of children
     */
    public @Nullable Node getChild(int child) {
        return getChild(child, false);
    }

    public @Nullable Node getNamedChild(int child) {
        return getChild(child, true);
    }

    private native @Nullable Node getChild(int child, boolean named);

    /**
     * @return the child in that field, {@code null} if there is none
     * @throws NullPointerException if {@code name} is {@code null}
     */
    public native @Nullable Node getChildByFieldName(@Nullable String name);

    public int getChildCount() {
        return getChildCount(false);
    }

    public int getNamedChildCount() {
        return getChildCount(true);
    }

    private native int getChildCount(boolean named);

    public @Nullable List<Node> getChildren() {
        return List.of(getChildren(this, false));
    }

    public @Nullable List<Node> getNamedChildren() {
        return List.of(getChildren(this, true));
    }

    private static native Node[] getChildren(@Nullable Node node, boolean named);

    /** The source code this node spans, decoded from the tree's UTF-8 bytes. */
    public @Nullable String getContent() {
        return isNull() ? null : tree.getSource(getStartByte(), getEndByte());
    }

    /**
     * The smallest node within this node spanning the given byte range.
     *
     * @throws IllegalArgumentException if either offset is negative, or if
     * {@code startByte} is greater than {@code endByte}
     */
    public @Nullable Node getDescendant(int startByte, int endByte) {
        return getDescendant(startByte, endByte, false);
    }

    public @Nullable Node getNamedDescendant(int startByte, int endByte) {
        return getDescendant(startByte, endByte, true);
    }

    private native @Nullable Node getDescendant(int startByte, int endByte, boolean named);

    /**
     * The smallest node within this node spanning the given range of points.
     *
     * @throws NullPointerException if either point is {@code null}
     */
    public @Nullable Node getDescendant(@Nullable Point startPoint, @Nullable Point endPoint) {
        return getDescendant(startPoint, endPoint, false);
    }

    public @Nullable Node getNamedDescendant(@Nullable Point startPoint, @Nullable Point endPoint) {
        return getDescendant(startPoint, endPoint, true);
    }

    private native @Nullable Node getDescendant(@Nullable Point startPoint, @Nullable Point endPoint,
                                                boolean named);

    /** Includes the node itself. */
    public native int getDescendantCount();

    public native int getEndByte();

    public native @Nullable Point getEndPoint();

    /**
     * @return the field name, {@code null} if that child does not reside in a field
     * @throws IndexOutOfBoundsException if the index is negative, or greater than
     * or equal to the total number of children
     */
    public native @Nullable String getFieldNameForChild(int child);

    /**
     * The first child extending beyond the given byte offset.
     *
     * @throws IllegalArgumentException if the offset is outside this node's range
     */
    public @Nullable Node getFirstChildForByte(int offset) {
        return getFirstChildForByte(offset, false);
    }

    public @Nullable Node getFirstNamedChildForByte(int offset) {
        return getFirstChildForByte(offset, true);
    }

    private native @Nullable Node getFirstChildForByte(int offset, boolean named);

    /** @return the next sibling, {@code null} if there is none */
    public @Nullable Node getNextSibling() {
        return getNextSibling(false);
    }

    public @Nullable Node getNextNamedSibling() {
        return getNextSibling(true);
    }

    private native @Nullable Node getNextSibling(boolean named);

    /** @return the previous sibling, {@code null} if there is none */
    public @Nullable Node getPrevSibling() {
        return getPrevSibling(false);
    }

    public @Nullable Node getPrevNamedSibling() {
        return getPrevSibling(true);
    }

    private native @Nullable Node getPrevSibling(boolean named);

    /** @return the parent, {@code null} if this is the root */
    public native @Nullable Node getParent();

    /**
     * The s-expression of this subtree. Not part of the upstream API - upstream
     * exposes {@code ts_node_string} through its printer classes instead.
     */
    public native @Nullable String getSexp();

    public native int getStartByte();

    public native @Nullable Point getStartPoint();

    public @Nullable Tree getTree() {
        return tree;
    }

    public @Nullable String getType() {
        return getType(false);
    }

    /** The type as it appears in the grammar, ignoring aliases. */
    public @Nullable String getGrammarType() {
        return getType(true);
    }

    private native @Nullable String getType(boolean grammar);

    /** Whether this node or any node in its subtree has been edited. */
    public native boolean hasChanges();

    /** Whether this node is an {@code ERROR}, or contains one in its subtree. */
    public native boolean hasError();

    public native boolean isError();

    /** Extras are nodes the grammar does not require anywhere, such as comments. */
    public native boolean isExtra();

    /** Missing nodes are inserted by the parser to recover from a syntax error. */
    public native boolean isMissing();

    /** Named nodes come from named grammar rules, anonymous ones from literals. */
    public native boolean isNamed();

    public native boolean isNull();

    /** Delegates to {@code ts_node_eq}: the same id within the same tree. */
    @Override
    public boolean equals(@Nullable Object obj) {
        if (obj == this) return true;
        if (obj == null || getClass() != obj.getClass()) return false;
        return equals(this, (Node) obj);
    }

    private static native boolean equals(@Nullable Node node, @Nullable Node other);

    @Override
    public int hashCode() {
        return Long.hashCode(id);
    }

    @Override
    public String toString() {
        return isNull() ? "<null node>" : getType() + " [" + getStartPoint() + " - " + getEndPoint() + "]";
    }

    /**
     * Depth-first iterator over this subtree, in source order, starting with this
     * node. Upstream documents the same but pushes children with {@code addAll},
     * which appends to the tail and so walks the tree breadth-first.
     */
    @Override
    public Iterator<Node> iterator() {
        return new Iterator<>() {

            private final Deque<Node> stack = new ArrayDeque<>(Collections.singletonList(Node.this));

            @Override
            public boolean hasNext() {
                return !stack.isEmpty();
            }

            @Override
            public Node next() {
                if (!hasNext()) throw new NoSuchElementException();
                Node node = stack.pop();
                List<Node> children = node.getChildren();
                for (int child = children.size() - 1; child >= 0; child--) {
                    stack.push(children.get(child));
                }
                return node;
            }
        };
    }
}
