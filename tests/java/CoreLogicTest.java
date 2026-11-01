package io.hestia.tv.core;

import java.util.GregorianCalendar;
import java.util.Calendar;

public final class CoreLogicTest {
    private static void require(boolean condition, String what) {
        if (!condition) throw new AssertionError(what);
    }

    public static void main(String[] args) {
        I18n.apply("fr");
        require(I18n.tr("Som", "Sound").equals("Son"), "French from the table");
        require(I18n.tr("x", "Not in the table").equals("Not in the table"), "English when missing");
        I18n.apply("pt-BR");
        require(I18n.tr("Som", "Sound").equals("Som"), "Portuguese at the call");
        I18n.apply("en");
        require(I18n.tr("Som", "Sound").equals("Sound"), "English at the call");

        long evening = new GregorianCalendar(2026, Calendar.OCTOBER, 22, 21, 47).getTimeInMillis();
        TimeText.h24 = false;
        require(TimeText.clock(evening).equals("9:47"), "English 12 h");
        TimeText.h24 = true;
        require(TimeText.clock(evening).equals("21:47"), "English 24 h");
        require(TimeText.date(evening).equals("Thursday, October 22"), TimeText.date(evening));
        I18n.apply("fr");
        TimeText.h24 = false;
        require(TimeText.clock(evening).equals("21:47"), "French is always 24 h");
        require(TimeText.dateLine(evening).equals("Jeudi 22 octobre"), TimeText.dateLine(evening));
        require(TimeText.minutes(187_000).equals("3:07"), "track position");
        require(TimeText.capitalized("").isEmpty(), "empty");
        System.out.println("checks passed");
    }
}
