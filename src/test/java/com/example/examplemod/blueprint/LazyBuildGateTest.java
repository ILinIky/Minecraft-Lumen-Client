package com.example.examplemod.blueprint;

public final class LazyBuildGateTest {
    private static int checks;
    private static void check(boolean value, String why) { checks++; if (!value) throw new AssertionError(why); }
    public static void main(String[] args) {
        var toggle = new LazyBuildGate();
        check(toggle.toggle() && toggle.active(), "First shortcut press enables continuous building");
        check(toggle.maySend(0) && toggle.maySend(100), "Releasing the key does not end toggle session");
        toggle.submitted(100, 6);
        check(!toggle.toggle() && !toggle.active(), "Second shortcut press stops even while awaiting server");
        toggle.acknowledge(true);
        check(!toggle.active(), "Late response cannot restart disabled toggle");
        toggle.toggle(); toggle.stop();
        check(!toggle.active() && !toggle.maySend(1000), "Safety stop disables automatic continuation");
        check(toggle.toggle() && toggle.active(), "Single fresh press restarts after a stop");
        var box = new net.minecraft.world.phys.AABB(0, 0, 0, 1, 1, 1);
        var eye = new net.minecraft.world.phys.Vec3(4.49, 1.6, 0.95);
        var edge = LazyBuildGeometry.nearestFacePoint(box, net.minecraft.core.Direction.UP, eye);
        check(eye.distanceToSqr(edge) < 3.6 * 3.6 && eye.distanceToSqr(new net.minecraft.world.phys.Vec3(0.5, 1, 0.5)) > 3.6 * 3.6,
                "Reachable edge remains available when face center is out of reach");
        var slab = new net.minecraft.world.phys.AABB(0, 0, 0, 1, 0.5, 1);
        check(LazyBuildGeometry.nearestFacePoint(slab, net.minecraft.core.Direction.UP, eye).y == 0.5,
                "Surface sample respects slab outline height");
        for (var face : net.minecraft.core.Direction.values()) {
            var point = LazyBuildGeometry.nearestFacePoint(box, face, eye);
            check(point.x >= 0 && point.x <= 1 && point.y >= 0 && point.y <= 1 && point.z >= 0 && point.z <= 1,
                    "Surface sample stays inside outline bounds for " + face);
        }
        var gate = new LazyBuildGate();
        check(!gate.begin(false) && !gate.maySend(0), "Never build without explicit input");
        check(gate.begin(true) && gate.maySend(0), "Explicit explicit input starts session");
        gate.submitted(0, 6);
        check(!gate.maySend(6) && !gate.maySend(50), "Local prediction or elapsed interval never replaces server confirmation");
        check(!gate.timedOut(59), "Allow server time to respond");
        gate.acknowledge(true);
        check(gate.confirmed() == 1 && gate.maySend(6), "Matching server state permits next placement");
        gate.acknowledge(true);
        check(gate.confirmed() == 1, "Duplicate server update does not double count");
        gate.submitted(6, 10);
        gate.acknowledge(true);
        check(!gate.maySend(15) && gate.maySend(16), "Pacing applies even with immediate confirmation");
        gate.submitted(16, 1);
        gate.acknowledge(true);
        check(!gate.maySend(19) && gate.maySend(20), "Never exceed five placements per second");
        gate.submitted(20, 6);
        gate.acknowledge(false);
        check(gate.state() == LazyBuildGate.State.STOPPED, "Server rejection stops session");
        check(!gate.begin(true) && !gate.maySend(1000), "No automatic retry after rejection");
        gate.acknowledge(true);
        check(gate.state() == LazyBuildGate.State.STOPPED, "Late server response cannot resume stopped session");
        gate.release();
        check(gate.begin(true) && gate.confirmed() == 0, "Explicit reset rearms session");
        gate.submitted(100, 6);
        check(gate.timedOut(160) && !gate.maySend(1000), "Timeout stops instead of flooding retries");
        gate.release(); gate.begin(true); gate.submitted(200, 6);
        gate.release(); gate.acknowledge(true);
        check(gate.state() == LazyBuildGate.State.IDLE && gate.confirmed() == 0, "Cancellation clears pending client bookkeeping");
        gate.begin(true); gate.stop();
        check(!gate.maySend(2000) && !gate.begin(true), "Menu/blueprint/manual-slot change requires explicit restart");
        gate.release(); gate.begin(true); gate.submitted(300, 6);
        try { gate.submitted(301, 6); throw new AssertionError("Allowed overlapping placement"); }
        catch (IllegalStateException expected) { checks++; }
        System.out.println("LazyBuildGateTest: " + checks + " checks passed");
    }
}
