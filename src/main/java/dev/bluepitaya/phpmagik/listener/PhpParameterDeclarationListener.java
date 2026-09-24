package dev.bluepitaya.phpmagik.listener;

import dev.bluepitaya.phpmagik.CompleteIndexer;
import dev.bluepitaya.phpmagik.PhpFile;
import dev.bluepitaya.phpmagik.phpsymbol.PhpParameterDeclaration;
import dev.bluepitaya.phpmagik.phpsymbol.PhpSymbolCollection;
import dev.bluepitaya.phpmagik.phpsymbol.PhpSymbolOwner;
import dev.bluepitaya.phpmagik.phpsymbol.PhpType;
import dev.bluepitaya.phpmagik.ts.Node;
import dev.bluepitaya.phpmagik.ts.Nodes;
import dev.bluepitaya.phpmagik.ts.Range;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

import java.util.ArrayDeque;
import java.util.Deque;

@NullMarked
public final class PhpParameterDeclarationListener implements Listener {

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
            case "named_type" -> fillNamedType(ctx, node);
        }
    }

    public void exit(CompleteIndexer.Ctx ctx, Node node) {
        if ("simple_parameter".equals(node.getType())) {
            commit(declarations.poll());
        }
    }

    private void open(CompleteIndexer.Ctx ctx) {
        PhpParameterDeclaration declaration = new PhpParameterDeclaration(file, ctx.depth());
        if (ctx.peek() instanceof PhpSymbolOwner owner) {
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

    private void fillNamedType(CompleteIndexer.Ctx ctx, Node node) {
        PhpParameterDeclaration declaration = declarations.peek();
        if (declaration == null || ctx.depth() != declaration.depth() + 1) {
            return;
        }

        String text = Nodes.text(node);
        if (text != null) {
            declaration.type(PhpType.named(text));
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
        if ("primitive_type".equals(node.getType())) {
            fillType(ctx, node);
        }
    }

    private void fillType(CompleteIndexer.Ctx ctx, Node node) {
        PhpParameterDeclaration declaration = declarations.peek();
        if (declaration == null || ctx.depth() != declaration.depth() + 1) {
            return;
        }

        PhpType phpType = PhpType.of(Nodes.text(node));
        if (phpType != null) {
            declaration.type(phpType);
        }
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
