package dev.bluepitaya.phpmagik.listener;

import dev.bluepitaya.phpmagik.CompleteIndexer;
import dev.bluepitaya.phpmagik.PhpFile;
import dev.bluepitaya.phpmagik.phpsymbol.PhpClassDeclaration;
import dev.bluepitaya.phpmagik.phpsymbol.PhpPropertyDeclaration;
import dev.bluepitaya.phpmagik.phpsymbol.PhpSymbolCollection;
import dev.bluepitaya.phpmagik.phpsymbol.PhpType;
import dev.bluepitaya.phpmagik.ts.Node;

import java.util.Optional;
import java.util.StringJoiner;

import static dev.bluepitaya.phpmagik.ts.Node.Flag.MISSING;

public final class PhpPropertyDeclarationListener implements Listener {

    private final PhpSymbolCollection collection;
    private final PhpFile file;

    public PhpPropertyDeclarationListener(
            PhpSymbolCollection collection, PhpFile file
    ) {
        this.collection = collection;
        this.file = file;
    }

    public void enter(CompleteIndexer.Ctx ctx, Node node) {
        switch (node.getType()) {
            case "property_element", "property_promotion_parameter" -> declare(node);
        }
    }

    private void declare(Node node) {
        Optional<Node> nameOpt = node.getChildByFieldNameOpt("name");
        if (nameOpt.isEmpty() || !nameOpt.get().isType("variable_name") || nameOpt.get().is(MISSING)) {
            return;
        }

        Optional<Node> declared = node.isType("property_promotion_parameter") ? Optional.of(node) : node.getParentOpt();
        String modifier = declared.map(PhpPropertyDeclarationListener::modifiersOf).orElse("");

        PhpPropertyDeclaration declaration = new PhpPropertyDeclaration(
                file, modifier, nameOpt.get().getContent(), nameOpt.get().getRange());

        PhpClassDeclaration owner = collection.classOf(file, node.getRange());
        if (owner != null) {
            declaration.owner(owner);
        }

        Optional<Node> typeNode = declared.flatMap(d -> d.getChildByFieldNameOpt("type"));
        if (typeNode.isPresent()) {
            PhpType type = PhpType.parse(typeNode.get().getContent(), collection.nameScopeOf(file, typeNode.get().getRange()));
            if (type != null) {
                declaration.type(type);
            }
        }

        collection.add(declaration);
    }

    private static String modifiersOf(Node declared) {
        StringJoiner modifiers = new StringJoiner(" ");
        for (Node child : declared.getChildren()) {
            switch (child.getType()) {
                case "visibility_modifier", "static_modifier", "readonly_modifier", "var_modifier",
                     "abstract_modifier", "final_modifier" -> modifiers.add(child.getContent());
            }
        }
        return modifiers.toString();
    }
}
