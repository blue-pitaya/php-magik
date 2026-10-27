package dev.bluepitaya.phpmagik.listener;

import dev.bluepitaya.phpmagik.CompleteIndexer;
import dev.bluepitaya.phpmagik.PhpFile;
import dev.bluepitaya.phpmagik.phpsymbol.PhpClassReference;
import dev.bluepitaya.phpmagik.phpsymbol.PhpDoc;
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

    private static final Pattern TYPE_TAG = Pattern.compile(
            "@(?:var|param|return|throws|property(?:-read|-write)?)(?=\\s)");
    private static final Pattern CLASS_NAME =
            Pattern.compile("\\\\?[A-Za-z_][A-Za-z0-9_]*(?:\\\\[A-Za-z_][A-Za-z0-9_]*)*");

    private final PhpSymbolCollection collection;
    private final PhpFile file;

    public PhpDocReferenceListener(PhpSymbolCollection collection, PhpFile file) {
        this.collection = collection;
        this.file = file;
    }

    public void extra(CompleteIndexer.Ctx ctx, Node node) {
        String comment = node.getContent();
        if (!node.isType("comment") || !comment.startsWith("/**")) {
            return;
        }

        PhpDoc doc = PhpDoc.parse(comment);
        List<PhpDoc.Template> templates = doc == null ? List.of() : doc.templates();
        Matcher tag = TYPE_TAG.matcher(comment);
        while (tag.find()) {
            int start = blanksEnd(comment, tag.end());
            int end = start + PhpDoc.end(comment.substring(start));
            addClassNames(node.getStartPoint(), comment, start, end, templates);
        }
    }

    private void addClassNames(Point origin, String comment, int start, int end, List<PhpDoc.Template> templates) {
        Matcher name = CLASS_NAME.matcher(comment).region(start, end);
        while (name.find()) {
            if (!isTypeName(comment, name.start(), name.end())) {
                continue;
            }
            Range range = new Range(pointAt(origin, comment, name.start()), pointAt(origin, comment, name.end()));
            if (PhpType.parse(name.group(), collection.nameScopeOf(file, range), templates) instanceof PhpType.ClassType type
                    && !isRelative(type.fqn())) {
                collection.add(new PhpClassReference(file, type, range));
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
        int row = origin.row();
        int column = origin.column();
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
}
