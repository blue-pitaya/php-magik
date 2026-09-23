package dev.bluepitaya.phpmagik.testing;

import dev.bluepitaya.phpmagik.PhpFile;
import dev.bluepitaya.phpmagik.SymbolFinder;
import dev.bluepitaya.phpmagik.Workspace;
import dev.bluepitaya.phpmagik.lsp.Logger;
import dev.bluepitaya.phpmagik.phpsymbol.PhpSymbolCollection;
import dev.bluepitaya.phpmagik.ts.Parser;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;

public final class Fixture implements AutoCloseable {

    private static final Path LSP_TESTS = Path.of("lsp-tests");

    private final Parser parser;
    private final Logger log;
    private final Workspace workspace;

    private final SymbolFinder finder;

    private Fixture(Path root) throws IOException {
        this.parser = new Parser();
        Path logPath = Files.createTempFile("php-magik-test", ".log");
        logPath.toFile().deleteOnExit();
        this.log = new Logger(logPath);

        this.workspace = new Workspace(parser, log);
        this.workspace.index(root);

        this.finder = new SymbolFinder(workspace);
    }

    public static Fixture index(String name) {
        try {
            return new Fixture(LSP_TESTS.resolve(name));
        } catch (IOException cause) {
            throw new UncheckedIOException("cannot index lsp-tests/" + name, cause);
        }
    }

    public PhpFile file(String fileName) {
        return workspace.files().stream()
                .filter(f -> f.path().endsWith(fileName))
                .findFirst()
                .orElseThrow(() -> new AssertionError("no indexed file named " + fileName));
    }

    public Workspace workspace() {
        return workspace;
    }

    public PhpSymbolCollection symbols() {
        return workspace.symbols();
    }

    public SymbolFinder finder() {
        return finder;
    }

    public Logger log() {
        return log;
    }

    @Override
    public void close() throws IOException {
        workspace.close();
        log.close();
    }
}
