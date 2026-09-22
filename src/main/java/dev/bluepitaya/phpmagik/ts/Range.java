package dev.bluepitaya.phpmagik.ts;

import org.jspecify.annotations.NullMarked;

/**
 * The span of a node, start inclusive and end exclusive. Points, not byte
 * offsets, so it maps straight onto an LSP range - with the caveat every
 * {@link Point} carries: the column is a UTF-8 byte offset into its row.
 */
@NullMarked
public record Range(Point start, Point end) {

    public static Range of(Node node) {
        return new Range(node.getStartPoint(), node.getEndPoint());
    }

    /** Start inclusive, end exclusive, so adjacent ranges never both match. */
    public boolean contains(Point point) {
        return start.compareTo(point) <= 0 && end.compareTo(point) > 0;
    }

    /** Whether {@code other} covers this range, which it does when they are equal. */
    public boolean isWithin(Range other) {
        return other.start.compareTo(start) <= 0 && other.end.compareTo(end) >= 0;
    }

    @Override
    public String toString() {
        return start + " - " + end;
    }
}
