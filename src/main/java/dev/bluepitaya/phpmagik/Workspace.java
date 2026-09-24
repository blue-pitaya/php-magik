package dev.bluepitaya.phpmagik;

import dev.bluepitaya.phpmagik.listener.*;
import dev.bluepitaya.phpmagik.lsp.Logger;
import dev.bluepitaya.phpmagik.phpsymbol.PhpSymbolCollection;
import dev.bluepitaya.phpmagik.ts.Node;
import dev.bluepitaya.phpmagik.ts.Parser;
import dev.bluepitaya.phpmagik.ts.Tree;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

public final class Workspace implements AutoCloseable {

    private final Parser parser;
    private final Logger log;
    private final List<PhpFile> files = new ArrayList<>();
    private final PhpSymbolCollection symbols = new PhpSymbolCollection();

    public Workspace(Parser parser, Logger log) {
        this.parser = parser;
        this.log = log;
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

        /* single file lsp */
        if (Files.isRegularFile(root)) {
            addFile(root);
        } else {
            try (Stream<Path> paths = Files.walk(root)) {
                for (Path path : paths.filter(path -> isPhpSource(root, path)).sorted().toList()) {
                    addFile(path);
                }
            }
        }
        log.log("indexed " + files.size() + " file(s)");
    }

    private boolean isPhpSource(Path root, Path path) {
        if (!path.getFileName().toString().endsWith(".php")) return false;
        /* root name starting with dot must pass */
        for (Path part : root.relativize(path)) {
            if (part.toString().startsWith(".")) return false;
        }

        return Files.isRegularFile(path);
    }

    private void addFile(Path path) throws IOException {
        byte[] content = Files.readAllBytes(path);
        if (content.length == 0) {
            log.log("index: " + path + " (empty, skipped)");
            return;
        }
        /* before parsing, so a file that brings the scan down is named in the log */
        log.log("index: " + path);

        Tree tree = parser.parse(content);

        /* the file first: every symbol the walk records points back at it */
        PhpFile file = new PhpFile(files.size(), pathToUri(path), path.toString(), content, tree);
        files.add(file);

        indexSymbols(file);
    }

    public void reparse(PhpFile file, byte[] content) {
        /* the file takes the new source before the walk, which reads both off it */
        file.replace(content, parser.parse(content));

        symbols.removeFile(file);
        indexSymbols(file);
    }

    private void indexSymbols(PhpFile file) {
        var collection = new PhpSymbolCollection();
        var indexer = new CompleteIndexer();
        try (Tree tree = file.tree()) {
            Node root = tree.getRootNode();
            if (root == null) {
                return; //TODO: maybe throw?
            }

            indexer.walk(root, AggregatedListener.create(collection, file));
        }

        new DefinitionFiller().fill(collection);
        new MethodVarsTypeInferer().infer(collection);

        symbols.addAll(collection);
    }

    private String pathToUri(Path path) {
        Path absolute;
        try {
            absolute = path.toRealPath();
        } catch (IOException cause) {
            absolute = path.toAbsolutePath();
        }
        return "file://" + absolute;
    }

    @Override
    public void close() {
        for (PhpFile file : files) {
            file.tree().close();
        }
        parser.close();
    }
}
