package dev.bluepitaya.phpmagik.listener;

import dev.bluepitaya.phpmagik.CompleteIndexer;
import dev.bluepitaya.phpmagik.PhpFile;
import dev.bluepitaya.phpmagik.phpsymbol.PhpSymbolCollection;
import dev.bluepitaya.phpmagik.ts.Node;
import org.jspecify.annotations.NullMarked;

import java.util.List;

@NullMarked
public final class AggregatedListener implements Listener {

    private final List<Listener> listeners;

    public AggregatedListener(Listener... listeners) {
        this.listeners = List.of(listeners);
    }

    public static AggregatedListener create(PhpSymbolCollection collection, PhpFile file) {
        return new AggregatedListener(
                new PhpNamespaceDefinitionListener(collection, file),
                new PhpClassDeclarationListener(collection, file),
                new PhpPropertyDeclarationListener(collection, file),
                new PhpMethodDeclarationListener(collection, file),
                new PhpFunctionDefinitionListener(collection, file),
                new PhpParameterDeclarationListener(collection, file),
                new PhpMethodVarListener(collection, file)
        );
    }

    public void enter(CompleteIndexer.Ctx ctx, Node node) {
        for (Listener listener : listeners) {
            listener.enter(ctx, node);
        }
    }

    public void exit(CompleteIndexer.Ctx ctx, Node node) {
        for (Listener listener : listeners) {
            listener.exit(ctx, node);
        }
    }

    public void leaf(CompleteIndexer.Ctx ctx, Node node) {
        for (Listener listener : listeners) {
            listener.leaf(ctx, node);
        }
    }

    public void token(CompleteIndexer.Ctx ctx, Node node, String field) {
        for (Listener listener : listeners) {
            listener.token(ctx, node, field);
        }
    }

    public void extra(CompleteIndexer.Ctx ctx, Node node) {
        for (Listener listener : listeners) {
            listener.extra(ctx, node);
        }
    }

    public void error(CompleteIndexer.Ctx ctx, Node node) {
        for (Listener listener : listeners) {
            listener.error(ctx, node);
        }
    }

    public void unexpected(CompleteIndexer.Ctx ctx, Node parent, Node child, String field) {
        for (Listener listener : listeners) {
            listener.unexpected(ctx, parent, child, field);
        }
    }

    public void unknown(CompleteIndexer.Ctx ctx, Node node) {
        for (Listener listener : listeners) {
            listener.unknown(ctx, node);
        }
    }
}
