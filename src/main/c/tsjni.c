/*
 * Ported from seart-group/java-tree-sitter (lib/ch_usi_si_seart_treesitter.cc),
 * MIT licensed - see java-tree-sitter/LICENSE next to this file.
 */

#include "tsjni.h"

#include <stdint.h>

#define JNI_VERSION JNI_VERSION_10

#define PACKAGE "dev/bluepitaya/phpmagik/ts/"

#define _loadClass(VARIABLE, NAME)                                             \
  do {                                                                         \
    VARIABLE = __loadClass(env, NAME);                                         \
    if (VARIABLE == NULL)                                                      \
      return JNI_ERR;                                                          \
  } while (0)

jclass _nullPointerExceptionClass;
jclass _illegalArgumentExceptionClass;
jclass _indexOutOfBoundsExceptionClass;
jmethodID _indexOutOfBoundsExceptionConstructor;
jclass _parsingExceptionClass;
jmethodID _parsingExceptionConstructor;

jclass _externalClass;
jfieldID _externalPointerField;

jclass _nodeClass;
jmethodID _nodeConstructor;
jfieldID _nodeContext0Field;
jfieldID _nodeContext1Field;
jfieldID _nodeContext2Field;
jfieldID _nodeContext3Field;
jfieldID _nodeIdField;
jfieldID _nodeTreeField;

jclass _pointClass;
jmethodID _pointConstructor;
jfieldID _pointRowField;
jfieldID _pointColumnField;

jclass _treeClass;
jmethodID _treeConstructor;

static jclass __loadClass(JNIEnv *env, const char *name) {
  jclass local = (*env)->FindClass(env, name);
  if (local == NULL) {
    return NULL;
  }
  jclass global = (jclass)(*env)->NewGlobalRef(env, local);
  (*env)->DeleteLocalRef(env, local);
  return global;
}

JNIEXPORT jint JNICALL JNI_OnLoad(JavaVM *vm, void *reserved) {
  (void)reserved;

  JNIEnv *env;
  if ((*vm)->GetEnv(vm, (void **)&env, JNI_VERSION) != JNI_OK) {
    return JNI_ERR;
  }

  _loadClass(_nullPointerExceptionClass, "java/lang/NullPointerException");
  _loadClass(_illegalArgumentExceptionClass, "java/lang/IllegalArgumentException");
  _loadClass(_indexOutOfBoundsExceptionClass, "java/lang/IndexOutOfBoundsException");
  _indexOutOfBoundsExceptionConstructor =
      (*env)->GetMethodID(env, _indexOutOfBoundsExceptionClass, "<init>", "(I)V");
  _loadClass(_parsingExceptionClass, PACKAGE "ParsingException");
  _parsingExceptionConstructor =
      (*env)->GetMethodID(env, _parsingExceptionClass, "<init>", "(Ljava/lang/String;)V");

  _loadClass(_externalClass, PACKAGE "External");
  _externalPointerField = (*env)->GetFieldID(env, _externalClass, "pointer", "J");

  _loadClass(_nodeClass, PACKAGE "Node");
  _nodeConstructor =
      (*env)->GetMethodID(env, _nodeClass, "<init>", "(IIIIJL" PACKAGE "Tree;)V");
  _nodeContext0Field = (*env)->GetFieldID(env, _nodeClass, "context0", "I");
  _nodeContext1Field = (*env)->GetFieldID(env, _nodeClass, "context1", "I");
  _nodeContext2Field = (*env)->GetFieldID(env, _nodeClass, "context2", "I");
  _nodeContext3Field = (*env)->GetFieldID(env, _nodeClass, "context3", "I");
  _nodeIdField = (*env)->GetFieldID(env, _nodeClass, "id", "J");
  _nodeTreeField = (*env)->GetFieldID(env, _nodeClass, "tree", "L" PACKAGE "Tree;");

  _loadClass(_pointClass, PACKAGE "Point");
  _pointConstructor = (*env)->GetMethodID(env, _pointClass, "<init>", "(II)V");
  _pointRowField = (*env)->GetFieldID(env, _pointClass, "row", "I");
  _pointColumnField = (*env)->GetFieldID(env, _pointClass, "column", "I");

  _loadClass(_treeClass, PACKAGE "Tree");
  _treeConstructor = (*env)->GetMethodID(env, _treeClass, "<init>", "(J[B)V");

  if ((*env)->ExceptionCheck(env) == JNI_TRUE) {
    return JNI_ERR;
  }

  return JNI_VERSION;
}

