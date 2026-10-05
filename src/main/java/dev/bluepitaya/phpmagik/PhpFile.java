package dev.bluepitaya.phpmagik;

import org.jspecify.annotations.NullMarked;

import java.io.IOException;
import java.nio.file.Path;


@NullMarked
public final class PhpFile {

    private final int fileId;
    private final Path path;

    private byte[] content;

    PhpFile(int fileId, Path path, byte[] content) {
        this.fileId = fileId;
        this.path = path;
        this.content = content;
    }

    public int fileId() {
        return fileId;
    }

    public String uri() {
        Path absolute;
        try {
            absolute = path.toRealPath();
        } catch (IOException cause) {
            absolute = path.toAbsolutePath();
        }
        return "file://" + absolute;
    }

    public Path path() {
        return path;
    }

    public byte[] content() {
        return content;
    }

    void replace(byte[] content) {
        this.content = content;
    }
}
