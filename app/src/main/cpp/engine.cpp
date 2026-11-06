#include <jni.h>
#include <oboe/Oboe.h>
#include <android/log.h>
#include <atomic>
#include <mutex>
#include <vector>
#include <thread>
#include <chrono>
#include <cstring>
#include "resampler.h"
#include "eq.h"

#define LOG(...) __android_log_print(ANDROID_LOG_INFO, "MetroEngine", __VA_ARGS__)

class Engine : public oboe::AudioStreamDataCallback, public oboe::AudioStreamErrorCallback {
public:
    int open(int rate, bool exclusive) {
        std::lock_guard<std::mutex> g(sm);
        closeLocked();
        reqRate = rate; reqExcl = exclusive;
        int actual = openLocked(rate, exclusive);
        if (actual <= 0 && exclusive) actual = openLocked(rate, false);
        if (actual <= 0) actual = openLocked(48000, false);
        return actual;
    }
    void close() { std::lock_guard<std::mutex> g(sm); closeLocked(); }
    bool start() {
        std::lock_guard<std::mutex> g(sm);
        wantPlay = true;
        if (!stream) return false;
        targetGain = 1.f;
        return stream->requestStart() == oboe::Result::OK;
    }
    void pause() {
        { std::lock_guard<std::mutex> g(sm); wantPlay = false; targetGain = 0.f; }
        std::this_thread::sleep_for(std::chrono::milliseconds(40));
        std::lock_guard<std::mutex> g(sm);
        if (stream && !wantPlay) stream->requestPause();
    }
    void flush() {
        std::lock_guard<std::mutex> g(rm);
        rd = wr = count = 0;
        played = 0; written = 0;
        rs.reset();
        rsIn = 0; rsOut = 0;
    }
    void setAbort(bool a) { abortWrite = a; }

    bool write(const uint8_t* data, int bytes, int inRate, int ch, int enc) {
        int bps = enc == 2 ? 2 : enc == 21 ? 3 : 4;
        int frames = bytes / (bps * ch);
        if (frames <= 0) return true;
        tmp.resize((size_t)frames * 2);
        for (int i = 0; i < frames; i++) {
            float s[8] = {0};
            int n = std::min(ch, 8);
            for (int c = 0; c < n; c++) {
                const uint8_t* p = data + ((size_t)i * ch + c) * bps;
                float v;
                if (enc == 2) v = (*(const int16_t*)p) / 32768.f;
                else if (enc == 21) { int32_t x = (p[2] << 24) | (p[1] << 16) | (p[0] << 8); v = (x >> 8) / 8388608.f; }
                else if (enc == 22) v = (*(const int32_t*)p) / 2147483648.f;
                else v = *(const float*)p;
                s[c] = v;
            }
            float l, r;
            if (ch == 1) l = r = s[0];
            else if (ch >= 6) { l = s[0] + 0.707f * s[2] + 0.707f * s[4]; r = s[1] + 0.707f * s[2] + 0.707f * s[5]; l *= 0.5f; r *= 0.5f; }
            else { l = s[0]; r = s[1]; }
            tmp[(size_t)i * 2] = l; tmp[(size_t)i * 2 + 1] = r;
        }
        int outRate = actualRate.load();
        if (outRate <= 0) return false;
        if (rsIn != inRate || rsOut != outRate) { rs.configure(inRate, outRate, 2); rsIn = inRate; rsOut = outRate; }
        rsBuf.clear();
        rs.process(tmp.data(), frames, rsBuf);
        return push(rsBuf.data(), (int)(rsBuf.size() / 2));
    }

