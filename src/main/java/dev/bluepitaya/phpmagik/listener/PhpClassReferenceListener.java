package dev.bluepitaya.phpmagik.listener;

import dev.bluepitaya.phpmagik.CompleteIndexer;
import dev.bluepitaya.phpmagik.PhpFile;
import dev.bluepitaya.phpmagik.phpsymbol.PhpReference;
import dev.bluepitaya.phpmagik.phpsymbol.PhpSymbolCollection;
import dev.bluepitaya.phpmagik.phpsymbol.PhpSymbolOwner;
import dev.bluepitaya.phpmagik.ts.Node;
import dev.bluepitaya.phpmagik.ts.Nodes;
import dev.bluepitaya.phpmagik.ts.Range;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

@NullMarked
public final class PhpClassReferenceListener implements Listener {

    private final PhpSymbolCollection collection;
    private final PhpFile file;

    public PhpClassReferenceListener(
            PhpSymbolCollection collection, PhpFile file
    ) {
        this.collection = collection;
        this.file = file;
    }

    public void enter(CompleteIndexer.Ctx ctx, Node node) {
        switch (node.getType()) {
            case "qualified_name", "relative_name" -> {
                if (isScopeOf(node, ctx.parent())) {
                    add(ctx, lastName(node), node);
                }
            }
            case null, default -> {
            }
        }
    }

    public void leaf(CompleteIndexer.Ctx ctx, Node node) {
        if ("name".equals(node.getType()) && isScopeOf(node, ctx.parent())) {
            add(ctx, Nodes.text(node), node);
        }
    }

    private void add(CompleteIndexer.Ctx ctx, @Nullable String name, Node node) {
        if (name == null) {
            return;
        }

        var reference = new PhpReference(file, PhpReference.Kind.CLASS);
        reference.name(name);
        reference.range(Range.of(node));
        if (ctx.peek() instanceof PhpSymbolOwner owner) {
            reference.owner(owner);
        }
        collection.add(reference);
    }

    private static boolean isScopeOf(Node node, @Nullable Node parent) {
        if (parent == null) {
            return false;
        }

        Node scope = switch (parent.getType()) {
            case "scoped_call_expression", "scoped_property_access_expression" -> parent.getChildByFieldName("scope");
            case "class_constant_access_expression" -> Nodes.namedChild(parent, 0);
            case null, default -> null;
        };
        return node.equals(scope);
    }

    private static @Nullable String lastName(Node qualified) {
        String name = null;
        int count = qualified.getNamedChildCount();
        for (int i = 0; i < count; i++) {
            Node child = qualified.getNamedChild(i);
            if ("name".equals(child.getType())) {
                name = Nodes.text(child);
            }
        }
        return name;
    }

    public void exit(CompleteIndexer.Ctx ctx, Node node) {
    }

    public void token(CompleteIndexer.Ctx ctx, Node node, String field) {
    }

    public void extra(CompleteIndexer.Ctx ctx, Node node) {
    }

    public void error(CompleteIndexer.Ctx ctx, Node node) {
    }

    public void unexpected(CompleteIndexer.Ctx ctx, Node parent, Node child, String field) {
    }

    public void unknown(CompleteIndexer.Ctx ctx, Node node) {
    }
}
