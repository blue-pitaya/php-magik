package dev.bluepitaya.phpmagik;

import dev.bluepitaya.phpmagik.lsp.Logger;
import dev.bluepitaya.phpmagik.phpsymbol.PhpSymbolCollection;
import dev.bluepitaya.phpmagik.ts.Parser;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

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

    public PhpFile findFile(String uri) {
        for (PhpFile file : files) {
            if (file.uri() != null && file.uri().equals(uri)) return file;
        }
        return null;
    }

    public void index(Path root) throws IOException {
        log.log("index root: " + root);

        for (Indexer.Indexed indexed : indexer.index(root)) {
            files.add(indexed.file());
            symbols.addAll(indexed.collection());
        }

        new ReferenceResolver().resolve(symbols);
        log.log("indexed " + files.size() + " file(s)");
    }

    public void reparse(PhpFile file, byte[] content) {
        /* the file takes the new source before the walk, which reads both off it */
        file.replace(content, parser.parse(content));

        symbols.removeFile(file);
        symbols.addAll(indexer.indexInto(file));
        new ReferenceResolver().resolve(symbols);
    }

    @Override
    public void close() {
        for (PhpFile file : files) {
            file.tree().close();
        }
        parser.close();
    }
}
