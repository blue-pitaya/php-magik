/*
 * Ported from seart-group/java-tree-sitter
 * (lib/ch_usi_si_seart_treesitter_Node.cc), MIT licensed - see
 * java-tree-sitter/LICENSE next to this file.
 */

#include "tsjni.h"

#include <stdint.h>
#include <stdlib.h>

#include "dev_bluepitaya_phpmagik_ts_Node.h"

JNIEXPORT jobject JNICALL Java_dev_bluepitaya_phpmagik_ts_Node_getChild(
    JNIEnv *env, jobject thisObject, jint child, jboolean named) {
  uint32_t (*child_counter)(TSNode) =
      named ? ts_node_named_child_count : ts_node_child_count;
  TSNode (*child_getter)(TSNode, uint32_t) =
      named ? ts_node_named_child : ts_node_child;
  TSNode node = __unmarshalNode(env, thisObject);
  if (child < 0 || (uint32_t)child >= child_counter(node)) {
    __throwIOB(env, child);
    return NULL;
  }
  return __marshalNode(env, child_getter(node, (uint32_t)child),
                       __nodeTree(env, thisObject));
}

JNIEXPORT jobject JNICALL Java_dev_bluepitaya_phpmagik_ts_Node_getChildByFieldName(
    JNIEnv *env, jobject thisObject, jstring name) {
  if (name == NULL) {
    __throwNPE(env, "Field name must not be null!");
    return NULL;
  }
  jsize length = (*env)->GetStringUTFLength(env, name);
  const char *characters = (*env)->GetStringUTFChars(env, name, NULL);
  TSNode node = __unmarshalNode(env, thisObject);
  TSNode child = ts_node_child_by_field_name(node, characters, (uint32_t)length);
  (*env)->ReleaseStringUTFChars(env, name, characters);
  return __marshalNode(env, child, __nodeTree(env, thisObject));
}

JNIEXPORT jint JNICALL Java_dev_bluepitaya_phpmagik_ts_Node_getChildCount(
    JNIEnv *env, jobject thisObject, jboolean named) {
  uint32_t (*child_counter)(TSNode) =
      named ? ts_node_named_child_count : ts_node_child_count;
  TSNode node = __unmarshalNode(env, thisObject);
  return ts_node_is_null(node) ? (jint)0 : (jint)child_counter(node);
}

JNIEXPORT jobjectArray JNICALL Java_dev_bluepitaya_phpmagik_ts_Node_getChildren(
    JNIEnv *env, jclass thisClass, jobject nodeObject, jboolean named) {
  (void)thisClass;
  uint32_t (*child_counter)(TSNode) =
      named ? ts_node_named_child_count : ts_node_child_count;
  TSNode (*child_getter)(TSNode, uint32_t) =
      named ? ts_node_named_child : ts_node_child;
  TSNode node = __unmarshalNode(env, nodeObject);
  uint32_t count = ts_node_is_null(node) ? 0 : child_counter(node);
  jobject treeObject = __nodeTree(env, nodeObject);
  jobjectArray children = (*env)->NewObjectArray(env, (jsize)count, _nodeClass, NULL);
  for (uint32_t i = 0; i < count; i++) {
    jobject childObject = __marshalNode(env, child_getter(node, i), treeObject);
    (*env)->SetObjectArrayElement(env, children, (jsize)i, childObject);
    (*env)->DeleteLocalRef(env, childObject);
  }
  return children;
}

JNIEXPORT jobject JNICALL Java_dev_bluepitaya_phpmagik_ts_Node_getDescendant__IIZ(
    JNIEnv *env, jobject thisObject, jint startByte, jint endByte, jboolean named) {
  if (startByte < 0 || endByte < 0) {
    __throwIAE(env, "The start and end bytes must not be negative!");
    return NULL;
  }
  if (startByte > endByte) {
    __throwIAE(env, "The start byte must not be greater than the end byte!");
    return NULL;
  }
  TSNode (*descendant_getter)(TSNode, uint32_t, uint32_t) =
      named ? ts_node_named_descendant_for_byte_range
            : ts_node_descendant_for_byte_range;
  TSNode node = __unmarshalNode(env, thisObject);
  TSNode descendant = descendant_getter(node, (uint32_t)startByte, (uint32_t)endByte);
  return __marshalNode(env, descendant, __nodeTree(env, thisObject));
}

