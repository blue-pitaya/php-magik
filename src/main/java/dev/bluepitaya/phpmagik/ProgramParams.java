package dev.bluepitaya.phpmagik;

import java.util.LinkedHashMap;

final class ProgramParams {

    public final String rootPath;

    public static final class ArgException extends RuntimeException {
        ArgException(String message) {
            super(message);
        }
    }

    ProgramParams(String[] args) {
        var params = new LinkedHashMap<String, String>();

        for (var i = 0; i < args.length; i++) {
            var arg = args[i];
            if (!arg.startsWith("--")) {
                throw new ArgException("unexpected argument: " + arg);
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
        this.rootPath = rootPath;
    }
}
