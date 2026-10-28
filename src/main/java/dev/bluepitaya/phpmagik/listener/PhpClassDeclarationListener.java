package dev.bluepitaya.phpmagik.listener;

import dev.bluepitaya.phpmagik.CompleteIndexer;
import dev.bluepitaya.phpmagik.PhpFile;
import dev.bluepitaya.phpmagik.phpsymbol.PhpClassDeclaration;
import dev.bluepitaya.phpmagik.phpsymbol.PhpSymbolCollection;
import dev.bluepitaya.phpmagik.ts.Node;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static dev.bluepitaya.phpmagik.ts.Node.Flag.ERROR;
import static dev.bluepitaya.phpmagik.ts.Node.Flag.MISSING;

public final class PhpClassDeclarationListener implements Listener {

    private final PhpSymbolCollection collection;
    private final PhpFile file;

    public PhpClassDeclarationListener(PhpSymbolCollection collection, PhpFile file) {
        this.collection = collection;
        this.file = file;
    }

    public void enter(CompleteIndexer.Ctx ctx, Node node) {
        if (!node.isType("class_declaration")) {
            return;
        }

        Optional<Node> name = node.getChildByFieldNameOpt("name");
        if (name.isEmpty() || name.get().is(MISSING) || name.get().is(ERROR)) {
            return;
        }

        List<String> modifiers = new ArrayList<>();
        for (Node child : node.getNamedChildren()) {
            if (child.isType("abstract_modifier") || child.isType("final_modifier") || child.isType("readonly_modifier")) {
                modifiers.add(child.getContent());
            }
        }

        String namespace = collection.namespaceOfOpt(file, node.getRange())
                .map(ns -> ns.name())
                .orElse("");

        collection.add(new PhpClassDeclaration(
                file,
                namespace,
                String.join(" ", modifiers),
                name.get().getContent(),
                name.get().getRange(),
                node.getRange()));
    }
}
