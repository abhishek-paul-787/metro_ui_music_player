#pragma once
#include <vector>
#include <cmath>
#include <cstdint>
#include <algorithm>

// Polyphase Kaiser-windowed sinc resampler (64 taps, 1024 phases, linear phase interpolation).
// Stopband roughly -100 dB. Interleaved float in/out.
class Resampler {
public:
    static constexpr int HALF = 32;
    static constexpr int TAPS = HALF * 2;
    static constexpr int PHASES = 1024;

    void configure(double inRate, double outRate, int channels) {
        ch = channels;
        step = inRate / outRate;
        passthrough = (inRate == outRate);
        buf.assign((size_t)HALF * ch, 0.f);
        pos = HALF;
        if (passthrough) return;
        double fc = std::min(1.0, outRate / inRate) * 0.985;
        const double beta = 10.0;
        tab.assign((size_t)(PHASES + 1) * TAPS, 0.f);
        double i0b = bessel0(beta);
        for (int p = 0; p <= PHASES; p++) {
            double frac = (double)p / PHASES, sum = 0;
            std::vector<double> row(TAPS);
            for (int k = 0; k < TAPS; k++) {
                double d = (k - HALF + 1) - frac;
                double x = fc * d;
                double s = (std::fabs(x) < 1e-9) ? 1.0 : std::sin(M_PI * x) / (M_PI * x);
                double r = d / HALF;
                double w = (std::fabs(r) >= 1.0) ? 0.0 : bessel0(beta * std::sqrt(1.0 - r * r)) / i0b;
                row[k] = fc * s * w;
                sum += row[k];
            }
            for (int k = 0; k < TAPS; k++) tab[(size_t)p * TAPS + k] = (float)(row[k] / sum);
        }
    }

    void reset() { buf.assign((size_t)HALF * ch, 0.f); pos = HALF; }

    void process(const float* in, int nFrames, std::vector<float>& out) {
        if (passthrough) { out.insert(out.end(), in, in + (size_t)nFrames * ch); return; }
        buf.insert(buf.end(), in, in + (size_t)nFrames * ch);
        int64_t avail = (int64_t)(buf.size() / ch);
        float coef[TAPS];
        while (true) {
            int64_t i0 = (int64_t)pos;
            if (i0 + HALF >= avail) break;
            double fp = (pos - (double)i0) * PHASES;
            int p = (int)fp; float a = (float)(fp - p);
            const float* r0 = &tab[(size_t)p * TAPS];
            const float* r1 = r0 + TAPS;
            for (int k = 0; k < TAPS; k++) coef[k] = r0[k] + a * (r1[k] - r0[k]);
            const float* x = &buf[(size_t)(i0 - HALF + 1) * ch];
            if (ch == 2) {
                float l = 0, r = 0;
                for (int k = 0; k < TAPS; k++) { l += coef[k] * x[2 * k]; r += coef[k] * x[2 * k + 1]; }
                out.push_back(l); out.push_back(r);
            } else {
                for (int c = 0; c < ch; c++) {
                    float acc = 0;
                    for (int k = 0; k < TAPS; k++) acc += coef[k] * x[k * ch + c];
                    out.push_back(acc);
                }
            }
            pos += step;
        }
        int64_t keep = (int64_t)pos - HALF + 1;
        if (keep > 0) { buf.erase(buf.begin(), buf.begin() + (size_t)keep * ch); pos -= (double)keep; }
    }

private:
    static double bessel0(double x) {
        double sum = 1, term = 1, q = x * x / 4;
        for (int k = 1; k < 50; k++) { term *= q / ((double)k * k); sum += term; if (term < 1e-12 * sum) break; }
        return sum;
    }
    int ch = 2; double step = 1, pos = HALF; bool passthrough = true;
    std::vector<float> buf, tab;
};
