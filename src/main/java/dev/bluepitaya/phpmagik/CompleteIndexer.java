package dev.bluepitaya.phpmagik;

import dev.bluepitaya.phpmagik.phpsymbol.PhpSymbolOwner;
import dev.bluepitaya.phpmagik.ts.Node;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.List;

/**
 * A walk of the whole PHP grammar, transcribed from {@code tree-sitter-php/php_only/src/node-types.json}.
 * Always performed per single PhpFile.
 * Doc:
 * - https://tree-sitter.github.io/tree-sitter/using-parsers
 * - https://tree-sitter.github.io/tree-sitter/creating-parsers
 */
@NullMarked
public final class CompleteIndexer {

    public interface Listener {

        void enter(Ctx ctx, Node node);

        void exit(Ctx ctx, Node node);

        void leaf(Ctx ctx, Node node);

        void token(Ctx ctx, Node node, String field);

        void extra(Ctx ctx, Node node);

        void error(Ctx ctx, Node node);

        void unexpected(Ctx ctx, Node parent, Node child, String field);

        void unknown(Ctx ctx, Node node);
    }

    public static final String NONE = "";

    public record Slot(Node node, String field) {
    }

    public static final class Ctx {

        private final Listener listener;
        private final Deque<Node> path = new ArrayDeque<>();
        private final Deque<PhpSymbolOwner> owners = new ArrayDeque<>();

        public Ctx(Listener listener) {
            this.listener = listener;
        }

        public List<Node> path() {
            List<Node> nodes = new ArrayList<>(path);
            Collections.reverse(nodes);
            return List.copyOf(nodes);
        }

        public int depth() {
            return path.size();
        }

        public @Nullable Node parent() {
            return path.size() < 2 ? null : path.stream().skip(1).findFirst().orElse(null);
        }

        public void push(PhpSymbolOwner owner) {
            owners.push(owner);
        }

        public @Nullable PhpSymbolOwner pop() {
            return owners.poll();
        }

        public @Nullable PhpSymbolOwner peek() {
            return owners.peek();
        }

        public List<Slot> slots(Node node) {
            List<Slot> slots = new ArrayList<>();
            int count = node.getChildCount();
            for (int i = 0; i < count; i++) {
                Node child = node.getChild(i);
                String field = node.getFieldNameForChild(i);

                if (child.isError() || child.isMissing()) {
                    listener.error(this, child);
                } else if (child.isExtra()) {
                    /* comments: the node types file lists them under no parent */
                    listener.extra(this, child);
                } else if (field != null) {
                    slots.add(new Slot(child, field));
                } else if (child.isNamed()) {
                    slots.add(new Slot(child, NONE));
                } else {
                    /* bare punctuation and keywords, in no field and named nowhere */
                    listener.token(this, child, NONE);
                }
            }
            return slots;
        }

        void enter(Node node) {
            path.push(node);
            listener.enter(this, node);
        }

        void exit(Node node) {
            listener.exit(this, node);
            path.pop();
        }

        void leaf(Node node) {
            path.push(node);
            listener.leaf(this, node);
            path.pop();
        }

        void token(Slot slot) {
            listener.token(this, slot.node(), slot.field());
        }

        void unexpected(Node parent, Slot slot) {
            listener.unexpected(this, parent, slot.node(), slot.field());
        }

        void unknown(Node node) {
            listener.unknown(this, node);
        }
    }

    public void walk(Node root, Listener listener) {
        visit(new Ctx(listener), root);
    }

    private String type(Node node) {
        String type = node.getType();
        return type == null ? "" : type;
    }

    private void visit(Ctx ctx, Node node) {
        switch (type(node)) {
            case "abstract_modifier" -> visitAbstractModifier(ctx, node);
            case "anonymous_class" -> visitAnonymousClass(ctx, node);
            case "anonymous_function" -> visitAnonymousFunction(ctx, node);
            case "anonymous_function_use_clause" -> visitAnonymousFunctionUseClause(ctx, node);
            case "argument" -> visitArgument(ctx, node);
            case "argument_placeholder" -> visitArgumentPlaceholder(ctx, node);
            case "arguments" -> visitArguments(ctx, node);
            case "array_creation_expression" -> visitArrayCreationExpression(ctx, node);
            case "array_element_initializer" -> visitArrayElementInitializer(ctx, node);
            case "arrow_function" -> visitArrowFunction(ctx, node);
            case "assignment_expression" -> visitAssignmentExpression(ctx, node);
            case "attribute" -> visitAttribute(ctx, node);
            case "attribute_group" -> visitAttributeGroup(ctx, node);
            case "attribute_list" -> visitAttributeList(ctx, node);
            case "augmented_assignment_expression" -> visitAugmentedAssignmentExpression(ctx, node);
            case "base_clause" -> visitBaseClause(ctx, node);
            case "binary_expression" -> visitBinaryExpression(ctx, node);
            case "boolean" -> visitBoolean(ctx, node);
            case "bottom_type" -> visitBottomType(ctx, node);
            case "break_statement" -> visitBreakStatement(ctx, node);
            case "by_ref" -> visitByRef(ctx, node);
            case "case_statement" -> visitCaseStatement(ctx, node);
            case "cast_expression" -> visitCastExpression(ctx, node);
            case "cast_type" -> visitCastType(ctx, node);
            case "catch_clause" -> visitCatchClause(ctx, node);
            case "class_constant_access_expression" -> visitClassConstantAccessExpression(ctx, node);
            case "class_declaration" -> visitClassDeclaration(ctx, node);
            case "class_interface_clause" -> visitClassInterfaceClause(ctx, node);
            case "clone_expression" -> visitCloneExpression(ctx, node);
            case "colon_block" -> visitColonBlock(ctx, node);
            case "comment" -> visitComment(ctx, node);
            case "compound_statement" -> visitCompoundStatement(ctx, node);
            case "conditional_expression" -> visitConditionalExpression(ctx, node);
            case "const_declaration" -> visitConstDeclaration(ctx, node);
            case "const_element" -> visitConstElement(ctx, node);
            case "continue_statement" -> visitContinueStatement(ctx, node);
            case "declaration_list" -> visitDeclarationList(ctx, node);
            case "declare_directive" -> visitDeclareDirective(ctx, node);
            case "declare_statement" -> visitDeclareStatement(ctx, node);
            case "default_statement" -> visitDefaultStatement(ctx, node);
            case "disjunctive_normal_form_type" -> visitDisjunctiveNormalFormType(ctx, node);
            case "do_statement" -> visitDoStatement(ctx, node);
            case "dynamic_variable_name" -> visitDynamicVariableName(ctx, node);
            case "echo_statement" -> visitEchoStatement(ctx, node);
            case "else_clause" -> visitElseClause(ctx, node);
            case "else_if_clause" -> visitElseIfClause(ctx, node);
            case "empty_statement" -> visitEmptyStatement(ctx, node);
            case "encapsed_string" -> visitEncapsedString(ctx, node);
            case "enum_case" -> visitEnumCase(ctx, node);
            case "enum_declaration" -> visitEnumDeclaration(ctx, node);
            case "enum_declaration_list" -> visitEnumDeclarationList(ctx, node);
            case "error_suppression_expression" -> visitErrorSuppressionExpression(ctx, node);
            case "escape_sequence" -> visitEscapeSequence(ctx, node);
            case "exit_statement" -> visitExitStatement(ctx, node);
            case "expression_statement" -> visitExpressionStatement(ctx, node);
            case "final_modifier" -> visitFinalModifier(ctx, node);
            case "finally_clause" -> visitFinallyClause(ctx, node);
            case "float" -> visitFloat(ctx, node);
            case "for_statement" -> visitForStatement(ctx, node);
            case "foreach_statement" -> visitForeachStatement(ctx, node);
            case "formal_parameters" -> visitFormalParameters(ctx, node);
            case "function_call_expression" -> visitFunctionCallExpression(ctx, node);
            case "function_definition" -> visitFunctionDefinition(ctx, node);
            case "function_static_declaration" -> visitFunctionStaticDeclaration(ctx, node);
            case "global_declaration" -> visitGlobalDeclaration(ctx, node);
            case "goto_statement" -> visitGotoStatement(ctx, node);
            case "heredoc" -> visitHeredoc(ctx, node);
            case "heredoc_body" -> visitHeredocBody(ctx, node);
            case "heredoc_end" -> visitHeredocEnd(ctx, node);
            case "heredoc_start" -> visitHeredocStart(ctx, node);
            case "if_statement" -> visitIfStatement(ctx, node);
            case "include_expression" -> visitIncludeExpression(ctx, node);
            case "include_once_expression" -> visitIncludeOnceExpression(ctx, node);
            case "integer" -> visitInteger(ctx, node);
            case "interface_declaration" -> visitInterfaceDeclaration(ctx, node);
            case "intersection_type" -> visitIntersectionType(ctx, node);
            case "list_literal" -> visitListLiteral(ctx, node);
            case "match_block" -> visitMatchBlock(ctx, node);
            case "match_condition_list" -> visitMatchConditionList(ctx, node);
            case "match_conditional_expression" -> visitMatchConditionalExpression(ctx, node);
            case "match_default_expression" -> visitMatchDefaultExpression(ctx, node);
            case "match_expression" -> visitMatchExpression(ctx, node);
            case "member_access_expression" -> visitMemberAccessExpression(ctx, node);
            case "member_call_expression" -> visitMemberCallExpression(ctx, node);
            case "method_declaration" -> visitMethodDeclaration(ctx, node);
            case "name" -> visitName(ctx, node);
            case "named_label_statement" -> visitNamedLabelStatement(ctx, node);
            case "named_type" -> visitNamedType(ctx, node);
            case "namespace_definition" -> visitNamespaceDefinition(ctx, node);
            case "namespace_name" -> visitNamespaceName(ctx, node);
            case "namespace_use_clause" -> visitNamespaceUseClause(ctx, node);
            case "namespace_use_declaration" -> visitNamespaceUseDeclaration(ctx, node);
            case "namespace_use_group" -> visitNamespaceUseGroup(ctx, node);
            case "nowdoc" -> visitNowdoc(ctx, node);
            case "nowdoc_body" -> visitNowdocBody(ctx, node);
            case "nowdoc_string" -> visitNowdocString(ctx, node);
            case "null" -> visitNull(ctx, node);
            case "nullsafe_member_access_expression" -> visitNullsafeMemberAccessExpression(ctx, node);
            case "nullsafe_member_call_expression" -> visitNullsafeMemberCallExpression(ctx, node);
            case "object_creation_expression" -> visitObjectCreationExpression(ctx, node);
            case "operation" -> visitOperation(ctx, node);
            case "optional_type" -> visitOptionalType(ctx, node);
            case "pair" -> visitPair(ctx, node);
            case "parenthesized_expression" -> visitParenthesizedExpression(ctx, node);
            case "php_end_tag" -> visitPhpEndTag(ctx, node);
            case "php_tag" -> visitPhpTag(ctx, node);
            case "primitive_type" -> visitPrimitiveType(ctx, node);
            case "print_intrinsic" -> visitPrintIntrinsic(ctx, node);
            case "program" -> visitProgram(ctx, node);
            case "property_declaration" -> visitPropertyDeclaration(ctx, node);
            case "property_element" -> visitPropertyElement(ctx, node);
            case "property_hook" -> visitPropertyHook(ctx, node);
            case "property_hook_list" -> visitPropertyHookList(ctx, node);
            case "property_promotion_parameter" -> visitPropertyPromotionParameter(ctx, node);
            case "qualified_name" -> visitQualifiedName(ctx, node);
            case "readonly_modifier" -> visitReadonlyModifier(ctx, node);
            case "reference_assignment_expression" -> visitReferenceAssignmentExpression(ctx, node);
            case "reference_modifier" -> visitReferenceModifier(ctx, node);
            case "relative_name" -> visitRelativeName(ctx, node);
            case "relative_scope" -> visitRelativeScope(ctx, node);
            case "require_expression" -> visitRequireExpression(ctx, node);
            case "require_once_expression" -> visitRequireOnceExpression(ctx, node);
            case "return_statement" -> visitReturnStatement(ctx, node);
            case "scoped_call_expression" -> visitScopedCallExpression(ctx, node);
            case "scoped_property_access_expression" -> visitScopedPropertyAccessExpression(ctx, node);
            case "sequence_expression" -> visitSequenceExpression(ctx, node);
            case "shell_command_expression" -> visitShellCommandExpression(ctx, node);
            case "simple_parameter" -> visitSimpleParameter(ctx, node);
            case "static_modifier" -> visitStaticModifier(ctx, node);
            case "static_variable_declaration" -> visitStaticVariableDeclaration(ctx, node);
            case "string" -> visitString(ctx, node);
            case "string_content" -> visitStringContent(ctx, node);
            case "subscript_expression" -> visitSubscriptExpression(ctx, node);
            case "switch_block" -> visitSwitchBlock(ctx, node);
            case "switch_statement" -> visitSwitchStatement(ctx, node);
            case "throw_expression" -> visitThrowExpression(ctx, node);
            case "trait_declaration" -> visitTraitDeclaration(ctx, node);
            case "try_statement" -> visitTryStatement(ctx, node);
            case "type_list" -> visitTypeList(ctx, node);
            case "unary_op_expression" -> visitUnaryOpExpression(ctx, node);
            case "union_type" -> visitUnionType(ctx, node);
            case "unset_statement" -> visitUnsetStatement(ctx, node);
            case "update_expression" -> visitUpdateExpression(ctx, node);
            case "use_as_clause" -> visitUseAsClause(ctx, node);
            case "use_declaration" -> visitUseDeclaration(ctx, node);
            case "use_instead_of_clause" -> visitUseInsteadOfClause(ctx, node);
            case "use_list" -> visitUseList(ctx, node);
            case "var_modifier" -> visitVarModifier(ctx, node);
            case "variable_name" -> visitVariableName(ctx, node);
            case "variadic_parameter" -> visitVariadicParameter(ctx, node);
            case "variadic_placeholder" -> visitVariadicPlaceholder(ctx, node);
            case "variadic_unpacking" -> visitVariadicUnpacking(ctx, node);
            case "visibility_modifier" -> visitVisibilityModifier(ctx, node);
            case "while_statement" -> visitWhileStatement(ctx, node);
            case "yield_expression" -> visitYieldExpression(ctx, node);
            default -> ctx.unknown(node);
        }
    }

