package dev.bluepitaya.phpmagik.listener;

import dev.bluepitaya.phpmagik.CompleteIndexer;
import dev.bluepitaya.phpmagik.PhpFile;
import dev.bluepitaya.phpmagik.phpsymbol.PhpClassDeclaration;
import dev.bluepitaya.phpmagik.phpsymbol.PhpClassReference;
import dev.bluepitaya.phpmagik.phpsymbol.PhpImport;
import dev.bluepitaya.phpmagik.phpsymbol.PhpNameScope;
import dev.bluepitaya.phpmagik.phpsymbol.PhpSymbolCollection;
import dev.bluepitaya.phpmagik.phpsymbol.PhpType;
import dev.bluepitaya.phpmagik.ts.Node;
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
        Node parent = ctx.parent().orElse(null);
        if (parent == null || !isClassName(node, parent)) {
            return;
        }

        PhpType.ClassType type = typeOf(node, parent);
        if (type == null) {
            return;
        }

        collection.add(new PhpClassReference(file, type, node.getRange()));
    }

    private PhpType.@Nullable ClassType typeOf(Node node, Node parent) {
        if (isSelf(node)) {
            PhpClassDeclaration declaration = collection.classOf(file, node.getRange());
            String fqn = declaration != null ? declaration.fqn() : null;
            return fqn != null ? new PhpType.ClassType(fqn, PhpNameScope.GLOBAL) : null;
        }
        if (parent.isType("namespace_use_clause")) {
            PhpImport imported = importOf(parent);
            return imported != null ? new PhpType.ClassType(imported.name(), PhpNameScope.GLOBAL) : null;
        }

        return new PhpType.ClassType(node.getContent(), collection.nameScopeOf(file, node.getRange()));
    }

    private @Nullable PhpImport importOf(Node clause) {
        for (PhpImport imported : collection.nameScopeOf(file, clause.getRange()).imports()) {
            if (imported.range().equals(clause.getRange())) {
                return imported;
            }
        }
        return null;
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
            case "namespace_use_clause" -> !node.equals(parent.getChildByFieldName("alias")) && importOf(parent) != null;
            case null, default -> false;
        };
    }
}
