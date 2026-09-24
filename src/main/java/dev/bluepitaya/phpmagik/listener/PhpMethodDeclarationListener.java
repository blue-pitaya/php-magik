package dev.bluepitaya.phpmagik.listener;

import dev.bluepitaya.phpmagik.CompleteIndexer;
import dev.bluepitaya.phpmagik.PhpFile;
import dev.bluepitaya.phpmagik.phpsymbol.PhpClassDeclaration;
import dev.bluepitaya.phpmagik.phpsymbol.PhpMethodDeclaration;
import dev.bluepitaya.phpmagik.phpsymbol.PhpSymbolCollection;
import dev.bluepitaya.phpmagik.phpsymbol.PhpType;
import dev.bluepitaya.phpmagik.ts.Node;
import dev.bluepitaya.phpmagik.ts.Nodes;
import dev.bluepitaya.phpmagik.ts.Range;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

import java.util.ArrayDeque;
import java.util.Deque;

@NullMarked
public final class PhpMethodDeclarationListener implements Listener {

    private final PhpSymbolCollection collection;
    private final PhpFile file;
    private final Deque<PhpMethodDeclaration> declarations = new ArrayDeque<>();

    public PhpMethodDeclarationListener(
            PhpSymbolCollection collection, PhpFile file
    ) {
        this.collection = collection;
        this.file = file;
    }

    public void enter(CompleteIndexer.Ctx ctx, Node node) {
        if ("method_declaration".equals(node.getType())) {
            open(ctx, node);
        }
    }

    public void exit(CompleteIndexer.Ctx ctx, Node node) {
        if ("method_declaration".equals(node.getType())) {
            PhpMethodDeclaration declaration = declarations.poll();
            ctx.pop();
            commit(declaration);
        }
    }

    public void leaf(CompleteIndexer.Ctx ctx, Node node) {
        if ("name".equals(node.getType())) {
            fill(ctx, node);
        }
    }

    private void open(CompleteIndexer.Ctx ctx, Node node) {
        PhpMethodDeclaration declaration = new PhpMethodDeclaration(file, ctx.depth());
        if (ctx.peek() instanceof PhpClassDeclaration owner) {
            declaration.owner(owner);
        }
        declaration.scope(Range.of(node));

        Node returnTypeNode = node.getChildByFieldName("return_type");
        PhpType returnType = PhpType.parse(Nodes.text(returnTypeNode));
        if (returnType != null) {
            declaration.returnType(returnType);
        }

        declarations.push(declaration);
        ctx.push(declaration);
    }

    private void fill(CompleteIndexer.Ctx ctx, Node node) {
        PhpMethodDeclaration declaration = declarations.peek();
        if (declaration == null || ctx.depth() != declaration.depth() + 1) {
            return;
        }

        String text = Nodes.text(node);
        if (text != null) {
            declaration.name(text);
            declaration.range(Range.of(node));
        }
    }

    private void commit(@Nullable PhpMethodDeclaration declaration) {
        if (declaration == null || declaration.name() == null
                || declaration.range() == null) {
            return;
        }

        collection.add(declaration);
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
