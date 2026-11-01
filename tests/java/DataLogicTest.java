package io.hestia.tv.data;

import io.hestia.tv.core.I18n;

public final class DataLogicTest {
    private static void require(boolean condition, String what) {
        if (!condition) throw new AssertionError(what);
    }

    public static void main(String[] args) {
        long hour = 3600_000L, now = 100 * hour;
        require(Freshness.isStale(now, 0, hour), "never fetched");
        require(Freshness.isStale(now, now - 2 * hour, hour), "too old");
        require(!Freshness.isStale(now, now - hour / 2, hour), "still fresh");
        require(Freshness.isStale(now, now + hour, hour), "clock went back");

        I18n.apply("fr");
        require(WeatherText.condition(0, true).equals("Ensoleillé"), "fr clear day");
        require(WeatherText.condition(0, false).equals("Ciel dégagé"), "fr clear night");
        require(WeatherText.condition(-1, true).isEmpty(), "no reading");
        I18n.apply("en");
        require(WeatherText.condition(45, true).equals("Fog"), "en fog");
        require(WeatherText.condition(82, true).equals("Heavy Rain"), "en heavy rain");
        require(WeatherText.condition(61, true).equals("Rain"), "en rain");
        I18n.apply("pt");
        require(WeatherText.condition(95, true).equals("Tempestade"), "pt storm");

        require(WeatherText.degrees(20, "C").equals("20°"), "celsius");
        require(WeatherText.degrees(20, "F").equals("68°"), "fahrenheit");
        require(WeatherText.degrees(Double.NaN, "C").equals("—"), "no temperature");
        System.out.println("checks passed");
    }
}
