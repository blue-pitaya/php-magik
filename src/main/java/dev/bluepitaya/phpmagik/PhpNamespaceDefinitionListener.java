package dev.bluepitaya.phpmagik;

import dev.bluepitaya.phpmagik.phpsymbol.PhpNamespaceDefinition;
import dev.bluepitaya.phpmagik.phpsymbol.PhpSymbolCollection;
import dev.bluepitaya.phpmagik.ts.Node;
import dev.bluepitaya.phpmagik.ts.Nodes;
import dev.bluepitaya.phpmagik.ts.Range;
import org.jspecify.annotations.NullMarked;

/**
 * The name field of a namespace_definition is a namespace_name, a subtree of
 * one name per segment - never a leaf, and so never a {@code leaf} callback.
 * Its own text is the qualified name, which is what gets recorded, taken when
 * the subtree opens one level below the definition that owns it.
 */
@NullMarked
public final class PhpNamespaceDefinitionListener implements CompleteIndexer.Listener {

    private static final int NONE = -1;

    private final PhpSymbolCollection collection;
    private final PhpFile file;
    private int depth = NONE;

    public PhpNamespaceDefinitionListener(
            PhpSymbolCollection collection, PhpFile file
    ) {
        this.collection = collection;
        this.file = file;
    }

    public void enter(CompleteIndexer.Ctx ctx, Node node) {
        switch (node.getType()) {
            case "namespace_definition" -> depth = ctx.depth();
            case "namespace_name" -> {
                if (ctx.depth() != depth + 1) {
                    return;
                }
                depth = NONE;

                String name = Nodes.text(node);
                if (name != null) {
                    collection.add(new PhpNamespaceDefinition(
                            name,
                            Range.of(node),
                            file
                    ));
                }
            }
        }
    }

    public void exit(CompleteIndexer.Ctx ctx, Node node) {
    }

    public void leaf(CompleteIndexer.Ctx ctx, Node node) {
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
