package dev.bluepitaya.phpmagik;

import dev.bluepitaya.phpmagik.index.Workspace;
import dev.bluepitaya.phpmagik.lsp.LspServer;

import java.io.IOException;
import java.nio.file.Path;

public final class Main {

    public static void main(String[] args) throws IOException {
        String rootPath = null;
        for (int i = 0; i < args.length; i++) {
            if (args[i].equals("--path") && i + 1 < args.length) {
                rootPath = args[++i];
            } else if (args[i].startsWith("--path=")) {
                rootPath = args[i].substring("--path=".length());
            } else {
                System.err.println("wrong args");
                System.exit(1);
            }
        }
        if (rootPath == null) {
            System.err.println("wrong args");
            System.exit(1);
        }

        Workspace workspace = new Workspace();
        try {
            workspace.index(Path.of(rootPath));
        } catch (IOException cause) {
            System.err.println("scan error: " + cause.getMessage());
            System.exit(1);
        }

        new LspServer(workspace).run();
    }
}
