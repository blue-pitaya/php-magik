package dev.bluepitaya.phpmagik;

import dev.bluepitaya.phpmagik.phpsymbol.PhpPropertyDeclaration;
import dev.bluepitaya.phpmagik.phpsymbol.PhpSymbolCollection;
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
            case "property_declaration", "property_element", "property_promotion_parameter" ->
                    declarations.push(new PhpPropertyDeclaration(file, ctx.depth()));
            /* a type or a visibility modifier is a subtree, not a leaf */
            default -> fill(ctx, node);
        }
    }

    public void exit(CompleteIndexer.Ctx ctx, Node node) {
        switch (node.getType()) {
            case "property_element" -> {
                PhpPropertyDeclaration declared = declarations.poll();
                PhpPropertyDeclaration shared = declarations.peek();
                if (declared == null || shared == null) {
                    return;
                }

                declared.$modifier(shared.$modifier());
                String type = shared.type();
                if (type != null) {
                    declared.type(type);
                }
                commit(declared);
            }
            case "property_promotion_parameter" -> commit(declarations.poll());
            case "property_declaration" -> declarations.poll();
        }
    }

    public void leaf(CompleteIndexer.Ctx ctx, Node node) {
        fill(ctx, node);
    }

    private void fill(CompleteIndexer.Ctx ctx, Node node) {
        PhpPropertyDeclaration declaration = declarations.peek();
        if (declaration == null || ctx.depth() != declaration.depth() + 1) {
            return;
        }

        switch (node.getType()) {
            case "abstract_modifier", "final_modifier", "readonly_modifier",
                 "static_modifier", "var_modifier", "visibility_modifier" -> {
                String text = Nodes.text(node);
                if (text != null) {
                    String modifier = declaration.$modifier();
                    declaration.$modifier(
                            modifier.isEmpty() ? text : modifier + " " + text);
                }
            }
            case "disjunctive_normal_form_type", "intersection_type", "named_type",
                 "optional_type", "primitive_type", "union_type" -> {
                String text = Nodes.text(node);
                if (text != null) {
                    declaration.type(text);
                }
            }
            case "variable_name" -> {
                /* the first one names it; a later one is the default value */
                String text = Nodes.text(node);
                if (declaration.name() == null && text != null) {
                    declaration.name(text);
                    declaration.range(Range.of(node));
                }
            }
        }
    }

    private void commit(@Nullable PhpPropertyDeclaration declaration) {
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
