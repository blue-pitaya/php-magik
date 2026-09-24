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
final class Indexer {

    private final Parser parser;
    private final Logger log;

    Indexer(Parser parser, Logger log) {
        this.parser = parser;
        this.log = log;
    }

    List<Indexed> index(Path root) throws IOException {
        List<Path> sources;
        /* single file lsp */
        if (Files.isRegularFile(root)) {
            sources = List.of(root);
        } else {
            try (Stream<Path> paths = Files.walk(root)) {
                sources = paths.filter(path -> isPhpSource(root, path)).sorted().toList();
            }
        }
        return indexAll(sources);
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
                collect(indexed, indexOne(i, sources.get(i), parser));
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
        List<Future<Indexed>> futures = new ArrayList<>(sources.size());
        List<Indexed> indexed = new ArrayList<>(sources.size());
        try {
            for (int i = 0; i < sources.size(); i++) {
                int fileId = i;
                Path path = sources.get(i);
                futures.add(pool.submit(() -> indexOne(fileId, path, threadParser.get())));
            }
            for (Future<Indexed> future : futures) {
                collect(indexed, future.get());
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

    private static void collect(List<Indexed> indexed, @Nullable Indexed one) {
        if (one != null) {
            indexed.add(one);
        }
    }

    private static void shutdown(ExecutorService pool, List<Parser> parsers) {
        pool.shutdownNow();
        try {
            pool.awaitTermination(1, TimeUnit.MINUTES);
        } catch (InterruptedException cause) {
            Thread.currentThread().interrupt();
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
        /* before parsing, so a file that brings the scan down is named in the log */
        log.log("index: " + path);

        Tree tree = parser.parse(content);
        PhpFile file = new PhpFile(fileId, pathToUri(path), path.toString(), content, tree);
        return new Indexed(file, indexInto(file));
    }

    private boolean isPhpSource(Path root, Path path) {
        if (!path.getFileName().toString().endsWith(".php")) return false;
        /* root name starting with dot must pass */
        for (Path part : root.relativize(path)) {
            if (part.toString().startsWith(".")) return false;
        }

        return Files.isRegularFile(path);
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

    record Indexed(PhpFile file, PhpSymbolCollection collection) {
    }
}
