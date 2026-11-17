package com.nadia.plugin.worldmap.util;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

public class IcaoNNumberConverterTest {

    @Test
    public void testKnownIcaoToNNumberConversions() {
        assertEquals("N1", IcaoNNumberConverter.icaoToNNumber("A00001"));
        assertEquals("N900AA", IcaoNNumberConverter.icaoToNNumber("AC6F2D"));
        assertEquals("N12345", IcaoNNumberConverter.icaoToNNumber("A061D9"));
        assertEquals("N86QU", IcaoNNumberConverter.icaoToNNumber("ABCDEF"));
    }

    @Test
    public void testKnownNNumberToIcaoConversions() {
        assertEquals("a00001", IcaoNNumberConverter.nNumberToIcao("N1"));
        assertEquals("ac6f2d", IcaoNNumberConverter.nNumberToIcao("N900AA"));
        assertEquals("a061d9", IcaoNNumberConverter.nNumberToIcao("N12345"));
        assertEquals("abcdef", IcaoNNumberConverter.nNumberToIcao("N86QU"));
    }

    @Test
    public void testCaseInsensitivityAndHexPrefix() {
        assertEquals("N900AA", IcaoNNumberConverter.icaoToNNumber("ac6f2d"));
        assertEquals("N900AA", IcaoNNumberConverter.icaoToNNumber("0xac6f2d"));
        assertEquals("ac6f2d", IcaoNNumberConverter.nNumberToIcao("n900aa"));
    }

    @Test
    public void testInvalidInputs() {
        assertNull(IcaoNNumberConverter.icaoToNNumber(null));
        assertNull(IcaoNNumberConverter.icaoToNNumber(""));
        assertNull(IcaoNNumberConverter.icaoToNNumber("B12345"));
        assertNull(IcaoNNumberConverter.icaoToNNumber("ZZZZZZ"));

        assertNull(IcaoNNumberConverter.nNumberToIcao(null));
        assertNull(IcaoNNumberConverter.nNumberToIcao(""));
        assertNull(IcaoNNumberConverter.nNumberToIcao("C-GXYZ"));
        assertNull(IcaoNNumberConverter.nNumberToIcao("N1234567"));
    }

    @Test
    public void testExtractNNumber() {
        assertEquals("N900AA", IcaoNNumberConverter.extractNNumber("N900AA", "AC6F2D"));
        assertEquals("N900AA", IcaoNNumberConverter.extractNNumber("AAL900", "AC6F2D"));
        assertEquals("N12345", IcaoNNumberConverter.extractNNumber("N12345", null));
        assertNull(IcaoNNumberConverter.extractNNumber("BAW123", "400123"));
    }
}
