package dev.bluepitaya.phpmagik.listener;

import dev.bluepitaya.phpmagik.CompleteIndexer;
import dev.bluepitaya.phpmagik.PhpFile;
import dev.bluepitaya.phpmagik.phpsymbol.PhpNamespaceDefinition;
import dev.bluepitaya.phpmagik.phpsymbol.PhpSymbolCollection;
import dev.bluepitaya.phpmagik.ts.Node;
import dev.bluepitaya.phpmagik.ts.Point;
import dev.bluepitaya.phpmagik.ts.Range;
import org.jspecify.annotations.NullMarked;

import java.util.ArrayDeque;
import java.util.Deque;

@NullMarked
public final class PhpNamespaceDefinitionListener implements Listener {

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
            case "namespace_definition" -> {
                PhpNamespaceDefinition definition = new PhpNamespaceDefinition(file, ctx.depth());
                definition.scope(scope(node));
                definitions.push(definition);
            }
            case "namespace_name" -> {
                PhpNamespaceDefinition definition = definitions.peek();
                /* the first such subtree names it; anything later belongs to
                 * whatever the body holds */
                if (definition == null || definition.name() != null
                        || ctx.depth() != definition.depth() + 1) {
                    return;
                }

                definition.name(node.getContent());
                definition.range(node.getRange());
                collection.add(definition);
            }
        }
    }

    public void exit(CompleteIndexer.Ctx ctx, Node node) {
        if (node.isType("namespace_definition")) {
            definitions.poll();
        }
    }

    private Range scope(Node definition) {
        Node parent = definition.getParent();
        if (definition.getChildByFieldName("body") != null || parent == null) {
            return definition.getRange();
        }

        Point end = parent.getEndPoint();
        boolean after = false;
        for (Node sibling : parent.getNamedChildren()) {
            if (after && sibling.isType("namespace_definition")) {
                end = sibling.getStartPoint();
                break;
            }
            after = after || sibling.equals(definition);
        }
        return new Range(definition.getStartPoint(), end);
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
