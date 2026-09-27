package dev.bluepitaya.phpmagik;

import dev.bluepitaya.phpmagik.ts.Tree;
import org.jspecify.annotations.NullMarked;

import java.io.IOException;
import java.nio.file.Path;


@NullMarked
public final class PhpFile {

    private final int fileId;
    private final Path path;

    private byte[] content;
    private Tree tree;

    PhpFile(int fileId, Path path, byte[] content, Tree tree) {
        this.fileId = fileId;
        this.path = path;
        this.content = content;
        this.tree = tree;
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

    public Tree tree() {
        return tree;
    }

    void replace(byte[] content, Tree tree) {
        this.tree.close();
        this.content = content;
        this.tree = tree;
    }
}
