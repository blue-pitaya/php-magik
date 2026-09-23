package dev.bluepitaya.phpmagik;

import dev.bluepitaya.phpmagik.phpsymbol.PhpClassDeclaration;
import dev.bluepitaya.phpmagik.phpsymbol.PhpMethodDeclaration;
import dev.bluepitaya.phpmagik.phpsymbol.PhpPropertyDeclaration;
import dev.bluepitaya.phpmagik.phpsymbol.PhpSymbolCollection;
import dev.bluepitaya.phpmagik.phpsymbol.PhpSymbolOwner;
import dev.bluepitaya.phpmagik.ts.Node;
import dev.bluepitaya.phpmagik.ts.Nodes;
import dev.bluepitaya.phpmagik.ts.Range;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

import java.util.ArrayDeque;
import java.util.Deque;

@NullMarked
public final class PhpPropertyDeclarationListener implements CompleteIndexer.Listener {

    private final PhpSymbolCollection collection;
    private final PhpFile file;
    private final Deque<PhpPropertyDeclaration> declarations = new ArrayDeque<>();

    public PhpPropertyDeclarationListener(
            PhpSymbolCollection collection, PhpFile file
    ) {
        this.collection = collection;
        this.file = file;
    }

    public void enter(CompleteIndexer.Ctx ctx, Node node) {
        switch (node.getType()) {
            case "property_element", "property_promotion_parameter" -> open(ctx);
            case "variable_name" -> fill(ctx, node);
        }
    }

    public void exit(CompleteIndexer.Ctx ctx, Node node) {
        switch (node.getType()) {
            case "property_element", "property_promotion_parameter" ->
                    commit(declarations.poll());
        }
    }

    private void open(CompleteIndexer.Ctx ctx) {
        PhpPropertyDeclaration declaration = new PhpPropertyDeclaration(file, ctx.depth());
        PhpSymbolOwner enclosing = ctx.peek();
        /* a promoted parameter sits in the constructor, which sits in the class */
        if (enclosing instanceof PhpMethodDeclaration method) {
            enclosing = method.owner();
        }
        if (enclosing instanceof PhpClassDeclaration owner) {
            declaration.owner(owner);
        }

        declarations.push(declaration);
    }

    private void fill(CompleteIndexer.Ctx ctx, Node node) {
        PhpPropertyDeclaration declaration = declarations.peek();
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

    private void commit(@Nullable PhpPropertyDeclaration declaration) {
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
