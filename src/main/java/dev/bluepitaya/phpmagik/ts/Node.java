package dev.bluepitaya.phpmagik.ts;

import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

import java.util.*;

import static dev.bluepitaya.phpmagik.ts.Tree.*;

@NullMarked
public final class Node {

    public enum Flag {
        NAMED(Tree.NAMED),
        EXTRA(Tree.EXTRA),
        MISSING(Tree.MISSING),
        ERROR(Tree.ERROR);

        private final int bit;

        Flag(int bit) {
            this.bit = bit;
        }
    }

    private final Tree tree;
    private final int index;

    Node(Tree tree, int index) {
        this.tree = tree;
        this.index = index;
    }

    public static @Nullable Node namedChild(@Nullable Node node, int index) {
        if (node == null || index < 0 || index >= node.getNamedChildCount()) return null;
        return node.getNamedChild(index);
    }

    public List<Node> getChildren() {
        List<Node> children = new ArrayList<>();
        childNodes().forEach(children::add);

        return children;
    }

    public List<Node> getNamedChildren() {
        List<Node> children = getChildren();
        children.removeIf(ch -> !tree.is(ch.index, NAMED));

        return children;
    }

    public int getNamedChildCount() {
        return getNamedChildren().size();
    }

    public Node getNamedChild(int child) {
        return getNamedChildren().get(child);
    }

    @Deprecated
    public @Nullable Node getChildByFieldName(String name) {
        for (Node child : childNodes()) {
            if (name.equals(tree.field(child.index))) return child;
        }
        return null;
    }

    public Optional<Node> getChildByFieldNameOpt(String name) {
        for (Node child : childNodes()) {
            if (name.equals(tree.field(child.index))) {
                return Optional.of(child);
            }
        }

        return Optional.empty();
    }

    public @Nullable String getFieldName() {
        return tree.field(index);
    }

    @Deprecated
    public @Nullable Node getParent() {
        return node(tree.get(index, PARENT));
    }

    public Optional<Node> getParentOpt() {
        return Optional.ofNullable(node(tree.get(index, PARENT)));
    }

    public Node getParentOrThrow() {
        Node parent = node(tree.get(index, PARENT));
        if (parent == null) {
            throw new NoSuchElementException(getType() + " has no parent");
        }
        return parent;
    }

    private Iterable<Node> childNodes() {
        return () -> new Iterator<>() {
            private int child = tree.firstChild(index);

            @Override
            public boolean hasNext() {
                return child != NONE;
            }

            @Override
            public Node next() {
                if (child == NONE) {
                    throw new NoSuchElementException();
                }
                Node node = new Node(tree, child);
                child = tree.get(child, NEXT);
                return node;
            }
        };
    }

    public @Nullable Node getPrevSibling() {
        Node parent = node(tree.get(index, PARENT));
        if (parent == null) {
            return null;
        }
        Node previous = null;
        for (Node child : parent.childNodes()) {
            if (child.index == index) {
                break;
            }
            previous = child;
        }
        return previous;
    }

    public Node getDescendant(Point start, Point end) {
        int node = index;
        int child = tree.firstChild(node);
        while (child != NONE) {
            Point childStart = tree.start(child);
            Point childEnd = tree.end(child);
            int past = childEnd.compareTo(start);
            if (childEnd.compareTo(end) < 0 || past < 0 || (past == 0 && !childStart.equals(childEnd))) {
                child = tree.get(child, NEXT);
            } else if (start.compareTo(childStart) < 0) {
                break;
            } else {
                node = child;
                child = tree.firstChild(node);
            }
        }
        return new Node(tree, node);
    }

    public Point getStartPoint() {
        return tree.start(index);
    }

    public Point getEndPoint() {
        return tree.end(index);
    }

    public Range getRange() {
        return new Range(getStartPoint(), getEndPoint());
    }

    public String getType() {
        return tree.type(index);
    }

    public boolean isType(String type) {
        return type.equals(getType());
    }

    public String getContent() {
        return tree.content(index);
    }

    public boolean is(Flag flag) {
        return tree.is(index, flag.bit);
    }

    @Override
    public boolean equals(@Nullable Object other) {
        return other instanceof Node node && node.tree == tree && node.index == index;
    }

    @Override
    public int hashCode() {
        return index;
    }

    @Override
    public String toString() {
        return getType() + " [" + getStartPoint() + " - " + getEndPoint() + "]";
    }

    private @Nullable Node node(int node) {
        return node == NONE ? null : new Node(tree, node);
    }
}
