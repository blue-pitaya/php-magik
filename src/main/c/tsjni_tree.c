/*
 * Ported from seart-group/java-tree-sitter
 * (lib/ch_usi_si_seart_treesitter_Tree.cc), MIT licensed - see
 * java-tree-sitter/LICENSE next to this file.
 */

#include "tsjni.h"

#include <stdint.h>

#include "dev_bluepitaya_phpmagik_ts_Tree.h"

JNIEXPORT void JNICALL Java_dev_bluepitaya_phpmagik_ts_Tree_delete(
    JNIEnv *env, jclass thisClass, jlong pointer) {
  (void)env;
  (void)thisClass;
  ts_tree_delete((TSTree *)(intptr_t)pointer);
}

JNIEXPORT jobject JNICALL Java_dev_bluepitaya_phpmagik_ts_Tree_getRootNode(
    JNIEnv *env, jobject thisObject) {
  TSTree *tree = (TSTree *)(intptr_t)__getPointer(env, thisObject);
  return __marshalNode(env, ts_tree_root_node(tree), thisObject);
}
