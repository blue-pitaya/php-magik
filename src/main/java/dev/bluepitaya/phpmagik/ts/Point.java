package dev.bluepitaya.phpmagik.ts;

import java.util.Objects;

/**
 * Port of the by-value struct:
 *
 * <pre>
 * typedef struct TSPoint {
 *   uint32_t row;
 *   uint32_t column;
 * } TSPoint;
 * </pre>
 *
 * The column is a UTF-8 byte offset into the row, not a character index - LSP
 * positions need converting before they can be compared against one.
 *
 * <p>Instances are immutable, and the arithmetic below either yields a new point
 * or an existing one: adding the origin to a point returns that same point.
 */
public final class Point implements Comparable<Point> {

    private static final Point ORIGIN = new Point(0, 0);

    private final int row;
    private final int column;

    public Point(int row, int column) {
        this.row = row;
        this.column = column;
    }

    public static Point origin() {
        return ORIGIN;
    }

    public int getRow() {
        return row;
    }

    public int getColumn() {
        return column;
    }

    public boolean isOrigin() {
        return equals(ORIGIN);
    }

    /** Rows first, then columns. */
    @Override
    public int compareTo(Point other) {
        Objects.requireNonNull(other, "Other point must not be null!");
        int compare = Integer.compare(row, other.row);
        return compare != 0 ? compare : Integer.compare(column, other.column);
    }

    public Point add(Point other) {
        Objects.requireNonNull(other, "Other point must not be null!");
        if (isOrigin()) return other;
        if (other.isOrigin()) return this;
        return add(other.row, other.column);
    }

    public Point subtract(Point other) {
        Objects.requireNonNull(other, "Other point must not be null!");
        if (other.isOrigin()) return this;
        if (equals(other)) return ORIGIN;
        return add(-other.row, -other.column);
    }

    public Point multiply(int value) {
        switch (value) {
            case 0: return ORIGIN;
            case 1: return this;
            default: return new Point(row * value, column * value);
        }
    }

    private Point add(int row, int column) {
        return new Point(this.row + row, this.column + column);
    }

    @Override
    public boolean equals(Object other) {
        if (other == this) return true;
        if (!(other instanceof Point point)) return false;
        return row == point.row && column == point.column;
    }

    @Override
    public int hashCode() {
        return 31 * row + column;
    }

    @Override
    public String toString() {
        return row + ":" + column;
    }
}
