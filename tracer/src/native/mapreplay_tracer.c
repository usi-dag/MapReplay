#include <jvmti.h>
#include <jni.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <pthread.h>
#include <stdint.h>

static FILE *trace_file = NULL;
static FILE *log_file = NULL;
static pthread_mutex_t tracer_mutex = PTHREAD_MUTEX_INITIALIZER;

static int log_enabled = 0;

static jboolean JNICALL nativeIsLogEnabled(JNIEnv *env, jclass cls)
{
    return log_enabled ? JNI_TRUE : JNI_FALSE;
}

static void init_tracer_config(void)
{
    const char *env = getenv("LOG_TRACER");
    log_enabled = (env && strcmp(env, "true") == 0) ? 1 : 0;
}

/* Open files lazily to avoid issues during VM initialization */
static void open_files_lazy(void)
{
    pthread_mutex_lock(&tracer_mutex);
    const char *base = getenv("TRACE_FILE");
    if (!base || strlen(base) == 0)
        base = "trace";  // fallback default if TRACE_FILE not set

    char rip_path[512];
    snprintf(rip_path, sizeof(rip_path), "%s.rip", base);

    if (!trace_file)
        trace_file = fopen(rip_path, "ab");

    /* Open log file only if enabled */
    if (log_enabled && !log_file) {
        char log_path[512];
        snprintf(log_path, sizeof(log_path), "%s.log", base);
        log_file = fopen(log_path, "ab");
    }

    pthread_mutex_unlock(&tracer_mutex);
}

static void close_files(void)
{
    pthread_mutex_lock(&tracer_mutex);
    if (trace_file)
    {
        fclose(trace_file);
        trace_file = NULL;
    }
    if (log_file)
    {
        fclose(log_file);
        log_file = NULL;
    }
    pthread_mutex_unlock(&tracer_mutex);
}

/* Native method implementations */

static inline void write_byte(FILE *f, jbyte v)
{
    fputc((unsigned char)v, f);
}

static inline void write_int(FILE *f, jint v)
{
    unsigned char buf[4];
    buf[0] = (v >> 24) & 0xFF;
    buf[1] = (v >> 16) & 0xFF;
    buf[2] = (v >> 8) & 0xFF;
    buf[3] = v & 0xFF;
    fwrite(buf, 1, 4, f);
}

static inline void write_long(FILE *f, jlong v)
{
    unsigned char buf[8];
    uint64_t value = (uint64_t)v;
    buf[0] = (value >> 56) & 0xFF;
    buf[1] = (value >> 48) & 0xFF;
    buf[2] = (value >> 40) & 0xFF;
    buf[3] = (value >> 32) & 0xFF;
    buf[4] = (value >> 24) & 0xFF;
    buf[5] = (value >> 16) & 0xFF;
    buf[6] = (value >> 8) & 0xFF;
    buf[7] = value & 0xFF;
    fwrite(buf, 1, 8, f);
}

static inline void write_float(FILE *f, jfloat v)
{
    union {
        jfloat f;
        uint32_t i;
    } u;
    u.f = v;
    write_int(f, (jint)u.i); // reuse big-endian int encoding
}

/* -------------------------------------------------------------
 * Native methods
 * ------------------------------------------------------------- */

static void JNICALL nativeInit(JNIEnv *env, jclass cls)
{
    open_files_lazy();
}

static void JNICALL nativeTraceByte(JNIEnv *env, jclass cls, jbyte v)
{
    pthread_mutex_lock(&tracer_mutex);
    if (trace_file)
        write_byte(trace_file, v);
    pthread_mutex_unlock(&tracer_mutex);
}

static void JNICALL nativeTraceInt(JNIEnv *env, jclass cls, jint v)
{
    pthread_mutex_lock(&tracer_mutex);
    if (trace_file)
        write_int(trace_file, v);
    pthread_mutex_unlock(&tracer_mutex);
}

static void JNICALL nativeTraceLong(JNIEnv *env, jclass cls, jlong v)
{
    pthread_mutex_lock(&tracer_mutex);
    if (trace_file)
        write_long(trace_file, v);
    pthread_mutex_unlock(&tracer_mutex);
}

