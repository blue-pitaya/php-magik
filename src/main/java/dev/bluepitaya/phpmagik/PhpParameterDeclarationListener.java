package dev.bluepitaya.phpmagik;

import dev.bluepitaya.phpmagik.phpsymbol.PhpMethodDeclaration;
import dev.bluepitaya.phpmagik.phpsymbol.PhpParameterDeclaration;
import dev.bluepitaya.phpmagik.phpsymbol.PhpSymbolCollection;
import dev.bluepitaya.phpmagik.ts.Node;
import dev.bluepitaya.phpmagik.ts.Nodes;
import dev.bluepitaya.phpmagik.ts.Range;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

import java.util.ArrayDeque;
import java.util.Deque;

@NullMarked
public final class PhpParameterDeclarationListener implements CompleteIndexer.Listener {

    private final PhpSymbolCollection collection;
    private final PhpFile file;
    private final Deque<PhpParameterDeclaration> declarations = new ArrayDeque<>();

    public PhpParameterDeclarationListener(
            PhpSymbolCollection collection, PhpFile file
    ) {
        this.collection = collection;
        this.file = file;
    }

    public void enter(CompleteIndexer.Ctx ctx, Node node) {
        switch (node.getType()) {
            case "simple_parameter" -> open(ctx);
            case "variable_name" -> fill(ctx, node);
        }
    }

    public void exit(CompleteIndexer.Ctx ctx, Node node) {
        if ("simple_parameter".equals(node.getType())) {
            commit(declarations.poll());
        }
    }

    private void open(CompleteIndexer.Ctx ctx) {
        PhpParameterDeclaration declaration = new PhpParameterDeclaration(file, ctx.depth());
        if (ctx.peek() instanceof PhpMethodDeclaration owner) {
            declaration.owner(owner);
        }

        declarations.push(declaration);
    }

    private void fill(CompleteIndexer.Ctx ctx, Node node) {
        PhpParameterDeclaration declaration = declarations.peek();
        if (declaration == null || ctx.depth() != declaration.depth() + 1) {
            return;
        }

        /* the first one names it; a later one is the default value */
        String text = Nodes.text(node);
        if (declaration.name() == null && text != null) {
            declaration.name(text);
            declaration.range(Range.of(node));
        }
    }

    private void commit(@Nullable PhpParameterDeclaration declaration) {
        if (declaration == null || declaration.name() == null
                || declaration.range() == null) {
            return;
        }

        collection.add(declaration);
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
