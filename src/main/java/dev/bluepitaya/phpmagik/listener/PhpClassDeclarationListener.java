package dev.bluepitaya.phpmagik.listener;

import dev.bluepitaya.phpmagik.CompleteIndexer;
import dev.bluepitaya.phpmagik.PhpFile;
import dev.bluepitaya.phpmagik.phpsymbol.PhpClassDeclaration;
import dev.bluepitaya.phpmagik.phpsymbol.PhpSymbolCollection;
import dev.bluepitaya.phpmagik.ts.Node;
import dev.bluepitaya.phpmagik.ts.Nodes;
import dev.bluepitaya.phpmagik.ts.Range;
import org.jspecify.annotations.NullMarked;

import java.util.ArrayDeque;
import java.util.Deque;

@NullMarked
public final class PhpClassDeclarationListener implements CompleteIndexer.Listener {

    private final PhpSymbolCollection collection;
    private final PhpFile file;
    private final Deque<PhpClassDeclaration> declarations = new ArrayDeque<>();

    public PhpClassDeclarationListener(
            PhpSymbolCollection collection, PhpFile file
    ) {
        this.collection = collection;
        this.file = file;
    }

    public void enter(CompleteIndexer.Ctx ctx, Node node) {
        if ("class_declaration".equals(node.getType())) {
            PhpClassDeclaration declaration = new PhpClassDeclaration(file, ctx.depth());
            declarations.push(declaration);
            ctx.push(declaration);
        }
    }

    public void exit(CompleteIndexer.Ctx ctx, Node node) {
        if ("class_declaration".equals(node.getType())) {
            PhpClassDeclaration declaration = declarations.poll();
            ctx.pop();
            if (declaration == null || declaration.name() == null
                    || declaration.range() == null) {
                return;
            }

            declaration.scope(Range.of(node));
            collection.add(declaration);
        }
    }

    public void leaf(CompleteIndexer.Ctx ctx, Node node) {
        PhpClassDeclaration declaration = declarations.peek();
        if (declaration == null || ctx.depth() != declaration.depth() + 1) {
            return;
        }

        switch (node.getType()) {
            case "abstract_modifier", "final_modifier", "readonly_modifier" -> {
                String text = Nodes.text(node);
                if (text != null) {
                    String modifier = declaration.$modifier();
                    declaration.$modifier(
                            modifier.isEmpty() ? text : modifier + " " + text);
                }
            }
            case "name" -> {
                String text = Nodes.text(node);
                if (text != null) {
                    declaration.name(text);
                    declaration.range(Range.of(node));
                }
            }
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
