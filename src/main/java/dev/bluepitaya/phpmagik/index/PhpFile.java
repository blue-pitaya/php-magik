package dev.bluepitaya.phpmagik.index;

import dev.bluepitaya.phpmagik.ts.Tree;

import java.util.List;

public final class PhpFile {

    private final int fileId;
    private final String uri;
    private final String path;

    private byte[] content;
    private Tree tree;
    private List<PhpUseStatement> uses;

    PhpFile(int fileId, String uri, String path, byte[] content, Tree tree,
            List<PhpUseStatement> uses) {
        this.fileId = fileId;
        this.uri = uri;
        this.path = path;
        this.content = content;
        this.tree = tree;
        this.uses = List.copyOf(uses);
    }

    public int fileId() {
        return fileId;
    }

    public String uri() {
        return uri;
    }

    public String path() {
        return path;
    }

    public byte[] content() {
        return content;
    }

    public Tree tree() {
        return tree;
    }

    /** What this file imports, in source order. */
    public List<PhpUseStatement> uses() {
        return uses;
    }

    void replace(byte[] content, Tree tree, List<PhpUseStatement> uses) {
        this.tree.close();
        this.content = content;
        this.tree = tree;
        this.uses = List.copyOf(uses);
    }
}
