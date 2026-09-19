#include <jni.h>
#include <android/log.h>
#include <node.h>
#include <pthread.h>
#include <unistd.h>
#include <cstdio>
#include <cstring>
#include <string>
#include <vector>

namespace {
int pipe_stdout[2];
int pipe_stderr[2];
pthread_t thread_stdout;
pthread_t thread_stderr;
bool redirect_started = false;
const char *TAG = "KTPWARP-NODE";

void *stderr_thread(void *) {
    ssize_t size;
    char buffer[4096];
    while ((size = read(pipe_stderr[0], buffer, sizeof(buffer) - 1)) > 0) {
        if (buffer[size - 1] == '\n') --size;
        buffer[size] = 0;
        __android_log_write(ANDROID_LOG_ERROR, TAG, buffer);
    }
    return nullptr;
}

void *stdout_thread(void *) {
    ssize_t size;
    char buffer[4096];
    while ((size = read(pipe_stdout[0], buffer, sizeof(buffer) - 1)) > 0) {
        if (buffer[size - 1] == '\n') --size;
        buffer[size] = 0;
        __android_log_write(ANDROID_LOG_INFO, TAG, buffer);
    }
    return nullptr;
}

int redirect_stdio() {
    if (redirect_started) return 0;
    redirect_started = true;

    setvbuf(stdout, nullptr, _IONBF, 0);
    setvbuf(stderr, nullptr, _IONBF, 0);

    if (pipe(pipe_stdout) != 0 || pipe(pipe_stderr) != 0) return -1;
    dup2(pipe_stdout[1], STDOUT_FILENO);
    dup2(pipe_stderr[1], STDERR_FILENO);

    if (pthread_create(&thread_stdout, nullptr, stdout_thread, nullptr) != 0) return -1;
    pthread_detach(thread_stdout);

    if (pthread_create(&thread_stderr, nullptr, stderr_thread, nullptr) != 0) return -1;
    pthread_detach(thread_stderr);

    return 0;
}
}

extern "C"
JNIEXPORT jint JNICALL
Java_io_github_celeswuff_ktpwarp_server_NodeServerService_startNodeWithArguments(
        JNIEnv *env,
        jobject,
        jobjectArray arguments) {

    const jsize argc = env->GetArrayLength(arguments);
    std::vector<std::string> strings;
    strings.reserve(argc);

    size_t total = 0;
    for (jsize i = 0; i < argc; ++i) {
        auto value = static_cast<jstring>(env->GetObjectArrayElement(arguments, i));
        const char *chars = env->GetStringUTFChars(value, nullptr);
        strings.emplace_back(chars);
        total += strings.back().size() + 1;
        env->ReleaseStringUTFChars(value, chars);
        env->DeleteLocalRef(value);
    }

    std::vector<char> storage(total);
    std::vector<char *> argv(argc);
    char *cursor = storage.data();

    for (jsize i = 0; i < argc; ++i) {
        std::memcpy(cursor, strings[i].c_str(), strings[i].size() + 1);
        argv[i] = cursor;
        cursor += strings[i].size() + 1;
    }

    if (redirect_stdio() != 0) {
        __android_log_write(
                ANDROID_LOG_ERROR,
                TAG,
                "Failed to redirect Node stdout/stderr to logcat.");
    }

    return static_cast<jint>(node::Start(argc, argv.data()));
}
