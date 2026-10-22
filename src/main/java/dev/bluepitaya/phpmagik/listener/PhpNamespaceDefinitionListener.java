package dev.bluepitaya.phpmagik.listener;

import dev.bluepitaya.phpmagik.CompleteIndexer;
import dev.bluepitaya.phpmagik.PhpFile;
import dev.bluepitaya.phpmagik.phpsymbol.PhpNamespaceDefinition;
import dev.bluepitaya.phpmagik.phpsymbol.PhpSymbolCollection;
import dev.bluepitaya.phpmagik.ts.Node;
import dev.bluepitaya.phpmagik.ts.Point;
import dev.bluepitaya.phpmagik.ts.Range;

import java.util.ArrayDeque;
import java.util.Deque;

public final class PhpNamespaceDefinitionListener implements Listener {

    private final PhpSymbolCollection collection;
    private final PhpFile file;
    private final Deque<PhpNamespaceDefinition> definitions = new ArrayDeque<>();

    public PhpNamespaceDefinitionListener(PhpSymbolCollection collection, PhpFile file) {
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

    private Range scope(Node node) {
        // example: namespace App { ... }
        if (node.getChildByFieldName("body") != null) {
            return node.getRange();
        }

        // example: namespace App;
        Node parent = node.getParentOrThrow();
        Point end = parent.getEndPoint();
        boolean pastNode = false;
        for (Node sibling : parent.getNamedChildren()) {
            if (pastNode) {
                if (sibling.isType("namespace_definition")) {
                    end = sibling.getStartPoint();
                    break;
                }
            } else {
                pastNode = sibling.equals(node);
            }
        }

        return new Range(node.getStartPoint(), end);
    }
}
