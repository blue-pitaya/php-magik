package dev.bluepitaya.phpmagik;

import dev.bluepitaya.phpmagik.phpsymbol.PhpNamespaceDefinition;
import dev.bluepitaya.phpmagik.phpsymbol.PhpSymbolCollection;
import dev.bluepitaya.phpmagik.ts.Node;
import dev.bluepitaya.phpmagik.ts.Nodes;
import dev.bluepitaya.phpmagik.ts.Range;
import org.jspecify.annotations.NullMarked;

@NullMarked
public final class PhpNamespaceDefinitionListener implements CompleteIndexer.Listener {

    private final PhpSymbolCollection collection;
    private final PhpFile file;
    private int state = 0;

    public PhpNamespaceDefinitionListener(
            PhpSymbolCollection collection, PhpFile file
    ) {
        this.collection = collection;
        this.file = file;
    }

    public void enter(CompleteIndexer.Ctx ctx, Node node) {
        String type = node.getType();
        switch (state) {
            case 0 -> {
                if ("namespace_definition".equals(type)) {
                    state = 1;
                }
            }
            case 1 -> {
                if ("name".equals(type)) {
                    state = 2;
                } else {
                    //TODO: can be also a body but its nor supported yet
                    state = 0;
                }
            }
        }
    }

    public void exit(CompleteIndexer.Ctx ctx, Node node) {
    }

    public void leaf(CompleteIndexer.Ctx ctx, Node node) {
        if (state == 2) {
            collection.add(new PhpNamespaceDefinition(
                    Nodes.text(node),
                    Range.of(node),
                    file
            ));
        }
        state = 0;
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
