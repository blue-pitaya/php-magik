package dev.bluepitaya.phpmagik.phpsymbol;

import dev.bluepitaya.phpmagik.ts.Range;

/**
 * Where a plain variable comes into being: whatever binds it. A parameter, an
 * assignment, a {@code foreach} value, a {@code catch} variable, a
 * {@code global} or a function {@code static}.
 *
 * <p>PHP declares none of them, so every assignment counts as one of these, not
 * just the first. The earliest is what go-to-definition jumps to; each later one
 * may retype the variable from that point on.
 *
 * @param name the leading {@code $} is kept, so {@code "$foo"}
 * @param ns enclosing namespace, {@code null} if not in one
 * @param functionName enclosing function, {@code null} at file scope; with
 * {@code fileId} it is the scope a name is unique within
 * @param type declared on a parameter or a {@code catch}, or read straight off
 * the right-hand side of an assignment - a literal, a {@code new}, an operator;
 * {@code null} when the value needs {@code valueSource} followed to be named,
 * and on a binding that says nothing about what it holds
 * @param valueSource where the assigned value came from when it was a variable,
 * a property read or a call: the range of the name identifying it, which is
 * exactly the range of the usage symbol the index recorded there. {@code null}
 * on a parameter, and whenever the value names nothing the index tracks
 * @param range spans the name as written
 */
public record PhpVarDefinition(
        String name,
        String ns,
        String functionName,
        String type,
        Range valueSource,
        Range range,
        int fileId) implements PhpSymbol {
}
