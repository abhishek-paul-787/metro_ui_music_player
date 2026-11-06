#pragma once
#include <atomic>
#include <cmath>
#include <algorithm>

// 10-band peaking EQ + preamp, stereo interleaved, double-precision biquads.
class Eq {
public:
    static constexpr int N = 10;
    static constexpr double FREQ[N] = {31, 62, 125, 250, 500, 1000, 2000, 4000, 8000, 16000};

    void set(const float* g, float preampDb, bool en) {
        for (int i = 0; i < N; i++) gains[i].store(g[i]);
        preamp.store(preampDb); enabled.store(en);
    }

    void process(float* data, int frames, int rate) {
        bool en = enabled.load();
        if (!en) { active = false; return; }
        refresh(rate);
        float pg = pre;
        for (int i = 0; i < frames; i++) {
            for (int c = 0; c < 2; c++) {
                double x = data[i * 2 + c] * pg;
                for (int b = 0; b < nb; b++) {
                    Bq& q = bq[b];
                    double y = q.b0 * x + q.z1[c];
                    q.z1[c] = q.b1 * x - q.a1 * y + q.z2[c];
                    q.z2[c] = q.b2 * x - q.a2 * y;
                    x = y;
                }
                data[i * 2 + c] = (float)x;
            }
        }
    }

private:
    struct Bq { double b0 = 1, b1 = 0, b2 = 0, a1 = 0, a2 = 0, z1[2] = {0, 0}, z2[2] = {0, 0}; };
    void refresh(int rate) {
        float g[N]; bool changed = !active || rate != curRate;
        for (int i = 0; i < N; i++) { g[i] = gains[i].load(); if (g[i] != cur[i]) changed = true; }
        float pd = preamp.load(); if (pd != curPre) changed = true;
        if (!changed) return;
        nb = 0;
        for (int i = 0; i < N; i++) {
            cur[i] = g[i];
            if (std::fabs(g[i]) < 0.01f || FREQ[i] >= rate * 0.45) continue;
            double A = std::pow(10.0, g[i] / 40.0), w0 = 2 * M_PI * FREQ[i] / rate;
            double al = std::sin(w0) / (2 * 1.41), cs = std::cos(w0);
            double a0 = 1 + al / A;
            Bq& q = bq[nb];
            q.b0 = (1 + al * A) / a0; q.b1 = -2 * cs / a0; q.b2 = (1 - al * A) / a0;
            q.a1 = -2 * cs / a0; q.a2 = (1 - al / A) / a0;
            nb++;
        }
        curPre = pd; pre = (float)std::pow(10.0, pd / 20.0);
        curRate = rate; active = true;
    }
    std::atomic<float> gains[N] = {}; std::atomic<float> preamp{0}; std::atomic<bool> enabled{false};
    float cur[N] = {}; float curPre = 0, pre = 1; int curRate = 0, nb = 0; bool active = false;
    Bq bq[N];
};
