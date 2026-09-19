package dev.bluepitaya.phpmagik;

import dev.bluepitaya.phpmagik.index.Workspace;
import dev.bluepitaya.phpmagik.lsp.LspServer;

import java.io.IOException;
import java.nio.file.Path;

public final class Main {

    public static void main(String[] args) throws IOException {
        var programParams = new ProgramParams(args);
        var rootPath = programParams.rootPath;
        var workspace = new Workspace();

        try {
            workspace.index(Path.of(rootPath));
        } catch (IOException cause) {
            System.err.println("scan error: " + cause.getMessage());
            System.exit(1);
        }

        new LspServer(workspace).run();
    }
}
