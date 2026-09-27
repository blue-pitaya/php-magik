package dev.bluepitaya.phpmagik.listener;

import dev.bluepitaya.phpmagik.CompleteIndexer;
import dev.bluepitaya.phpmagik.PhpFile;
import dev.bluepitaya.phpmagik.phpsymbol.PhpClassDeclaration;
import dev.bluepitaya.phpmagik.phpsymbol.PhpSymbolCollection;
import dev.bluepitaya.phpmagik.ts.Node;
import dev.bluepitaya.phpmagik.ts.Nodes;
import dev.bluepitaya.phpmagik.ts.Range;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

import java.util.ArrayDeque;
import java.util.Deque;

@NullMarked
public final class PhpClassDeclarationListener implements Listener {

    private final PhpSymbolCollection collection;
    private final PhpFile file;
    private final Deque<PhpClassDeclaration> declarations = new ArrayDeque<>();
    private @Nullable String namespace;

    public PhpClassDeclarationListener(
            PhpSymbolCollection collection, PhpFile file
    ) {
        this.collection = collection;
        this.file = file;
    }

    public void enter(CompleteIndexer.Ctx ctx, Node node) {
        switch (node.getType()) {
            case "namespace_definition" -> namespace = null;
            case "namespace_name" -> {
                if ("namespace_definition".equals(Nodes.type(ctx.parent()))) {
                    namespace = Nodes.text(node);
                }
            }
            case "class_declaration" -> {
                PhpClassDeclaration declaration = new PhpClassDeclaration(file, ctx.depth());
                if (namespace != null) {
                    declaration.namespace(namespace);
                }
                declarations.push(declaration);
                ctx.push(declaration);
            }
            case null, default -> {
            }
        }
    }

    public void exit(CompleteIndexer.Ctx ctx, Node node) {
        if ("namespace_definition".equals(node.getType())
                && node.getChildByFieldName("body") != null) {
            namespace = null;
        }
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
