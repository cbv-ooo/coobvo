package dev.android.dynamicisland.island.source;

import android.net.TrafficStats;
import android.os.SystemClock;

/**
 * Samples device-wide TrafficStats (no INTERNET permission).
 * Soft-fail: any bad reading returns 0 speed.
 */
final class NetSpeedTracker {
    private long lastRx = -1;
    private long lastTx = -1;
    private long lastAt;
    private long rxBps;
    private long txBps;

    void sample() {
        try {
            long rx = TrafficStats.getTotalRxBytes();
            long tx = TrafficStats.getTotalTxBytes();
            long now = SystemClock.elapsedRealtime();
            if (rx < 0 || tx < 0) return;
            if (lastRx >= 0 && lastTx >= 0 && now > lastAt) {
                long dt = Math.max(1, now - lastAt);
                long dr = Math.max(0, rx - lastRx);
                long dtb = Math.max(0, tx - lastTx);
                if (rx >= lastRx) rxBps = dr * 1000 / dt;
                if (tx >= lastTx) txBps = dtb * 1000 / dt;
            }
            lastRx = rx;
            lastTx = tx;
            lastAt = now;
        } catch (Throwable ignored) {
        }
    }

    long busierBps() {
        return Math.max(rxBps, txBps);
    }

    boolean rxDominant() {
        return rxBps >= txBps;
    }

    static String format(long bps) {
        if (bps < 1024) return bps + " B/s";
        if (bps < 1024 * 1024) return (bps / 1024) + " KB/s";
        return String.format("%.1f MB/s", bps / (1024.0 * 1024.0));
    }
}
