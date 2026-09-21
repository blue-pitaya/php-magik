package dev.bluepitaya.phpmagik.index;

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

    public void index(Path root) throws IOException {
        /* single file lsp */
        if (Files.isRegularFile(root)) {
            addFile(root);
            return;
        }

        try (Stream<Path> paths = Files.walk(root)) {
            for (Path path : paths.filter(path -> isPhpSource(root, path)).sorted().toList()) {
                addFile(path);
            }
        }
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
            return;
        }

        Tree tree = parser.parse(content);
        int fileId = files.size();

        Indexer indexer = new Indexer(fileId);
        indexer.parseProgram(tree.getRootNode());
        vars.addAll(indexer.vars());
        funcs.addAll(indexer.funcs());

        files.add(new PhpFile(fileId, pathToUri(path), path.toString(), content, tree,
                indexer.uses()));
    }

    public void reparse(PhpFile file, byte[] content) {
        Tree tree = parser.parse(content);

        Indexer indexer = new Indexer(file.fileId());
        indexer.parseProgram(tree.getRootNode());

        vars.removeIf(v -> v.fileId() == file.fileId());
        funcs.removeIf(f -> f.fileId() == file.fileId());
        vars.addAll(indexer.vars());
        funcs.addAll(indexer.funcs());

        file.replace(content, tree, indexer.uses());
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
