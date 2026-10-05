package dev.bluepitaya.phpmagik.ts;

import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

import static dev.bluepitaya.phpmagik.ts.Tree.ERROR;
import static dev.bluepitaya.phpmagik.ts.Tree.EXTRA;
import static dev.bluepitaya.phpmagik.ts.Tree.MISSING;
import static dev.bluepitaya.phpmagik.ts.Tree.NAMED;
import static dev.bluepitaya.phpmagik.ts.Tree.NEXT;
import static dev.bluepitaya.phpmagik.ts.Tree.NONE;
import static dev.bluepitaya.phpmagik.ts.Tree.PARENT;

@NullMarked
public final class Node {

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
        return children(false);
    }

    public List<Node> getNamedChildren() {
        return children(true);
    }

    public int getNamedChildCount() {
        return getNamedChildren().size();
    }

    public Node getNamedChild(int child) {
        return getNamedChildren().get(child);
    }

    public @Nullable Node getChildByFieldName(String name) {
        for (int child = tree.firstChild(index); child != NONE; child = tree.get(child, NEXT)) {
            if (name.equals(tree.field(child))) return new Node(tree, child);
        }
        return null;
    }

    public @Nullable String getFieldName() {
        return tree.field(index);
    }

    public @Nullable Node getParent() {
        return node(tree.get(index, PARENT));
    }

    public @Nullable Node getPrevSibling() {
        int previous = NONE;
        for (int child = tree.get(index, PARENT) + 1; child != index; child = tree.get(child, NEXT)) {
            previous = child;
        }
        return node(previous);
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

    public boolean isError() {
        return tree.is(index, ERROR);
    }

    public boolean isExtra() {
        return tree.is(index, EXTRA);
    }

    public boolean isMissing() {
        return tree.is(index, MISSING);
    }

    public boolean isNamed() {
        return tree.is(index, NAMED);
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

    private List<Node> children(boolean named) {
        List<Node> children = new ArrayList<>();
        for (int child = tree.firstChild(index); child != NONE; child = tree.get(child, NEXT)) {
            if (!named || tree.is(child, NAMED)) children.add(new Node(tree, child));
        }
        return children;
    }
}
