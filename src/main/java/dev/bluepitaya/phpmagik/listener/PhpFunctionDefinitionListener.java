package dev.bluepitaya.phpmagik.listener;

import dev.bluepitaya.phpmagik.CompleteIndexer;
import dev.bluepitaya.phpmagik.PhpFile;
import dev.bluepitaya.phpmagik.phpsymbol.PhpFunctionDefinition;
import dev.bluepitaya.phpmagik.phpsymbol.PhpSymbolCollection;
import dev.bluepitaya.phpmagik.ts.Node;
import dev.bluepitaya.phpmagik.ts.Nodes;
import dev.bluepitaya.phpmagik.ts.Range;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

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
        if ("function_definition".equals(node.getType())) {
            PhpFunctionDefinition definition = new PhpFunctionDefinition(file, ctx.depth());
            definition.scope(Range.of(node));
            stack.push(definition);
            ctx.push(definition);
        }
    }

    public void exit(CompleteIndexer.Ctx ctx, Node node) {
        if ("function_definition".equals(node.getType())) {
            PhpFunctionDefinition definition = stack.poll();
            ctx.pop();
            commit(definition);
        }
    }

    public void leaf(CompleteIndexer.Ctx ctx, Node node) {
        if ("name".equals(node.getType())) {
            fill(ctx, node);
        }
    }

    private void fill(CompleteIndexer.Ctx ctx, Node node) {
        PhpFunctionDefinition definition = stack.peek();
        if (definition == null || ctx.depth() != definition.depth() + 1) {
            return;
        }

        String text = Nodes.text(node);
        if (text != null) {
            definition.name(text);
            definition.range(Range.of(node));
        }
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
