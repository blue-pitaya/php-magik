package dev.bluepitaya.phpmagik;

import dev.bluepitaya.phpmagik.lsp.Logger;
import dev.bluepitaya.phpmagik.lsp.LspServer;
import dev.bluepitaya.phpmagik.ts.Parser;

import java.io.IOException;
import java.nio.file.Path;

public final class Main {

    private static final Path LOG_PATH = Path.of("/tmp/php-magik.log");

    public static void main(String[] args) throws IOException {
        var programParams = new ProgramParams(args);
        var rootPath = programParams.rootPath;

        /* opened here, not in the server: the scan happens first, and its lines
         * belong in the log too */
        try (var parser = new Parser(); var log = new Logger(LOG_PATH)) {
            /* the log is appended to and shared by every server, so say which one
             * this is - the lines after it are this process's until the next banner */
            log.log("=== php-magik started, pid " + ProcessHandle.current().pid());

            var workspace = new Workspace(parser, log);

            try {
                workspace.index(Path.of(rootPath));
            } catch (IOException cause) {
                log.log("scan error: " + cause.getMessage());
                System.err.println("scan error: " + cause.getMessage());
                System.exit(1);
            }

            new LspServer(workspace, log).run();
        }
    }
}
