package dev.bluepitaya.phpmagik.listener;

import dev.bluepitaya.phpmagik.CompleteIndexer;
import dev.bluepitaya.phpmagik.PhpFile;
import dev.bluepitaya.phpmagik.phpsymbol.PhpClassDeclaration;
import dev.bluepitaya.phpmagik.phpsymbol.PhpMethodDeclaration;
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
                if (isClassName(node, ctx.parent())) {
                    add(ctx, lastName(node), node);
                }
            }
            case null, default -> {
            }
        }
    }

    public void leaf(CompleteIndexer.Ctx ctx, Node node) {
        switch (node.getType()) {
            case "name" -> {
                if (isClassName(node, ctx.parent())) {
                    String name = Nodes.text(node);
                    boolean self = "self".equalsIgnoreCase(name) || "static".equalsIgnoreCase(name);
                    add(ctx, self ? enclosingClassName(ctx) : name, node);
                }
            }
            case "relative_scope" -> {
                String scope = Nodes.text(node);
                if (isScope(node, ctx.parent())
                        && ("self".equalsIgnoreCase(scope) || "static".equalsIgnoreCase(scope))) {
                    add(ctx, enclosingClassName(ctx), node);
                }
            }
            case null, default -> {
            }
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

    private boolean isClassName(Node node, @Nullable Node parent) {
        if (parent == null) {
            return false;
        }

        return switch (parent.getType()) {
            case "named_type", "object_creation_expression", "base_clause", "class_interface_clause" -> true;
            case "binary_expression" -> node.equals(parent.getChildByFieldName("right"))
                    && "instanceof".equalsIgnoreCase(Nodes.text(parent.getChildByFieldName("operator")));
            case "namespace_use_clause" -> isClassImport(node, parent);
            case null, default -> isScope(node, parent);
        };
    }

    private boolean isClassImport(Node node, Node clause) {
        if (node.equals(clause.getChildByFieldName("alias")) || clause.getChildByFieldName("type") != null) {
            return false;
        }

        Node declaration = clause.getParent();
        if (declaration != null && "namespace_use_group".equals(declaration.getType())) {
            declaration = declaration.getParent();
        }
        return declaration == null || declaration.getChildByFieldName("type") == null;
    }

    private boolean isScope(Node node, @Nullable Node parent) {
        if (parent == null) {
            return false;
        }

        return switch (parent.getType()) {
            case "scoped_call_expression", "scoped_property_access_expression" ->
                    node.equals(parent.getChildByFieldName("scope"));
            case "class_constant_access_expression" -> node.equals(Nodes.namedChild(parent, 0));
            case null, default -> false;
        };
    }

    private @Nullable String enclosingClassName(CompleteIndexer.Ctx ctx) {
        @Nullable PhpSymbolOwner owner = ctx.peek();
        if (owner instanceof PhpMethodDeclaration method) {
            owner = method.owner();
        }
        return owner instanceof PhpClassDeclaration declaration ? declaration.name() : null;
    }

    private @Nullable String lastName(Node qualified) {
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