    private void visitExpression(Ctx ctx, Node node) {
        switch (type(node)) {
            case "assignment_expression" -> visitAssignmentExpression(ctx, node);
            case "augmented_assignment_expression" -> visitAugmentedAssignmentExpression(ctx, node);
            case "binary_expression" -> visitBinaryExpression(ctx, node);
            case "cast_expression" -> visitCastExpression(ctx, node);
            case "clone_expression" -> visitCloneExpression(ctx, node);
            case "conditional_expression" -> visitConditionalExpression(ctx, node);
            case "error_suppression_expression" -> visitErrorSuppressionExpression(ctx, node);
            case "include_expression" -> visitIncludeExpression(ctx, node);
            case "include_once_expression" -> visitIncludeOnceExpression(ctx, node);
            case "match_expression" -> visitMatchExpression(ctx, node);
            case "reference_assignment_expression" -> visitReferenceAssignmentExpression(ctx, node);
            case "require_expression" -> visitRequireExpression(ctx, node);
            case "require_once_expression" -> visitRequireOnceExpression(ctx, node);
            case "unary_op_expression" -> visitUnaryOpExpression(ctx, node);
            case "yield_expression" -> visitYieldExpression(ctx, node);
            default -> visitPrimaryExpression(ctx, node);
        }
    }

    private void visitPrimaryExpression(Ctx ctx, Node node) {
        switch (type(node)) {
            case "anonymous_function" -> visitAnonymousFunction(ctx, node);
            case "array_creation_expression" -> visitArrayCreationExpression(ctx, node);
            case "arrow_function" -> visitArrowFunction(ctx, node);
            case "cast_expression" -> visitCastExpression(ctx, node);
            case "class_constant_access_expression" -> visitClassConstantAccessExpression(ctx, node);
            case "dynamic_variable_name" -> visitDynamicVariableName(ctx, node);
            case "function_call_expression" -> visitFunctionCallExpression(ctx, node);
            case "member_access_expression" -> visitMemberAccessExpression(ctx, node);
            case "member_call_expression" -> visitMemberCallExpression(ctx, node);
            case "name" -> visitName(ctx, node);
            case "nullsafe_member_access_expression" -> visitNullsafeMemberAccessExpression(ctx, node);
            case "nullsafe_member_call_expression" -> visitNullsafeMemberCallExpression(ctx, node);
            case "object_creation_expression" -> visitObjectCreationExpression(ctx, node);
            case "parenthesized_expression" -> visitParenthesizedExpression(ctx, node);
            case "print_intrinsic" -> visitPrintIntrinsic(ctx, node);
            case "qualified_name" -> visitQualifiedName(ctx, node);
            case "relative_name" -> visitRelativeName(ctx, node);
            case "scoped_call_expression" -> visitScopedCallExpression(ctx, node);
            case "scoped_property_access_expression" -> visitScopedPropertyAccessExpression(ctx, node);
            case "shell_command_expression" -> visitShellCommandExpression(ctx, node);
            case "subscript_expression" -> visitSubscriptExpression(ctx, node);
            case "throw_expression" -> visitThrowExpression(ctx, node);
            case "update_expression" -> visitUpdateExpression(ctx, node);
            case "variable_name" -> visitVariableName(ctx, node);
            default -> visitLiteral(ctx, node);
        }
    }

    private void visitLiteral(Ctx ctx, Node node) {
        switch (type(node)) {
            case "boolean" -> visitBoolean(ctx, node);
            case "encapsed_string" -> visitEncapsedString(ctx, node);
            case "float" -> visitFloat(ctx, node);
            case "heredoc" -> visitHeredoc(ctx, node);
            case "integer" -> visitInteger(ctx, node);
            case "nowdoc" -> visitNowdoc(ctx, node);
            case "null" -> visitNull(ctx, node);
            case "string" -> visitString(ctx, node);
            default -> ctx.unknown(node);
        }
    }

    private void visitStatement(Ctx ctx, Node node) {
        switch (type(node)) {
            case "break_statement" -> visitBreakStatement(ctx, node);
            case "class_declaration" -> visitClassDeclaration(ctx, node);
            case "compound_statement" -> visitCompoundStatement(ctx, node);
            case "const_declaration" -> visitConstDeclaration(ctx, node);
            case "continue_statement" -> visitContinueStatement(ctx, node);
            case "declare_statement" -> visitDeclareStatement(ctx, node);
            case "do_statement" -> visitDoStatement(ctx, node);
            case "echo_statement" -> visitEchoStatement(ctx, node);
            case "empty_statement" -> visitEmptyStatement(ctx, node);
            case "enum_declaration" -> visitEnumDeclaration(ctx, node);
            case "exit_statement" -> visitExitStatement(ctx, node);
            case "expression_statement" -> visitExpressionStatement(ctx, node);
            case "for_statement" -> visitForStatement(ctx, node);
            case "foreach_statement" -> visitForeachStatement(ctx, node);
            case "function_definition" -> visitFunctionDefinition(ctx, node);
            case "function_static_declaration" -> visitFunctionStaticDeclaration(ctx, node);
            case "global_declaration" -> visitGlobalDeclaration(ctx, node);
            case "goto_statement" -> visitGotoStatement(ctx, node);
            case "if_statement" -> visitIfStatement(ctx, node);
            case "interface_declaration" -> visitInterfaceDeclaration(ctx, node);
            case "named_label_statement" -> visitNamedLabelStatement(ctx, node);
            case "namespace_definition" -> visitNamespaceDefinition(ctx, node);
            case "namespace_use_declaration" -> visitNamespaceUseDeclaration(ctx, node);
            case "return_statement" -> visitReturnStatement(ctx, node);
            case "switch_statement" -> visitSwitchStatement(ctx, node);
            case "trait_declaration" -> visitTraitDeclaration(ctx, node);
            case "try_statement" -> visitTryStatement(ctx, node);
            case "unset_statement" -> visitUnsetStatement(ctx, node);
            case "while_statement" -> visitWhileStatement(ctx, node);
            default -> ctx.unknown(node);
        }
    }

    private void visitType(Ctx ctx, Node node) {
        switch (type(node)) {
            case "disjunctive_normal_form_type" -> visitDisjunctiveNormalFormType(ctx, node);
            case "intersection_type" -> visitIntersectionType(ctx, node);
            case "named_type" -> visitNamedType(ctx, node);
            case "optional_type" -> visitOptionalType(ctx, node);
            case "primitive_type" -> visitPrimitiveType(ctx, node);
            case "union_type" -> visitUnionType(ctx, node);
            default -> ctx.unknown(node);
        }
    }

    private void visitAbstractModifier(Ctx ctx, Node node) {
        ctx.leaf(node);
    }

    private void visitAnonymousClass(Ctx ctx, Node node) {
        ctx.enter(node);
        for (Slot slot : ctx.slots(node)) {
            switch (slot.field()) {
                case "attributes" -> visitAttributeList(ctx, slot.node());
                case "body" -> visitDeclarationList(ctx, slot.node());
                case NONE -> {
                    switch (type(slot.node())) {
                        case "abstract_modifier" -> visitAbstractModifier(ctx, slot.node());
                        case "arguments" -> visitArguments(ctx, slot.node());
                        case "base_clause" -> visitBaseClause(ctx, slot.node());
                        case "class_interface_clause" -> visitClassInterfaceClause(ctx, slot.node());
                        case "final_modifier" -> visitFinalModifier(ctx, slot.node());
                        case "readonly_modifier" -> visitReadonlyModifier(ctx, slot.node());
                        case "static_modifier" -> visitStaticModifier(ctx, slot.node());
                        case "var_modifier" -> visitVarModifier(ctx, slot.node());
                        case "visibility_modifier" -> visitVisibilityModifier(ctx, slot.node());
                        default -> ctx.unexpected(node, slot);
                    }
                }
                default -> ctx.unexpected(node, slot);
            }
        }
        ctx.exit(node);
    }