JNIEXPORT jobject JNICALL
Java_dev_bluepitaya_phpmagik_ts_Node_getDescendant__Ldev_bluepitaya_phpmagik_ts_Point_2Ldev_bluepitaya_phpmagik_ts_Point_2Z(
    JNIEnv *env, jobject thisObject, jobject startPointObject, jobject endPointObject,
    jboolean named) {
  if (startPointObject == NULL) {
    __throwNPE(env, "Start point must not be null!");
    return NULL;
  }
  if (endPointObject == NULL) {
    __throwNPE(env, "End point must not be null!");
    return NULL;
  }
  TSNode (*descendant_getter)(TSNode, TSPoint, TSPoint) =
      named ? ts_node_named_descendant_for_point_range
            : ts_node_descendant_for_point_range;
  TSNode node = __unmarshalNode(env, thisObject);
  TSPoint startPoint = __unmarshalPoint(env, startPointObject);
  TSPoint endPoint = __unmarshalPoint(env, endPointObject);
  TSNode descendant = descendant_getter(node, startPoint, endPoint);
  return __marshalNode(env, descendant, __nodeTree(env, thisObject));
}

JNIEXPORT jint JNICALL Java_dev_bluepitaya_phpmagik_ts_Node_getDescendantCount(
    JNIEnv *env, jobject thisObject) {
  TSNode node = __unmarshalNode(env, thisObject);
  return ts_node_is_null(node) ? (jint)0 : (jint)ts_node_descendant_count(node);
}

JNIEXPORT jint JNICALL Java_dev_bluepitaya_phpmagik_ts_Node_getEndByte(
    JNIEnv *env, jobject thisObject) {
  TSNode node = __unmarshalNode(env, thisObject);
  return ts_node_is_null(node) ? (jint)0 : (jint)ts_node_end_byte(node);
}

JNIEXPORT jobject JNICALL Java_dev_bluepitaya_phpmagik_ts_Node_getEndPoint(
    JNIEnv *env, jobject thisObject) {
  TSNode node = __unmarshalNode(env, thisObject);
  TSPoint origin = {0, 0};
  return __marshalPoint(env, ts_node_is_null(node) ? origin : ts_node_end_point(node));
}

JNIEXPORT jstring JNICALL Java_dev_bluepitaya_phpmagik_ts_Node_getFieldNameForChild(
    JNIEnv *env, jobject thisObject, jint child) {
  TSNode node = __unmarshalNode(env, thisObject);
  if (child < 0 || (uint32_t)child >= ts_node_child_count(node)) {
    __throwIOB(env, child);
    return NULL;
  }
  const char *name = ts_node_field_name_for_child(node, (uint32_t)child);
  return (name == NULL) ? NULL : (*env)->NewStringUTF(env, name);
}

JNIEXPORT jobject JNICALL Java_dev_bluepitaya_phpmagik_ts_Node_getFirstChildForByte(
    JNIEnv *env, jobject thisObject, jint offset, jboolean named) {
  TSNode node = __unmarshalNode(env, thisObject);
  if (offset < 0 || (uint32_t)offset < ts_node_start_byte(node) ||
      (uint32_t)offset > ts_node_end_byte(node)) {
    __throwIAE(env, "The byte offset is outside of this node's range!");
    return NULL;
  }
  TSNode (*child_getter)(TSNode, uint32_t) =
      named ? ts_node_first_named_child_for_byte : ts_node_first_child_for_byte;
  TSNode child = child_getter(node, (uint32_t)offset);
  return __marshalNode(env, child, __nodeTree(env, thisObject));
}

JNIEXPORT jobject JNICALL Java_dev_bluepitaya_phpmagik_ts_Node_getNextSibling(
    JNIEnv *env, jobject thisObject, jboolean named) {
  TSNode (*sibling_getter)(TSNode) =
      named ? ts_node_next_named_sibling : ts_node_next_sibling;
  TSNode sibling = sibling_getter(__unmarshalNode(env, thisObject));
  return __marshalNode(env, sibling, __nodeTree(env, thisObject));
}

JNIEXPORT jobject JNICALL Java_dev_bluepitaya_phpmagik_ts_Node_getPrevSibling(
    JNIEnv *env, jobject thisObject, jboolean named) {
  TSNode (*sibling_getter)(TSNode) =
      named ? ts_node_prev_named_sibling : ts_node_prev_sibling;
  TSNode sibling = sibling_getter(__unmarshalNode(env, thisObject));
  return __marshalNode(env, sibling, __nodeTree(env, thisObject));
}

