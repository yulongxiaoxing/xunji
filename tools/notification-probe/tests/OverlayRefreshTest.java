package cn.xunji.probe;

public final class OverlayRefreshTest {
    private static void check(boolean ok, String message) { if (!ok) throw new AssertionError(message); }
    public static void main(String[] args) {
        OverlayRefreshGate gate = new OverlayRefreshGate();
        check(gate.request(1000) == 0, "The first refresh should not wait for a quiet period");
        check(gate.request(1005) == -1, "New events must not replace an already queued refresh");
        check(gate.request(1010) == -1, "Continuous scroll must not postpone the queued frame");
        gate.frameStarted(1010);
        check(gate.request(1011) == 31, "Updates should be throttled relative to the last frame");
        check(gate.request(1030) == -1, "Events during a throttle window must coalesce");
        gate.frameStarted(1042);
        check(gate.request(1100) == 0, "A quiet screen should react immediately to a new event");
        gate.reset();
        check(gate.request(1101) == 0, "Session cleanup must not strand a pending refresh");
        check(!OverlayRefreshGate.touchSafe(1100, 1090, 1000), "Fresh scroll must block clicking stale positions");
        check(!OverlayRefreshGate.touchSafe(1100, 1000, 1090), "Fresh movement must block clicking rebound rows");
        check(OverlayRefreshGate.touchSafe(1200, 1000, 1100), "Clicking must resume after positions settle");
        System.out.println("10 overlay refresh and touch-safety checks passed");
    }
}
