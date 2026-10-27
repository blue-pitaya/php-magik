package dev.bluepitaya.phpmagik.listener;

import dev.bluepitaya.phpmagik.CompleteIndexer;
import dev.bluepitaya.phpmagik.PhpFile;
import dev.bluepitaya.phpmagik.phpsymbol.PhpFunctionDefinition;
import dev.bluepitaya.phpmagik.phpsymbol.PhpSymbolCollection;
import dev.bluepitaya.phpmagik.ts.Node;
import org.jspecify.annotations.NullMarked;

import java.util.ArrayDeque;
import java.util.Deque;

@NullMarked
public final class PhpFunctionDefinitionListener implements Listener {

    private final PhpSymbolCollection collection;
    private final PhpFile file;
    private final Deque<PhpFunctionDefinition> stack = new ArrayDeque<>();

    public PhpFunctionDefinitionListener(
            PhpSymbolCollection collection, PhpFile file
    ) {
        this.collection = collection;
        this.file = file;
    }

    public void enter(CompleteIndexer.Ctx ctx, Node node) {
        if (node.isType("function_definition")) {
            PhpFunctionDefinition definition = new PhpFunctionDefinition(file, ctx.depth());
            PhpSignatureReader.read(definition, node, collection.nameScopeOf(file, node.getRange()));
            stack.push(definition);
        }
    }

    public void exit(CompleteIndexer.Ctx ctx, Node node) {
        if (node.isType("function_definition")) {
            stack.poll();
        }
    }

    public void leaf(CompleteIndexer.Ctx ctx, Node node) {
        if (node.isType("name")) {
            fill(ctx, node);
        }
    }

    private void fill(CompleteIndexer.Ctx ctx, Node node) {
        PhpFunctionDefinition definition = stack.peek();
        if (definition == null || ctx.depth() != definition.depth() + 1
                || definition.name() != null) {
            return;
        }

        definition.name(node.getContent());
        definition.range(node.getRange());
        collection.add(definition);
    }
}
