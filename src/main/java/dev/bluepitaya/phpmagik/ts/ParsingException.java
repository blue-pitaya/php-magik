package dev.bluepitaya.phpmagik.ts;

/** The one exception type kept from upstream's hierarchy. */
public class ParsingException extends RuntimeException {

    public ParsingException(String message) {
        super(message);
    }

    public ParsingException(Throwable cause) {
        super(cause);
    }
}
