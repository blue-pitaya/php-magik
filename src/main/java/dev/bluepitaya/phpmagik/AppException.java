package dev.bluepitaya.phpmagik;

import org.jspecify.annotations.NullMarked;

/** General exception for handling this projects specific errors */
@NullMarked
public class AppException extends RuntimeException {
    public AppException(String message) {
        super(message);
    }
}