    private void visitAnonymousFunction(Ctx ctx, Node node) {
        ctx.enter(node);
        for (Slot slot : ctx.slots(node)) {
            switch (slot.field()) {
                case "attributes" -> visitAttributeList(ctx, slot.node());
                case "body" -> visitCompoundStatement(ctx, slot.node());
                case "parameters" -> visitFormalParameters(ctx, slot.node());
                case "reference_modifier" -> visitReferenceModifier(ctx, slot.node());
                case "return_type" -> visitReturnType(ctx, slot);
                case "static_modifier" -> visitStaticModifier(ctx, slot.node());
                case NONE -> {
                    switch (type(slot.node())) {
                        case "anonymous_function_use_clause" -> visitAnonymousFunctionUseClause(ctx, slot.node());
                        default -> ctx.unexpected(node, slot);
                    }
                }
                default -> ctx.unexpected(node, slot);
            }
        }
        ctx.exit(node);
    }

    private void visitAnonymousFunctionUseClause(Ctx ctx, Node node) {
        ctx.enter(node);
        for (Slot slot : ctx.slots(node)) {
            if (!NONE.equals(slot.field())) {
                ctx.unexpected(node, slot);
                continue;
            }
            switch (type(slot.node())) {
                case "by_ref" -> visitByRef(ctx, slot.node());
                case "variable_name" -> visitVariableName(ctx, slot.node());
                default -> ctx.unexpected(node, slot);
            }
        }
        ctx.exit(node);
    }

    private void visitArgument(Ctx ctx, Node node) {
        ctx.enter(node);
        for (Slot slot : ctx.slots(node)) {
            switch (slot.field()) {
                case "name" -> visitName(ctx, slot.node());
                case "reference_modifier" -> visitReferenceModifier(ctx, slot.node());
                case NONE -> {
                    switch (type(slot.node())) {
                        case "argument_placeholder" -> visitArgumentPlaceholder(ctx, slot.node());
                        case "name" -> visitName(ctx, slot.node());
                        case "variadic_unpacking" -> visitVariadicUnpacking(ctx, slot.node());
                        default -> visitExpression(ctx, slot.node());
                    }
                }
                default -> ctx.unexpected(node, slot);
            }
        }
        ctx.exit(node);
    }

    private void visitArgumentPlaceholder(Ctx ctx, Node node) {
        ctx.leaf(node);
    }

    private void visitArguments(Ctx ctx, Node node) {
        ctx.enter(node);
        for (Slot slot : ctx.slots(node)) {
            if (!NONE.equals(slot.field())) {
                ctx.unexpected(node, slot);
                continue;
            }
            switch (type(slot.node())) {
                case "argument" -> visitArgument(ctx, slot.node());
                case "variadic_placeholder" -> visitVariadicPlaceholder(ctx, slot.node());
                default -> ctx.unexpected(node, slot);
            }
        }
        ctx.exit(node);
    }

    private void visitArrayCreationExpression(Ctx ctx, Node node) {
        ctx.enter(node);
        for (Slot slot : ctx.slots(node)) {
            if (!NONE.equals(slot.field())) {
                ctx.unexpected(node, slot);
                continue;
            }
            switch (type(slot.node())) {
                case "array_element_initializer" -> visitArrayElementInitializer(ctx, slot.node());
                default -> ctx.unexpected(node, slot);
            }
        }
        ctx.exit(node);
    }

    private void visitArrayElementInitializer(Ctx ctx, Node node) {
        ctx.enter(node);
        for (Slot slot : ctx.slots(node)) {
            if (!NONE.equals(slot.field())) {
                ctx.unexpected(node, slot);
                continue;
            }
            switch (type(slot.node())) {
                case "by_ref" -> visitByRef(ctx, slot.node());
                case "variadic_unpacking" -> visitVariadicUnpacking(ctx, slot.node());
                default -> visitExpression(ctx, slot.node());
            }
        }
        ctx.exit(node);
    }

    private void visitArrowFunction(Ctx ctx, Node node) {
        ctx.enter(node);
        for (Slot slot : ctx.slots(node)) {
            switch (slot.field()) {
                case "attributes" -> visitAttributeList(ctx, slot.node());
                case "body" -> visitExpression(ctx, slot.node());
                case "parameters" -> visitFormalParameters(ctx, slot.node());
                case "reference_modifier" -> visitReferenceModifier(ctx, slot.node());
                case "return_type" -> visitReturnType(ctx, slot);
                case "static_modifier" -> visitStaticModifier(ctx, slot.node());
                default -> ctx.unexpected(node, slot);
            }
        }
        ctx.exit(node);
    }

    private void visitAssignmentExpression(Ctx ctx, Node node) {
        ctx.enter(node);
        for (Slot slot : ctx.slots(node)) {
            switch (slot.field()) {
                case "left" -> visitAssignmentTarget(ctx, slot);
                case "right" -> visitExpression(ctx, slot.node());
                default -> ctx.unexpected(node, slot);
            }
        }
        ctx.exit(node);
    }

    private void visitAttribute(Ctx ctx, Node node) {
        ctx.enter(node);
        for (Slot slot : ctx.slots(node)) {
            switch (slot.field()) {
                case "parameters" -> visitArguments(ctx, slot.node());
                case NONE -> visitClassName(ctx, node, slot);
                default -> ctx.unexpected(node, slot);
            }
        }
        ctx.exit(node);
    }

    private void visitAttributeGroup(Ctx ctx, Node node) {
        ctx.enter(node);
        for (Slot slot : ctx.slots(node)) {
            if (!NONE.equals(slot.field())) {
                ctx.unexpected(node, slot);
                continue;
            }
            switch (type(slot.node())) {
                case "attribute" -> visitAttribute(ctx, slot.node());
                default -> ctx.unexpected(node, slot);
            }
        }
        ctx.exit(node);
    }

    private void visitAttributeList(Ctx ctx, Node node) {
        ctx.enter(node);
        for (Slot slot : ctx.slots(node)) {
            if (!NONE.equals(slot.field())) {
                ctx.unexpected(node, slot);
                continue;
            }
            switch (type(slot.node())) {
                case "attribute_group" -> visitAttributeGroup(ctx, slot.node());
                default -> ctx.unexpected(node, slot);
            }
        }
        ctx.exit(node);
    }

    private void visitAugmentedAssignmentExpression(Ctx ctx, Node node) {
        ctx.enter(node);
        for (Slot slot : ctx.slots(node)) {
            switch (slot.field()) {
                case "left" -> visitAssignmentTarget(ctx, slot);
                case "operator" -> ctx.token(slot);
                case "right" -> visitExpression(ctx, slot.node());
                default -> ctx.unexpected(node, slot);
            }
        }
        ctx.exit(node);
    }

    private void visitBaseClause(Ctx ctx, Node node) {
        ctx.enter(node);
        for (Slot slot : ctx.slots(node)) {
            if (!NONE.equals(slot.field())) {
                ctx.unexpected(node, slot);
                continue;
            }
            visitClassName(ctx, node, slot);
        }
        ctx.exit(node);
    }

    private void visitBinaryExpression(Ctx ctx, Node node) {
        ctx.enter(node);
        for (Slot slot : ctx.slots(node)) {
            switch (slot.field()) {
                case "left" -> visitExpression(ctx, slot.node());
                case "operator" -> ctx.token(slot);
                case "right" -> {
                    switch (type(slot.node())) {
                        case "dynamic_variable_name" -> visitDynamicVariableName(ctx, slot.node());
                        case "member_access_expression" -> visitMemberAccessExpression(ctx, slot.node());
                        case "name" -> visitName(ctx, slot.node());
                        case "nullsafe_member_access_expression" ->
                                visitNullsafeMemberAccessExpression(ctx, slot.node());
                        case "parenthesized_expression" -> visitParenthesizedExpression(ctx, slot.node());
                        case "qualified_name" -> visitQualifiedName(ctx, slot.node());
                        case "relative_name" -> visitRelativeName(ctx, slot.node());
                        case "scoped_property_access_expression" ->
                                visitScopedPropertyAccessExpression(ctx, slot.node());
                        case "subscript_expression" -> visitSubscriptExpression(ctx, slot.node());
                        case "variable_name" -> visitVariableName(ctx, slot.node());
                        default -> visitExpression(ctx, slot.node());
                    }
                }
                default -> ctx.unexpected(node, slot);
            }
        }
        ctx.exit(node);
    }

    private void visitBoolean(Ctx ctx, Node node) {
        ctx.leaf(node);
    }

    private void visitBottomType(Ctx ctx, Node node) {
        ctx.leaf(node);
    }

    private void visitBreakStatement(Ctx ctx, Node node) {
        ctx.enter(node);
        for (Slot slot : ctx.slots(node)) {
            if (!NONE.equals(slot.field())) {
                ctx.unexpected(node, slot);
                continue;
            }
            visitExpression(ctx, slot.node());
        }
        ctx.exit(node);
    }

    private void visitByRef(Ctx ctx, Node node) {
        ctx.enter(node);
        for (Slot slot : ctx.slots(node)) {
            if (!NONE.equals(slot.field())) {
                ctx.unexpected(node, slot);
                continue;
            }
            visitVariableTarget(ctx, slot);
        }
        ctx.exit(node);
    }

    private void visitCaseStatement(Ctx ctx, Node node) {
        ctx.enter(node);
        for (Slot slot : ctx.slots(node)) {
            switch (slot.field()) {
                case "value" -> visitExpression(ctx, slot.node());
                case NONE -> visitStatement(ctx, slot.node());
                default -> ctx.unexpected(node, slot);
            }
        }
        ctx.exit(node);
    }

    private void visitCastExpression(Ctx ctx, Node node) {
        ctx.enter(node);
        for (Slot slot : ctx.slots(node)) {
            switch (slot.field()) {
                case "type" -> visitCastType(ctx, slot.node());
                case "value" -> {
                    switch (type(slot.node())) {
                        case "clone_expression" -> visitCloneExpression(ctx, slot.node());
                        case "error_suppression_expression" -> visitErrorSuppressionExpression(ctx, slot.node());
                        case "include_expression" -> visitIncludeExpression(ctx, slot.node());
                        case "include_once_expression" -> visitIncludeOnceExpression(ctx, slot.node());
                        case "unary_op_expression" -> visitUnaryOpExpression(ctx, slot.node());
                        default -> visitPrimaryExpression(ctx, slot.node());
                    }
                }
                default -> ctx.unexpected(node, slot);
            }
        }
        ctx.exit(node);
    }

    private void visitCastType(Ctx ctx, Node node) {
        ctx.leaf(node);
    }

    private void visitCatchClause(Ctx ctx, Node node) {
        ctx.enter(node);
        for (Slot slot : ctx.slots(node)) {
            switch (slot.field()) {
                case "body" -> visitCompoundStatement(ctx, slot.node());
                case "name" -> visitVariableName(ctx, slot.node());
                case "type" -> visitTypeList(ctx, slot.node());
                default -> ctx.unexpected(node, slot);
            }
        }
        ctx.exit(node);
    }

    private void visitClassConstantAccessExpression(Ctx ctx, Node node) {
        ctx.enter(node);
        for (Slot slot : ctx.slots(node)) {
            if (!NONE.equals(slot.field())) {
                ctx.unexpected(node, slot);
                continue;
            }
            switch (type(slot.node())) {
                case "class_constant_access_expression" -> visitClassConstantAccessExpression(ctx, slot.node());
                case "relative_scope" -> visitRelativeScope(ctx, slot.node());
                default -> visitPrimaryExpression(ctx, slot.node());
            }
        }
        ctx.exit(node);
    }

