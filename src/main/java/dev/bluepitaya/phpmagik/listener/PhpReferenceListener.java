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
        if (kind != PhpReference.Kind.FUNCTION && parent != null) {
            receive(reference, parent.getChildByFieldName("object"));
        }
        collection.add(reference);
    }

    private static PhpReference.@Nullable Kind kindOf(@Nullable Node parent) {
        return switch (Nodes.type(parent)) {
            case "function_call_expression" -> PhpReference.Kind.FUNCTION;
            case "member_access_expression", "nullsafe_member_access_expression" -> PhpReference.Kind.PROPERTY;
            case "member_call_expression", "nullsafe_member_call_expression" -> PhpReference.Kind.METHOD;
            case null, default -> null;
        };
    }

    private static void receive(PhpReference reference, @Nullable Node object) {
        List<PhpReference.Step> path = new ArrayList<>();
        @Nullable Node current = object;
        while (current != null) {
            switch (current.getType()) {
                case "variable_name" -> {
                    String receiver = Nodes.text(current);
                    if (receiver != null) {
                        reference.receiverVar(receiver);
                        reference.receiverPath(List.copyOf(path.reversed()));
                    }
                    return;
                }
                case "member_access_expression", "nullsafe_member_access_expression",
                     "member_call_expression", "nullsafe_member_call_expression" -> {
                    String name = Nodes.text(current.getChildByFieldName("name"));
                    if (name == null) {
                        return;
                    }
                    path.add(new PhpReference.Step(name, current.getType().endsWith("call_expression")));
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
