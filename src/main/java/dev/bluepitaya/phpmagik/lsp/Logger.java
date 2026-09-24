package dev.bluepitaya.phpmagik.lsp;

import org.jspecify.annotations.NullMarked;

import java.io.Closeable;
import java.io.IOException;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;

@NullMarked
public final class Logger implements Closeable {

    private final Writer out;

    public Logger(Path path) throws IOException {
        this.out = Files.newBufferedWriter(path, StandardOpenOption.CREATE,
                StandardOpenOption.WRITE, StandardOpenOption.APPEND);
    }

    public synchronized void log(String message) {
        try {
            out.write(message);
            out.write('\n');
            out.flush();
        } catch (IOException ignored) {
        }
    }

    @Override
    public void close() throws IOException {
        out.close();
    }
}