    private void visitClassDeclaration(Ctx ctx, Node node) {
        ctx.enter(node);
        for (Slot slot : ctx.slots(node)) {
            switch (slot.field()) {
                case "attributes" -> visitAttributeList(ctx, slot.node());
                case "body" -> visitDeclarationList(ctx, slot.node());
                case "name" -> visitName(ctx, slot.node());
                case NONE -> {
                    switch (type(slot.node())) {
                        case "abstract_modifier" -> visitAbstractModifier(ctx, slot.node());
                        case "base_clause" -> visitBaseClause(ctx, slot.node());
                        case "class_interface_clause" -> visitClassInterfaceClause(ctx, slot.node());
                        case "final_modifier" -> visitFinalModifier(ctx, slot.node());
                        case "readonly_modifier" -> visitReadonlyModifier(ctx, slot.node());
                        case "static_modifier" -> visitStaticModifier(ctx, slot.node());
                        case "var_modifier" -> visitVarModifier(ctx, slot.node());
                        case "visibility_modifier" -> visitVisibilityModifier(ctx, slot.node());
                        default -> ctx.unexpected(node, slot);
                    }
                }
                default -> ctx.unexpected(node, slot);
            }
        }
        ctx.exit(node);
    }

    private void visitClassInterfaceClause(Ctx ctx, Node node) {
        ctx.enter(node);
        for (Slot slot : ctx.slots(node)) {
            if (!NONE.equals(slot.field())) {
                ctx.unexpected(node, slot);
                continue;
            }
            visitClassName(ctx, node, slot);
        }
        ctx.exit(node);
    }

    private void visitCloneExpression(Ctx ctx, Node node) {
        ctx.enter(node);
        for (Slot slot : ctx.slots(node)) {
            if (!NONE.equals(slot.field())) {
                ctx.unexpected(node, slot);
                continue;
            }
            visitPrimaryExpression(ctx, slot.node());
        }
        ctx.exit(node);
    }

    private void visitColonBlock(Ctx ctx, Node node) {
        ctx.enter(node);
        for (Slot slot : ctx.slots(node)) {
            if (!NONE.equals(slot.field())) {
                ctx.unexpected(node, slot);
                continue;
            }
            visitStatement(ctx, slot.node());
        }
        ctx.exit(node);
    }

    private void visitComment(Ctx ctx, Node node) {
        ctx.leaf(node);
    }

    private void visitCompoundStatement(Ctx ctx, Node node) {
        ctx.enter(node);
        for (Slot slot : ctx.slots(node)) {
            if (!NONE.equals(slot.field())) {
                ctx.unexpected(node, slot);
                continue;
            }
            visitStatement(ctx, slot.node());
        }
        ctx.exit(node);
    }

    private void visitConditionalExpression(Ctx ctx, Node node) {
        ctx.enter(node);
        for (Slot slot : ctx.slots(node)) {
            switch (slot.field()) {
                case "alternative", "body", "condition" -> visitExpression(ctx, slot.node());
                default -> ctx.unexpected(node, slot);
            }
        }
        ctx.exit(node);
    }

    private void visitConstDeclaration(Ctx ctx, Node node) {
        ctx.enter(node);
        for (Slot slot : ctx.slots(node)) {
            switch (slot.field()) {
                case "attributes" -> visitAttributeList(ctx, slot.node());
                case "type" -> visitType(ctx, slot.node());
                case NONE -> {
                    switch (type(slot.node())) {
                        case "abstract_modifier" -> visitAbstractModifier(ctx, slot.node());
                        case "const_element" -> visitConstElement(ctx, slot.node());
                        case "final_modifier" -> visitFinalModifier(ctx, slot.node());
                        case "readonly_modifier" -> visitReadonlyModifier(ctx, slot.node());
                        case "static_modifier" -> visitStaticModifier(ctx, slot.node());
                        case "var_modifier" -> visitVarModifier(ctx, slot.node());
                        case "visibility_modifier" -> visitVisibilityModifier(ctx, slot.node());
                        default -> ctx.unexpected(node, slot);
                    }
                }
                default -> ctx.unexpected(node, slot);
            }
        }
        ctx.exit(node);
    }

    private void visitConstElement(Ctx ctx, Node node) {
        ctx.enter(node);
        for (Slot slot : ctx.slots(node)) {
            if (!NONE.equals(slot.field())) {
                ctx.unexpected(node, slot);
                continue;
            }
            switch (type(slot.node())) {
                case "name" -> visitName(ctx, slot.node());
                default -> visitExpression(ctx, slot.node());
            }
        }
        ctx.exit(node);
    }

    private void visitContinueStatement(Ctx ctx, Node node) {
        ctx.enter(node);
        for (Slot slot : ctx.slots(node)) {
            if (!NONE.equals(slot.field())) {
                ctx.unexpected(node, slot);
                continue;
            }
            visitExpression(ctx, slot.node());
        }
        ctx.exit(node);
    }

    private void visitDeclarationList(Ctx ctx, Node node) {
        ctx.enter(node);
        for (Slot slot : ctx.slots(node)) {
            if (!NONE.equals(slot.field())) {
                ctx.unexpected(node, slot);
                continue;
            }
            switch (type(slot.node())) {
                case "const_declaration" -> visitConstDeclaration(ctx, slot.node());
                case "method_declaration" -> visitMethodDeclaration(ctx, slot.node());
                case "property_declaration" -> visitPropertyDeclaration(ctx, slot.node());
                case "use_declaration" -> visitUseDeclaration(ctx, slot.node());
                default -> ctx.unexpected(node, slot);
            }
        }
        ctx.exit(node);
    }

    private void visitDeclareDirective(Ctx ctx, Node node) {
        ctx.enter(node);
        for (Slot slot : ctx.slots(node)) {
            if (!NONE.equals(slot.field())) {
                ctx.unexpected(node, slot);
                continue;
            }
            visitLiteral(ctx, slot.node());
        }
        ctx.exit(node);
    }

    private void visitDeclareStatement(Ctx ctx, Node node) {
        ctx.enter(node);
        for (Slot slot : ctx.slots(node)) {
            if (!NONE.equals(slot.field())) {
                ctx.unexpected(node, slot);
                continue;
            }
            switch (type(slot.node())) {
                case "declare_directive" -> visitDeclareDirective(ctx, slot.node());
                default -> visitStatement(ctx, slot.node());
            }
        }
        ctx.exit(node);
    }

    private void visitDefaultStatement(Ctx ctx, Node node) {
        ctx.enter(node);
        for (Slot slot : ctx.slots(node)) {
            if (!NONE.equals(slot.field())) {
                ctx.unexpected(node, slot);
                continue;
            }
            visitStatement(ctx, slot.node());
        }
        ctx.exit(node);
    }

    private void visitDisjunctiveNormalFormType(Ctx ctx, Node node) {
        ctx.enter(node);
        for (Slot slot : ctx.slots(node)) {
            if (!NONE.equals(slot.field())) {
                ctx.unexpected(node, slot);
                continue;
            }
            switch (type(slot.node())) {
                case "intersection_type" -> visitIntersectionType(ctx, slot.node());
                case "named_type" -> visitNamedType(ctx, slot.node());
                case "optional_type" -> visitOptionalType(ctx, slot.node());
                case "primitive_type" -> visitPrimitiveType(ctx, slot.node());
                default -> ctx.unexpected(node, slot);
            }
        }
        ctx.exit(node);
    }

    private void visitDoStatement(Ctx ctx, Node node) {
        ctx.enter(node);
        for (Slot slot : ctx.slots(node)) {
            switch (slot.field()) {
                case "body" -> visitStatement(ctx, slot.node());
                case "condition" -> visitParenthesizedExpression(ctx, slot.node());
                default -> ctx.unexpected(node, slot);
            }
        }
        ctx.exit(node);
    }

    private void visitDynamicVariableName(Ctx ctx, Node node) {
        ctx.enter(node);
        for (Slot slot : ctx.slots(node)) {
            if (!NONE.equals(slot.field())) {
                ctx.unexpected(node, slot);
                continue;
            }
            switch (type(slot.node())) {
                case "dynamic_variable_name" -> visitDynamicVariableName(ctx, slot.node());
                case "variable_name" -> visitVariableName(ctx, slot.node());
                default -> visitExpression(ctx, slot.node());
            }
        }
        ctx.exit(node);
    }

    private void visitEchoStatement(Ctx ctx, Node node) {
        ctx.enter(node);
        for (Slot slot : ctx.slots(node)) {
            if (!NONE.equals(slot.field())) {
                ctx.unexpected(node, slot);
                continue;
            }
            switch (type(slot.node())) {
                case "sequence_expression" -> visitSequenceExpression(ctx, slot.node());
                default -> visitExpression(ctx, slot.node());
            }
        }
        ctx.exit(node);
    }

    private void visitElseClause(Ctx ctx, Node node) {
        ctx.enter(node);
        for (Slot slot : ctx.slots(node)) {
            switch (slot.field()) {
                case "body" -> visitBlockOrStatement(ctx, slot);
                default -> ctx.unexpected(node, slot);
            }
        }
        ctx.exit(node);
    }

    private void visitElseIfClause(Ctx ctx, Node node) {
        ctx.enter(node);
        for (Slot slot : ctx.slots(node)) {
            switch (slot.field()) {
                case "body" -> visitBlockOrStatement(ctx, slot);
                case "condition" -> visitParenthesizedExpression(ctx, slot.node());
                default -> ctx.unexpected(node, slot);
            }
        }
        ctx.exit(node);
    }

    private void visitEmptyStatement(Ctx ctx, Node node) {
        ctx.leaf(node);
    }

    private void visitEncapsedString(Ctx ctx, Node node) {
        ctx.enter(node);
        for (Slot slot : ctx.slots(node)) {
            if (!NONE.equals(slot.field())) {
                ctx.unexpected(node, slot);
                continue;
            }
            visitInterpolated(ctx, slot);
        }
        ctx.exit(node);
    }

    private void visitEnumCase(Ctx ctx, Node node) {
        ctx.enter(node);
        for (Slot slot : ctx.slots(node)) {
            switch (slot.field()) {
                case "attributes" -> visitAttributeList(ctx, slot.node());
                case "name" -> visitName(ctx, slot.node());
                case "value" -> visitExpression(ctx, slot.node());
                default -> ctx.unexpected(node, slot);
            }
        }
        ctx.exit(node);
    }

    private void visitEnumDeclaration(Ctx ctx, Node node) {
        ctx.enter(node);
        for (Slot slot : ctx.slots(node)) {
            switch (slot.field()) {
                case "attributes" -> visitAttributeList(ctx, slot.node());
                case "body" -> visitEnumDeclarationList(ctx, slot.node());
                case "name" -> visitName(ctx, slot.node());
                case NONE -> {
                    switch (type(slot.node())) {
                        case "class_interface_clause" -> visitClassInterfaceClause(ctx, slot.node());
                        case "primitive_type" -> visitPrimitiveType(ctx, slot.node());
                        default -> ctx.unexpected(node, slot);
                    }
                }
                default -> ctx.unexpected(node, slot);
            }
        }
        ctx.exit(node);
    }

    private void visitEnumDeclarationList(Ctx ctx, Node node) {
        ctx.enter(node);
        for (Slot slot : ctx.slots(node)) {
            if (!NONE.equals(slot.field())) {
                ctx.unexpected(node, slot);
                continue;
            }
            switch (type(slot.node())) {
                case "const_declaration" -> visitConstDeclaration(ctx, slot.node());
                case "enum_case" -> visitEnumCase(ctx, slot.node());
                case "method_declaration" -> visitMethodDeclaration(ctx, slot.node());
                case "use_declaration" -> visitUseDeclaration(ctx, slot.node());
                default -> ctx.unexpected(node, slot);
            }
        }
        ctx.exit(node);
    }

