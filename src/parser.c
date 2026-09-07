// Copyright (C) 2026 blue-pitaya
//
// This file is part of php-magik.
//
// php-magik is free software: you can redistribute it and/or modify it
// under the terms of the GNU General Public License as published by the
// Free Software Foundation, either version 3 of the License, or (at your
// option) any later version.
//
// php-magik is distributed in the hope that it will be useful, but
// WITHOUT ANY WARRANTY; without even the implied warranty of
// MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
// General Public License for more details.
//
// You should have received a copy of the GNU General Public License
// along with php-magik. If not, see <https://www.gnu.org/licenses/>.

#include "parser.h"
#include "vector.h"
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <tree_sitter/api.h>

static char *get_node_text(TSNode node, struct parser_ctx *ctx)
{
	uint32_t start = ts_node_start_byte(node);
	return strndup(ctx->file_content + start,
		       ts_node_end_byte(node) - start);
}

static const char *literal_type(const char *t)
{
	if (!strcmp(t, "integer")) {
		return "int";
	}
	if (!strcmp(t, "float")) {
		return "float";
	}
	if (!strcmp(t, "string") || !strcmp(t, "encapsed_string") ||
	    !strcmp(t, "heredoc") || !strcmp(t, "nowdoc")) {
		return "string";
	}
	if (!strcmp(t, "boolean")) {
		return "bool";
	}
	if (!strcmp(t, "null")) {
		return "null";
	}
	if (!strcmp(t, "array_creation_expression")) {
		return "array";
	}
	if (!strcmp(t, "anonymous_function") || !strcmp(t, "arrow_function")) {
		return "Closure";
	}
	return NULL;
}

/* latest known type of variable `name` in the current function scope */
static const char *scope_var_type(const char *name, struct parser_ctx *ctx)
{
	for (int i = ctx->vars.len - 1; i >= 0; i--) {
		struct php_var *v = vec_get(&ctx->vars, i);
		if (v->type && !strcmp(v->name, name) &&
		    ((!v->function_name && !ctx->function_name) ||
		     (v->function_name && ctx->function_name &&
		      !strcmp(v->function_name, ctx->function_name)))) {
			return v->type;
		}
	}
	return NULL;
}

/* declared type of property `prop` ("$x") on already-collected class `cls` */
static const char *class_prop_type(const char *cls, const char *prop,
				   struct parser_ctx *ctx)
{
	for (int i = 0; i < ctx->vars.len; i++) {
		struct php_var *v = vec_get(&ctx->vars, i);
		if (v->kind == VAR_PROPERTY && v->class_name &&
		    !strcmp(v->class_name, cls) && !strcmp(v->name, prop)) {
			return v->type;
		}
	}
	return NULL;
}

static const char *strip_type(const char *t) /* ?Foo, \Foo -> Foo */
{
	while (t && (*t == '?' || *t == '\\')) {
		t++;
	}
	return t;
}

/* class behind `$var->` : current class for $this, else scope lookup */
static const char *object_class(const char *obj, struct parser_ctx *ctx)
{
	return !strcmp(obj, "$this") ? ctx->class_name :
				       strip_type(scope_var_type(obj, ctx));
}

