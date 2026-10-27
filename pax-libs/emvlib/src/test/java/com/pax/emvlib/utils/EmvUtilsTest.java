package com.pax.emvlib.utils;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import com.pax.emvlib.base.IEmvLoadLibCallback;
import java.util.List;
import org.junit.Test;

public class EmvUtilsTest {

    @Test
    public void directKernelLoaders_areDeviceThenDpas() {
        List<IEmvLoadLibCallback> loaders = EmvUtils.directKernelLoaders();
        assertEquals(2, loaders.size());
        assertTrue(loaders.get(0) instanceof com.pax.emvlib.base.EmvLoadLibImpl);
        assertTrue(loaders.get(1) instanceof com.pax.emvlib.dpas.EmvLoadLibImpl);
    }

    @Test
    public void resolveLoadCallbacks_fallsBackToDirectLoadersWhenRouterIsEmpty() {
        List<IEmvLoadLibCallback> loaders = EmvUtils.resolveLoadCallbacks();
        assertEquals(2, loaders.size());
        assertTrue(loaders.get(0) instanceof com.pax.emvlib.base.EmvLoadLibImpl);
        assertTrue(loaders.get(1) instanceof com.pax.emvlib.dpas.EmvLoadLibImpl);
    }
}
