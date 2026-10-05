package dev.bluepitaya.phpmagik.listener;

import dev.bluepitaya.phpmagik.CompleteIndexer;
import dev.bluepitaya.phpmagik.PhpFile;
import dev.bluepitaya.phpmagik.phpsymbol.PhpFunctionDefinition;
import dev.bluepitaya.phpmagik.phpsymbol.PhpSymbolCollection;
import dev.bluepitaya.phpmagik.ts.Node;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

import java.util.ArrayDeque;
import java.util.Deque;

@NullMarked
public final class PhpFunctionDefinitionListener implements Listener {

    private final PhpSymbolCollection collection;
    private final PhpFile file;
    private final Deque<PhpFunctionDefinition> stack = new ArrayDeque<>();
    private final PhpNameResolver names;

    public PhpFunctionDefinitionListener(
            PhpSymbolCollection collection, PhpFile file, PhpNameResolver names
    ) {
        this.collection = collection;
        this.file = file;
        this.names = names;
    }

    public void enter(CompleteIndexer.Ctx ctx, Node node) {
        if (node.isType("function_definition")) {
            PhpFunctionDefinition definition = new PhpFunctionDefinition(file, ctx.depth());
            PhpSignatureReader.read(definition, node, names);
            stack.push(definition);
            ctx.push(definition);
        }
    }

    public void exit(CompleteIndexer.Ctx ctx, Node node) {
        if (node.isType("function_definition")) {
            PhpFunctionDefinition definition = stack.poll();
            ctx.pop();
            commit(definition);
        }
    }

    public void leaf(CompleteIndexer.Ctx ctx, Node node) {
        if (node.isType("name")) {
            fill(ctx, node);
        }
    }

    private void fill(CompleteIndexer.Ctx ctx, Node node) {
        PhpFunctionDefinition definition = stack.peek();
        if (definition == null || ctx.depth() != definition.depth() + 1) {
            return;
        }

        definition.name(node.getContent());
        definition.range(node.getRange());
    }

    private void commit(@Nullable PhpFunctionDefinition definition) {
        if (definition == null || definition.name() == null
                || definition.range() == null) {
            return;
        }

        collection.add(definition);
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