/* type of an expression node: literal, new X, $var, $obj->prop */
static char *infer_expr_type(TSNode expr, struct parser_ctx *ctx)
{
	const char *et = ts_node_type(expr);
	const char *lit = literal_type(et);
	TSNode t;

	if (lit) {
		return strdup(lit);
	}
	if (!strcmp(et, "object_creation_expression")) {
		t = ts_node_named_child(expr, 0); /* name/qualified_name */
		return ts_node_is_null(t) ? NULL : get_node_text(t, ctx);
	}
	if (!strcmp(et, "variable_name")) {
		char *v = get_node_text(expr, ctx);
		const char *vt = scope_var_type(v, ctx);
		free(v);
		return vt ? strdup(vt) : NULL;
	}
	if (!strcmp(et, "member_access_expression")) {
		TSNode obj = ts_node_child_by_field_name(expr, "object", 6);
		TSNode nm = ts_node_child_by_field_name(expr, "name", 4);
		if (!strcmp(ts_node_type(obj), "variable_name") &&
		    !strcmp(ts_node_type(nm), "name")) {
			char *o = get_node_text(obj, ctx);
			const char *cls = object_class(o, ctx);
			free(o);
			if (cls) {
				char *pn = get_node_text(nm, ctx);
				char *prop = malloc(strlen(pn) + 2);
				prop[0] = '$';
				strcpy(prop + 1, pn);
				free(pn);
				const char *pt =
					class_prop_type(cls, prop, ctx);
				free(prop);
				return pt ? strdup(pt) : NULL;
			}
		}
	}
	if (!strcmp(et, "parenthesized_expression")) {
		return infer_expr_type(ts_node_named_child(expr, 0), ctx);
	}
	if (!strcmp(et, "unary_op_expression")) { /* -1, +$x, !$b, ~$n */
		TSNode arg = ts_node_named_child(expr, 0);
		const char *op = ts_node_type(ts_node_child(expr, 0));
		if (!strcmp(op, "!")) {
			return strdup("bool");
		}
		if (!strcmp(op, "~")) {
			return strdup("int");
		}
		return ts_node_is_null(arg) ? NULL : infer_expr_type(arg, ctx);
	}
	if (!strcmp(et, "binary_expression")) {
		const char *op = ts_node_type(
			ts_node_child_by_field_name(expr, "operator", 8));
		if (!strcmp(op, ".")) {
			return strdup("string");
		}
		if (!strcmp(op, "%") || !strcmp(op, "<<") ||
		    !strcmp(op, ">>") || !strcmp(op, "&") || !strcmp(op, "|") ||
		    !strcmp(op, "^")) {
			return strdup("int");
		}
		if (strstr("== != === !== < > <= >= <=> && || and or xor instanceof",
			   op)) {
			return strdup("bool");
		}
		if (!strcmp(op, "+") || !strcmp(op, "-") || !strcmp(op, "*") ||
		    !strcmp(op, "/") || !strcmp(op, "**")) {
			char *l = infer_expr_type(
				ts_node_child_by_field_name(expr, "left", 4),
				ctx);
			char *r = infer_expr_type(
				ts_node_child_by_field_name(expr, "right", 5),
				ctx);
			int is_float = (l && !strcmp(l, "float")) ||
				       (r && !strcmp(r, "float")) ||
				       !strcmp(op, "/");
			int known = l && r;
			free(l);
			free(r);
			if (!known) {
				return NULL;
			}
			return strdup(is_float ? "float" : "int");
		}
	}

	return NULL;
}

static char *infer_type(TSNode node, struct parser_ctx *ctx)
{
	TSNode parent = ts_node_parent(node);
	const char *pt = ts_node_type(parent);
	TSNode t;

	/* declared type on parameter (incl. variadic/promoted) */
	if (strstr(pt, "parameter")) {
		t = ts_node_child_by_field_name(parent, "type", 4);
		return ts_node_is_null(t) ? NULL : get_node_text(t, ctx);
	}
	/* typed property: type field sits on property_declaration */
	if (!strcmp(pt, "property_element")) {
		t = ts_node_child_by_field_name(ts_node_parent(parent), "type",
						4);
		return ts_node_is_null(t) ? NULL : get_node_text(t, ctx);
	}
	/* $x = <rhs>, only when we are the left side */
	if (!strcmp(pt, "assignment_expression") &&
	    ts_node_eq(ts_node_child_by_field_name(parent, "left", 4), node)) {
		return infer_expr_type(
			ts_node_child_by_field_name(parent, "right", 5), ctx);
	}
	return NULL;
}