    private void visitErrorSuppressionExpression(Ctx ctx, Node node) {
        ctx.enter(node);
        for (Slot slot : ctx.slots(node)) {
            if (!NONE.equals(slot.field())) {
                ctx.unexpected(node, slot);
                continue;
            }
            visitExpression(ctx, slot.node());
        }
        ctx.exit(node);
    }

    private void visitEscapeSequence(Ctx ctx, Node node) {
        ctx.leaf(node);
    }

    private void visitExitStatement(Ctx ctx, Node node) {
        ctx.enter(node);
        for (Slot slot : ctx.slots(node)) {
            if (!NONE.equals(slot.field())) {
                ctx.unexpected(node, slot);
                continue;
            }
            visitExpression(ctx, slot.node());
        }
        ctx.exit(node);
    }

    private void visitExpressionStatement(Ctx ctx, Node node) {
        ctx.enter(node);
        for (Slot slot : ctx.slots(node)) {
            if (!NONE.equals(slot.field())) {
                ctx.unexpected(node, slot);
                continue;
            }
            visitExpression(ctx, slot.node());
        }
        ctx.exit(node);
    }

    private void visitFinalModifier(Ctx ctx, Node node) {
        ctx.leaf(node);
    }

    private void visitFinallyClause(Ctx ctx, Node node) {
        ctx.enter(node);
        for (Slot slot : ctx.slots(node)) {
            switch (slot.field()) {
                case "body" -> visitCompoundStatement(ctx, slot.node());
                default -> ctx.unexpected(node, slot);
            }
        }
        ctx.exit(node);
    }

    private void visitFloat(Ctx ctx, Node node) {
        ctx.leaf(node);
    }

    private void visitForStatement(Ctx ctx, Node node) {
        ctx.enter(node);
        for (Slot slot : ctx.slots(node)) {
            switch (slot.field()) {
                case "body" -> visitStatement(ctx, slot.node());
                case "condition", "initialize", "update" -> {
                    switch (type(slot.node())) {
                        case "sequence_expression" -> visitSequenceExpression(ctx, slot.node());
                        default -> visitExpression(ctx, slot.node());
                    }
                }
                default -> ctx.unexpected(node, slot);
            }
        }
        ctx.exit(node);
    }

    private void visitForeachStatement(Ctx ctx, Node node) {
        ctx.enter(node);
        for (Slot slot : ctx.slots(node)) {
            switch (slot.field()) {
                case "body" -> visitBlockOrStatement(ctx, slot);
                case NONE -> {
                    switch (type(slot.node())) {
                        case "by_ref" -> visitByRef(ctx, slot.node());
                        case "list_literal" -> visitListLiteral(ctx, slot.node());
                        case "pair" -> visitPair(ctx, slot.node());
                        default -> visitExpression(ctx, slot.node());
                    }
                }
                default -> ctx.unexpected(node, slot);
            }
        }
        ctx.exit(node);
    }

    private void visitFormalParameters(Ctx ctx, Node node) {
        ctx.enter(node);
        for (Slot slot : ctx.slots(node)) {
            if (!NONE.equals(slot.field())) {
                ctx.unexpected(node, slot);
                continue;
            }
            switch (type(slot.node())) {
                case "property_promotion_parameter" -> visitPropertyPromotionParameter(ctx, slot.node());
                case "simple_parameter" -> visitSimpleParameter(ctx, slot.node());
                case "variadic_parameter" -> visitVariadicParameter(ctx, slot.node());
                default -> ctx.unexpected(node, slot);
            }
        }
        ctx.exit(node);
    }

    private void visitFunctionCallExpression(Ctx ctx, Node node) {
        ctx.enter(node);
        for (Slot slot : ctx.slots(node)) {
            switch (slot.field()) {
                case "arguments" -> visitArguments(ctx, slot.node());
                case "function" -> visitPrimaryExpression(ctx, slot.node());
                default -> ctx.unexpected(node, slot);
            }
        }
        ctx.exit(node);
    }

    private void visitFunctionDefinition(Ctx ctx, Node node) {
        ctx.enter(node);
        for (Slot slot : ctx.slots(node)) {
            switch (slot.field()) {
                case "attributes" -> visitAttributeList(ctx, slot.node());
                case "body" -> visitCompoundStatement(ctx, slot.node());
                case "name" -> visitName(ctx, slot.node());
                case "parameters" -> visitFormalParameters(ctx, slot.node());
                case "return_type" -> visitReturnType(ctx, slot);
                case NONE -> {
                    switch (type(slot.node())) {
                        case "reference_modifier" -> visitReferenceModifier(ctx, slot.node());
                        default -> ctx.unexpected(node, slot);
                    }
                }
                default -> ctx.unexpected(node, slot);
            }
        }
        ctx.exit(node);
    }

    private void visitFunctionStaticDeclaration(Ctx ctx, Node node) {
        ctx.enter(node);
        for (Slot slot : ctx.slots(node)) {
            if (!NONE.equals(slot.field())) {
                ctx.unexpected(node, slot);
                continue;
            }
            switch (type(slot.node())) {
                case "static_variable_declaration" -> visitStaticVariableDeclaration(ctx, slot.node());
                default -> ctx.unexpected(node, slot);
            }
        }
        ctx.exit(node);
    }

    private void visitGlobalDeclaration(Ctx ctx, Node node) {
        ctx.enter(node);
        for (Slot slot : ctx.slots(node)) {
            if (!NONE.equals(slot.field())) {
                ctx.unexpected(node, slot);
                continue;
            }
            switch (type(slot.node())) {
                case "dynamic_variable_name" -> visitDynamicVariableName(ctx, slot.node());
                case "variable_name" -> visitVariableName(ctx, slot.node());
                default -> ctx.unexpected(node, slot);
            }
        }
        ctx.exit(node);
    }

    private void visitGotoStatement(Ctx ctx, Node node) {
        ctx.enter(node);
        for (Slot slot : ctx.slots(node)) {
            if (!NONE.equals(slot.field())) {
                ctx.unexpected(node, slot);
                continue;
            }
            visitName(ctx, slot.node());
        }
        ctx.exit(node);
    }

    private void visitHeredoc(Ctx ctx, Node node) {
        ctx.enter(node);
        for (Slot slot : ctx.slots(node)) {
            switch (slot.field()) {
                case "end_tag" -> visitHeredocEnd(ctx, slot.node());
                case "identifier" -> visitHeredocStart(ctx, slot.node());
                case "value" -> visitHeredocBody(ctx, slot.node());
                default -> ctx.unexpected(node, slot);
            }
        }
        ctx.exit(node);
    }

    private void visitHeredocBody(Ctx ctx, Node node) {
        ctx.enter(node);
        for (Slot slot : ctx.slots(node)) {
            if (!NONE.equals(slot.field())) {
                ctx.unexpected(node, slot);
                continue;
            }
            visitInterpolated(ctx, slot);
        }
        ctx.exit(node);
    }

    private void visitHeredocEnd(Ctx ctx, Node node) {
        ctx.leaf(node);
    }

    private void visitHeredocStart(Ctx ctx, Node node) {
        ctx.leaf(node);
    }

    private void visitIfStatement(Ctx ctx, Node node) {
        ctx.enter(node);
        for (Slot slot : ctx.slots(node)) {
            switch (slot.field()) {
                case "alternative" -> {
                    switch (type(slot.node())) {
                        case "else_clause" -> visitElseClause(ctx, slot.node());
                        case "else_if_clause" -> visitElseIfClause(ctx, slot.node());
                        default -> ctx.unexpected(node, slot);
                    }
                }
                case "body" -> visitBlockOrStatement(ctx, slot);
                case "condition" -> visitParenthesizedExpression(ctx, slot.node());
                default -> ctx.unexpected(node, slot);
            }
        }
        ctx.exit(node);
    }

    private void visitIncludeExpression(Ctx ctx, Node node) {
        ctx.enter(node);
        for (Slot slot : ctx.slots(node)) {
            if (!NONE.equals(slot.field())) {
                ctx.unexpected(node, slot);
                continue;
            }
            visitExpression(ctx, slot.node());
        }
        ctx.exit(node);
    }

    private void visitIncludeOnceExpression(Ctx ctx, Node node) {
        ctx.enter(node);
        for (Slot slot : ctx.slots(node)) {
            if (!NONE.equals(slot.field())) {
                ctx.unexpected(node, slot);
                continue;
            }
            visitExpression(ctx, slot.node());
        }
        ctx.exit(node);
    }

    private void visitInteger(Ctx ctx, Node node) {
        ctx.leaf(node);
    }

    private void visitInterfaceDeclaration(Ctx ctx, Node node) {
        ctx.enter(node);
        for (Slot slot : ctx.slots(node)) {
            switch (slot.field()) {
                case "attributes" -> visitAttributeList(ctx, slot.node());
                case "body" -> visitDeclarationList(ctx, slot.node());
                case "name" -> visitName(ctx, slot.node());
                case NONE -> {
                    switch (type(slot.node())) {
                        case "base_clause" -> visitBaseClause(ctx, slot.node());
                        default -> ctx.unexpected(node, slot);
                    }
                }
                default -> ctx.unexpected(node, slot);
            }
        }
        ctx.exit(node);
    }

    private void visitIntersectionType(Ctx ctx, Node node) {
        ctx.enter(node);
        for (Slot slot : ctx.slots(node)) {
            if (!NONE.equals(slot.field())) {
                ctx.unexpected(node, slot);
                continue;
            }
            switch (type(slot.node())) {
                case "named_type" -> visitNamedType(ctx, slot.node());
                case "optional_type" -> visitOptionalType(ctx, slot.node());
                case "primitive_type" -> visitPrimitiveType(ctx, slot.node());
                default -> ctx.unexpected(node, slot);
            }
        }
        ctx.exit(node);
    }

    private void visitListLiteral(Ctx ctx, Node node) {
        ctx.enter(node);
        for (Slot slot : ctx.slots(node)) {
            if (!NONE.equals(slot.field())) {
                ctx.unexpected(node, slot);
                continue;
            }
            switch (type(slot.node())) {
                case "by_ref" -> visitByRef(ctx, slot.node());
                case "list_literal" -> visitListLiteral(ctx, slot.node());
                default -> visitExpression(ctx, slot.node());
            }
        }
        ctx.exit(node);
    }

    private void visitMatchBlock(Ctx ctx, Node node) {
        ctx.enter(node);
        for (Slot slot : ctx.slots(node)) {
            if (!NONE.equals(slot.field())) {
                ctx.unexpected(node, slot);
                continue;
            }
            switch (type(slot.node())) {
                case "match_conditional_expression" -> visitMatchConditionalExpression(ctx, slot.node());
                case "match_default_expression" -> visitMatchDefaultExpression(ctx, slot.node());
                default -> ctx.unexpected(node, slot);
            }
        }
        ctx.exit(node);
    }

    private void visitMatchConditionList(Ctx ctx, Node node) {
        ctx.enter(node);
        for (Slot slot : ctx.slots(node)) {
            if (!NONE.equals(slot.field())) {
                ctx.unexpected(node, slot);
                continue;
            }
            visitExpression(ctx, slot.node());
        }
        ctx.exit(node);
    }

