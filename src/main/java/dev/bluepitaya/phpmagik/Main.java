package dev.bluepitaya.phpmagik;

import dev.bluepitaya.phpmagik.lsp.Logger;
import dev.bluepitaya.phpmagik.lsp.LspServer;
import dev.bluepitaya.phpmagik.ts.Parser;

import java.io.IOException;
import java.nio.file.Path;

public final class Main {

    private static final Path LOG_PATH = Path.of("/tmp/php-magik.log");

    public static void main(String[] args) throws IOException {
        ProgramParams programParams;
        try {
            programParams = ProgramParams.parse(args);
        } catch (ProgramParams.ArgException cause) {
            System.err.println(cause.getMessage());
            System.err.println("usage: php-magik --path <dir|file>");
            System.exit(2);
            return;
        }
        var rootPath = programParams.rootPath();

        try (var parser = new Parser(); var log = new Logger(LOG_PATH)) {
            log.log("=== php-magik started, pid " + ProcessHandle.current().pid());

            var workspace = new Workspace(parser, log);
            try {
                workspace.index(programParams.rootPath());
            } catch (IOException cause) {
                log.log("scan error: " + cause.getMessage());
                System.err.println("scan error: " + cause.getMessage());
                System.exit(1);
            }

            new LspServer(workspace, log).run();
        }
    }
}
