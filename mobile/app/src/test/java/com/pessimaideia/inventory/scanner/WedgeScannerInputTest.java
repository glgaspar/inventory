package com.pessimaideia.inventory.scanner;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class WedgeScannerInputTest {

    @Test
    public void noTerminatorWhileTheCodeIsStillArriving() {
        assertEquals(-1, WedgeScannerInput.firstTerminator(""));
        assertEquals(-1, WedgeScannerInput.firstTerminator("789600400"));
    }

    @Test
    public void newlineCarriageReturnAndTabEndACode() {
        assertEquals(3, WedgeScannerInput.firstTerminator("123\n"));
        assertEquals(3, WedgeScannerInput.firstTerminator("123\r\n"));
        assertEquals(3, WedgeScannerInput.firstTerminator("123\t456"));
    }
}
