package dev.bluepitaya.phpmagik.ts;

public final class LibraryLoader {

    static {
        System.loadLibrary("tsjni");
    }

    private LibraryLoader() {
    }

    public static void load() {
    }
}
