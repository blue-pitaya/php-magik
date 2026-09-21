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

    /**
     * Appends, and deliberately does not truncate: the path is the same for every
     * server, and more than one runs at a time - one per project root, plus
     * whatever is left over from a restart. Each process keeps its own file
     * offset, so a truncating writer leaves the others writing past a hole, and a
     * hole reads back as NUL bytes.
     */
    public Logger(Path path) throws IOException {
        this.out = Files.newBufferedWriter(path, StandardOpenOption.CREATE,
                StandardOpenOption.WRITE, StandardOpenOption.APPEND);
    }

    public void log(String message) {
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
