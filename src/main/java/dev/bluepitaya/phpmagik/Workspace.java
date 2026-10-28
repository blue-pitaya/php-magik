package dev.bluepitaya.phpmagik;

import dev.bluepitaya.phpmagik.listener.*;
import dev.bluepitaya.phpmagik.lsp.Logger;
import dev.bluepitaya.phpmagik.phpsymbol.PhpSymbolCollection;
import dev.bluepitaya.phpmagik.ts.Node;
import dev.bluepitaya.phpmagik.ts.Tree;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.PathMatcher;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

public final class Workspace {

    private final Logger log;
    private final List<PathMatcher> excludes;
    private final List<PhpFile> files = new ArrayList<>();
    private final PhpSymbolCollection symbols = new PhpSymbolCollection();

    public Workspace(Logger log, Config config) {
        this.log = log;
        this.excludes = config.excludeMatchers();
    }

    public List<PhpFile> files() {
        return files;
    }

    public PhpSymbolCollection symbols() {
        return symbols;
    }

    public Optional<PhpFile> findFile(String uri) {
        return files.stream()
                .filter(x -> x.uri().equals(uri))
                .findFirst();
    }

    public PhpFile findFileOrThrow(String uri) {
        return findFile(uri).orElseThrow(() -> new AppException("file not found: " + uri));
    }

    public void index(Path root) throws IOException {
        log.log("index root: " + root);

        List<Path> sources = phpFilePaths(root);
        indexAll(sources);

        new ReferenceResolver(symbols).resolve();
        new VarUsageTypePropagator().propagate(symbols);
        log.log("indexed " + files.size() + " file(s)");
    }

    public void reparse(PhpFile file, byte[] content) {
        file.replace(content);

        symbols.removeSymbolsOfFile(file);
        symbols.addAll(indexInto(file));

        new ReferenceResolver(symbols).resolve();
        new VarUsageTypePropagator().propagate(symbols);
        log.log("reparsed: " + file.path().getFileName());
    }

    private PhpSymbolCollection indexInto(PhpFile file) {
        var collection = new PhpSymbolCollection();
        Node root = Tree.parse(file.content()).getRootNode();

        List<Listener> listeners = List.of(
                new PhpNamespaceDefinitionListener(collection, file),
                new PhpImportListener(collection, file),
                new PhpClassDeclarationListener(collection, file),
                new PhpPropertyDeclarationListener(collection, file),
                new PhpVarDocListener(collection, file),
                new PhpDocReferenceListener(collection, file),
                new PhpMethodDeclarationListener(collection, file),
                new PhpFunctionDefinitionListener(collection, file),
                new PhpParameterDeclarationListener(collection, file),
                new VarInsideFunctionLikeListener(collection, file),
                new PhpReferenceListener(collection, file),
                new PhpClassReferenceListener(collection, file)
        );
        for (Listener listener : listeners) {
            CompleteIndexer indexer = new CompleteIndexer();
            indexer.walk(root, listener);
        }

        new DefinitionFiller().fill(collection);
        new PhpTypeInferer().infer(collection, root);
        new PhpReturnTypeInferer().infer(collection, root);
        new VarUsageTypePropagator().propagate(collection);

        return collection;
    }

    private void indexAll(List<Path> sources) throws IOException {
        List<PhpFile> read = new ArrayList<>(sources.size());
        for (int i = 0; i < sources.size(); i++) {
            Path path = sources.get(i);
            byte[] content = Files.readAllBytes(path);
            if (content.length == 0) {
                log.log("index: " + path + " (empty, skipped)");
                continue;
            }
            log.log("index: " + path);
            read.add(new PhpFile(i, path, content));
        }

        List<PhpSymbolCollection> collections = read.parallelStream().map(this::indexInto).toList();
        files.addAll(read);
        collections.forEach(symbols::addAll);
    }

    private List<Path> phpFilePaths(Path root) throws IOException {
        // user want specific file, so dont even check if .php or exluded
        if (Files.isRegularFile(root)) {
            return List.of(root);
        }

        try (Stream<Path> paths = Files.walk(root)) {
            return paths
                    .filter(path -> path.getFileName().toString().endsWith(".php"))
                    .filter(path -> excludes.stream().noneMatch(exclude -> exclude.matches(path.toAbsolutePath())))
                    .filter(Files::isRegularFile)
                    .sorted()
                    .toList();
        }
    }
}
