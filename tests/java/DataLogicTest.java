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

        require(City.of("Lyon", "Auvergne-Rhône-Alpes", "France", 45.75, 4.85).region.equals("Auvergne-Rhône-Alpes, France"), "region");
        require(City.of("Paris", "Paris", "France", 48.85, 2.35).region.equals("France"), "no repeated name");
        require(City.of("<b>Nîmes</b>", "", "", 43.8, 4.3).name.equals("Nîmes"), "plain text only");
        require(City.of("", "", "", 1, 1) == null, "no name");
        require(City.of("X", "", "", 91, 1) == null && City.of("X", "", "", 1, -181) == null, "impossible place");
        require(City.of("X", "", "", Double.NaN, 1) == null, "coordinate missing from the answer");
        require(City.coordinate(45.75) .equals("45.7500") && City.coordinate(-4.5).equals("-4.5000"), "coordinate format");
        java.util.Locale.setDefault(java.util.Locale.FRANCE);
        require(City.coordinate(45.75).equals("45.7500"), "no decimal comma on a French TV");
        require(City.searchUrl("x", "fr") == null && City.searchUrl("  ", "fr") == null && City.searchUrl(null, "fr") == null, "too short");
        String url = City.searchUrl(" Saint-Étienne & co#x ", "fr");
        require(url.startsWith("https://geocoding-api.open-meteo.com/") && url.endsWith("&language=fr&name=Saint-%C3%89tienne+%26+co%23x"), url);

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
