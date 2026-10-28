package dev.bluepitaya.phpmagik.listener;

import dev.bluepitaya.phpmagik.CompleteIndexer;
import dev.bluepitaya.phpmagik.PhpFile;
import dev.bluepitaya.phpmagik.phpsymbol.PhpImport;
import dev.bluepitaya.phpmagik.phpsymbol.PhpSymbolCollection;
import dev.bluepitaya.phpmagik.ts.Node;

import java.util.Optional;

public final class PhpImportListener implements Listener {

    private final PhpSymbolCollection collection;
    private final PhpFile file;

    public PhpImportListener(PhpSymbolCollection collection, PhpFile file) {
        this.collection = collection;
        this.file = file;
    }

    public void enter(CompleteIndexer.Ctx ctx, Node node) {
        // use App\Foo;
        if (!node.isType("namespace_use_clause")) {
            return;
        }

        Optional<String> importedOpt = importedName(node);
        if (importedOpt.isEmpty() || !importsClass(node)) {
            return;
        }

        String imported = importedOpt.get();
        // use App\Foo as Bar; use App\Foo;
        Optional<Node> alias = node.getChildByFieldNameOpt("alias");
        String key = alias.map(Node::getContent).orElse(imported.substring(imported.lastIndexOf('\\') + 1));

        collection.add(new PhpImport(file, key, imported, node.getRange()));
    }

    private static Optional<String> importedName(Node node) {
        // use Foo; use App\Foo;
        Node imported = node.getNamedChildren().stream()
                .filter(ch -> ch.isType("name") || ch.isType("qualified_name"))
                .findFirst()
                .orElse(null);
        if (imported == null) {
            return Optional.empty();
        }

        String content = imported.getContent();
        // use \App\Foo;
        String text = content.startsWith("\\") ? content.substring(1) : content;

        Optional<Node> group = node.getParentOpt();
        // use App\Foo;
        if (group.isEmpty() || !group.get().isType("namespace_use_group")) {
            return Optional.of(text);
        }

        // use App\{Foo, Bar};
        Optional<Node> declaration = group.get().getParentOpt();
        if (declaration.isEmpty()) {
            return Optional.of(text);
        }

        // use App\{Foo, Bar};
        for (Node child : declaration.get().getNamedChildren()) {
            if (child.isType("namespace_name")) {
                return Optional.of(child.getContent() + "\\" + text);
            }
        }

        return Optional.of(text);
    }

    private static boolean importsClass(Node node) {
        // use App\{function foo, Bar};
        if (node.getChildByFieldNameOpt("type").isPresent()) {
            return false;
        }

        Optional<Node> declaration = node.getParentOpt();
        // use function App\{foo, bar};
        if (declaration.isPresent() && declaration.get().isType("namespace_use_group")) {
            declaration = declaration.get().getParentOpt();
        }

        // use function App\foo;
        return declaration.isEmpty() || declaration.get().getChildByFieldNameOpt("type").isEmpty();
    }
}
