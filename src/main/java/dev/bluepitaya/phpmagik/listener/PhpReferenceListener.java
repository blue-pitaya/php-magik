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
public final class PhpReferenceListener implements Listener {

    private final PhpSymbolCollection collection;
    private final PhpFile file;

    public PhpReferenceListener(
            PhpSymbolCollection collection, PhpFile file
    ) {
        this.collection = collection;
        this.file = file;
    }

    public void leaf(CompleteIndexer.Ctx ctx, Node node) {
        if (!"name".equals(node.getType())) {
            return;
        }

        Node parent = ctx.parent();
        PhpReference.Kind kind = kindOf(parent);
        if (kind == null) {
            return;
        }

        String text = Nodes.text(node);
        if (text == null) {
            return;
        }

        var reference = new PhpReference(file, kind);
        reference.name(text);
        reference.range(Range.of(node));
        if (ctx.peek() instanceof PhpSymbolOwner owner) {
            reference.owner(owner);
        }
        if (kind != PhpReference.Kind.FUNCTION) {
            String receiver = receiverOf(parent);
            if (receiver != null) {
                reference.receiverVar(receiver);
            }
        }
        collection.add(reference);
    }

    private static PhpReference.@Nullable Kind kindOf(@Nullable Node parent) {
        return switch (Nodes.type(parent)) {
            case "function_call_expression" -> PhpReference.Kind.FUNCTION;
            case "member_access_expression" -> PhpReference.Kind.PROPERTY;
            case "member_call_expression" -> PhpReference.Kind.METHOD;
            case null, default -> null;
        };
    }

    private static @Nullable String receiverOf(@Nullable Node parent) {
        if (parent == null) {
            return null;
        }
        Node object = parent.getChildByFieldName("object");
        if (object == null || !"variable_name".equals(object.getType())) {
            return null;
        }
        return Nodes.text(object);
    }

    public void enter(CompleteIndexer.Ctx ctx, Node node) {
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
