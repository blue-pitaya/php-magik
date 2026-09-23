package dev.bluepitaya.phpmagik;

import dev.bluepitaya.phpmagik.phpsymbol.PhpMethodDeclaration;
import dev.bluepitaya.phpmagik.phpsymbol.PhpMethodLocalVarDeclaration;
import dev.bluepitaya.phpmagik.phpsymbol.PhpMethodVarUsage;
import dev.bluepitaya.phpmagik.phpsymbol.PhpSymbolCollection;
import dev.bluepitaya.phpmagik.ts.Node;
import dev.bluepitaya.phpmagik.ts.Nodes;
import dev.bluepitaya.phpmagik.ts.Range;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

@NullMarked
public final class PhpMethodVarListener implements CompleteIndexer.Listener {

    private final PhpSymbolCollection collection;
    private final PhpFile file;

    public PhpMethodVarListener(
            PhpSymbolCollection collection, PhpFile file
    ) {
        this.collection = collection;
        this.file = file;
    }

    public void enter(CompleteIndexer.Ctx ctx, Node node) {
        if ("variable_name".equals(node.getType())) {
            collect(ctx, node);
        }
    }

    private void collect(CompleteIndexer.Ctx ctx, Node node) {
        if (!(ctx.peek() instanceof PhpMethodDeclaration owner)) {
            return;
        }

        Node parent = ctx.parent();
        /* a parameter declares itself, at this very range */
        if (isParameter(parent)) {
            return;
        }

        String text = Nodes.text(node);
        if (text == null) {
            return;
        }

        if (isAssigned(node, parent)) {
            var declaration = new PhpMethodLocalVarDeclaration(file);
            declaration.owner(owner);
            declaration.name(text);
            declaration.range(Range.of(node));
            collection.add(declaration);
            return;
        }

        var usage = new PhpMethodVarUsage(file);
        usage.owner(owner);
        usage.name(text);
        usage.range(Range.of(node));
        collection.add(usage);
    }

    private static boolean isAssigned(Node node, @Nullable Node parent) {
        if (parent == null || !"assignment_expression".equals(parent.getType())) {
            return false;
        }

        return node.equals(parent.getChildByFieldName("left"));
    }

    private static boolean isParameter(@Nullable Node parent) {
        return switch (Nodes.type(parent)) {
            case "simple_parameter", "variadic_parameter", "property_promotion_parameter" -> true;
            case null, default -> false;
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
