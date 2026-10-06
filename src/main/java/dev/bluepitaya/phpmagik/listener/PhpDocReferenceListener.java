package dev.bluepitaya.phpmagik.listener;

import dev.bluepitaya.phpmagik.CompleteIndexer;
import dev.bluepitaya.phpmagik.PhpDoc;
import dev.bluepitaya.phpmagik.PhpFile;
import dev.bluepitaya.phpmagik.phpdoc.PhpDocTypes;
import dev.bluepitaya.phpmagik.phpsymbol.PhpClassReference;
import dev.bluepitaya.phpmagik.phpsymbol.PhpSymbolCollection;
import dev.bluepitaya.phpmagik.phpsymbol.PhpType;
import dev.bluepitaya.phpmagik.ts.Node;
import dev.bluepitaya.phpmagik.ts.Point;
import dev.bluepitaya.phpmagik.ts.Range;
import org.jspecify.annotations.NullMarked;

import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@NullMarked
public final class PhpDocReferenceListener implements Listener {

    private static final Pattern VAR_TAG = Pattern.compile("@var(?=\\s)");
    private static final Pattern CLASS_NAME =
            Pattern.compile("\\\\?[A-Za-z_][A-Za-z0-9_]*(?:\\\\[A-Za-z_][A-Za-z0-9_]*)*");

    private final PhpSymbolCollection collection;
    private final PhpFile file;
    private final PhpNameResolver names;

    public PhpDocReferenceListener(PhpSymbolCollection collection, PhpFile file, PhpNameResolver names) {
        this.collection = collection;
        this.file = file;
        this.names = names;
    }

    public void extra(CompleteIndexer.Ctx ctx, Node node) {
        String comment = node.getContent();
        if (!node.isType("comment") || !comment.startsWith("/**")) {
            return;
        }

        PhpDoc doc = PhpDoc.parse(comment);
        List<PhpDoc.Template> templates = doc == null ? List.of() : doc.templates();
        Matcher tag = VAR_TAG.matcher(comment);
        while (tag.find()) {
            int start = blanksEnd(comment, tag.end());
            int end = start + PhpDocTypes.end(comment.substring(start));
            addClassNames(node.getStartPoint(), comment, start, end, templates);
        }
    }

    private void addClassNames(Point origin, String comment, int start, int end, List<PhpDoc.Template> templates) {
        Matcher name = CLASS_NAME.matcher(comment).region(start, end);
        while (name.find()) {
            if (!isTypeName(comment, name.start(), name.end())) {
                continue;
            }
            if (names.type(name.group(), templates) instanceof PhpType.ClassType(String fqn) && !isRelative(fqn)) {
                Range range = new Range(pointAt(origin, comment, name.start()), pointAt(origin, comment, name.end()));
                collection.add(new PhpClassReference(file, fqn, range));
            }
        }
    }

    private static boolean isTypeName(String text, int start, int end) {
        char before = start == 0 ? ' ' : text.charAt(start - 1);
        char after = end == text.length() ? ' ' : text.charAt(end);
        if (before == '$' || before == '-' || after == '-') {
            return false;
        }

        int next = blanksEnd(text, end);
        boolean shapeKey = text.startsWith(":", next) && !text.startsWith("::", next);
        return !shapeKey;
    }

    private static boolean isRelative(String fqn) {
        return "self".equalsIgnoreCase(fqn) || "static".equalsIgnoreCase(fqn) || "parent".equalsIgnoreCase(fqn);
    }

    private static int blanksEnd(String text, int from) {
        int end = from;
        while (end < text.length() && (text.charAt(end) == ' ' || text.charAt(end) == '\t')) {
            end++;
        }
        return end;
    }

    private static Point pointAt(Point origin, String text, int offset) {
        int row = origin.getRow();
        int column = origin.getColumn();
        for (int i = 0; i < offset; ) {
            int codePoint = text.codePointAt(i);
            if (codePoint == '\n') {
                row++;
                column = 0;
            } else {
                column += utf8Length(codePoint);
            }
            i += Character.charCount(codePoint);
        }
        return new Point(row, column);
    }

    private static int utf8Length(int codePoint) {
        if (codePoint < 0x80) {
            return 1;
        }
        if (codePoint < 0x800) {
            return 2;
        }
        return codePoint < 0x10000 ? 3 : 4;
    }

    public void enter(CompleteIndexer.Ctx ctx, Node node) {
    }

    public void exit(CompleteIndexer.Ctx ctx, Node node) {
    }

    public void leaf(CompleteIndexer.Ctx ctx, Node node) {
    }

    public void token(CompleteIndexer.Ctx ctx, Node node, String field) {
    }

    public void error(CompleteIndexer.Ctx ctx, Node node) {
    }

    public void unexpected(CompleteIndexer.Ctx ctx, Node parent, Node child, String field) {
    }

    public void unknown(CompleteIndexer.Ctx ctx, Node node) {
    }
}
