package dev.bluepitaya.phpmagik;

import dev.bluepitaya.phpmagik.listener.AggregatedListener;
import dev.bluepitaya.phpmagik.lsp.Logger;
import dev.bluepitaya.phpmagik.phpsymbol.PhpSymbolCollection;
import dev.bluepitaya.phpmagik.ts.Node;
import dev.bluepitaya.phpmagik.ts.Parser;
import dev.bluepitaya.phpmagik.ts.Tree;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;

@NullMarked
public final class Workspace implements AutoCloseable {

    record Indexed(PhpFile file, PhpSymbolCollection collection) {
    }

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

    public @Nullable PhpFile findFile(String uri) {
        return files.stream()
                .filter(x -> x.uri().equals(uri))
                .findFirst()
                .orElse(null);
    }

    public void index(Path root) throws IOException {
        log.log("index root: " + root);

        List<Path> sources;
        if (Files.isRegularFile(root)) {
            sources = List.of(root);
        } else {
            try (Stream<Path> paths = Files.walk(root)) {
                sources = paths.filter(path -> isPhpSource(root, path)).sorted().toList();
            }
        }

        for (Indexed indexed : indexAll(sources)) {
            files.add(indexed.file());
            symbols.addAll(indexed.collection());
        }

        new ReferenceResolver(symbols).resolve();
        log.log("indexed " + files.size() + " file(s)");
    }

    public void reparse(PhpFile file, byte[] content) {
        file.replace(content, parser.parse(content));

        symbols.removeSymbolsOfFile(file);
        symbols.addAll(indexInto(file));

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

    PhpSymbolCollection indexInto(PhpFile file) {
        var collection = new PhpSymbolCollection();
        try (Tree tree = file.tree()) {
            Node root = tree.getRootNode();
            if (root != null) {
                new CompleteIndexer().walk(root, AggregatedListener.create(collection, file));

                new DefinitionFiller().fill(collection);
                new PhpTypeInferer().infer(collection, root);
                new VarUsageTypePropagator().propagate(collection);
            }
        }
        return collection;
    }

    private List<Indexed> indexAll(List<Path> sources) throws IOException {
        int cores = Runtime.getRuntime().availableProcessors();
        if (sources.size() <= 1 || cores <= 1) {
            List<Indexed> indexed = new ArrayList<>(sources.size());
            for (int i = 0; i < sources.size(); i++) {
                var one = indexOne(i, sources.get(i), parser);
                if (one != null) {
                    indexed.add(one);
                }
            }
            return indexed;
        }
        return indexConcurrently(sources, Math.min(sources.size(), cores));
    }

    private List<Indexed> indexConcurrently(List<Path> sources, int workers) throws IOException {
        List<Parser> parsers = Collections.synchronizedList(new ArrayList<>());
        ThreadLocal<Parser> threadParser = ThreadLocal.withInitial(() -> {
            Parser created = new Parser();
            parsers.add(created);
            return created;
        });
        ExecutorService pool = Executors.newFixedThreadPool(workers);
        List<Future<@Nullable Indexed>> futures = new ArrayList<>(sources.size());
        List<Indexed> indexed = new ArrayList<>(sources.size());
        try {
            for (int i = 0; i < sources.size(); i++) {
                int fileId = i;
                Path path = sources.get(i);
                futures.add(pool.submit(() -> indexOne(fileId, path, threadParser.get())));
            }
            for (Future<@Nullable Indexed> future : futures) {
                @Nullable Indexed one = future.get();
                if (one != null) {
                    indexed.add(one);
                }
            }
        } catch (InterruptedException cause) {
            Thread.currentThread().interrupt();
            throw new AppException("indexing interrupted");
        } catch (ExecutionException cause) {
            Throwable actual = cause.getCause();
            switch (actual) {
                case IOException io -> throw io;
                case RuntimeException re -> throw re;
                case Error err -> throw err;
                case null, default -> throw new AppException("indexing failed");
            }
        } finally {
            shutdown(pool, parsers);
        }

        return indexed;
    }

    private void shutdown(ExecutorService pool, List<Parser> parsers) {
        pool.shutdownNow();
        boolean terminated = false;
        try {
            terminated = pool.awaitTermination(1, TimeUnit.MINUTES);
        } catch (InterruptedException cause) {
            Thread.currentThread().interrupt();
        }
        if (!terminated) {
            log.log("index: pool did not terminate in 1m; leaking " + parsers.size() + " parsers still in use");
            return;
        }
        for (Parser parser : parsers) {
            parser.close();
        }
    }

    private @Nullable Indexed indexOne(int fileId, Path path, Parser parser) throws IOException {
        byte[] content = Files.readAllBytes(path);
        if (content.length == 0) {
            log.log("index: " + path + " (empty, skipped)");
            return null;
        }
        log.log("index: " + path);

        Tree tree = parser.parse(content);
        PhpFile file = new PhpFile(fileId, path, content, tree);
        return new Indexed(file, indexInto(file));
    }

    private boolean isPhpSource(Path root, Path path) {
        if (!path.getFileName().toString().endsWith(".php")) return false;
        for (Path part : root.relativize(path)) {
            if (part.toString().startsWith(".")) return false;
        }

        return Files.isRegularFile(path);
    }

}
