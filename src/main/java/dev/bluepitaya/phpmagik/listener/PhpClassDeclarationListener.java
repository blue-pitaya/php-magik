package dev.bluepitaya.phpmagik.listener;

import dev.bluepitaya.phpmagik.CompleteIndexer;
import dev.bluepitaya.phpmagik.PhpFile;
import dev.bluepitaya.phpmagik.phpsymbol.PhpClassDeclaration;
import dev.bluepitaya.phpmagik.phpsymbol.PhpNamespaceDefinition;
import dev.bluepitaya.phpmagik.phpsymbol.PhpSymbolCollection;
import dev.bluepitaya.phpmagik.ts.Node;
import org.jspecify.annotations.NullMarked;

import java.util.ArrayDeque;
import java.util.Deque;

@NullMarked
public final class PhpClassDeclarationListener implements Listener {

    private final PhpSymbolCollection collection;
    private final PhpFile file;
    private final Deque<PhpClassDeclaration> declarations = new ArrayDeque<>();

    public PhpClassDeclarationListener(PhpSymbolCollection collection, PhpFile file) {
        this.collection = collection;
        this.file = file;
    }

    public void enter(CompleteIndexer.Ctx ctx, Node node) {
        if (node.isType("class_declaration")) {
            PhpClassDeclaration declaration = new PhpClassDeclaration(file, ctx.depth());
            PhpNamespaceDefinition namespace = collection.namespaceOf(file, node.getRange());
            if (namespace != null) {
                declaration.namespace(namespace);
            }
            declaration.scope(node.getRange());
            declarations.push(declaration);
        }
    }

    public void exit(CompleteIndexer.Ctx ctx, Node node) {
        if (node.isType("class_declaration")) {
            declarations.poll();
        }
    }

    public void leaf(CompleteIndexer.Ctx ctx, Node node) {
        PhpClassDeclaration declaration = declarations.peek();
        if (declaration == null || ctx.depth() != declaration.depth() + 1) {
            return;
        }

        switch (node.getType()) {
            case "abstract_modifier", "final_modifier", "readonly_modifier" -> {
                String text = node.getContent();
                String modifier = declaration.$modifier();
                declaration.$modifier(
                        modifier.isEmpty() ? text : modifier + " " + text);
            }
            case "name" -> {
                if (declaration.name() == null) {
                    declaration.name(node.getContent());
                    declaration.range(node.getRange());
                    collection.add(declaration);
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
