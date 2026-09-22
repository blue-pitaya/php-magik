package dev.bluepitaya.phpmagik;

import dev.bluepitaya.phpmagik.phpsymbol.PhpUseStatement;
import dev.bluepitaya.phpmagik.ts.Tree;

import java.util.List;

public final class PhpFile {

    private final int fileId;
    private final String uri;
    private final String path;

    private byte[] content;
    private Tree tree;
    private List<PhpUseStatement> uses = List.of();

    PhpFile(int fileId, String uri, String path, byte[] content, Tree tree) {
        this.fileId = fileId;
        this.uri = uri;
        this.path = path;
        this.content = content;
        this.tree = tree;
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

    public List<PhpUseStatement> uses() {
        return uses;
    }

    void uses(List<PhpUseStatement> uses) {
        this.uses = List.copyOf(uses);
    }

    void replace(byte[] content, Tree tree) {
        this.tree.close();
        this.content = content;
        this.tree = tree;
    }
}
