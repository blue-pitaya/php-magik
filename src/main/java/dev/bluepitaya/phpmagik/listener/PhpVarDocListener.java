package dev.bluepitaya.phpmagik.listener;

import dev.bluepitaya.phpmagik.CompleteIndexer;
import dev.bluepitaya.phpmagik.PhpDoc;
import dev.bluepitaya.phpmagik.PhpFile;
import dev.bluepitaya.phpmagik.phpsymbol.PhpPropertyDeclaration;
import dev.bluepitaya.phpmagik.phpsymbol.PhpSymbolCollection;
import dev.bluepitaya.phpmagik.phpsymbol.PhpType;
import dev.bluepitaya.phpmagik.ts.Node;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

@NullMarked
public final class PhpVarDocListener implements Listener {

    private final PhpSymbolCollection collection;
    private final PhpFile file;
    private final PhpNameResolver names;

    public PhpVarDocListener(PhpSymbolCollection collection, PhpFile file, PhpNameResolver names) {
        this.collection = collection;
        this.file = file;
        this.names = names;
    }

    public void exit(CompleteIndexer.Ctx ctx, Node node) {
        if (!node.isType("property_declaration")) {
            return;
        }

        PhpDoc doc = PhpDoc.of(node);
        if (doc == null || doc.vars().isEmpty()) {
            return;
        }

        for (PhpPropertyDeclaration property : collection.propertiesIn(file, node.getRange())) {
            PhpDoc.Var var = varOf(doc, property.name());
            PhpType type = var == null ? null : names.type(var.type());
            if (type != null) {
                // TODO: report a diagnostic when the @var type contradicts the declared type instead of silently shadowing it
                property.type(type);
            }
        }
    }

    private static PhpDoc.@Nullable Var varOf(PhpDoc doc, @Nullable String name) {
        for (PhpDoc.Var var : doc.vars()) {
            if (var.name() == null || var.name().equals(name)) {
                return var;
            }
        }
        return null;
    }

    public void enter(CompleteIndexer.Ctx ctx, Node node) {
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
