#include <tree_sitter/api.h>

#include "dev_bluepitaya_phpmagik_ts_Tree.h"

#define TREE(name) dev_bluepitaya_phpmagik_ts_Tree_##name

const TSLanguage *tree_sitter_php_only(void);

static jint *row(jint *rows, jint node) { return rows + node * TREE(STRIDE); }

static jobjectArray strings(JNIEnv *env, uint32_t length) {
  return (*env)->NewObjectArray(
      env, (jsize)length, (*env)->FindClass(env, "java/lang/String"), NULL);
}

static void put(JNIEnv *env, jobjectArray array, uint32_t index,
                const char *value) {
  jstring string = (*env)->NewStringUTF(env, value);
  (*env)->SetObjectArrayElement(env, array, (jsize)index, string);
  (*env)->DeleteLocalRef(env, string);
}

static jint visit(jint *rows, const TSTreeCursor *cursor, jint parent) {
  jint node = (jint)ts_tree_cursor_current_descendant_index(cursor);
  TSNode current = ts_tree_cursor_current_node(cursor);
  TSPoint start = ts_node_start_point(current);
  TSPoint end = ts_node_end_point(current);
  jint *columns = row(rows, node);
  columns[TREE(TYPE)] = ts_node_symbol(current);
  columns[TREE(FIELD)] = ts_tree_cursor_current_field_id(cursor);
  columns[TREE(FLAGS)] = (ts_node_is_named(current) ? TREE(NAMED) : 0) |
                         (ts_node_is_extra(current) ? TREE(EXTRA) : 0) |
                         (ts_node_is_missing(current) ? TREE(MISSING) : 0) |
                         (ts_node_is_error(current) ? TREE(ERROR) : 0);
  columns[TREE(PARENT)] = parent;
  columns[TREE(NEXT)] = TREE(NONE);
  columns[TREE(START_BYTE)] = (jint)ts_node_start_byte(current);
  columns[TREE(END_BYTE)] = (jint)ts_node_end_byte(current);
  columns[TREE(START_ROW)] = (jint)start.row;
  columns[TREE(START_COLUMN)] = (jint)start.column;
  columns[TREE(END_ROW)] = (jint)end.row;
  columns[TREE(END_COLUMN)] = (jint)end.column;
  return node;
}

static void walk(TSNode root, jint *rows) {
  TSTreeCursor cursor = ts_tree_cursor_new(root);
  jint node = visit(rows, &cursor, TREE(NONE));
  for (;;) {
    if (ts_tree_cursor_goto_first_child(&cursor)) {
      node = visit(rows, &cursor, node);
      continue;
    }
    while (!ts_tree_cursor_goto_next_sibling(&cursor) &&
           ts_tree_cursor_goto_parent(&cursor)) {
      node = row(rows, node)[TREE(PARENT)];
    }
    if (node == 0) {
      break;
    }
    jint sibling = visit(rows, &cursor, row(rows, node)[TREE(PARENT)]);
    row(rows, node)[TREE(NEXT)] = sibling;
    node = sibling;
  }
  ts_tree_cursor_delete(&cursor);
}

JNIEXPORT jobjectArray JNICALL
Java_dev_bluepitaya_phpmagik_ts_Tree_types(JNIEnv *env, jclass tree) {
  (void)tree;
  const TSLanguage *language = tree_sitter_php_only();
  uint32_t count = ts_language_symbol_count(language);
  jobjectArray types = strings(env, count);
  for (uint32_t symbol = 0; symbol < count; symbol++) {
    put(env, types, symbol,
        ts_language_symbol_name(language, (TSSymbol)symbol));
  }
  return types;
}

JNIEXPORT jobjectArray JNICALL
Java_dev_bluepitaya_phpmagik_ts_Tree_fields(JNIEnv *env, jclass tree) {
  (void)tree;
  const TSLanguage *language = tree_sitter_php_only();
  uint32_t count = ts_language_field_count(language);
  jobjectArray fields = strings(env, count + 1);
  for (uint32_t field = 1; field <= count; field++) {
    put(env, fields, field,
        ts_language_field_name_for_id(language, (TSFieldId)field));
  }
  return fields;
}

JNIEXPORT jintArray JNICALL Java_dev_bluepitaya_phpmagik_ts_Tree_flatten(
    JNIEnv *env, jclass tree, jbyteArray source) {
  (void)tree;
  TSParser *parser = ts_parser_new();
  ts_parser_set_language(parser, tree_sitter_php_only());
  jsize length = (*env)->GetArrayLength(env, source);
  jbyte *bytes = (*env)->GetByteArrayElements(env, source, NULL);
  TSTree *parsed = ts_parser_parse_string(parser, NULL, (const char *)bytes,
                                          (uint32_t)length);
  (*env)->ReleaseByteArrayElements(env, source, bytes, JNI_ABORT);
  ts_parser_delete(parser);

  TSNode root = ts_tree_root_node(parsed);
  jintArray nodes = (*env)->NewIntArray(
      env, (jsize)(ts_node_descendant_count(root) * TREE(STRIDE)));
  jint *rows =
      nodes == NULL ? NULL : (*env)->GetIntArrayElements(env, nodes, NULL);
  if (rows != NULL) {
    walk(root, rows);
    (*env)->ReleaseIntArrayElements(env, nodes, rows, 0);
  }
  ts_tree_delete(parsed);
  return nodes;
}
