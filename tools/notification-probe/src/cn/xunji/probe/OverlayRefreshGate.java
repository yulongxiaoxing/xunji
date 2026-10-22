package cn.xunji.probe;

/** Coalesces events without letting a continuous scroll postpone the pending frame. */
public final class OverlayRefreshGate {
    private boolean pending;
    private long lastFrame = -1000;
    public long request(long now) {
        if (pending) return -1;
        pending = true;
        return Math.max(0, lastFrame + 32 - now);
    }
    public void frameStarted(long now) { pending = false; lastFrame = now; }
    public void reset() { pending = false; lastFrame = -1000; }
    public static boolean touchSafe(long now, long lastScroll, long lastMove) {
        return now - lastScroll >= 80 && now - lastMove >= 64;
    }
}