static void JNICALL nativeTraceFloat(JNIEnv *env, jclass cls, jfloat v)
{
    pthread_mutex_lock(&tracer_mutex);
    if (trace_file)
        write_float(trace_file, v);
    pthread_mutex_unlock(&tracer_mutex);
}

static void JNICALL nativeLog(JNIEnv *env, jclass cls, jstring msg)
{
    if (!log_enabled || !msg)
        return;
    const char *utf = (*env)->GetStringUTFChars(env, msg, NULL);
    if (!utf)
        return;
    pthread_mutex_lock(&tracer_mutex);
    if (log_file)
        fputs(utf, log_file);
    pthread_mutex_unlock(&tracer_mutex);
    (*env)->ReleaseStringUTFChars(env, msg, utf);
}

static void JNICALL nativeClose(JNIEnv *env, jclass cls)
{
    close_files();
}

/* Native method table */
static JNINativeMethod tracer_methods[] = {
    {"nativeInit", "()V", (void *)nativeInit},
    {"nativeTraceByte", "(B)V", (void *)nativeTraceByte},
    {"nativeTraceInt", "(I)V", (void *)nativeTraceInt},
    {"nativeTraceLong", "(J)V", (void *)nativeTraceLong},
    {"nativeTraceFloat", "(F)V", (void *)nativeTraceFloat},
    {"nativeLog", "(Ljava/lang/String;)V", (void *)nativeLog},
    {"nativeClose", "()V", (void *)nativeClose}};

static int registered = 0;

/* Helper: register natives for java/util/NativeTracer 
   This should be called after NativeTracer has been loaded, 
   but not earlier than the JNI initialization is complete. 
*/
static void register_natives(JNIEnv *env)
{
    if (registered == 1)
        return;

    jclass cls = (*env)->FindClass(env, "jdk/internal/mapreplay/NativeTracer");
    if (!cls)
    {
        (*env)->ExceptionClear(env);
        return;
    }
    jint count = sizeof(tracer_methods) / sizeof(tracer_methods[0]);
    if ((*env)->RegisterNatives(env, cls, tracer_methods, count) != JNI_OK)
    {
        fprintf(stderr, "[tracer_agent] RegisterNatives failed\n");
    }
    else
    {
        registered = 1;
    }

}

/* JVMTI callback: class prepare.
    This is a workaround: as soon as the JVM is ready to call the callback to class prepare (for any class), 
    it means that JNI is ready, so we can call register_natives. Note that doing so at class loading 
    rather than class preparation would be too early!
*/
static void JNICALL callbackClassPrepare(jvmtiEnv *jvmti, JNIEnv *jnienv, jthread thread, jclass klass)
{
    register_natives(jnienv);
}


JNIEXPORT jint JNICALL Agent_OnLoad(JavaVM *vm, char *options, void *reserved)
{
    jvmtiEnv *jvmti;
    if ((*vm)->GetEnv(vm, (void **)&jvmti, JVMTI_VERSION_1_2) != JNI_OK)
    {
        fprintf(stderr, "[tracer_agent] Failed to get JVMTI env\n");
        return JNI_ERR;
    }

    /* Request ClassPrepare events */
    jvmtiCapabilities caps = {0};
    caps.can_generate_all_class_hook_events = 1;
    (*jvmti)->AddCapabilities(jvmti, &caps);

    /* Set callbacks */
    jvmtiEventCallbacks cb = {0};
    cb.ClassPrepare = &callbackClassPrepare;
    (*jvmti)->SetEventCallbacks(jvmti, &cb, sizeof(cb));

    /* Enable event notification */
    (*jvmti)->SetEventNotificationMode(jvmti, JVMTI_ENABLE, JVMTI_EVENT_CLASS_PREPARE, NULL);

    init_tracer_config();

    return JNI_OK;
}

/* Cleanup */
JNIEXPORT void JNICALL Agent_OnUnload(JavaVM *vm)
{
    close_files();
}