JNIEXPORT void JNICALL JNI_OnUnload(JavaVM *vm, void *reserved) {
  (void)reserved;

  JNIEnv *env;
  if ((*vm)->GetEnv(vm, (void **)&env, JNI_VERSION) != JNI_OK) {
    return;
  }

  (*env)->DeleteGlobalRef(env, _nullPointerExceptionClass);
  (*env)->DeleteGlobalRef(env, _illegalArgumentExceptionClass);
  (*env)->DeleteGlobalRef(env, _indexOutOfBoundsExceptionClass);
  (*env)->DeleteGlobalRef(env, _parsingExceptionClass);
  (*env)->DeleteGlobalRef(env, _externalClass);
  (*env)->DeleteGlobalRef(env, _nodeClass);
  (*env)->DeleteGlobalRef(env, _pointClass);
  (*env)->DeleteGlobalRef(env, _treeClass);
}

jint __throwNPE(JNIEnv *env, const char *message) {
  return (*env)->ThrowNew(env, _nullPointerExceptionClass, message);
}

jint __throwIAE(JNIEnv *env, const char *message) {
  return (*env)->ThrowNew(env, _illegalArgumentExceptionClass, message);
}

jint __throwIOB(JNIEnv *env, jint index) {
  jthrowable exception = (jthrowable)(*env)->NewObject(
      env, _indexOutOfBoundsExceptionClass, _indexOutOfBoundsExceptionConstructor, index);
  return (*env)->Throw(env, exception);
}

jint __throwPE(JNIEnv *env, const char *message) {
  jthrowable exception = (jthrowable)(*env)->NewObject(
      env, _parsingExceptionClass, _parsingExceptionConstructor,
      (*env)->NewStringUTF(env, message));
  return (*env)->Throw(env, exception);
}

jlong __getPointer(JNIEnv *env, jobject objectInstance) {
  return (*env)->GetLongField(env, objectInstance, _externalPointerField);
}

jobject __nodeTree(JNIEnv *env, jobject nodeObject) {
  return (*env)->GetObjectField(env, nodeObject, _nodeTreeField);
}

jobject __marshalNode(JNIEnv *env, TSNode node, jobject treeObject) {
  if (node.id == NULL) {
    return NULL;
  }
  return (*env)->NewObject(env, _nodeClass, _nodeConstructor, (jint)node.context[0],
                           (jint)node.context[1], (jint)node.context[2],
                           (jint)node.context[3], (jlong)(intptr_t)node.id, treeObject);
}

TSNode __unmarshalNode(JNIEnv *env, jobject nodeObject) {
  jobject treeObject = (*env)->GetObjectField(env, nodeObject, _nodeTreeField);
  jlong tree = (treeObject == NULL) ? (jlong)0 : __getPointer(env, treeObject);
  TSNode node;
  node.context[0] = (uint32_t)(*env)->GetIntField(env, nodeObject, _nodeContext0Field);
  node.context[1] = (uint32_t)(*env)->GetIntField(env, nodeObject, _nodeContext1Field);
  node.context[2] = (uint32_t)(*env)->GetIntField(env, nodeObject, _nodeContext2Field);
  node.context[3] = (uint32_t)(*env)->GetIntField(env, nodeObject, _nodeContext3Field);
  node.id = (const void *)(intptr_t)(*env)->GetLongField(env, nodeObject, _nodeIdField);
  node.tree = (const TSTree *)(intptr_t)tree;
  return node;
}

jobject __marshalPoint(JNIEnv *env, TSPoint point) {
  return (*env)->NewObject(env, _pointClass, _pointConstructor, (jint)point.row,
                           (jint)point.column);
}

TSPoint __unmarshalPoint(JNIEnv *env, jobject pointObject) {
  TSPoint point;
  point.row = (uint32_t)(*env)->GetIntField(env, pointObject, _pointRowField);
  point.column = (uint32_t)(*env)->GetIntField(env, pointObject, _pointColumnField);
  return point;
}
