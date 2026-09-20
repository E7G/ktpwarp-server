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
FILE *log_file = nullptr;
pthread_mutex_t log_mutex = PTHREAD_MUTEX_INITIALIZER;
const char *TAG = "KTPWARP-NODE";

void write_output(int priority, const char *buffer, size_t size) {
    std::string message(buffer, size);
    while (!message.empty() && (message.back() == '\n' || message.back() == '\r')) {
        message.pop_back();
    }
    if (!message.empty()) __android_log_write(priority, TAG, message.c_str());

    if (log_file != nullptr && size > 0) {
        pthread_mutex_lock(&log_mutex);
        fwrite(buffer, 1, size, log_file);
        if (buffer[size - 1] != '\n') fputc('\n', log_file);
        fflush(log_file);
        pthread_mutex_unlock(&log_mutex);
    }
}

void *stderr_thread(void *) {
    ssize_t size;
    char buffer[4096];
    while ((size = read(pipe_stderr[0], buffer, sizeof(buffer))) > 0) {
        write_output(ANDROID_LOG_ERROR, buffer, static_cast<size_t>(size));
    }
    return nullptr;
}

void *stdout_thread(void *) {
    ssize_t size;
    char buffer[4096];
    while ((size = read(pipe_stdout[0], buffer, sizeof(buffer))) > 0) {
        write_output(ANDROID_LOG_INFO, buffer, static_cast<size_t>(size));
    }
    return nullptr;
}

int redirect_stdio(const char *log_path) {
    if (redirect_started) return 0;
    redirect_started = true;

    if (log_path != nullptr && log_path[0] != '\0') {
        log_file = fopen(log_path, "a");
        if (log_file != nullptr) setvbuf(log_file, nullptr, _IONBF, 0);
    }

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
        jobjectArray arguments,
        jstring logPath) {

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

    const char *log_path_chars = nullptr;
    if (logPath != nullptr) log_path_chars = env->GetStringUTFChars(logPath, nullptr);

    const int redirect_result = redirect_stdio(log_path_chars);

    if (logPath != nullptr && log_path_chars != nullptr) {
        env->ReleaseStringUTFChars(logPath, log_path_chars);
    }

    if (redirect_result != 0) {
        __android_log_write(ANDROID_LOG_ERROR, TAG, "Failed to redirect Node stdout/stderr.");
    }

    return static_cast<jint>(node::Start(argc, argv.data()));
}
