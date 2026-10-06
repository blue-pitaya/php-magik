package dev.bluepitaya.phpmagik.listener;

import dev.bluepitaya.phpmagik.CompleteIndexer;
import dev.bluepitaya.phpmagik.PhpFile;
import dev.bluepitaya.phpmagik.phpsymbol.PhpClassDeclaration;
import dev.bluepitaya.phpmagik.phpsymbol.PhpMethodDeclaration;
import dev.bluepitaya.phpmagik.phpsymbol.PhpSymbolCollection;
import dev.bluepitaya.phpmagik.ts.Node;
import org.jspecify.annotations.NullMarked;

import java.util.ArrayDeque;
import java.util.Deque;

@NullMarked
public final class PhpMethodDeclarationListener implements Listener {

    private final PhpSymbolCollection collection;
    private final PhpFile file;
    private final Deque<PhpMethodDeclaration> declarations = new ArrayDeque<>();
    private final PhpNameResolver names;

    public PhpMethodDeclarationListener(
            PhpSymbolCollection collection, PhpFile file, PhpNameResolver names
    ) {
        this.collection = collection;
        this.file = file;
        this.names = names;
    }

    public void enter(CompleteIndexer.Ctx ctx, Node node) {
        if (node.isType("method_declaration")) {
            open(ctx, node);
        }
    }

    public void exit(CompleteIndexer.Ctx ctx, Node node) {
        if (node.isType("method_declaration")) {
            declarations.poll();
        }
    }

    public void leaf(CompleteIndexer.Ctx ctx, Node node) {
        if (node.isType("name")) {
            fill(ctx, node);
        }
    }

    private void open(CompleteIndexer.Ctx ctx, Node node) {
        PhpMethodDeclaration declaration = new PhpMethodDeclaration(file, ctx.depth());
        PhpClassDeclaration owner = collection.classOf(file, node.getRange());
        if (owner != null) {
            declaration.owner(owner);
        }
        PhpSignatureReader.read(declaration, node, names);

        declarations.push(declaration);
    }

    private void fill(CompleteIndexer.Ctx ctx, Node node) {
        PhpMethodDeclaration declaration = declarations.peek();
        if (declaration == null || ctx.depth() != declaration.depth() + 1
                || declaration.name() != null) {
            return;
        }

        declaration.name(node.getContent());
        declaration.range(node.getRange());
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