JNIEXPORT jobject JNICALL Java_dev_bluepitaya_phpmagik_ts_Node_getParent(
    JNIEnv *env, jobject thisObject) {
  TSNode parent = ts_node_parent(__unmarshalNode(env, thisObject));
  return __marshalNode(env, parent, __nodeTree(env, thisObject));
}

JNIEXPORT jstring JNICALL Java_dev_bluepitaya_phpmagik_ts_Node_getSexp(
    JNIEnv *env, jobject thisObject) {
  char *string = ts_node_string(__unmarshalNode(env, thisObject));
  jstring result = (*env)->NewStringUTF(env, string);
  free(string);
  return result;
}

JNIEXPORT jint JNICALL Java_dev_bluepitaya_phpmagik_ts_Node_getStartByte(
    JNIEnv *env, jobject thisObject) {
  TSNode node = __unmarshalNode(env, thisObject);
  return ts_node_is_null(node) ? (jint)0 : (jint)ts_node_start_byte(node);
}

JNIEXPORT jobject JNICALL Java_dev_bluepitaya_phpmagik_ts_Node_getStartPoint(
    JNIEnv *env, jobject thisObject) {
  TSNode node = __unmarshalNode(env, thisObject);
  TSPoint origin = {0, 0};
  return __marshalPoint(env, ts_node_is_null(node) ? origin : ts_node_start_point(node));
}

JNIEXPORT jstring JNICALL Java_dev_bluepitaya_phpmagik_ts_Node_getType(
    JNIEnv *env, jobject thisObject, jboolean grammar) {
  TSNode node = __unmarshalNode(env, thisObject);
  if (ts_node_is_null(node)) {
    return NULL;
  }
  const char *(*type_getter)(TSNode) = grammar ? ts_node_grammar_type : ts_node_type;
  return (*env)->NewStringUTF(env, type_getter(node));
}

JNIEXPORT jboolean JNICALL Java_dev_bluepitaya_phpmagik_ts_Node_hasChanges(
    JNIEnv *env, jobject thisObject) {
  return ts_node_has_changes(__unmarshalNode(env, thisObject)) ? JNI_TRUE : JNI_FALSE;
}

JNIEXPORT jboolean JNICALL Java_dev_bluepitaya_phpmagik_ts_Node_hasError(
    JNIEnv *env, jobject thisObject) {
  return ts_node_has_error(__unmarshalNode(env, thisObject)) ? JNI_TRUE : JNI_FALSE;
}

JNIEXPORT jboolean JNICALL Java_dev_bluepitaya_phpmagik_ts_Node_isError(
    JNIEnv *env, jobject thisObject) {
  return ts_node_is_error(__unmarshalNode(env, thisObject)) ? JNI_TRUE : JNI_FALSE;
}

JNIEXPORT jboolean JNICALL Java_dev_bluepitaya_phpmagik_ts_Node_isExtra(
    JNIEnv *env, jobject thisObject) {
  return ts_node_is_extra(__unmarshalNode(env, thisObject)) ? JNI_TRUE : JNI_FALSE;
}

JNIEXPORT jboolean JNICALL Java_dev_bluepitaya_phpmagik_ts_Node_isMissing(
    JNIEnv *env, jobject thisObject) {
  return ts_node_is_missing(__unmarshalNode(env, thisObject)) ? JNI_TRUE : JNI_FALSE;
}

JNIEXPORT jboolean JNICALL Java_dev_bluepitaya_phpmagik_ts_Node_isNamed(
    JNIEnv *env, jobject thisObject) {
  return ts_node_is_named(__unmarshalNode(env, thisObject)) ? JNI_TRUE : JNI_FALSE;
}

JNIEXPORT jboolean JNICALL Java_dev_bluepitaya_phpmagik_ts_Node_isNull(
    JNIEnv *env, jobject thisObject) {
  return ts_node_is_null(__unmarshalNode(env, thisObject)) ? JNI_TRUE : JNI_FALSE;
}

JNIEXPORT jboolean JNICALL Java_dev_bluepitaya_phpmagik_ts_Node_equals(
    JNIEnv *env, jclass thisClass, jobject nodeObject, jobject otherObject) {
  (void)thisClass;
  TSNode node = __unmarshalNode(env, nodeObject);
  TSNode other = __unmarshalNode(env, otherObject);
  return ts_node_eq(node, other) ? JNI_TRUE : JNI_FALSE;
}