/* first `return <expr>;` in the body, not descending into closures */
static char *infer_from_returns(TSNode root, struct parser_ctx *ctx)
{
	const char *t = ts_node_type(root);

	if (!strcmp(t, "anonymous_function") || !strcmp(t, "arrow_function")) {
		return NULL;
	}
	if (!strcmp(t, "return_statement")) {
		TSNode expr = ts_node_named_child(root, 0);
		return ts_node_is_null(expr) ? strdup("void") :
					       infer_expr_type(expr, ctx);
	}
	uint32_t count = ts_node_named_child_count(root);
	for (uint32_t i = 0; i < count; i++) {
		char *r = infer_from_returns(ts_node_named_child(root, i), ctx);
		if (r) {
			return r;
		}
	}
	return NULL;
}

static char *infer_return_type(TSNode func, struct parser_ctx *ctx)
{
	TSNode rt = ts_node_child_by_field_name(func, "return_type", 11);
	if (!ts_node_is_null(rt)) {
		return get_node_text(rt, ctx);
	}
	TSNode body = ts_node_child_by_field_name(func, "body", 4);
	return ts_node_is_null(body) ? NULL : infer_from_returns(body, ctx);
}

static void push_var(char *name, char *type, enum php_var_kind kind,
		     TSNode node, struct parser_ctx *ctx)
{
	TSPoint p = ts_node_start_point(node);
	struct php_var var = {
		.name = name,
		.ns = ctx->ns ? strdup(ctx->ns) : NULL,
		.class_name = ctx->class_name ? strdup(ctx->class_name) : NULL,
		.function_name =
			ctx->function_name ? strdup(ctx->function_name) : NULL,
		.kind = kind,
		.line = p.row,
		.col = p.column,
		.type = type,
		.file_id = ctx->file_id,
	};
	vec_push(&ctx->vars, &var);
}

static void add_var(TSNode node, struct parser_ctx *ctx)
{
	const char *ptype = ts_node_type(ts_node_parent(node));
	enum php_var_kind kind =
		!strcmp(ptype, "property_element") ? VAR_PROPERTY :
		!strcmp(ptype, "simple_parameter") ||
				!strcmp(ptype, "variadic_parameter") ||
				!strcmp(ptype, "property_promotion_parameter") ?
						     VAR_PARAM :
						     VAR_USE;
	push_var(get_node_text(node, ctx), infer_type(node, ctx), kind, node,
		 ctx);
}

/* $obj->foo -> property usage "$foo" on class `cls` */
static void add_obj_prop(TSNode name_node, const char *cls,
			 enum php_var_kind kind, struct parser_ctx *ctx)
{
	char *text = get_node_text(name_node, ctx);
	char *name = malloc(strlen(text) + 2);
	name[0] = '$';
	strcpy(name + 1, text);
	free(text);
	const char *pt = class_prop_type(cls, name, ctx);
	char *saved = ctx->class_name;
	ctx->class_name = (char *)cls; /* attribute entry to owning class */
	push_var(name, pt ? strdup(pt) : NULL, kind, name_node, ctx);
	ctx->class_name = saved;
}

void php_vars_free(struct vec *v)
{
	for (int i = 0; i < v->len; i++) {
		struct php_var *var = vec_get(v, i);
		free(var->name);
		free(var->ns);
		free(var->class_name);
		free(var->function_name);
		free(var->type);
	}
	vec_free(v);
}

/* takes ownership of name and ret */
static void push_function(char *name, char *ret, enum php_func_kind kind,
			  TSNode node, struct parser_ctx *ctx)
{
	TSPoint p = ts_node_start_point(node);
	struct php_function fn = {
		.name = name,
		.ns = ctx->ns ? strdup(ctx->ns) : NULL,
		.class_name = ctx->class_name ? strdup(ctx->class_name) : NULL,
		.function_name = NULL,
		.kind = kind,
		.return_type = ret,
		.line = p.row,
		.col = p.column,
		.file_id = ctx->file_id,
	};
	vec_push(&ctx->funcs, &fn);
}

