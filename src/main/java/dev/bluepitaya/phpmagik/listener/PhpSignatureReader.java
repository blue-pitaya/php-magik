package dev.bluepitaya.phpmagik.listener;

import dev.bluepitaya.phpmagik.PhpReturnTypeInferer;
import dev.bluepitaya.phpmagik.phpdoc.PhpDoc;
import dev.bluepitaya.phpmagik.phpdoc.PhpDocParser;
import dev.bluepitaya.phpmagik.phpsymbol.PhpFunctionLike;
import dev.bluepitaya.phpmagik.phpsymbol.PhpType;
import dev.bluepitaya.phpmagik.ts.Node;
import org.jspecify.annotations.NullMarked;

@NullMarked
final class PhpSignatureReader {

    private PhpSignatureReader() {
    }

    static void read(PhpFunctionLike function, Node declaration, PhpNameResolver names) {
        function.scope(declaration.getRange());
        function.parameters(names.parametersText(declaration.getChildByFieldName("parameters")));

        PhpDoc doc = PhpDocParser.of(declaration);
        if (doc != null) {
            function.doc(doc);
        }

        Node hint = declaration.getChildByFieldName("return_type");
        if (hint != null) {
            function.declaredReturnType(names.typeText(hint));
        }
        PhpType returnType = PhpReturnTypeInferer.declared(hint, doc, names);
        if (returnType != null) {
            function.returnType(returnType);
        }
    }
}
