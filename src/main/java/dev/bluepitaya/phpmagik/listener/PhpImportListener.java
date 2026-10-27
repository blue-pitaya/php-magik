package dev.bluepitaya.phpmagik.listener;

import dev.bluepitaya.phpmagik.CompleteIndexer;
import dev.bluepitaya.phpmagik.PhpFile;
import dev.bluepitaya.phpmagik.phpsymbol.PhpImport;
import dev.bluepitaya.phpmagik.phpsymbol.PhpSymbolCollection;
import dev.bluepitaya.phpmagik.ts.Node;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

@NullMarked
public final class PhpImportListener implements Listener {

    private final PhpSymbolCollection collection;
    private final PhpFile file;

    public PhpImportListener(PhpSymbolCollection collection, PhpFile file) {
        this.collection = collection;
        this.file = file;
    }

    public void enter(CompleteIndexer.Ctx ctx, Node node) {
        if (!node.isType("namespace_use_clause")) {
            return;
        }

        String imported = importedName(node);
        if (imported == null || !importsClass(node)) {
            return;
        }

        Node alias = node.getChildByFieldName("alias");
        String key = alias != null ? alias.getContent() : imported.substring(imported.lastIndexOf('\\') + 1);
        collection.add(new PhpImport(file, key, imported, node.getRange()));
    }

    private static @Nullable String importedName(Node clause) {
        @Nullable Node imported = null;
        for (int i = 0; i < clause.getNamedChildCount() && imported == null; i++) {
            Node child = clause.getNamedChild(i);
            if (child.isType("name") || child.isType("qualified_name")) {
                imported = child;
            }
        }
        if (imported == null) {
            return null;
        }
        String text = imported.getContent();
        if (text.startsWith("\\")) {
            text = text.substring(1);
        }

        @Nullable Node group = clause.getParent();
        @Nullable Node declaration = group == null ? null : group.getParent();
        if (group == null || declaration == null || !group.isType("namespace_use_group")) {
            return text;
        }
        for (int i = 0; i < declaration.getNamedChildCount(); i++) {
            Node child = declaration.getNamedChild(i);
            if (child.isType("namespace_name")) {
                return child.getContent() + "\\" + text;
            }
        }
        return text;
    }

    private static boolean importsClass(Node clause) {
        if (clause.getChildByFieldName("type") != null) {
            return false;
        }

        Node declaration = clause.getParent();
        if (declaration != null && declaration.isType("namespace_use_group")) {
            declaration = declaration.getParent();
        }
        return declaration == null || declaration.getChildByFieldName("type") == null;
    }
}