/* $obj->foo(...) -> method usage on class `cls` */
static void add_method_call(TSNode name_node, const char *cls,
			    struct parser_ctx *ctx)
{
	char *saved = ctx->class_name;
	ctx->class_name = (char *)cls; /* attribute call to owning class */
	push_function(get_node_text(name_node, ctx), NULL, FUNC_METHOD,
		      name_node, ctx);
	ctx->class_name = saved;
}

void php_funcs_free(struct vec *v)
{
	for (int i = 0; i < v->len; i++) {
		struct php_function *fn = vec_get(v, i);
		free(fn->name);
		free(fn->ns);
		free(fn->class_name);
		free(fn->function_name);
		free(fn->return_type);
	}
	vec_free(v);
}

/* records every (variable_name) in the subtree: declarations and usages */
static void collect_variables(TSNode root, struct parser_ctx *ctx)
{
	const char *t = ts_node_type(root);

	if (!strcmp(t, "variable_name")) {
		add_var(root, ctx);
		return;
	}
	if (!strcmp(t, "member_access_expression")) {
		TSNode obj = ts_node_child_by_field_name(root, "object", 6);
		TSNode name = ts_node_child_by_field_name(root, "name", 4);
		if (!strcmp(ts_node_type(obj), "variable_name") &&
		    !strcmp(ts_node_type(name), "name")) {
			char *o = get_node_text(obj, ctx);
			const char *cls = object_class(o, ctx);
			if (cls) {
				add_obj_prop(name, cls,
					     !strcmp(o, "$this") ? VAR_THIS :
								   VAR_OBJ,
					     ctx);
			}
			free(o);
		}
		/* fall through: recurse for the object var itself */
	}
	if (!strcmp(t, "function_call_expression")) {
		TSNode fn = ts_node_child_by_field_name(root, "function", 8);
		const char *ft = ts_node_type(fn);
		if (!strcmp(ft, "name") || !strcmp(ft, "qualified_name")) {
			push_function(get_node_text(fn, ctx), NULL, FUNC_CALL,
				      fn, ctx);
		}
		/* fall through: recurse for arguments */
	}
	if (!strcmp(t, "member_call_expression")) {
		TSNode obj = ts_node_child_by_field_name(root, "object", 6);
		TSNode name = ts_node_child_by_field_name(root, "name", 4);
		if (!strcmp(ts_node_type(obj), "variable_name") &&
		    !strcmp(ts_node_type(name), "name")) {
			char *o = get_node_text(obj, ctx);
			const char *cls = object_class(o, ctx);
			if (cls) {
				add_method_call(name, cls, ctx);
			}
			free(o);
		}
		/* fall through: recurse for object and arguments */
	}
	uint32_t count = ts_node_named_child_count(root);
	for (uint32_t i = 0; i < count; i++) {
		collect_variables(ts_node_named_child(root, i), ctx);
	}
}

static int parse_function_like(TSNode root, struct parser_ctx *ctx)
{
	TSNode name = ts_node_child_by_field_name(root, "name", 4);
	if (ts_node_is_null(name)) {
		return -1;
	}
	ctx->function_name = get_node_text(name, ctx);
	collect_variables(root, ctx); /* before return inference: fills scope */
	push_function(strdup(ctx->function_name), infer_return_type(root, ctx),
		      FUNC_DEF, root, ctx);
	free(ctx->function_name);
	ctx->function_name = NULL;
	return 0;
}

static int parse_declaration_list(TSNode root, struct parser_ctx *ctx)
{
	uint32_t count = ts_node_named_child_count(root);
	for (uint32_t i = 0; i < count; i++) {
		TSNode node = ts_node_named_child(root, i);
		const char *type = ts_node_type(node);

		if (!strcmp(type, "method_declaration")) {
			parse_function_like(node, ctx);
		} else if (!strcmp(type, "property_declaration")) { /* vars */
			collect_variables(node, ctx);
		}
	}
	return 0;
}