    private void visitMatchConditionalExpression(Ctx ctx, Node node) {
        ctx.enter(node);
        for (Slot slot : ctx.slots(node)) {
            switch (slot.field()) {
                case "conditional_expressions" -> visitMatchConditionList(ctx, slot.node());
                case "return_expression" -> visitExpression(ctx, slot.node());
                default -> ctx.unexpected(node, slot);
            }
        }
        ctx.exit(node);
    }

    private void visitMatchDefaultExpression(Ctx ctx, Node node) {
        ctx.enter(node);
        for (Slot slot : ctx.slots(node)) {
            switch (slot.field()) {
                case "return_expression" -> visitExpression(ctx, slot.node());
                default -> ctx.unexpected(node, slot);
            }
        }
        ctx.exit(node);
    }

    private void visitMatchExpression(Ctx ctx, Node node) {
        ctx.enter(node);
        for (Slot slot : ctx.slots(node)) {
            switch (slot.field()) {
                case "body" -> visitMatchBlock(ctx, slot.node());
                case "condition" -> visitParenthesizedExpression(ctx, slot.node());
                default -> ctx.unexpected(node, slot);
            }
        }
        ctx.exit(node);
    }

    private void visitMemberAccessExpression(Ctx ctx, Node node) {
        ctx.enter(node);
        for (Slot slot : ctx.slots(node)) {
            switch (slot.field()) {
                case "name" -> visitMemberName(ctx, slot);
                case "object" -> visitPrimaryExpression(ctx, slot.node());
                default -> ctx.unexpected(node, slot);
            }
        }
        ctx.exit(node);
    }

    private void visitMemberCallExpression(Ctx ctx, Node node) {
        ctx.enter(node);
        for (Slot slot : ctx.slots(node)) {
            switch (slot.field()) {
                case "arguments" -> visitArguments(ctx, slot.node());
                case "name" -> visitMemberName(ctx, slot);
                case "object" -> visitPrimaryExpression(ctx, slot.node());
                default -> ctx.unexpected(node, slot);
            }
        }
        ctx.exit(node);
    }

    private void visitMethodDeclaration(Ctx ctx, Node node) {
        ctx.enter(node);
        for (Slot slot : ctx.slots(node)) {
            switch (slot.field()) {
                case "attributes" -> visitAttributeList(ctx, slot.node());
                case "body" -> visitCompoundStatement(ctx, slot.node());
                case "name" -> visitName(ctx, slot.node());
                case "parameters" -> visitFormalParameters(ctx, slot.node());
                case "return_type" -> visitReturnType(ctx, slot);
                case NONE -> {
                    switch (type(slot.node())) {
                        case "abstract_modifier" -> visitAbstractModifier(ctx, slot.node());
                        case "final_modifier" -> visitFinalModifier(ctx, slot.node());
                        case "readonly_modifier" -> visitReadonlyModifier(ctx, slot.node());
                        case "reference_modifier" -> visitReferenceModifier(ctx, slot.node());
                        case "static_modifier" -> visitStaticModifier(ctx, slot.node());
                        case "var_modifier" -> visitVarModifier(ctx, slot.node());
                        case "visibility_modifier" -> visitVisibilityModifier(ctx, slot.node());
                        default -> ctx.unexpected(node, slot);
                    }
                }
                default -> ctx.unexpected(node, slot);
            }
        }
        ctx.exit(node);
    }

    private void visitName(Ctx ctx, Node node) {
        ctx.leaf(node);
    }

    private void visitNamedLabelStatement(Ctx ctx, Node node) {
        ctx.enter(node);
        for (Slot slot : ctx.slots(node)) {
            if (!NONE.equals(slot.field())) {
                ctx.unexpected(node, slot);
                continue;
            }
            visitName(ctx, slot.node());
        }
        ctx.exit(node);
    }

    private void visitNamedType(Ctx ctx, Node node) {
        ctx.enter(node);
        for (Slot slot : ctx.slots(node)) {
            if (!NONE.equals(slot.field())) {
                ctx.unexpected(node, slot);
                continue;
            }
            visitClassName(ctx, node, slot);
        }
        ctx.exit(node);
    }

    private void visitNamespaceDefinition(Ctx ctx, Node node) {
        ctx.enter(node);
        for (Slot slot : ctx.slots(node)) {
            switch (slot.field()) {
                case "body" -> visitCompoundStatement(ctx, slot.node());
                case "name" -> visitNamespaceName(ctx, slot.node());
                default -> ctx.unexpected(node, slot);
            }
        }
        ctx.exit(node);
    }

    private void visitNamespaceName(Ctx ctx, Node node) {
        ctx.enter(node);
        for (Slot slot : ctx.slots(node)) {
            if (!NONE.equals(slot.field())) {
                ctx.unexpected(node, slot);
                continue;
            }
            visitName(ctx, slot.node());
        }
        ctx.exit(node);
    }

    private void visitNamespaceUseClause(Ctx ctx, Node node) {
        ctx.enter(node);
        for (Slot slot : ctx.slots(node)) {
            switch (slot.field()) {
                case "alias" -> visitName(ctx, slot.node());
                case "type" -> ctx.token(slot);
                case NONE -> {
                    switch (type(slot.node())) {
                        case "name" -> visitName(ctx, slot.node());
                        case "qualified_name" -> visitQualifiedName(ctx, slot.node());
                        default -> ctx.unexpected(node, slot);
                    }
                }
                default -> ctx.unexpected(node, slot);
            }
        }
        ctx.exit(node);
    }

    private void visitNamespaceUseDeclaration(Ctx ctx, Node node) {
        ctx.enter(node);
        for (Slot slot : ctx.slots(node)) {
            switch (slot.field()) {
                case "body" -> visitNamespaceUseGroup(ctx, slot.node());
                case "type" -> ctx.token(slot);
                case NONE -> {
                    switch (type(slot.node())) {
                        case "namespace_name" -> visitNamespaceName(ctx, slot.node());
                        case "namespace_use_clause" -> visitNamespaceUseClause(ctx, slot.node());
                        default -> ctx.unexpected(node, slot);
                    }
                }
                default -> ctx.unexpected(node, slot);
            }
        }
        ctx.exit(node);
    }

    private void visitNamespaceUseGroup(Ctx ctx, Node node) {
        ctx.enter(node);
        for (Slot slot : ctx.slots(node)) {
            if (!NONE.equals(slot.field())) {
                ctx.unexpected(node, slot);
                continue;
            }
            switch (type(slot.node())) {
                case "namespace_use_clause" -> visitNamespaceUseClause(ctx, slot.node());
                default -> ctx.unexpected(node, slot);
            }
        }
        ctx.exit(node);
    }

    private void visitNowdoc(Ctx ctx, Node node) {
        ctx.enter(node);
        for (Slot slot : ctx.slots(node)) {
            switch (slot.field()) {
                case "end_tag" -> visitHeredocEnd(ctx, slot.node());
                case "identifier" -> visitHeredocStart(ctx, slot.node());
                case "value" -> visitNowdocBody(ctx, slot.node());
                default -> ctx.unexpected(node, slot);
            }
        }
        ctx.exit(node);
    }

    private void visitNowdocBody(Ctx ctx, Node node) {
        ctx.enter(node);
        for (Slot slot : ctx.slots(node)) {
            if (!NONE.equals(slot.field())) {
                ctx.unexpected(node, slot);
                continue;
            }
            switch (type(slot.node())) {
                case "nowdoc_string" -> visitNowdocString(ctx, slot.node());
                default -> ctx.unexpected(node, slot);
            }
        }
        ctx.exit(node);
    }

    private void visitNowdocString(Ctx ctx, Node node) {
        ctx.leaf(node);
    }

    private void visitNull(Ctx ctx, Node node) {
        ctx.leaf(node);
    }

    private void visitNullsafeMemberAccessExpression(Ctx ctx, Node node) {
        ctx.enter(node);
        for (Slot slot : ctx.slots(node)) {
            switch (slot.field()) {
                case "name" -> visitMemberName(ctx, slot);
                case "object" -> visitPrimaryExpression(ctx, slot.node());
                default -> ctx.unexpected(node, slot);
            }
        }
        ctx.exit(node);
    }

    private void visitNullsafeMemberCallExpression(Ctx ctx, Node node) {
        ctx.enter(node);
        for (Slot slot : ctx.slots(node)) {
            switch (slot.field()) {
                case "arguments" -> visitArguments(ctx, slot.node());
                case "name" -> visitMemberName(ctx, slot);
                case "object" -> visitPrimaryExpression(ctx, slot.node());
                default -> ctx.unexpected(node, slot);
            }
        }
        ctx.exit(node);
    }

    private void visitObjectCreationExpression(Ctx ctx, Node node) {
        ctx.enter(node);
        for (Slot slot : ctx.slots(node)) {
            if (!NONE.equals(slot.field())) {
                ctx.unexpected(node, slot);
                continue;
            }
            switch (type(slot.node())) {
                case "anonymous_class" -> visitAnonymousClass(ctx, slot.node());
                case "arguments" -> visitArguments(ctx, slot.node());
                default -> visitPrimaryExpression(ctx, slot.node());
            }
        }
        ctx.exit(node);
    }

    private void visitOperation(Ctx ctx, Node node) {
        ctx.leaf(node);
    }

    private void visitOptionalType(Ctx ctx, Node node) {
        ctx.enter(node);
        for (Slot slot : ctx.slots(node)) {
            if (!NONE.equals(slot.field())) {
                ctx.unexpected(node, slot);
                continue;
            }
            switch (type(slot.node())) {
                case "named_type" -> visitNamedType(ctx, slot.node());
                case "primitive_type" -> visitPrimitiveType(ctx, slot.node());
                default -> ctx.unexpected(node, slot);
            }
        }
        ctx.exit(node);
    }

    private void visitPair(Ctx ctx, Node node) {
        ctx.enter(node);
        for (Slot slot : ctx.slots(node)) {
            if (!NONE.equals(slot.field())) {
                ctx.unexpected(node, slot);
                continue;
            }
            switch (type(slot.node())) {
                case "by_ref" -> visitByRef(ctx, slot.node());
                case "list_literal" -> visitListLiteral(ctx, slot.node());
                default -> visitExpression(ctx, slot.node());
            }
        }
        ctx.exit(node);
    }

    private void visitParenthesizedExpression(Ctx ctx, Node node) {
        ctx.enter(node);
        for (Slot slot : ctx.slots(node)) {
            if (!NONE.equals(slot.field())) {
                ctx.unexpected(node, slot);
                continue;
            }
            visitExpression(ctx, slot.node());
        }
        ctx.exit(node);
    }

    private void visitPhpEndTag(Ctx ctx, Node node) {
        ctx.leaf(node);
    }

    private void visitPhpTag(Ctx ctx, Node node) {
        ctx.leaf(node);
    }

    private void visitPrimitiveType(Ctx ctx, Node node) {
        ctx.leaf(node);
    }

    private void visitPrintIntrinsic(Ctx ctx, Node node) {
        ctx.enter(node);
        for (Slot slot : ctx.slots(node)) {
            if (!NONE.equals(slot.field())) {
                ctx.unexpected(node, slot);
                continue;
            }
            visitExpression(ctx, slot.node());
        }
        ctx.exit(node);
    }

