
package com.pessimaideia.inventory.ui;

import static org.junit.Assert.assertEquals;

import com.pessimaideia.inventory.model.Movement;
import com.pessimaideia.inventory.model.Unit;

import org.junit.Test;

import java.util.Date;
import java.util.Locale;

import static org.junit.Assert.assertNull;

public class FormatsTest {

    private static final Locale PT_BR = Locale.forLanguageTag("pt-BR");

    @Test
    public void wholeNumberHasNoDecimals() {
        assertEquals("5 kg", Formats.amount(5, Unit.KG, Locale.US));
    }

    @Test
    public void decimalSeparatorFollowsLocale() {
        assertEquals("1.5 kg", Formats.amount(1.5, Unit.KG, Locale.US));
        assertEquals("1,5 kg", Formats.amount(1.5, Unit.KG, PT_BR));
    }

    @Test
    public void thousandsSeparatorFollowsLocale() {
        assertEquals("1,800 mL", Formats.amount(1800, Unit.ML, Locale.US));
        assertEquals("1.800 mL", Formats.amount(1800, Unit.ML, PT_BR));
    }

    @Test
    public void roundsToTwoDecimals() {
        assertEquals("0.33 L", Formats.amount(1.0 / 3, Unit.L, Locale.US));
    }

    @Test
    public void parseAcceptsDotOrComma() {
        assertEquals(1.5, Formats.parseAmount("1.5"), 0.0);
        assertEquals(1.5, Formats.parseAmount("1,5"), 0.0);
        assertEquals(2.0, Formats.parseAmount(" 2 "), 0.0);
    }

    @Test
    public void parseRejectsNonNumbers() {
        assertNull(Formats.parseAmount(""));
        assertNull(Formats.parseAmount("abc"));
        assertNull(Formats.parseAmount("1.000,5"));
        assertNull(Formats.parseAmount("NaN"));
    }

    @Test
    public void parseIsoReadsUtcTimestamps() {
        assertEquals(new Date(0), Formats.parseIso("1970-01-01T00:00:00Z"));
        assertEquals(new Date(90061000L), Formats.parseIso("1970-01-02T01:01:01Z"));
    }

    @Test
    public void parseIsoRejectsOtherText() {
        assertNull(Formats.parseIso("yesterday"));
        assertNull(Formats.parseIso("2026-13-40T00:00:00Z"));
    }
    @Test
    public void signedAmountShowsDirection() {
        assertEquals("+5 kg", Formats.signedAmount(5, Movement.Type.IN, Unit.KG, Locale.US));
        assertEquals("\u22121,5 L", Formats.signedAmount(1.5, Movement.Type.OUT, Unit.L, PT_BR));
    }
}
