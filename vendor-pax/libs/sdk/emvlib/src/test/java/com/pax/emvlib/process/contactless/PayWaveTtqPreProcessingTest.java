package com.pax.emvlib.process.contactless;

import static org.junit.Assert.assertArrayEquals;

import com.pax.emvbase.param.clss.PayWaveAid;
import com.pax.emvbase.param.clss.PayWaveInterFloorLimit;
import com.pax.emvlib.base.utils.EmvParamConvert;
import com.pax.jemv.clcommon.Clss_PreProcInfo;
import java.util.Collections;
import org.junit.Test;

/**
 * Pre-processing starts from the configured Visa TTQ with byte 2 bits 8-7 cleared, so a host TTQ
 * of 36E04000 can't force online and CVM on every tap.
 */
public class PayWaveTtqPreProcessingTest {

    private static final byte[] HOST_TTQ = {0x36, (byte) 0xE0, 0x40, 0x00};

    @Test
    public void onlineAndCvmRequiredBitsAreCleared() {
        assertArrayEquals(new byte[] {0x36, 0x20, 0x40, 0x00},
                EmvParamConvert.payWaveTtqForPreProcessing(HOST_TTQ));
    }

    @Test
    public void preProcessingGetsTheClearedCopyAndTheConfigIsUntouched() {
        PayWaveAid aid = new PayWaveAid();
        aid.setAid(new byte[] {(byte) 0xA0, 0x00, 0x00, 0x00, 0x03, 0x10, 0x10});
        aid.setTTQ(HOST_TTQ.clone());
        PayWaveInterFloorLimit sale = new PayWaveInterFloorLimit();
        sale.setTransactionType((byte) 0x00);
        sale.setContactlessCvmLimit(60000);
        sale.setCvmLimitSupported((byte) 1);
        aid.setPayWaveInterFloorLimitList(Collections.singletonList(sale));

        Clss_PreProcInfo info = EmvParamConvert.PayWavePreProcInfo(aid, (byte) 0x00);

        assertArrayEquals(new byte[] {0x36, 0x20, 0x40, 0x00}, info.aucReaderTTQ);
        assertArrayEquals(HOST_TTQ, aid.getTTQ());
    }

    @Test
    public void otherBitsAreKept() {
        byte[] ttq = {(byte) 0xFF, (byte) 0xFF, (byte) 0xFF, (byte) 0xFF};
        assertArrayEquals(new byte[] {(byte) 0xFF, 0x3F, (byte) 0xFF, (byte) 0xFF},
                EmvParamConvert.payWaveTtqForPreProcessing(ttq));
    }
}
