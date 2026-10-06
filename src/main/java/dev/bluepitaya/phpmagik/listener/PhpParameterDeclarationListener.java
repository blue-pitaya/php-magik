package dev.bluepitaya.phpmagik.listener;

import dev.bluepitaya.phpmagik.CompleteIndexer;
import dev.bluepitaya.phpmagik.PhpFile;
import dev.bluepitaya.phpmagik.phpsymbol.PhpParameterDeclaration;
import dev.bluepitaya.phpmagik.phpsymbol.PhpSymbolCollection;
import dev.bluepitaya.phpmagik.phpsymbol.PhpSymbolOwner;
import dev.bluepitaya.phpmagik.phpsymbol.PhpType;
import dev.bluepitaya.phpmagik.ts.Node;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

import java.util.ArrayDeque;
import java.util.Deque;

@NullMarked
public final class PhpParameterDeclarationListener implements Listener {

    private final PhpSymbolCollection collection;
    private final PhpFile file;
    private final Deque<PhpParameterDeclaration> declarations = new ArrayDeque<>();
    private final PhpNameResolver names;

    public PhpParameterDeclarationListener(
            PhpSymbolCollection collection, PhpFile file, PhpNameResolver names
    ) {
        this.collection = collection;
        this.file = file;
        this.names = names;
    }

    public void enter(CompleteIndexer.Ctx ctx, Node node) {
        switch (node.getType()) {
            case "simple_parameter" -> open(ctx, node);
            case "variable_name" -> fill(ctx, node);
            case "named_type" -> fillNamedType(ctx, node);
        }
    }

    public void exit(CompleteIndexer.Ctx ctx, Node node) {
        if (node.isType("simple_parameter")) {
            commit(declarations.poll());
        }
    }

    private void open(CompleteIndexer.Ctx ctx, Node node) {
        PhpParameterDeclaration declaration = new PhpParameterDeclaration(file, ctx.depth());
        PhpSymbolOwner owner = collection.ownerOf(file, node.getRange());
        if (owner != null) {
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
        if (declaration.name() == null) {
            declaration.name(node.getContent());
            declaration.range(node.getRange());
        }
    }

    private void fillNamedType(CompleteIndexer.Ctx ctx, Node node) {
        PhpParameterDeclaration declaration = declarations.peek();
        if (declaration == null || ctx.depth() != declaration.depth() + 1) {
            return;
        }

        declaration.type(PhpType.named(names.resolve(node.getContent())));
    }

    private void commit(@Nullable PhpParameterDeclaration declaration) {
        if (declaration == null || declaration.name() == null
                || declaration.range() == null) {
            return;
        }

        collection.add(declaration);
    }

    public void leaf(CompleteIndexer.Ctx ctx, Node node) {
        if (node.isType("primitive_type")) {
            fillType(ctx, node);
        }
    }

    private void fillType(CompleteIndexer.Ctx ctx, Node node) {
        PhpParameterDeclaration declaration = declarations.peek();
        if (declaration == null || ctx.depth() != declaration.depth() + 1) {
            return;
        }

        PhpType phpType = PhpType.of(node.getContent());
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
