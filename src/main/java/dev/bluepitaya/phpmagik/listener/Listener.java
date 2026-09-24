package dev.bluepitaya.phpmagik.listener;

import dev.bluepitaya.phpmagik.CompleteIndexer;
import dev.bluepitaya.phpmagik.ts.Node;
import org.jspecify.annotations.NullMarked;

@NullMarked
public interface Listener {

    void enter(CompleteIndexer.Ctx ctx, Node node);

    void exit(CompleteIndexer.Ctx ctx, Node node);

    void leaf(CompleteIndexer.Ctx ctx, Node node);

    void token(CompleteIndexer.Ctx ctx, Node node, String field);

    void extra(CompleteIndexer.Ctx ctx, Node node);

    void error(CompleteIndexer.Ctx ctx, Node node);

    void unexpected(CompleteIndexer.Ctx ctx, Node parent, Node child, String field);

    void unknown(CompleteIndexer.Ctx ctx, Node node);
}
