package dev.bluepitaya.phpmagik.ts;

import java.nio.charset.StandardCharsets;
import java.util.Iterator;

/**
 * The syntax tree of an entire source file, constructed from JNI once a parse
 * succeeds.
 *
 * <p>Upstream keeps the source as a {@code String} and re-encodes it to UTF-16LE
 * on every slice, because it parses with {@code TSInputEncodingUTF16}. Here the
 * bytes handed to the parser are kept as they are, so node offsets index
 * straight into them.
 */
public class Tree extends External implements Iterable<Node> {

    private final byte[] source;

    Tree(long pointer, byte[] source) {
        super(pointer, () -> delete(pointer));
        this.source = source;
    }

    private static native void delete(long pointer);

    public native Node getRootNode();

    /** The UTF-8 bytes this tree was parsed from; node offsets index into these. */
    public byte[] getSource() {
        return source;
    }

    String getSource(int startByte, int endByte) {
        return new String(source, startByte, endByte - startByte, StandardCharsets.UTF_8);
    }

    @Override
    public Iterator<Node> iterator() {
        return getRootNode().iterator();
    }
}
