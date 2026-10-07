package dev.bluepitaya.phpmagik.ts;

import org.jspecify.annotations.NullMarked;

@NullMarked
public record Range(Point start, Point end) {

    /** start inclusive, end exclusive */
    public boolean contains(Point point) {
        return start.compareTo(point) <= 0 && end.compareTo(point) > 0;
    }

    public boolean isWithin(Range other) {
        return other.start.compareTo(start) <= 0 && other.end.compareTo(end) >= 0;
    }

    @Override
    public String toString() {
        return start + " - " + end;
    }
}
