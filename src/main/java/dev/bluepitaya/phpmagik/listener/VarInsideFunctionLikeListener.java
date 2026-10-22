package dev.bluepitaya.phpmagik.listener;

import dev.bluepitaya.phpmagik.CompleteIndexer;
import dev.bluepitaya.phpmagik.PhpFile;
import dev.bluepitaya.phpmagik.phpsymbol.PhpFunctionLike;
import dev.bluepitaya.phpmagik.phpsymbol.PhpMethodLocalVarDeclaration;
import dev.bluepitaya.phpmagik.phpsymbol.PhpMethodVarUsage;
import dev.bluepitaya.phpmagik.phpsymbol.PhpSymbolCollection;
import dev.bluepitaya.phpmagik.phpsymbol.PhpType;
import dev.bluepitaya.phpmagik.ts.Node;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

@NullMarked
public final class VarInsideFunctionLikeListener implements Listener {

    private final PhpSymbolCollection collection;
    private final PhpFile file;

    public VarInsideFunctionLikeListener(
            PhpSymbolCollection collection, PhpFile file
    ) {
        this.collection = collection;
        this.file = file;
    }

    public void enter(CompleteIndexer.Ctx ctx, Node node) {
        if (!node.isType("variable_name")) {
            return;
        }

        if (!(collection.ownerOf(file, node.getRange()) instanceof PhpFunctionLike owner)) {
            return;
        }

        Node parent = ctx.parent();
        if (isParameter(parent)) {
            return;
        }

        String text = node.getContent();

        if (isAssigned(node, parent) || isIterated(node, parent)) {
            var declaration = new PhpMethodLocalVarDeclaration(file);
            declaration.owner(owner);
            declaration.name(text);
            declaration.range(node.getRange());
            String createdType = createdTypeOf(parent);
            if (createdType != null) {
                declaration.type(new PhpType.ClassType(createdType, collection.nameScopeOf(file, node.getRange())));
            }
            collection.add(declaration);
        } else {
            collection.add(new PhpMethodVarUsage(file, owner, text, node.getRange()));
        }

    }

    private static @Nullable String createdTypeOf(@Nullable Node parent) {
        if (parent == null) {
            return null;
        }
        Node right = parent.getChildByFieldName("right");
        if (right == null || !right.isType("object_creation_expression")) {
            return null;
        }

        int count = right.getNamedChildCount();
        for (int i = 0; i < count; i++) {
            Node child = right.getNamedChild(i);
            switch (child.getType()) {
                case "name", "qualified_name" -> {
                    return child.getContent();
                }
                default -> {
                }
            }
        }
        return null;
    }

    private static boolean isAssigned(Node node, @Nullable Node parent) {
        if (parent == null || !parent.isType("assignment_expression")) {
            return false;
        }

        return node.equals(parent.getChildByFieldName("left"));
    }

    private static boolean isIterated(Node node, @Nullable Node parent) {
        if (parent == null) {
            return false;
        }
        if (parent.isType("foreach_statement")) {
            return !node.equals(Node.namedChild(parent, 0));
        }
        Node foreach = parent.getParent();
        return parent.isType("pair") && foreach != null && foreach.isType("foreach_statement");
    }

    private static boolean isParameter(@Nullable Node parent) {
        if (parent == null) {
            return false;
        }

        return switch (parent.getType()) {
            case "simple_parameter", "variadic_parameter", "property_promotion_parameter" -> true;
            default -> false;
        };
    }

    public void exit(CompleteIndexer.Ctx ctx, Node node) {
    }

    public void leaf(CompleteIndexer.Ctx ctx, Node node) {
    }

    public void token(CompleteIndexer.Ctx ctx, Node node, String field) {
    }

    public void extra(CompleteIndexer.Ctx ctx, Node node) {
    }

    public void unexpected(CompleteIndexer.Ctx ctx, Node parent, Node child, String field) {
    }

    public void error(CompleteIndexer.Ctx ctx, Node node) {
    }

    public void unknown(CompleteIndexer.Ctx ctx, Node node) {
    }
}