    private void visitProgram(Ctx ctx, Node node) {
        ctx.enter(node);
        for (Slot slot : ctx.slots(node)) {
            if (!NONE.equals(slot.field())) {
                ctx.unexpected(node, slot);
                continue;
            }
            switch (type(slot.node())) {
                case "php_end_tag" -> visitPhpEndTag(ctx, slot.node());
                case "php_tag" -> visitPhpTag(ctx, slot.node());
                default -> visitStatement(ctx, slot.node());
            }
        }
        ctx.exit(node);
    }

    private void visitPropertyDeclaration(Ctx ctx, Node node) {
        ctx.enter(node);
        for (Slot slot : ctx.slots(node)) {
            switch (slot.field()) {
                case "attributes" -> visitAttributeList(ctx, slot.node());
                case "type" -> visitType(ctx, slot.node());
                case NONE -> {
                    switch (type(slot.node())) {
                        case "abstract_modifier" -> visitAbstractModifier(ctx, slot.node());
                        case "final_modifier" -> visitFinalModifier(ctx, slot.node());
                        case "property_element" -> visitPropertyElement(ctx, slot.node());
                        case "property_hook_list" -> visitPropertyHookList(ctx, slot.node());
                        case "readonly_modifier" -> visitReadonlyModifier(ctx, slot.node());
                        case "static_modifier" -> visitStaticModifier(ctx, slot.node());
                        case "var_modifier" -> visitVarModifier(ctx, slot.node());
                        case "visibility_modifier" -> visitVisibilityModifier(ctx, slot.node());
                        default -> ctx.unexpected(node, slot);
                    }
                }
                default -> ctx.unexpected(node, slot);
            }
        }
        ctx.exit(node);
    }

    private void visitPropertyElement(Ctx ctx, Node node) {
        ctx.enter(node);
        for (Slot slot : ctx.slots(node)) {
            switch (slot.field()) {
                case "default_value" -> visitExpression(ctx, slot.node());
                case "name" -> visitVariableName(ctx, slot.node());
                default -> ctx.unexpected(node, slot);
            }
        }
        ctx.exit(node);
    }

    private void visitPropertyHook(Ctx ctx, Node node) {
        ctx.enter(node);
        for (Slot slot : ctx.slots(node)) {
            switch (slot.field()) {
                case "attributes" -> visitAttributeList(ctx, slot.node());
                case "body" -> {
                    switch (type(slot.node())) {
                        case "compound_statement" -> visitCompoundStatement(ctx, slot.node());
                        default -> visitExpression(ctx, slot.node());
                    }
                }
                case "final" -> visitFinalModifier(ctx, slot.node());
                case "parameters" -> visitFormalParameters(ctx, slot.node());
                case "reference_modifier" -> visitReferenceModifier(ctx, slot.node());
                case NONE -> visitName(ctx, slot.node());
                default -> ctx.unexpected(node, slot);
            }
        }
        ctx.exit(node);
    }

    private void visitPropertyHookList(Ctx ctx, Node node) {
        ctx.enter(node);
        for (Slot slot : ctx.slots(node)) {
            if (!NONE.equals(slot.field())) {
                ctx.unexpected(node, slot);
                continue;
            }
            switch (type(slot.node())) {
                case "property_hook" -> visitPropertyHook(ctx, slot.node());
                default -> ctx.unexpected(node, slot);
            }
        }
        ctx.exit(node);
    }

    private void visitPropertyPromotionParameter(Ctx ctx, Node node) {
        ctx.enter(node);
        for (Slot slot : ctx.slots(node)) {
            switch (slot.field()) {
                case "attributes" -> visitAttributeList(ctx, slot.node());
                case "default_value" -> visitExpression(ctx, slot.node());
                case "name" -> {
                    switch (type(slot.node())) {
                        case "by_ref" -> visitByRef(ctx, slot.node());
                        case "variable_name" -> visitVariableName(ctx, slot.node());
                        default -> ctx.unexpected(node, slot);
                    }
                }
                case "readonly" -> visitReadonlyModifier(ctx, slot.node());
                case "type" -> visitType(ctx, slot.node());
                case "visibility" -> visitVisibilityModifier(ctx, slot.node());
                case NONE -> {
                    switch (type(slot.node())) {
                        case "property_hook_list" -> visitPropertyHookList(ctx, slot.node());
                        default -> ctx.unexpected(node, slot);
                    }
                }
                default -> ctx.unexpected(node, slot);
            }
        }
        ctx.exit(node);
    }

    private void visitQualifiedName(Ctx ctx, Node node) {
        ctx.enter(node);
        for (Slot slot : ctx.slots(node)) {
            switch (slot.field()) {
                case "prefix" -> {
                    switch (type(slot.node())) {
                        case "namespace_name" -> visitNamespaceName(ctx, slot.node());
                        default -> ctx.token(slot);
                    }
                }
                case NONE -> visitName(ctx, slot.node());
                default -> ctx.unexpected(node, slot);
            }
        }
        ctx.exit(node);
    }

    private void visitReadonlyModifier(Ctx ctx, Node node) {
        ctx.leaf(node);
    }

    private void visitReferenceAssignmentExpression(Ctx ctx, Node node) {
        ctx.enter(node);
        for (Slot slot : ctx.slots(node)) {
            switch (slot.field()) {
                case "left" -> visitAssignmentTarget(ctx, slot);
                case "right" -> visitExpression(ctx, slot.node());
                default -> ctx.unexpected(node, slot);
            }
        }
        ctx.exit(node);
    }

    private void visitReferenceModifier(Ctx ctx, Node node) {
        ctx.leaf(node);
    }

    private void visitRelativeName(Ctx ctx, Node node) {
        ctx.enter(node);
        for (Slot slot : ctx.slots(node)) {
            switch (slot.field()) {
                case "prefix" -> {
                    switch (type(slot.node())) {
                        case "namespace_name" -> visitNamespaceName(ctx, slot.node());
                        default -> ctx.token(slot);
                    }
                }
                case NONE -> visitName(ctx, slot.node());
                default -> ctx.unexpected(node, slot);
            }
        }
        ctx.exit(node);
    }

    private void visitRelativeScope(Ctx ctx, Node node) {
        ctx.leaf(node);
    }

    private void visitRequireExpression(Ctx ctx, Node node) {
        ctx.enter(node);
        for (Slot slot : ctx.slots(node)) {
            if (!NONE.equals(slot.field())) {
                ctx.unexpected(node, slot);
                continue;
            }
            visitExpression(ctx, slot.node());
        }
        ctx.exit(node);
    }

    private void visitRequireOnceExpression(Ctx ctx, Node node) {
        ctx.enter(node);
        for (Slot slot : ctx.slots(node)) {
            if (!NONE.equals(slot.field())) {
                ctx.unexpected(node, slot);
                continue;
            }
            visitExpression(ctx, slot.node());
        }
        ctx.exit(node);
    }

    private void visitReturnStatement(Ctx ctx, Node node) {
        ctx.enter(node);
        for (Slot slot : ctx.slots(node)) {
            if (!NONE.equals(slot.field())) {
                ctx.unexpected(node, slot);
                continue;
            }
            visitExpression(ctx, slot.node());
        }
        ctx.exit(node);
    }

    private void visitScopedCallExpression(Ctx ctx, Node node) {
        ctx.enter(node);
        for (Slot slot : ctx.slots(node)) {
            switch (slot.field()) {
                case "arguments" -> visitArguments(ctx, slot.node());
                case "name" -> visitMemberName(ctx, slot);
                case "scope" -> visitScope(ctx, slot);
                default -> ctx.unexpected(node, slot);
            }
        }
        ctx.exit(node);
    }

    private void visitScopedPropertyAccessExpression(Ctx ctx, Node node) {
        ctx.enter(node);
        for (Slot slot : ctx.slots(node)) {
            switch (slot.field()) {
                case "name" -> {
                    switch (type(slot.node())) {
                        case "dynamic_variable_name" -> visitDynamicVariableName(ctx, slot.node());
                        case "variable_name" -> visitVariableName(ctx, slot.node());
                        default -> ctx.unexpected(node, slot);
                    }
                }
                case "scope" -> visitScope(ctx, slot);
                default -> ctx.unexpected(node, slot);
            }
        }
        ctx.exit(node);
    }

    private void visitSequenceExpression(Ctx ctx, Node node) {
        ctx.enter(node);
        for (Slot slot : ctx.slots(node)) {
            if (!NONE.equals(slot.field())) {
                ctx.unexpected(node, slot);
                continue;
            }
            switch (type(slot.node())) {
                case "sequence_expression" -> visitSequenceExpression(ctx, slot.node());
                default -> visitExpression(ctx, slot.node());
            }
        }
        ctx.exit(node);
    }

    private void visitShellCommandExpression(Ctx ctx, Node node) {
        ctx.enter(node);
        for (Slot slot : ctx.slots(node)) {
            if (!NONE.equals(slot.field())) {
                ctx.unexpected(node, slot);
                continue;
            }
            visitInterpolated(ctx, slot);
        }
        ctx.exit(node);
    }

    private void visitSimpleParameter(Ctx ctx, Node node) {
        ctx.enter(node);
        for (Slot slot : ctx.slots(node)) {
            switch (slot.field()) {
                case "attributes" -> visitAttributeList(ctx, slot.node());
                case "default_value" -> visitExpression(ctx, slot.node());
                case "name" -> visitVariableName(ctx, slot.node());
                case "reference_modifier" -> visitReferenceModifier(ctx, slot.node());
                case "type" -> visitType(ctx, slot.node());
                default -> ctx.unexpected(node, slot);
            }
        }
        ctx.exit(node);
    }

    private void visitStaticModifier(Ctx ctx, Node node) {
        ctx.leaf(node);
    }

    private void visitStaticVariableDeclaration(Ctx ctx, Node node) {
        ctx.enter(node);
        for (Slot slot : ctx.slots(node)) {
            switch (slot.field()) {
                case "name" -> visitVariableName(ctx, slot.node());
                case "value" -> visitExpression(ctx, slot.node());
                default -> ctx.unexpected(node, slot);
            }
        }
        ctx.exit(node);
    }

    private void visitString(Ctx ctx, Node node) {
        ctx.enter(node);
        for (Slot slot : ctx.slots(node)) {
            if (!NONE.equals(slot.field())) {
                ctx.unexpected(node, slot);
                continue;
            }
            switch (type(slot.node())) {
                case "escape_sequence" -> visitEscapeSequence(ctx, slot.node());
                case "string_content" -> visitStringContent(ctx, slot.node());
                default -> ctx.unexpected(node, slot);
            }
        }
        ctx.exit(node);
    }

    private void visitStringContent(Ctx ctx, Node node) {
        ctx.leaf(node);
    }

    private void visitSubscriptExpression(Ctx ctx, Node node) {
        ctx.enter(node);
        for (Slot slot : ctx.slots(node)) {
            if (!NONE.equals(slot.field())) {
                ctx.unexpected(node, slot);
                continue;
            }
            visitExpression(ctx, slot.node());
        }
        ctx.exit(node);
    }

    private void visitSwitchBlock(Ctx ctx, Node node) {
        ctx.enter(node);
        for (Slot slot : ctx.slots(node)) {
            if (!NONE.equals(slot.field())) {
                ctx.unexpected(node, slot);
                continue;
            }
            switch (type(slot.node())) {
                case "case_statement" -> visitCaseStatement(ctx, slot.node());
                case "default_statement" -> visitDefaultStatement(ctx, slot.node());
                default -> ctx.unexpected(node, slot);
            }
        }
        ctx.exit(node);
    }

