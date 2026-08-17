package com.pax.poslib.neptune;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class NeptuneNativeLibsTest {

    @Test
    public void dalLibs_areDeviceConfigThenDclThenHdsd() {
        assertEquals(3, NeptuneNativeLibs.DAL_LIBS.length);
        assertArrayEquals(
                new String[] {"DeviceConfig", "DCL", "HDSD"},
                NeptuneNativeLibs.DAL_LIBS);
    }
}
