package dev.bluepitaya.phpmagik.index;

import dev.bluepitaya.phpmagik.ts.Parser;
import dev.bluepitaya.phpmagik.ts.Tree;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Stream;

public final class Workspace implements AutoCloseable {

    private final Parser parser;
    private final List<PhpFile> files = new ArrayList<>();
    private final List<PhpVar> vars = new ArrayList<>();
    private final List<PhpFunction> funcs = new ArrayList<>();

    public Workspace(Parser parser) {
        this.parser = parser;
    }

    public List<PhpFile> files() {
        return files;
    }

    public List<PhpVar> vars() {
        return vars;
    }

    public List<PhpFunction> funcs() {
        return funcs;
    }

    public PhpFile file(int fileId) {
        return files.get(fileId);
    }

    public PhpFile findFile(String uri) {
        for (PhpFile file : files) {
            if (file.uri() != null && file.uri().equals(uri)) return file;
        }
        return null;
    }

    /**
     * Indexes {@code root}: a single file is taken as-is, a directory is walked
     * for {@code .php} files, skipping dot-entries.
     */
    public void index(Path root) throws IOException {
        if (Files.isRegularFile(root)) {
            addFile(root);
            return;
        }
        walk(root);
    }

    private void walk(Path dir) throws IOException {
        List<Path> entries;
        /* sorted so the file ids, and anything that falls back to index order,
         * do not depend on the filesystem's directory-listing order */
        try (Stream<Path> stream = Files.list(dir)) {
            entries = stream.sorted(Comparator.comparing(p -> p.getFileName().toString()))
                    .toList();
        }
        for (Path path : entries) {
            if (path.getFileName().toString().startsWith(".")) continue;
            if (Files.isDirectory(path)) {
                walk(path);
            } else if (Files.isRegularFile(path)
                    && path.getFileName().toString().endsWith(".php")) {
                addFile(path);
            }
        }
    }

    private void addFile(Path path) throws IOException {
        byte[] content = Files.readAllBytes(path);
        if (content.length == 0) return;

        Tree tree = parser.parse(content);
        PhpFile file = new PhpFile(files.size(), pathToUri(path), path.toString(), content, tree);

        Indexer indexer = new Indexer(file.fileId());
        indexer.parseProgram(tree.getRootNode());
        vars.addAll(indexer.vars());
        funcs.addAll(indexer.funcs());

        files.add(file);
    }

    /**
     * Reparses an already-indexed file's content in place - its tree, source
     * buffer, and its variable and function entries - for {@code didOpen} and
     * {@code didChange}.
     *
     * @return {@code false} if {@code uri} is not in the index, e.g. a file
     * opened from outside the indexed root
     */
    public boolean reparseFile(String uri, byte[] content) {
        PhpFile file = findFile(uri);
        if (file == null) return false;

        Tree tree = parser.parse(content);

        Indexer indexer = new Indexer(file.fileId());
        indexer.parseProgram(tree.getRootNode());

        /* only drop the old entries once the new parse has fully succeeded, so
         * a bad reparse cannot corrupt the index */
        vars.removeIf(v -> v.fileId() == file.fileId());
        funcs.removeIf(f -> f.fileId() == file.fileId());
        vars.addAll(indexer.vars());
        funcs.addAll(indexer.funcs());

        file.replace(content, tree);
        return true;
    }

    /** {@code "file://"} plus an absolute path, matching the uris editors send. */
    static String pathToUri(Path path) {
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
