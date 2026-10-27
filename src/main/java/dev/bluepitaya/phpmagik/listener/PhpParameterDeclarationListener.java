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

    public PhpParameterDeclarationListener(
            PhpSymbolCollection collection, PhpFile file
    ) {
        this.collection = collection;
        this.file = file;
    }

    public void enter(CompleteIndexer.Ctx ctx, Node node) {
        switch (node.getType()) {
            case "simple_parameter", "property_promotion_parameter" -> open(ctx, node);
            case "variable_name" -> fill(ctx, node);
        }
    }

    public void exit(CompleteIndexer.Ctx ctx, Node node) {
        switch (node.getType()) {
            case "simple_parameter", "property_promotion_parameter" -> commit(declarations.poll());
        }
    }

    private void open(CompleteIndexer.Ctx ctx, Node node) {
        PhpParameterDeclaration declaration = new PhpParameterDeclaration(file, ctx.depth());
        PhpSymbolOwner owner = collection.ownerOf(file, node.getRange());
        if (owner != null) {
            declaration.owner(owner);
        }
        Node typeNode = node.getChildByFieldName("type");
        PhpType type = typeNode == null ? null : PhpType.parse(typeNode.getContent(), collection.nameScopeOf(file, typeNode.getRange()));
        if (type != null) {
            declaration.type(type);
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

    private void commit(@Nullable PhpParameterDeclaration declaration) {
        if (declaration == null || declaration.name() == null
                || declaration.range() == null) {
            return;
        }

        collection.add(declaration);
    }
}
