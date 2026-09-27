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
    private final PhpNameResolver names;

    public PhpClassReferenceListener(
            PhpSymbolCollection collection, PhpFile file, PhpNameResolver names
    ) {
        this.collection = collection;
        this.file = file;
        this.names = names;
    }

    public void enter(CompleteIndexer.Ctx ctx, Node node) {
        switch (node.getType()) {
            case "qualified_name", "relative_name" -> {
                if (isClassName(node, ctx.parent())) {
                    add(ctx, fqnOf(node, ctx.parent()), node);
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
                    add(ctx, self ? enclosingClassFqn(ctx) : fqnOf(node, ctx.parent()), node);
                }
            }
            case "relative_scope" -> {
                String scope = Nodes.text(node);
                if (isScope(node, ctx.parent())
                        && ("self".equalsIgnoreCase(scope) || "static".equalsIgnoreCase(scope))) {
                    add(ctx, enclosingClassFqn(ctx), node);
                }
            }
            case null, default -> {
            }
        }
    }

    private void add(CompleteIndexer.Ctx ctx, @Nullable String fqn, Node node) {
        if (fqn == null) {
            return;
        }

        var reference = new PhpReference(file, PhpReference.Kind.CLASS);
        reference.name(fqn);
        reference.range(Range.of(node));
        if (ctx.peek() instanceof PhpSymbolOwner owner) {
            reference.owner(owner);
        }
        collection.add(reference);
    }

    private @Nullable String fqnOf(Node node, @Nullable Node parent) {
        if (parent != null && "namespace_use_clause".equals(parent.getType())) {
            return names.importedName(parent);
        }

        String text = Nodes.text(node);
        return text == null ? null : names.resolve(text);
    }

    private boolean isClassName(Node node, @Nullable Node parent) {
        if (parent == null) {
            return false;
        }

        return switch (parent.getType()) {
            case "named_type", "object_creation_expression", "base_clause", "class_interface_clause" -> true;
            case "binary_expression" -> node.equals(parent.getChildByFieldName("right"))
                    && "instanceof".equalsIgnoreCase(Nodes.text(parent.getChildByFieldName("operator")));
            case "namespace_use_clause" -> !node.equals(parent.getChildByFieldName("alias")) && names.importsClass(parent);
            case null, default -> isScope(node, parent);
        };
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

    private @Nullable String enclosingClassFqn(CompleteIndexer.Ctx ctx) {
        @Nullable PhpSymbolOwner owner = ctx.peek();
        if (owner instanceof PhpMethodDeclaration method) {
            owner = method.owner();
        }
        return owner instanceof PhpClassDeclaration declaration ? declaration.fqn() : null;
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
