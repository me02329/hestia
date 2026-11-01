package io.hestia.tv.ui;

public final class UiLogicTest {
    private static void require(boolean condition, String what) {
        if (!condition) throw new AssertionError(what);
    }

    public static void main(String[] args) {
        require(Colors.clamp(2) == 1 && Colors.clamp(-1) == 0, "clamp");
        require(Colors.alpha(0xFF123456, 0.5f) == 0x80123456, "alpha");
        require(Colors.alpha(0x80123456, 1) == 0x80123456, "alpha keeps the base");
        require(Colors.blend(0xFF000000, 0xFFFFFFFF, 0) == 0xFF000000, "blend start");
        require(Colors.blend(0xFF000000, 0xFFFFFFFF, 1) == 0xFFFFFFFF, "blend end");
        Spring s = new Spring(0.3f, 1f, 0);
        s.target = 1;
        for (int i = 0; i < 300 && s.step(1 / 60f); i++) { }
        require(!s.step(1 / 60f) && s.value == 1, "a spring settles on its target");
        s.snap(0);
        require(s.value == 0 && s.settled(), "snap");
        System.out.println("checks passed");
    }
}