    int buffered() { std::lock_guard<std::mutex> g(rm); return (int)(count / 2); }
    int64_t playedFrames() { return played.load(); }
    int64_t writtenFrames() { return written.load(); }
    void setEq(const float* g, float pre, bool en) { eq.set(g, pre, en); }
    void info(int* out) {
        std::lock_guard<std::mutex> g(sm);
        if (!stream) { for (int i = 0; i < 6; i++) out[i] = 0; return; }
        out[0] = stream->getSampleRate();
        out[1] = stream->getSharingMode() == oboe::SharingMode::Exclusive ? 1 : 0;
        out[2] = stream->getFramesPerBurst();
        out[3] = stream->getBufferSizeInFrames();
        out[4] = stream->getDeviceId();
        out[5] = (int)stream->getPerformanceMode();
    }

    oboe::DataCallbackResult onAudioReady(oboe::AudioStream*, void* audio, int32_t n) override {
        float* out = (float*)audio;
        int got = 0;
        if (rm.try_lock()) {
            size_t want = (size_t)n * 2, take = std::min(want, count);
            take &= ~(size_t)1;
            size_t first = std::min(take, cap - rd);
            memcpy(out, &ring[rd], first * sizeof(float));
            if (take > first) memcpy(out + first, &ring[0], (take - first) * sizeof(float));
            rd = (rd + take) % cap; count -= take; got = (int)(take / 2);
            played += got;
            rm.unlock();
        }
        if (got < n) memset(out + (size_t)got * 2, 0, (size_t)(n - got) * 2 * sizeof(float));
        eq.process(out, got, actualRate.load());
        // fade ramp (anti-click on pause/resume)
        float tg = targetGain.load();
        float step = 1.f / (0.02f * (float)std::max(1, actualRate.load()));
        for (int i = 0; i < n; i++) {
            if (curGain < tg) curGain = std::min(tg, curGain + step);
            else if (curGain > tg) curGain = std::max(tg, curGain - step);
            float l = out[i * 2] * curGain, r = out[i * 2 + 1] * curGain;
            out[i * 2] = std::max(-1.f, std::min(1.f, l));
            out[i * 2 + 1] = std::max(-1.f, std::min(1.f, r));
        }
        return oboe::DataCallbackResult::Continue;
    }

    void onErrorAfterClose(oboe::AudioStream*, oboe::Result r) override {
        LOG("stream error %s - reopening", oboe::convertToText(r));
        std::thread([this] {
            std::this_thread::sleep_for(std::chrono::milliseconds(300));
            std::lock_guard<std::mutex> g(sm);
            closeLocked();
            int a = openLocked(reqRate, reqExcl);
            if (a <= 0) a = openLocked(48000, false);
            if (stream && wantPlay) { targetGain = 1.f; stream->requestStart(); }
        }).detach();
    }

private:
    int openLocked(int rate, bool exclusive) {
        oboe::AudioStreamBuilder b;
        b.setDirection(oboe::Direction::Output)
            ->setPerformanceMode(oboe::PerformanceMode::None)
            ->setSharingMode(exclusive ? oboe::SharingMode::Exclusive : oboe::SharingMode::Shared)
            ->setFormat(oboe::AudioFormat::Float)
            ->setFormatConversionAllowed(true)
            ->setChannelCount(2)
            ->setSampleRate(rate)
            ->setSampleRateConversionQuality(oboe::SampleRateConversionQuality::None)
            ->setUsage(oboe::Usage::Media)
            ->setContentType(oboe::ContentType::Music)
            ->setDataCallback(this)
            ->setErrorCallback(this);
        oboe::Result r = b.openStream(stream);
        if (r != oboe::Result::OK) { LOG("open failed %s", oboe::convertToText(r)); stream.reset(); actualRate = 0; return 0; }
        stream->setBufferSizeInFrames(stream->getBufferCapacityInFrames());
        actualRate = stream->getSampleRate();
        curGain = 0;
        if (cap == 0) { std::lock_guard<std::mutex> g(rm); cap = (size_t)384000 * 2 * 3 / 2; ring.assign(cap, 0.f); rd = wr = count = 0; }
        LOG("opened rate=%d req=%d excl=%d burst=%d", actualRate.load(), rate, (int)(stream->getSharingMode() == oboe::SharingMode::Exclusive), stream->getFramesPerBurst());
        return actualRate;
    }
    void closeLocked() {
        if (stream) { stream->stop(); stream->close(); stream.reset(); }
        actualRate = 0;
    }
    bool push(const float* d, int frames) {
        size_t remaining = (size_t)frames * 2;
        while (remaining > 0) {
            if (abortWrite) return false;
            {
                std::lock_guard<std::mutex> g(rm);
                size_t space = cap - count, n = std::min(space & ~(size_t)1, remaining);
                if (n > 0) {
                    size_t first = std::min(n, cap - wr);
                    memcpy(&ring[wr], d, first * sizeof(float));
                    if (n > first) memcpy(&ring[0], d + first, (n - first) * sizeof(float));
                    wr = (wr + n) % cap; count += n; d += n; remaining -= n;
                    written += (int64_t)(n / 2);
                }
            }
            if (remaining > 0) std::this_thread::sleep_for(std::chrono::milliseconds(5));
        }
        return true;
    }

