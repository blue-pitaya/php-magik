package dev.bluepitaya.phpmagik;

import dev.bluepitaya.phpmagik.lsp.Logger;
import dev.bluepitaya.phpmagik.phpsymbol.PhpClassDefinition;
import dev.bluepitaya.phpmagik.phpsymbol.PhpClassUsage;
import dev.bluepitaya.phpmagik.phpsymbol.PhpFunctionDefinition;
import dev.bluepitaya.phpmagik.phpsymbol.PhpFunctionUsage;
import dev.bluepitaya.phpmagik.phpsymbol.PhpMethodDefinition;
import dev.bluepitaya.phpmagik.phpsymbol.PhpMethodUsage;
import dev.bluepitaya.phpmagik.phpsymbol.PhpPropertyDefinition;
import dev.bluepitaya.phpmagik.phpsymbol.PhpPropertyUsage;
import dev.bluepitaya.phpmagik.phpsymbol.PhpVarDefinition;
import dev.bluepitaya.phpmagik.phpsymbol.PhpVarUsage;
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
    private final List<PhpVarDefinition> varDefinitions = new ArrayList<>();
    private final List<PhpVarUsage> varUsages = new ArrayList<>();
    private final List<PhpFunctionDefinition> functions = new ArrayList<>();
    private final List<PhpFunctionUsage> functionUsages = new ArrayList<>();
    private final List<PhpMethodDefinition> methods = new ArrayList<>();
    private final List<PhpMethodUsage> methodUsages = new ArrayList<>();
    private final List<PhpPropertyDefinition> properties = new ArrayList<>();
    private final List<PhpPropertyUsage> propertyUsages = new ArrayList<>();
    private final List<PhpClassDefinition> classes = new ArrayList<>();
    private final List<PhpClassUsage> classUsages = new ArrayList<>();

    public Workspace(Parser parser, Logger log) {
        this.parser = parser;
        this.log = log;
    }

    public List<PhpFile> files() {
        return files;
    }

    public List<PhpVarDefinition> varDefinitions() {
        return varDefinitions;
    }

    public List<PhpVarUsage> varUsages() {
        return varUsages;
    }

    public List<PhpFunctionDefinition> functions() {
        return functions;
    }

    public List<PhpFunctionUsage> functionUsages() {
        return functionUsages;
    }

    public List<PhpMethodDefinition> methods() {
        return methods;
    }

    public List<PhpMethodUsage> methodUsages() {
        return methodUsages;
    }

    public List<PhpPropertyDefinition> properties() {
        return properties;
    }

    public List<PhpPropertyUsage> propertyUsages() {
        return propertyUsages;
    }

    public List<PhpClassDefinition> classes() {
        return classes;
    }

    public List<PhpClassUsage> classUsages() {
        return classUsages;
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
        int fileId = files.size();

        Indexer indexer = new Indexer(fileId, content);
        indexer.index(tree.getRootNode());
        addSymbols(indexer);

        files.add(new PhpFile(fileId, pathToUri(path), path.toString(), content, tree,
                indexer.uses()));
    }

    public void reparse(PhpFile file, byte[] content) {
        Tree tree = parser.parse(content);

        Indexer indexer = new Indexer(file.fileId(), content);
        indexer.index(tree.getRootNode());

        forgetSymbols(file.fileId());
        addSymbols(indexer);

        file.replace(content, tree, indexer.uses());
    }

    private void addSymbols(Indexer indexer) {
        varDefinitions.addAll(indexer.varDefinitions());
        varUsages.addAll(indexer.varUsages());
        functions.addAll(indexer.functions());
        functionUsages.addAll(indexer.functionUsages());
        methods.addAll(indexer.methods());
        methodUsages.addAll(indexer.methodUsages());
        properties.addAll(indexer.properties());
        propertyUsages.addAll(indexer.propertyUsages());
        classes.addAll(indexer.classes());
        classUsages.addAll(indexer.classUsages());
    }

    private void forgetSymbols(int fileId) {
        varDefinitions.removeIf(symbol -> symbol.fileId() == fileId);
        varUsages.removeIf(symbol -> symbol.fileId() == fileId);
        functions.removeIf(symbol -> symbol.fileId() == fileId);
        functionUsages.removeIf(symbol -> symbol.fileId() == fileId);
        methods.removeIf(symbol -> symbol.fileId() == fileId);
        methodUsages.removeIf(symbol -> symbol.fileId() == fileId);
        properties.removeIf(symbol -> symbol.fileId() == fileId);
        propertyUsages.removeIf(symbol -> symbol.fileId() == fileId);
        classes.removeIf(symbol -> symbol.fileId() == fileId);
        classUsages.removeIf(symbol -> symbol.fileId() == fileId);
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
