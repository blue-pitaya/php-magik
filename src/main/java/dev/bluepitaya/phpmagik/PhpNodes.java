package dev.bluepitaya.phpmagik;

import dev.bluepitaya.phpmagik.ts.Node;
import dev.bluepitaya.phpmagik.ts.Nodes;
import dev.bluepitaya.phpmagik.ts.Range;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

import java.util.Set;

@NullMarked
public final class PhpNodes {

    public static final Set<String> NAME_TYPES = Set.of("name", "qualified_name");

    public static @Nullable Node memberName(Node access) {
        Node name = access.getChildByFieldName("name");
        return "name".equals(Nodes.type(name)) ? name : null;
    }

    public static @Nullable Range valueSource(@Nullable Node expr) {
        if (expr == null) return null;

        return switch (expr.getType()) {
            case "variable_name" -> Range.of(expr);
            case "function_call_expression" -> {
                Node called = expr.getChildByFieldName("function");
                String type = Nodes.type(called);
                yield type != null && NAME_TYPES.contains(type) ? Range.of(called) : Range.of(expr);
            }
            case "member_access_expression", "nullsafe_member_access_expression",
                 "member_call_expression", "nullsafe_member_call_expression" -> {
                /* whatever the object is: the access was recorded either way, and
                 * which class it is on is settled when it is resolved */
                Node name = memberName(expr);
                yield name == null ? Range.of(expr) : Range.of(name);
            }
            default -> Range.of(expr);
        };
    }
}
