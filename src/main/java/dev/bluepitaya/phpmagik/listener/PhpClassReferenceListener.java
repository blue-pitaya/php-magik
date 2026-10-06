package dev.bluepitaya.phpmagik.listener;

import dev.bluepitaya.phpmagik.CompleteIndexer;
import dev.bluepitaya.phpmagik.PhpFile;
import dev.bluepitaya.phpmagik.phpsymbol.PhpClassDeclaration;
import dev.bluepitaya.phpmagik.phpsymbol.PhpClassReference;
import dev.bluepitaya.phpmagik.phpsymbol.PhpSymbolCollection;
import dev.bluepitaya.phpmagik.ts.Node;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

@NullMarked
public final class PhpClassReferenceListener implements Listener {

    private final PhpSymbolCollection collection;
    private final PhpFile file;
    private final PhpNameResolver names;

    public PhpClassReferenceListener(
            PhpSymbolCollection collection, PhpFile file, PhpNameResolver names
    ) {
        this.collection = collection;
        this.file = file;
        this.names = names;
    }

    public void enter(CompleteIndexer.Ctx ctx, Node node) {
        if (node.isType("qualified_name") || node.isType("relative_name")) {
            add(ctx, node);
        }
    }

    public void leaf(CompleteIndexer.Ctx ctx, Node node) {
        if (node.isType("name") || node.isType("relative_scope") && isSelf(node)) {
            add(ctx, node);
        }
    }

    private void add(CompleteIndexer.Ctx ctx, Node node) {
        Node parent = ctx.parent();
        if (parent == null || !isClassName(node, parent)) {
            return;
        }

        String fqn = fqnOf(node, parent);
        if (fqn == null) {
            return;
        }

        collection.add(new PhpClassReference(file, fqn, node.getRange()));
    }

    private @Nullable String fqnOf(Node node, Node parent) {
        if (isSelf(node)) {
            PhpClassDeclaration declaration = collection.classOf(file, node.getRange());
            return declaration != null ? declaration.fqn() : null;
        }
        if (parent.isType("namespace_use_clause")) {
            return names.importedName(parent);
        }

        return names.resolve(node.getContent());
    }

    private static boolean isSelf(Node node) {
        String name = node.getContent();
        return "self".equalsIgnoreCase(name) || "static".equalsIgnoreCase(name);
    }

    private boolean isClassName(Node node, Node parent) {
        return switch (parent.getType()) {
            case "named_type", "object_creation_expression", "base_clause", "class_interface_clause" -> true;
            case "scoped_call_expression", "scoped_property_access_expression" ->
                    node.equals(parent.getChildByFieldName("scope"));
            case "class_constant_access_expression" -> node.equals(Node.namedChild(parent, 0));
            case "binary_expression" -> {
                Node operator = parent.getChildByFieldName("operator");
                yield operator != null && node.equals(parent.getChildByFieldName("right"))
                        && "instanceof".equalsIgnoreCase(operator.getContent());
            }
            case "namespace_use_clause" -> !node.equals(parent.getChildByFieldName("alias")) && names.importsClass(parent);
            case null, default -> false;
        };
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
