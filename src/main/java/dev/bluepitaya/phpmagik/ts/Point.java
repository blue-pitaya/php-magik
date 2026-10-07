package dev.bluepitaya.phpmagik.ts;

import org.jspecify.annotations.NullMarked;


@NullMarked
public record Point(int row, int column) implements Comparable<Point> {

    @Override
    public int compareTo(Point other) {
        int compare = Integer.compare(row, other.row);
        return compare != 0 ? compare : Integer.compare(column, other.column);
    }

    @Override
    public String toString() {
        return row + ":" + column;
    }
}
