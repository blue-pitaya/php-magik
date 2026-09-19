/*
 * Ported from seart-group/java-tree-sitter
 * (lib/ch_usi_si_seart_treesitter_Parser.cc), MIT licensed - see
 * java-tree-sitter/LICENSE next to this file.
 */

#include "tsjni.h"

#include <stdint.h>

#include "dev_bluepitaya_phpmagik_ts_Parser.h"

JNIEXPORT jlong JNICALL Java_dev_bluepitaya_phpmagik_ts_Parser_create(
    JNIEnv *env, jclass thisClass) {
  (void)env;
  (void)thisClass;
  TSParser *parser = ts_parser_new();
  ts_parser_set_language(parser, tree_sitter_php_only());
  return (jlong)(intptr_t)parser;
}

JNIEXPORT void JNICALL Java_dev_bluepitaya_phpmagik_ts_Parser_delete(
    JNIEnv *env, jclass thisClass, jlong pointer) {
  (void)env;
  (void)thisClass;
  ts_parser_delete((TSParser *)(intptr_t)pointer);
}

JNIEXPORT jobject JNICALL Java_dev_bluepitaya_phpmagik_ts_Parser_parse(
    JNIEnv *env, jobject thisObject, jbyteArray source) {
  if (source == NULL) {
    __throwNPE(env, "Source must not be null!");
    return NULL;
  }
  TSParser *parser = (TSParser *)(intptr_t)__getPointer(env, thisObject);
  jsize length = (*env)->GetArrayLength(env, source);
  jbyte *elements = (*env)->GetByteArrayElements(env, source, NULL);
  TSTree *tree =
      ts_parser_parse_string(parser, NULL, (const char *)elements, (uint32_t)length);
  (*env)->ReleaseByteArrayElements(env, source, elements, JNI_ABORT);
  ts_parser_reset(parser);
  if (tree == NULL) {
    __throwPE(env, "Parsing failed!");
    return NULL;
  }
  return (*env)->NewObject(env, _treeClass, _treeConstructor, (jlong)(intptr_t)tree,
                           source);
}
