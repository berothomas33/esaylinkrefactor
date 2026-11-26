package com.emvenhance.vendor.pax;

import androidx.annotation.Nullable;
import com.emvenhance.core.host.TerminalKeys;
import com.pax.bizlib.ped.PedHelper;
import com.pax.commonlib.utils.ConvertUtils;
import com.pax.commonlib.utils.LogUtils;
import com.pax.dal.IPed;
import com.pax.dal.entity.EAesCheckMode;
import com.pax.dal.entity.ECryptOperate;
import com.pax.dal.entity.ECryptOpt;
import com.pax.dal.entity.EPedKeyType;
import com.pax.dal.exceptions.PedDevException;
import com.pax.poslib.utils.PosDeviceUtils;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;

/**
 * The RKI keys in the PAX PED, slot for slot as card acceptance's {@code develop_rki_pilot}
 * ({@code PaxUtils.injectGrandKey / injectAesTmk / injectTdk / injectTpk / injectAPPTdk /
 * getKeyKCV / cypherPedAes}).
 *
 * <p>{@code TAESK} is the AES TDK type (code 32): NeptuneLite 4.27 names the same code
 * {@code PED_AES_TDK}; our 3.28 jar has only {@code TAESK}.
 */
public final class PaxTerminalKeys implements TerminalKeys {

    private static final String TAG = "PaxTerminalKeys";

    private static final byte CLEAR_SOURCE_INDEX = 0;
    private static final byte KCV_MODE = 0x03;
    private static final int AES_BLOCK = 16;
    /** The PED's calcAes takes at most 1024 bytes; longer data goes in CBC-chained chunks. */
    private static final int MAX_CHUNK = 1024;

    private static final byte INDEX_KEK = 2;
    private static final byte INDEX_KBPK = 1;
    private static final byte INDEX_TDK = 3;
    /** Where PinService makes the PIN block: one constant for both. */
    private static final byte INDEX_TPK = PosDeviceUtils.INDEX_AES_TPK;
    private static final byte INDEX_APP_TDK = 5;

    @Override
    public void writeClearKek(byte[] kek) {
        try {
            PedHelper.getPed().writeAesKey(EPedKeyType.TLK.getPedkeyType(), CLEAR_SOURCE_INDEX,
                    EPedKeyType.AES_TMK.getPedkeyType(), INDEX_KEK, kek, EAesCheckMode.KCV_NONE, null);
            LogUtils.i(TAG, "KEK written (AES_TMK " + INDEX_KEK + ")");
        } catch (PedDevException e) {
            throw new IllegalStateException("KEK write (AES_TMK " + INDEX_KEK + ") refused: "
                    + e.getErrCode(), e);
        }
    }

    @Override
    public void writeKeyBlock(Key key, String tr31Block) {
        byte parentIndex = key == Key.KBPK ? INDEX_KEK : INDEX_KBPK;
        byte index = index(key);
        try {
            PedHelper.getPed().writeTR31Key(EPedKeyType.AES_TMK.getPedkeyType(), parentIndex, index,
                    tr31Block.getBytes(StandardCharsets.US_ASCII));
            LogUtils.i(TAG, key + " written (TR-31 under AES_TMK " + parentIndex + " → index " + index + ")");
        } catch (PedDevException e) {
            throw new IllegalStateException(key + " TR-31 write (index " + index + ") refused: "
                    + e.getErrCode(), e);
        }
    }

    @Override
    @Nullable
    public String kcv(Key key) {
        try {
            byte[] kcv = PedHelper.getPed().getKCV(type(key), index(key), KCV_MODE, new byte[0]);
            return kcv == null ? null : ConvertUtils.bcd2Str(kcv).toUpperCase();
        } catch (PedDevException e) {
            LogUtils.e(TAG, key + " KCV unavailable: " + e.getErrCode());
            return null;
        }
    }

    @Override
    public byte[] encrypt(Key key, byte[] iv, byte[] data) {
        return calcAes(key, iv, data, ECryptOperate.ENCRYPT);
    }

    @Override
    public byte[] decrypt(Key key, byte[] iv, byte[] data) {
        return calcAes(key, iv, data, ECryptOperate.DECRYPT);
    }

    private static byte[] calcAes(Key key, byte[] iv, byte[] data, ECryptOperate operate) {
        if (key != Key.TDK && key != Key.APP_TDK) {
            throw new IllegalArgumentException(key + " is not a data key");
        }
        IPed ped = PedHelper.getPed();
        ByteArrayOutputStream result = new ByteArrayOutputStream();
        byte[] chainIv = iv;
        try {
            for (int offset = 0; offset < data.length; offset += MAX_CHUNK) {
                byte[] chunk = Arrays.copyOfRange(data, offset, Math.min(data.length, offset + MAX_CHUNK));
                byte[] out = ped.calcAes(index(key), chainIv, chunk, operate, ECryptOpt.CBC);
                result.write(out, 0, out.length);
                byte[] lastCipherBlock = operate == ECryptOperate.ENCRYPT ? out : chunk;
                chainIv = Arrays.copyOfRange(lastCipherBlock, lastCipherBlock.length - AES_BLOCK,
                        lastCipherBlock.length);
            }
        } catch (PedDevException e) {
            throw new IllegalStateException(key + " AES " + operate + " failed: " + e.getErrCode(), e);
        }
        return result.toByteArray();
    }

    private static byte index(Key key) {
        switch (key) {
            case KEK:
                return INDEX_KEK;
            case KBPK:
                return INDEX_KBPK;
            case TDK:
                return INDEX_TDK;
            case TPK:
                return INDEX_TPK;
            case APP_TDK:
                return INDEX_APP_TDK;
            default:
                throw new IllegalArgumentException(String.valueOf(key));
        }
    }

    private static EPedKeyType type(Key key) {
        switch (key) {
            case KEK:
            case KBPK:
                return EPedKeyType.AES_TMK;
            case TPK:
                return EPedKeyType.AES_TPK;
            case TDK:
            case APP_TDK:
                return EPedKeyType.TAESK;
            default:
                throw new IllegalArgumentException(String.valueOf(key));
        }
    }
}
