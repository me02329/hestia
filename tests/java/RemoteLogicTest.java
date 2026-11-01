package io.hestia.tv.remote;

public final class RemoteLogicTest {
    private static void require(boolean condition, String what) {
        if (!condition) throw new AssertionError(what);
    }

    public static void main(String[] args) {
        require("com.netflix.ninja".equals(KeyAction.appOf(KeyAction.app("com.netflix.ninja"))), "app round trip");
        require(KeyAction.appOf("app:evil/../pkg") == null, "malformed package refused");
        require(KeyAction.appOf("app:") == null, "empty package refused");
        require(KeyAction.appOf(KeyAction.PANEL) == null, "not an app");
        String id = "com.tcl.tvinput/.HdmiInputService/HW5";
        require(id.equals(KeyAction.inputOf(KeyAction.input(id))), "input round trip");
        require(KeyAction.inputOf("input:") == null, "empty input refused");
        require(KeyAction.inputOf(KeyAction.LIVE) == null, "not an input");
        System.out.println("checks passed");
    }
}
