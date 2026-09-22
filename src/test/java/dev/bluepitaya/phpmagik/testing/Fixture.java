package dev.bluepitaya.phpmagik.testing;

import dev.bluepitaya.phpmagik.PhpFile;
import dev.bluepitaya.phpmagik.SymbolFinder;
import dev.bluepitaya.phpmagik.Workspace;
import dev.bluepitaya.phpmagik.lsp.Logger;
import dev.bluepitaya.phpmagik.phpsymbol.PhpSymbolCollection;
import dev.bluepitaya.phpmagik.resolver.ClassResolver;
import dev.bluepitaya.phpmagik.resolver.FunctionResolver;
import dev.bluepitaya.phpmagik.resolver.MethodResolver;
import dev.bluepitaya.phpmagik.resolver.PropertyResolver;
import dev.bluepitaya.phpmagik.resolver.TypeInference;
import dev.bluepitaya.phpmagik.resolver.VariableResolver;
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
    private final VariableResolver variables;
    private final FunctionResolver functions;
    private final TypeInference types;
    private final MethodResolver methods;
    private final PropertyResolver properties;
    private final ClassResolver classes;

    private Fixture(Path root) throws IOException {
        this.parser = new Parser();
        Path logPath = Files.createTempFile("php-magik-test", ".log");
        logPath.toFile().deleteOnExit();
        this.log = new Logger(logPath);

        this.workspace = new Workspace(parser, log);
        this.workspace.index(root);

        this.finder = new SymbolFinder(workspace);
        this.variables = new VariableResolver(workspace);
        this.functions = new FunctionResolver(workspace);
        this.types = new TypeInference(workspace, functions);
        this.methods = new MethodResolver(workspace, types);
        this.properties = new PropertyResolver(workspace, types);
        this.classes = new ClassResolver(workspace, finder);
    }

    public static Fixture index(String name) {
        try {
            return new Fixture(LSP_TESTS.resolve(name));
        } catch (IOException cause) {
            throw new UncheckedIOException("cannot index lsp-tests/" + name, cause);
        }
    }

    public PhpFile file(String fileName) {
        for (PhpFile file : workspace.files()) {
            if (file.path().endsWith(fileName)) return file;
        }
        throw new AssertionError("no indexed file named " + fileName);
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

    public VariableResolver variables() {
        return variables;
    }

    public FunctionResolver functions() {
        return functions;
    }

    public TypeInference types() {
        return types;
    }

    public MethodResolver methods() {
        return methods;
    }

    public PropertyResolver properties() {
        return properties;
    }

    public ClassResolver classes() {
        return classes;
    }

    @Override
    public void close() throws IOException {
        workspace.close();
        log.close();
    }
}
