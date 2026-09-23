package dev.bluepitaya.phpmagik;

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
public final class PhpClassDeclarationListener implements CompleteIndexer.Listener {

    private static final class Frame {

        private final int depth;
        private String modifier = "";
        private @Nullable String name;
        private @Nullable Range range;

        private Frame(int depth) {
            this.depth = depth;
        }
    }

    private final PhpSymbolCollection collection;
    private final PhpFile file;
    private final Deque<Frame> frames = new ArrayDeque<>();

    public PhpClassDeclarationListener(
            PhpSymbolCollection collection, PhpFile file
    ) {
        this.collection = collection;
        this.file = file;
    }

    public void enter(CompleteIndexer.Ctx ctx, Node node) {
        if ("class_declaration".equals(node.getType())) {
            frames.push(new Frame(ctx.depth()));
        }
    }

    public void exit(CompleteIndexer.Ctx ctx, Node node) {
        if ("class_declaration".equals(node.getType())) {
            Frame frame = frames.poll();
            if (frame == null || frame.name == null || frame.range == null) {
                return;
            }

            collection.add(new PhpClassDeclaration(
                    frame.modifier,
                    frame.name,
                    frame.range,
                    Range.of(node),
                    file
            ));
        }
    }

    public void leaf(CompleteIndexer.Ctx ctx, Node node) {
        Frame frame = frames.peek();
        if (frame == null || ctx.depth() != frame.depth + 1) {
            return;
        }

        switch (node.getType()) {
            case "abstract_modifier", "final_modifier", "readonly_modifier" -> {
                String text = Nodes.text(node);
                if (text != null) {
                    frame.modifier = frame.modifier.isEmpty() ? text : frame.modifier + " " + text;
                }
            }
            case "name" -> {
                frame.name = Nodes.text(node);
                frame.range = Range.of(node);
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