    std::mutex sm, rm;
    std::shared_ptr<oboe::AudioStream> stream;
    std::vector<float> ring; size_t cap = 0, rd = 0, wr = 0, count = 0;
    std::atomic<int64_t> played{0}, written{0};
    std::atomic<bool> abortWrite{false};
    std::atomic<int> actualRate{0};
    std::atomic<float> targetGain{1.f}; float curGain = 0;
    int reqRate = 48000; bool reqExcl = true; bool wantPlay = false;
    Resampler rs; int rsIn = 0, rsOut = 0;
    std::vector<float> tmp, rsBuf;
    Eq eq;
};

static Engine gEngine;

extern "C" {
#define FN(name) Java_com_metro_music_audio_NativeEngine_##name
JNIEXPORT jint JNICALL FN(nOpen)(JNIEnv*, jobject, jint rate, jboolean excl) { return gEngine.open(rate, excl); }
JNIEXPORT void JNICALL FN(nClose)(JNIEnv*, jobject) { gEngine.close(); }
JNIEXPORT jboolean JNICALL FN(nStart)(JNIEnv*, jobject) { return gEngine.start(); }
JNIEXPORT void JNICALL FN(nPause)(JNIEnv*, jobject) { gEngine.pause(); }
JNIEXPORT void JNICALL FN(nFlush)(JNIEnv*, jobject) { gEngine.flush(); }
JNIEXPORT void JNICALL FN(nAbort)(JNIEnv*, jobject, jboolean a) { gEngine.setAbort(a); }
JNIEXPORT jboolean JNICALL FN(nWrite)(JNIEnv* env, jobject, jobject buf, jint off, jint bytes, jint inRate, jint ch, jint enc) {
    auto* p = (uint8_t*)env->GetDirectBufferAddress(buf);
    if (!p) return JNI_FALSE;
    return gEngine.write(p + off, bytes, inRate, ch, enc);
}
JNIEXPORT jint JNICALL FN(nBuffered)(JNIEnv*, jobject) { return gEngine.buffered(); }
JNIEXPORT jlong JNICALL FN(nPlayed)(JNIEnv*, jobject) { return gEngine.playedFrames(); }
JNIEXPORT jlong JNICALL FN(nWritten)(JNIEnv*, jobject) { return gEngine.writtenFrames(); }
JNIEXPORT void JNICALL FN(nSetEq)(JNIEnv* env, jobject, jfloatArray g, jfloat pre, jboolean en) {
    float v[Eq::N] = {0};
    env->GetFloatArrayRegion(g, 0, Eq::N, v);
    gEngine.setEq(v, pre, en);
}
JNIEXPORT jintArray JNICALL FN(nStreamInfo)(JNIEnv* env, jobject) {
    int v[6]; gEngine.info(v);
    jintArray a = env->NewIntArray(6);
    env->SetIntArrayRegion(a, 0, 6, v);
    return a;
}
}
