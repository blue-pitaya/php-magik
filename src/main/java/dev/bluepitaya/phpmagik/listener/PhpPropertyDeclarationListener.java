package dev.bluepitaya.phpmagik.listener;

import dev.bluepitaya.phpmagik.CompleteIndexer;
import dev.bluepitaya.phpmagik.PhpFile;
import dev.bluepitaya.phpmagik.phpsymbol.PhpClassDeclaration;
import dev.bluepitaya.phpmagik.phpsymbol.PhpPropertyDeclaration;
import dev.bluepitaya.phpmagik.phpsymbol.PhpSymbolCollection;
import dev.bluepitaya.phpmagik.phpsymbol.PhpType;
import dev.bluepitaya.phpmagik.ts.Node;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.StringJoiner;

@NullMarked
public final class PhpPropertyDeclarationListener implements Listener {

    private final PhpSymbolCollection collection;
    private final PhpFile file;
    private final Deque<PhpPropertyDeclaration> declarations = new ArrayDeque<>();
    private final PhpNameResolver names;

    public PhpPropertyDeclarationListener(
            PhpSymbolCollection collection, PhpFile file, PhpNameResolver names
    ) {
        this.collection = collection;
        this.file = file;
        this.names = names;
    }

    public void enter(CompleteIndexer.Ctx ctx, Node node) {
        switch (node.getType()) {
            case "property_element", "property_promotion_parameter" -> open(ctx, node);
            case "variable_name" -> fill(ctx, node);
        }
    }

    public void exit(CompleteIndexer.Ctx ctx, Node node) {
        switch (node.getType()) {
            case "property_element", "property_promotion_parameter" -> commit(declarations.poll());
        }
    }

    private void open(CompleteIndexer.Ctx ctx, Node node) {
        PhpPropertyDeclaration declaration = new PhpPropertyDeclaration(file, ctx.depth());
        PhpClassDeclaration owner = collection.classOf(file, node.getRange());
        if (owner != null) {
            declaration.owner(owner);
        }

        Node declared = node.isType("property_promotion_parameter") ? node : node.getParent();
        if (declared != null) {
            declaration.$modifier(modifiersOf(declared));
            Node typeNode = declared.getChildByFieldName("type");
            if (typeNode != null) {
                PhpType type = names.type(typeNode.getContent());
                if (type != null) {
                    declaration.type(type);
                }
            }
        }

        declarations.push(declaration);
    }

    private static String modifiersOf(Node declared) {
        StringJoiner modifiers = new StringJoiner(" ");
        for (Node child : declared.getChildren()) {
            switch (child.getType()) {
                case "visibility_modifier", "static_modifier", "readonly_modifier", "var_modifier",
                     "abstract_modifier", "final_modifier" -> modifiers.add(child.getContent());
                case null, default -> {
                }
            }
        }
        return modifiers.toString();
    }

    private void fill(CompleteIndexer.Ctx ctx, Node node) {
        PhpPropertyDeclaration declaration = declarations.peek();
        if (declaration == null || ctx.depth() != declaration.depth() + 1) {
            return;
        }

        /* the first one names it; a later one is the default value */
        if (declaration.name() == null) {
            declaration.name(node.getContent());
            declaration.range(node.getRange());
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
