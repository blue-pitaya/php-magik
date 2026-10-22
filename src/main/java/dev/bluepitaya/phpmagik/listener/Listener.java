package dev.bluepitaya.phpmagik.listener;

import dev.bluepitaya.phpmagik.CompleteIndexer;
import dev.bluepitaya.phpmagik.ts.Node;
import org.jspecify.annotations.NullMarked;

@NullMarked
public interface Listener {

    default void enter(CompleteIndexer.Ctx ctx, Node node) {
    }

    default void exit(CompleteIndexer.Ctx ctx, Node node) {
    }

    default void leaf(CompleteIndexer.Ctx ctx, Node node) {
    }

    default void token(CompleteIndexer.Ctx ctx, Node node, String field) {
    }

    default void extra(CompleteIndexer.Ctx ctx, Node node) {
    }

    default void error(CompleteIndexer.Ctx ctx, Node node) {
    }

    default void unexpected(CompleteIndexer.Ctx ctx, Node parent, Node child, String field) {
    }

    default void unknown(CompleteIndexer.Ctx ctx, Node node) {
    }
}
