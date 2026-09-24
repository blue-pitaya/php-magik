package dev.bluepitaya.phpmagik;

import org.jspecify.annotations.NullMarked;

import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.util.LinkedHashMap;

@NullMarked
record ProgramParams(Path rootPath, boolean print) {

    public static final class ArgException extends RuntimeException {
        ArgException(String message) {
            super(message);
        }
    }

    static ProgramParams parse(String[] args) {
        var params = new LinkedHashMap<String, String>();
        var print = false;

        for (var i = 0; i < args.length; i++) {
            var arg = args[i];
            if (!arg.startsWith("--")) {
                throw new ArgException("unexpected argument: " + arg);
            }

            if (arg.equals("--print")) {
                print = true;
                continue;
            }

            String name;
            String value;
            var equals = arg.indexOf('=');
            if (equals >= 0) {
                name = arg.substring(2, equals);
                value = arg.substring(equals + 1);
            } else {
                name = arg.substring(2);
                if (i + 1 >= args.length) {
                    throw new ArgException("--" + name + " needs a value");
                }
                value = args[++i];
            }

            params.put(name, value);
        }

        var rootPath = params.get("path");
        if (rootPath == null) {
            throw new ArgException("--path is required");
        }

        try {
            return new ProgramParams(Path.of(rootPath), print);
        } catch (InvalidPathException e) {
            throw new ArgException("--path is invalid: " + e.getReason());
        }
    }
}
