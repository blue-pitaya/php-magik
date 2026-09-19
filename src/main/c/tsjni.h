/*
 * Ported from seart-group/java-tree-sitter (lib/ch_usi_si_seart_treesitter.h),
 * MIT licensed - see java-tree-sitter/LICENSE next to this file.
 *
 * Differences from upstream: C instead of C++, this project's package, no
 * Query/TreeCursor/Language/logger bindings, and byte offsets are UTF-8 byte
 * offsets rather than upstream's halved UTF-16 offsets.
 */

#ifndef TSJNI_H
#define TSJNI_H

#include <jni.h>
#include <tree_sitter/api.h>

extern jclass _nullPointerExceptionClass;
extern jclass _illegalArgumentExceptionClass;
extern jclass _indexOutOfBoundsExceptionClass;
extern jmethodID _indexOutOfBoundsExceptionConstructor;
extern jclass _parsingExceptionClass;
extern jmethodID _parsingExceptionConstructor;

extern jclass _externalClass;
extern jfieldID _externalPointerField;

extern jclass _nodeClass;
extern jmethodID _nodeConstructor;
extern jfieldID _nodeContext0Field;
extern jfieldID _nodeContext1Field;
extern jfieldID _nodeContext2Field;
extern jfieldID _nodeContext3Field;
extern jfieldID _nodeIdField;
extern jfieldID _nodeTreeField;

extern jclass _pointClass;
extern jmethodID _pointConstructor;

extern jclass _treeClass;
extern jmethodID _treeConstructor;

/* Exported by tree-sitter-php/php_only/src/parser.c. */
const TSLanguage *tree_sitter_php_only(void);

jint __throwNPE(JNIEnv *env, const char *message);

jint __throwIAE(JNIEnv *env, const char *message);

jint __throwIOB(JNIEnv *env, jint index);

jint __throwPE(JNIEnv *env, const char *message);

jlong __getPointer(JNIEnv *env, jobject objectInstance);

jobject __nodeTree(JNIEnv *env, jobject nodeObject);

jobject __marshalNode(JNIEnv *env, TSNode node, jobject treeObject);

TSNode __unmarshalNode(JNIEnv *env, jobject nodeObject);

jobject __marshalPoint(JNIEnv *env, TSPoint point);

#endif