    private void visitSwitchStatement(Ctx ctx, Node node) {
        ctx.enter(node);
        for (Slot slot : ctx.slots(node)) {
            switch (slot.field()) {
                case "body" -> visitSwitchBlock(ctx, slot.node());
                case "condition" -> visitParenthesizedExpression(ctx, slot.node());
                default -> ctx.unexpected(node, slot);
            }
        }
        ctx.exit(node);
    }

    private void visitThrowExpression(Ctx ctx, Node node) {
        ctx.enter(node);
        for (Slot slot : ctx.slots(node)) {
            if (!NONE.equals(slot.field())) {
                ctx.unexpected(node, slot);
                continue;
            }
            visitExpression(ctx, slot.node());
        }
        ctx.exit(node);
    }

    private void visitTraitDeclaration(Ctx ctx, Node node) {
        ctx.enter(node);
        for (Slot slot : ctx.slots(node)) {
            switch (slot.field()) {
                case "attributes" -> visitAttributeList(ctx, slot.node());
                case "body" -> visitDeclarationList(ctx, slot.node());
                case "name" -> visitName(ctx, slot.node());
                default -> ctx.unexpected(node, slot);
            }
        }
        ctx.exit(node);
    }

    private void visitTryStatement(Ctx ctx, Node node) {
        ctx.enter(node);
        for (Slot slot : ctx.slots(node)) {
            switch (slot.field()) {
                case "body" -> visitCompoundStatement(ctx, slot.node());
                case NONE -> {
                    switch (type(slot.node())) {
                        case "catch_clause" -> visitCatchClause(ctx, slot.node());
                        case "finally_clause" -> visitFinallyClause(ctx, slot.node());
                        default -> ctx.unexpected(node, slot);
                    }
                }
                default -> ctx.unexpected(node, slot);
            }
        }
        ctx.exit(node);
    }

    private void visitTypeList(Ctx ctx, Node node) {
        ctx.enter(node);
        for (Slot slot : ctx.slots(node)) {
            if (!NONE.equals(slot.field())) {
                ctx.unexpected(node, slot);
                continue;
            }
            switch (type(slot.node())) {
                case "named_type" -> visitNamedType(ctx, slot.node());
                default -> ctx.unexpected(node, slot);
            }
        }
        ctx.exit(node);
    }

    private void visitUnaryOpExpression(Ctx ctx, Node node) {
        ctx.enter(node);
        for (Slot slot : ctx.slots(node)) {
            switch (slot.field()) {
                case "argument" -> visitExpression(ctx, slot.node());
                case "operator" -> ctx.token(slot);
                case NONE -> {
                    switch (type(slot.node())) {
                        case "integer" -> visitInteger(ctx, slot.node());
                        default -> ctx.unexpected(node, slot);
                    }
                }
                default -> ctx.unexpected(node, slot);
            }
        }
        ctx.exit(node);
    }

    private void visitUnionType(Ctx ctx, Node node) {
        ctx.enter(node);
        for (Slot slot : ctx.slots(node)) {
            if (!NONE.equals(slot.field())) {
                ctx.unexpected(node, slot);
                continue;
            }
            switch (type(slot.node())) {
                case "named_type" -> visitNamedType(ctx, slot.node());
                case "optional_type" -> visitOptionalType(ctx, slot.node());
                case "primitive_type" -> visitPrimitiveType(ctx, slot.node());
                default -> ctx.unexpected(node, slot);
            }
        }
        ctx.exit(node);
    }

    private void visitUnsetStatement(Ctx ctx, Node node) {
        ctx.enter(node);
        for (Slot slot : ctx.slots(node)) {
            if (!NONE.equals(slot.field())) {
                ctx.unexpected(node, slot);
                continue;
            }
            visitVariableTarget(ctx, slot);
        }
        ctx.exit(node);
    }

    private void visitUpdateExpression(Ctx ctx, Node node) {
        ctx.enter(node);
        for (Slot slot : ctx.slots(node)) {
            switch (slot.field()) {
                case "argument" -> visitVariableTarget(ctx, slot);
                case "operator" -> ctx.token(slot);
                default -> ctx.unexpected(node, slot);
            }
        }
        ctx.exit(node);
    }

    private void visitUseAsClause(Ctx ctx, Node node) {
        ctx.enter(node);
        for (Slot slot : ctx.slots(node)) {
            if (!NONE.equals(slot.field())) {
                ctx.unexpected(node, slot);
                continue;
            }
            switch (type(slot.node())) {
                case "class_constant_access_expression" -> visitClassConstantAccessExpression(ctx, slot.node());
                case "name" -> visitName(ctx, slot.node());
                case "visibility_modifier" -> visitVisibilityModifier(ctx, slot.node());
                default -> ctx.unexpected(node, slot);
            }
        }
        ctx.exit(node);
    }

    private void visitUseDeclaration(Ctx ctx, Node node) {
        ctx.enter(node);
        for (Slot slot : ctx.slots(node)) {
            if (!NONE.equals(slot.field())) {
                ctx.unexpected(node, slot);
                continue;
            }
            switch (type(slot.node())) {
                case "use_list" -> visitUseList(ctx, slot.node());
                default -> visitClassName(ctx, node, slot);
            }
        }
        ctx.exit(node);
    }

    private void visitUseInsteadOfClause(Ctx ctx, Node node) {
        ctx.enter(node);
        for (Slot slot : ctx.slots(node)) {
            if (!NONE.equals(slot.field())) {
                ctx.unexpected(node, slot);
                continue;
            }
            switch (type(slot.node())) {
                case "class_constant_access_expression" -> visitClassConstantAccessExpression(ctx, slot.node());
                case "name" -> visitName(ctx, slot.node());
                default -> ctx.unexpected(node, slot);
            }
        }
        ctx.exit(node);
    }

    private void visitUseList(Ctx ctx, Node node) {
        ctx.enter(node);
        for (Slot slot : ctx.slots(node)) {
            if (!NONE.equals(slot.field())) {
                ctx.unexpected(node, slot);
                continue;
            }
            switch (type(slot.node())) {
                case "use_as_clause" -> visitUseAsClause(ctx, slot.node());
                case "use_instead_of_clause" -> visitUseInsteadOfClause(ctx, slot.node());
                default -> ctx.unexpected(node, slot);
            }
        }
        ctx.exit(node);
    }

    private void visitVarModifier(Ctx ctx, Node node) {
        ctx.leaf(node);
    }

    private void visitVariableName(Ctx ctx, Node node) {
        ctx.enter(node);
        for (Slot slot : ctx.slots(node)) {
            if (!NONE.equals(slot.field())) {
                ctx.unexpected(node, slot);
                continue;
            }
            visitName(ctx, slot.node());
        }
        ctx.exit(node);
    }

    private void visitVariadicParameter(Ctx ctx, Node node) {
        ctx.enter(node);
        for (Slot slot : ctx.slots(node)) {
            switch (slot.field()) {
                case "attributes" -> visitAttributeList(ctx, slot.node());
                case "name" -> visitVariableName(ctx, slot.node());
                case "reference_modifier" -> visitReferenceModifier(ctx, slot.node());
                case "type" -> visitType(ctx, slot.node());
                default -> ctx.unexpected(node, slot);
            }
        }
        ctx.exit(node);
    }

    private void visitVariadicPlaceholder(Ctx ctx, Node node) {
        ctx.leaf(node);
    }

    private void visitVariadicUnpacking(Ctx ctx, Node node) {
        ctx.enter(node);
        for (Slot slot : ctx.slots(node)) {
            if (!NONE.equals(slot.field())) {
                ctx.unexpected(node, slot);
                continue;
            }
            visitExpression(ctx, slot.node());
        }
        ctx.exit(node);
    }

    private void visitVisibilityModifier(Ctx ctx, Node node) {
        ctx.enter(node);
        for (Slot slot : ctx.slots(node)) {
            if (!NONE.equals(slot.field())) {
                ctx.unexpected(node, slot);
                continue;
            }
            switch (type(slot.node())) {
                case "operation" -> visitOperation(ctx, slot.node());
                default -> ctx.unexpected(node, slot);
            }
        }
        ctx.exit(node);
    }

    private void visitWhileStatement(Ctx ctx, Node node) {
        ctx.enter(node);
        for (Slot slot : ctx.slots(node)) {
            switch (slot.field()) {
                case "body" -> visitBlockOrStatement(ctx, slot);
                case "condition" -> visitParenthesizedExpression(ctx, slot.node());
                default -> ctx.unexpected(node, slot);
            }
        }
        ctx.exit(node);
    }

    private void visitYieldExpression(Ctx ctx, Node node) {
        ctx.enter(node);
        for (Slot slot : ctx.slots(node)) {
            if (!NONE.equals(slot.field())) {
                ctx.unexpected(node, slot);
                continue;
            }
            switch (type(slot.node())) {
                case "array_element_initializer" -> visitArrayElementInitializer(ctx, slot.node());
                default -> visitExpression(ctx, slot.node());
            }
        }
        ctx.exit(node);
    }

    private void visitBlockOrStatement(Ctx ctx, Slot slot) {
        switch (type(slot.node())) {
            case "colon_block" -> visitColonBlock(ctx, slot.node());
            default -> visitStatement(ctx, slot.node());
        }
    }

    private void visitClassName(Ctx ctx, Node parent, Slot slot) {
        switch (type(slot.node())) {
            case "name" -> visitName(ctx, slot.node());
            case "qualified_name" -> visitQualifiedName(ctx, slot.node());
            case "relative_name" -> visitRelativeName(ctx, slot.node());
            default -> ctx.unexpected(parent, slot);
        }
    }

    private void visitReturnType(Ctx ctx, Slot slot) {
        switch (type(slot.node())) {
            case "bottom_type" -> visitBottomType(ctx, slot.node());
            default -> visitType(ctx, slot.node());
        }
    }

    private void visitMemberName(Ctx ctx, Slot slot) {
        switch (type(slot.node())) {
            case "dynamic_variable_name" -> visitDynamicVariableName(ctx, slot.node());
            case "name" -> visitName(ctx, slot.node());
            case "variable_name" -> visitVariableName(ctx, slot.node());
            default -> visitExpression(ctx, slot.node());
        }
    }

    private void visitScope(Ctx ctx, Slot slot) {
        switch (type(slot.node())) {
            case "relative_scope" -> visitRelativeScope(ctx, slot.node());
            default -> visitPrimaryExpression(ctx, slot.node());
        }
    }

    private void visitAssignmentTarget(Ctx ctx, Slot slot) {
        switch (type(slot.node())) {
            case "list_literal" -> visitListLiteral(ctx, slot.node());
            default -> visitPrimaryExpression(ctx, slot.node());
        }
    }

    private void visitVariableTarget(Ctx ctx, Slot slot) {
        visitPrimaryExpression(ctx, slot.node());
    }

    private void visitInterpolated(Ctx ctx, Slot slot) {
        switch (type(slot.node())) {
            case "escape_sequence" -> visitEscapeSequence(ctx, slot.node());
            case "string_content" -> visitStringContent(ctx, slot.node());
            default -> visitExpression(ctx, slot.node());
        }
    }
}
