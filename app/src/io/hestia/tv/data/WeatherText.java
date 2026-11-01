package io.hestia.tv.data;

import io.hestia.tv.core.I18n;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** The weather in words. Plain Java: tested on a JVM (tests/java). */
public final class WeatherText {
    private WeatherText() {}

    /** One day of the forecast: "2026-11-02", its WMO code and highest temperature (Celsius). */
    public static final class Day {
        final String date;
        public final int code;
        final double high;

        Day(String date, int code, double high) {
            this.date = date;
            this.code = code;
            this.high = high;
        }
    }

    /** "Mar 14° · Mer 12° · Jeu 9°" in the UI's language. */
    static String forecastLine(List<Day> days, String unitChoice) {
        List<String> parts = new ArrayList<>();
        for (Day d : days) parts.add(dayName(d.date) + " " + degrees(d.high, unitChoice));
        return String.join(" · ", parts);
    }

    private static String dayName(String date) {
        try {
            String name = new SimpleDateFormat("EEE", I18n.locale()).format(new SimpleDateFormat("yyyy-MM-dd", Locale.ROOT).parse(date));
            name = name.replace(".", "");
            return name.isEmpty() ? name : Character.toUpperCase(name.charAt(0)) + name.substring(1);
        } catch (ParseException e) {
            return "";
        }
    }

    private enum Sky { CLEAR, FEW, PARTLY, CLOUDY, FOG, DRIZZLE, HEAVY, SNOW, STORM, RAIN }

    /** Open-Meteo's WMO weather code as a kind of sky. */
    private static Sky sky(int code) {
        if (code == 0) return Sky.CLEAR;
        if (code == 1) return Sky.FEW;
        if (code == 2) return Sky.PARTLY;
        if (code == 3) return Sky.CLOUDY;
        if (code == 45 || code == 48) return Sky.FOG;
        if (code >= 51 && code <= 57) return Sky.DRIZZLE;
        if (code == 65 || code == 67 || code == 82) return Sky.HEAVY;
        if (code >= 71 && code <= 77 || code == 85 || code == 86) return Sky.SNOW;
        if (code >= 95) return Sky.STORM;
        return Sky.RAIN;
    }

    public static String condition(int code, boolean day) {
        if (code < 0) return "";
        Sky s = sky(code);
        if (I18n.LANG_FR.equals(I18n.lang)) {
            switch (s) {
                case CLEAR: return day ? "Ensoleillé" : "Ciel dégagé";
                case FEW: return "Peu nuageux";
                case PARTLY: return "Partiellement nuageux";
                case CLOUDY: return "Nuageux";
                case FOG: return "Brouillard";
                case DRIZZLE: return "Bruine";
                case HEAVY: return "Forte pluie";
                case SNOW: return "Neige";
                case STORM: return "Orages";
                default: return "Pluie";
            }
        }
        if (I18n.LANG_EN.equals(I18n.lang)) {
            switch (s) {
                case CLEAR: return "Clear";
                case FEW: return day ? "Mostly Sunny" : "Mostly Clear";
                case PARTLY: return "Partly Cloudy";
                case CLOUDY: return "Cloudy";
                case FOG: return "Fog";
                case DRIZZLE: return "Drizzle";
                case HEAVY: return "Heavy Rain";
                case SNOW: return "Snow";
                case STORM: return "Thunderstorms";
                default: return "Rain";
            }
        }
        switch (s) {
            case CLEAR: return day ? "Céu limpo" : "Noite limpa";
            case FEW: return "Poucas nuvens";
            case PARTLY: return "Parcialmente nublado";
            case CLOUDY: return "Nublado";
            case FOG: return "Neblina";
            case DRIZZLE: return "Garoa";
            case HEAVY: return "Chuva forte";
            case SNOW: return "Neve";
            case STORM: return "Tempestade";
            default: return "Chuva";
        }
    }

    /** Celsius or Fahrenheit (config.properties "temperature": C, F, or auto = Fahrenheit in the US). */
    public static String degrees(double celsius, String unitChoice) {
        if (Double.isNaN(celsius)) return "—";
        return Math.round(fahrenheit(unitChoice) ? celsius * 9 / 5 + 32 : celsius) + "°";
    }

    private static boolean fahrenheit(String choice) {
        String unit = choice.trim().toUpperCase(Locale.ROOT);
        if (unit.startsWith("F")) return true;
        if (unit.startsWith("C")) return false;
        return "US".equals(Locale.getDefault().getCountry());
    }
}
