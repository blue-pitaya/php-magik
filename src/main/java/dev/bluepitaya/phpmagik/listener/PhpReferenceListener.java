package dev.bluepitaya.phpmagik.listener;

import dev.bluepitaya.phpmagik.CompleteIndexer;
import dev.bluepitaya.phpmagik.PhpFile;
import dev.bluepitaya.phpmagik.phpsymbol.PhpReference;
import dev.bluepitaya.phpmagik.phpsymbol.PhpSymbolCollection;
import dev.bluepitaya.phpmagik.phpsymbol.PhpSymbolOwner;
import dev.bluepitaya.phpmagik.ts.Node;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

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
        if (!node.isType("name")) {
            return;
        }

        Node parent = ctx.parent();
        if (parent == null) {
            return;
        }
        
        PhpReference.Kind kind = kindOf(node, parent);
        if (kind == null) {
            return;
        }

        var reference = new PhpReference(file, kind);
        reference.name(node.getContent());
        reference.range(node.getRange());
        PhpSymbolOwner owner = collection.ownerOf(file, node.getRange());
        if (owner != null) {
            reference.owner(owner);
        }
        if (kind != PhpReference.Kind.FUNCTION) {
            receive(reference, parent.getChildByFieldName("object"));
            Node scope = parent.getChildByFieldName("scope");
            if (scope != null) {
                reference.receiverScope(scope.getContent());
            }
        }
        collection.add(reference);
    }

    private static PhpReference.@Nullable Kind kindOf(Node node, Node parent) {
        return switch (parent.getType()) {
            case "function_call_expression" -> PhpReference.Kind.FUNCTION;
            case "member_access_expression", "nullsafe_member_access_expression" -> PhpReference.Kind.PROPERTY;
            case "member_call_expression", "nullsafe_member_call_expression" -> PhpReference.Kind.METHOD;
            case "scoped_call_expression" -> node.equals(parent.getChildByFieldName("name"))
                    ? PhpReference.Kind.METHOD
                    : null;
            default -> null;
        };
    }

    private static void receive(PhpReference reference, @Nullable Node object) {
        List<PhpReference.Step> path = new ArrayList<>();
        @Nullable Node current = object;
        while (current != null) {
            switch (current.getType()) {
                case "variable_name" -> {
                    reference.receiverVar(current.getContent());
                    reference.receiverPath(List.copyOf(path.reversed()));
                    return;
                }
                case "member_access_expression", "nullsafe_member_access_expression",
                     "member_call_expression", "nullsafe_member_call_expression" -> {
                    Node name = current.getChildByFieldName("name");
                    if (name == null) {
                        return;
                    }
                    path.add(new PhpReference.Step(name.getContent(), current.getType().endsWith("call_expression")));
                    current = current.getChildByFieldName("object");
                }
                case null, default -> {
                    return;
                }
            }
        }
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