static int parse_class_declaration(TSNode root, struct parser_ctx *ctx)
{
	uint32_t count = ts_node_named_child_count(root);
	for (uint32_t i = 0; i < count; i++) {
		TSNode node = ts_node_named_child(root, i);
		const char *type = ts_node_type(node);

		if (!strcmp(type, "name")) {
			ctx->class_name = get_node_text(node, ctx);
		} else if (!strcmp(type, "declaration_list") ||
			   !strcmp(type, "enum_declaration_list")) {
			parse_declaration_list(node, ctx);
		}
	}
	free(ctx->class_name);
	ctx->class_name = NULL;
	return 0;
}

static int parse_namespace_definition(TSNode root, struct parser_ctx *ctx)
{
	uint32_t count = ts_node_named_child_count(root);
	for (uint32_t i = 0; i < count; i++) {
		TSNode node = ts_node_named_child(root, i);
		const char *type = ts_node_type(node);

		if (!strcmp(type, "namespace_name")) {
			free(ctx->ns);
			ctx->ns = get_node_text(node, ctx);
		} else if (!strcmp(type, "compound_statement")) {
			parse_program(node, ctx);
		}
	}
	return 0;
}

static size_t longest_str(const char *arr[], size_t n)
{
	size_t max = 0;
	for (size_t i = 0; i < n; i++) {
		size_t len = strlen(arr[i]);
		if (len > max) {
			max = len;
		}
	}
	return max;
}

void parser_print_php_vars(struct vec *vars)
{
	static const char *kind_str[] = { "property", "param", "use", "this->",
					  "obj->" };
	int width = (int)longest_str(kind_str,
				     sizeof(kind_str) / sizeof(kind_str[0]));

	for (int i = 0; i < vars->len; i++) {
		struct php_var *var = vec_get(vars, i);
		fprintf(stderr, "var %-*s %s%s%s @%u:%u in %s%s%s%s%s\n", width,
		       kind_str[var->kind], var->name, var->type ? ": " : "",
		       var->type ? var->type : "", var->line + 1, var->col + 1,
		       var->ns ? var->ns : "", var->ns ? "\\" : "",
		       var->class_name ? var->class_name : "",
		       var->class_name ? "::" : "",
		       var->function_name ? var->function_name : "(top-level)");
	}
}

void parser_print_php_funcs(struct vec *funcs)
{
	static const char *kind_str[] = { "def", "call", "obj->" };
	int width = (int)longest_str(kind_str,
				     sizeof(kind_str) / sizeof(kind_str[0]));

	for (int i = 0; i < funcs->len; i++) {
		struct php_function *fn = vec_get(funcs, i);
		fprintf(stderr, "func %-*s %s()%s%s @%u:%u in %s%s%s\n", width,
		       kind_str[fn->kind], fn->name,
		       fn->return_type ? ": " : "",
		       fn->return_type ? fn->return_type : "", fn->line + 1,
		       fn->col + 1, fn->ns ? fn->ns : "", fn->ns ? "\\" : "",
		       fn->class_name ? fn->class_name : "(top-level)");
	}
}

int parse_program(TSNode root, struct parser_ctx *ctx)
{
	uint32_t count = ts_node_named_child_count(root);
	for (uint32_t i = 0; i < count; i++) {
		TSNode node = ts_node_named_child(root, i);
		const char *type = ts_node_type(node);

		if (!strcmp(type, "function_definition")) {
			parse_function_like(node, ctx);
		} else if (!strcmp(type, "class_declaration") ||
			   !strcmp(type, "interface_declaration") ||
			   !strcmp(type, "trait_declaration") ||
			   !strcmp(type, "enum_declaration")) {
			parse_class_declaration(node, ctx);
		} else if (!strcmp(type, "namespace_definition")) {
			parse_namespace_definition(node, ctx);
		} else {
			collect_variables(node, ctx);
		}
	}
	return 0;
}
