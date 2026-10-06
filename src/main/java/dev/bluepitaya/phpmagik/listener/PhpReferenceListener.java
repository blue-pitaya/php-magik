package dev.bluepitaya.phpmagik.listener;

import dev.bluepitaya.phpmagik.CompleteIndexer;
import dev.bluepitaya.phpmagik.PhpFile;
import dev.bluepitaya.phpmagik.phpsymbol.PhpFunctionCall;
import dev.bluepitaya.phpmagik.phpsymbol.PhpMemberReference;
import dev.bluepitaya.phpmagik.phpsymbol.PhpMethodCall;
import dev.bluepitaya.phpmagik.phpsymbol.PhpPropertyAccess;
import dev.bluepitaya.phpmagik.phpsymbol.PhpStaticCall;
import dev.bluepitaya.phpmagik.phpsymbol.PhpSymbolCollection;
import dev.bluepitaya.phpmagik.phpsymbol.PhpSymbolOwner;
import dev.bluepitaya.phpmagik.ts.Node;
import dev.bluepitaya.phpmagik.ts.Range;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

import java.util.HashMap;
import java.util.Map;

@NullMarked
public final class PhpReferenceListener implements Listener {

    private final PhpSymbolCollection collection;
    private final PhpFile file;
    private final PhpNameResolver names;
    private final Map<Node, PhpMemberReference> members = new HashMap<>();

    public PhpReferenceListener(
            PhpSymbolCollection collection, PhpFile file, PhpNameResolver names
    ) {
        this.collection = collection;
        this.file = file;
        this.names = names;
    }

    public void leaf(CompleteIndexer.Ctx ctx, Node node) {
        Node parent = ctx.parent();
        if (!node.isType("name") || parent == null) {
            return;
        }

        String name = node.getContent();
        Range range = node.getRange();
        switch (parent.getType()) {
            case "function_call_expression" -> collection.add(new PhpFunctionCall(file, name, range));
            case "member_access_expression", "nullsafe_member_access_expression" -> {
                var access = propertyAccess(node, parent);
                members.put(parent, access);
                collection.add(access);
            }
            case "member_call_expression", "nullsafe_member_call_expression" -> {
                var call = methodCall(node, parent);
                members.put(parent, call);
                collection.add(call);
            }
            case "scoped_call_expression" -> {
                Node scope = parent.getChildByFieldName("scope");
                if (scope != null && node.equals(parent.getChildByFieldName("name"))) {
                    collection.add(new PhpStaticCall(file, ownerOf(node), name, range, names.resolve(scope.getContent())));
                }
            }
            default -> {
            }
        }
    }

    private PhpPropertyAccess propertyAccess(Node name, Node expression) {
        Node object = expression.getChildByFieldName("object");
        return new PhpPropertyAccess(
                file, ownerOf(name), name.getContent(), name.getRange(), variableOf(object), receiverOf(object));
    }

    private PhpMethodCall methodCall(Node name, Node expression) {
        Node object = expression.getChildByFieldName("object");
        return new PhpMethodCall(
                file, ownerOf(name), name.getContent(), name.getRange(), variableOf(object), receiverOf(object));
    }

    private @Nullable PhpSymbolOwner ownerOf(Node node) {
        return collection.ownerOf(file, node.getRange());
    }

    private static @Nullable String variableOf(@Nullable Node object) {
        return object != null && object.isType("variable_name") ? object.getContent() : null;
    }

    private @Nullable PhpMemberReference receiverOf(@Nullable Node object) {
        return object == null ? null : members.remove(object);
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
