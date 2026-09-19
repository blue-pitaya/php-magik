package dev.bluepitaya.phpmagik;

import dev.bluepitaya.phpmagik.ts.Node;
import dev.bluepitaya.phpmagik.ts.Parser;
import dev.bluepitaya.phpmagik.ts.Tree;

import java.util.List;

public final class Main {
    public static void main(String[] args) {
        var source = """
                <?php
                
                function add(int $a, int $b): int
                {
                    return $a + $b;
                }
                """;

        try (var parser = new Parser(); var tree = parser.parse(source)) {
            var root = tree.getRootNode();

            System.out.println("root node:   " + root);
            System.out.println("descendants: " + root.getDescendantCount());
            System.out.println("has error:   " + root.hasError());
            System.out.println();

            System.out.println(root.getSexp());
            System.out.println();

            print(root, 0);
            System.out.println();

            var function = find(tree, "function_definition");
            var name = function.getChildByFieldName("name");
            System.out.println("function name: " + name.getContent() + " at " + name.getStartPoint());

            // SOURCE is ASCII, so a char index is also the byte offset here.
            var offset = source.indexOf("$a + $b");
            var at = root.getNamedDescendant(offset, offset);
            System.out.println("node at byte " + offset + ": " + at.getType() + " \"" + at.getContent() + "\"");
        }
    }

    private static void print(Node node, int depth) {
        String label = "  ".repeat(depth) + node.getType();
        System.out.printf("%-40s %s - %s", label, node.getStartPoint(), node.getEndPoint());

        List<Node> children = node.getNamedChildren();
        if (children.isEmpty()) {
            System.out.printf("  \"%s\"", node.getContent().replace("\n", "\\n"));
        }
        System.out.println();

        for (Node child : children) {
            print(child, depth + 1);
        }
    }

    private static Node find(Tree tree, String type) {
        for (Node node : tree) {
            if (node.getType().equals(type)) return node;
        }
        return null;
    }
}
