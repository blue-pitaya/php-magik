package dev.bluepitaya.phpmagik.ts;

import java.lang.ref.Cleaner;

public abstract class External implements AutoCloseable {

    static {
        LibraryLoader.load();
    }

    private static final Cleaner cleaner = Cleaner.create();

    protected final long pointer;

    private final Cleaner.Cleanable cleanable;

    protected External(long pointer, Runnable deleter) {
        this.pointer = pointer;
        this.cleanable = cleaner.register(this, deleter);
    }

    @Override
    public void close() {
        cleanable.clean();
    }

    @Override
    public boolean equals(Object other) {
        if (other == this) return true;
        if (other == null || getClass() != other.getClass()) return false;
        return pointer == ((External) other).pointer;
    }

    @Override
    public int hashCode() {
        return Long.hashCode(pointer);
    }
}
