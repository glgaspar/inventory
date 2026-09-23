package com.pessimaideia.inventory.ui;

import com.pessimaideia.inventory.model.Movement;
import com.pessimaideia.inventory.model.Unit;
import java.text.NumberFormat;
import java.util.Locale;
import androidx.annotation.Nullable;
import java.text.DateFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.TimeZone;
public final class Formats {

    private Formats() {
        // only static methods
    }

    public static String amount(double value, Unit unit, Locale locale) {
        NumberFormat number = NumberFormat.getNumberInstance(locale);
        number.setMaximumFractionDigits(2);
        return number.format(value) + " " + unit.symbol;
    }

    @Nullable
    public static Double parseAmount(String text) {
        String normalized = text.trim().replace(',', '.');
        if (normalized.isEmpty()) return null;
        try {
            double value = Double.parseDouble(normalized);
            if (Double.isNaN(value) || Double.isInfinite(value)) return null;
            return value;
        } catch (NumberFormatException e) {
            return null;
        }
    }

    @Nullable
    public static Date parseIso(String text) {
        SimpleDateFormat format = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US);
        format.setTimeZone(TimeZone.getTimeZone("UTC"));
        format.setLenient(false);
        try {
            return format.parse(text);
        } catch (ParseException e) {
            return null;
        }
    }

    public static String shortDate(Date date, Locale locale) {
        return DateFormat.getDateInstance(DateFormat.SHORT, locale).format(date);
    }

    public static String shortDateTime(Date date, Locale locale) {
        return DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT, locale).format(date);
    }

    public static String signedAmount(double value, Movement.Type type, Unit unit, Locale locale) {
        String sign = type == Movement.Type.IN ? "+" : "\u2212";
        return sign + amount(value, unit, locale);
    }
}
