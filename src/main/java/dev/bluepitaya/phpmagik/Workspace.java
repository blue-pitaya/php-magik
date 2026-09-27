package dev.bluepitaya.phpmagik;

import dev.bluepitaya.phpmagik.lsp.Logger;
import dev.bluepitaya.phpmagik.phpsymbol.PhpSymbolCollection;
import dev.bluepitaya.phpmagik.ts.Parser;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

@NullMarked
public final class Workspace implements AutoCloseable {

    private final Parser parser;
    private final Logger log;
    private final Indexer indexer;
    private final List<PhpFile> files = new ArrayList<>();
    private final PhpSymbolCollection symbols = new PhpSymbolCollection();

    public Workspace(Parser parser, Logger log) {
        this.parser = parser;
        this.log = log;
        this.indexer = new Indexer(parser, log);
    }

    public List<PhpFile> files() {
        return files;
    }

    public PhpSymbolCollection symbols() {
        return symbols;
    }

    public @Nullable PhpFile findFile(String uri) {
        return files.stream()
                .filter(x -> x.uri().equals(uri))
                .findFirst()
                .orElse(null);
    }

    public void index(Path root) throws IOException {
        log.log("index root: " + root);

        for (Indexer.Indexed indexed : indexer.index(root)) {
            files.add(indexed.file());
            symbols.addAll(indexed.collection());
        }

        new ReferenceResolver(symbols).resolve();
        log.log("indexed " + files.size() + " file(s)");
    }

    public void reparse(PhpFile file, byte[] content) {
        file.replace(content, parser.parse(content));

        symbols.removeSymbolsOfFile(file);
        symbols.addAll(indexer.indexInto(file));

        new ReferenceResolver(symbols).resolve();
        log.log("reparsed: " + file.path().getFileName());
    }

    @Override
    public void close() {
        for (PhpFile file : files) {
            file.tree().close();
        }
        parser.close();
    }
}
