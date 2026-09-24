package dev.bluepitaya.phpmagik.listener;

import dev.bluepitaya.phpmagik.CompleteIndexer;
import dev.bluepitaya.phpmagik.PhpFile;
import dev.bluepitaya.phpmagik.phpsymbol.PhpNamespaceDefinition;
import dev.bluepitaya.phpmagik.phpsymbol.PhpSymbolCollection;
import dev.bluepitaya.phpmagik.ts.Node;
import dev.bluepitaya.phpmagik.ts.Nodes;
import dev.bluepitaya.phpmagik.ts.Range;
import org.jspecify.annotations.NullMarked;

import java.util.ArrayDeque;
import java.util.Deque;

@NullMarked
public final class PhpNamespaceDefinitionListener implements CompleteIndexer.Listener {

    private final PhpSymbolCollection collection;
    private final PhpFile file;
    private final Deque<PhpNamespaceDefinition> definitions = new ArrayDeque<>();

    public PhpNamespaceDefinitionListener(
            PhpSymbolCollection collection, PhpFile file
    ) {
        this.collection = collection;
        this.file = file;
    }

    public void enter(CompleteIndexer.Ctx ctx, Node node) {
        switch (node.getType()) {
            case "namespace_definition" -> definitions.push(new PhpNamespaceDefinition(file, ctx.depth()));
            case "namespace_name" -> {
                PhpNamespaceDefinition definition = definitions.peek();
                /* the first such subtree names it; anything later belongs to
                 * whatever the body holds */
                if (definition == null || definition.name() != null
                        || ctx.depth() != definition.depth() + 1) {
                    return;
                }

                String name = Nodes.text(node);
                if (name != null) {
                    definition.name(name);
                    definition.range(Range.of(node));
                }
            }
        }
    }

    public void exit(CompleteIndexer.Ctx ctx, Node node) {
        if ("namespace_definition".equals(node.getType())) {
            PhpNamespaceDefinition definition = definitions.poll();
            if (definition == null || definition.name() == null
                    || definition.range() == null) {
                return;
            }

            collection.add(definition);
        }
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
