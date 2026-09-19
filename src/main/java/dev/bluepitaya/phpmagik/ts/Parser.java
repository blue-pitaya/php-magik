package dev.bluepitaya.phpmagik.ts;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

public class Parser extends External {

    public Parser() {
        this(create());
    }

    private Parser(long pointer) {
        super(pointer, () -> delete(pointer));
    }

    private static native long create();

    private static native void delete(long pointer);

    /**
     * @throws ParsingException if parsing fails
     * @throws NullPointerException if {@code source} is {@code null}
     */
    public native Tree parse(byte[] source);

    public Tree parse(String source) {
        return parse(source.getBytes(StandardCharsets.UTF_8));
    }

    public Tree parse(Path path) {
        try {
            return parse(Files.readAllBytes(path));
        } catch (IOException cause) {
            throw new ParsingException(cause);
        }
    }
}
