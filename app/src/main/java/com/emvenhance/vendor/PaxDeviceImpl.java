package com.emvenhance.vendor;

import androidx.annotation.Nullable;
import com.pax.commonlib.utils.LogUtils;
import com.pax.dal.IDAL;
import com.pax.dal.IIcc;
import com.pax.dal.IPicc;
import com.pax.dal.entity.ApduRespInfo;
import com.pax.dal.entity.ApduSendInfo;
import com.pax.dal.entity.EPiccType;
import com.pax.jemv.device.model.ApduRespL2;
import com.pax.jemv.device.model.ApduSendL2;
import com.pax.jemv.device.model.DeviceRetCode;
import java.util.Calendar;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.text.SimpleDateFormat;
import java.util.Locale;
import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;

/**
 * PAX {@code IDevice} implementation — bridges the PAX JEMV kernel native layer
 * to Neptune DAL hardware (ICC, PICC, PED, clock, crypto).
 *
 * <p>Singleton — the kernel sets a single device impl via
 * {@code DeviceManager.getInstance().setIDevice(PaxDeviceImpl.getInstance())}.
 *
 * <p>Replaces the former {@code EmvDeviceImpl} from the deleted {@code :emvflow} module.
 * Cipher mode constants are inlined (no EDeviceCipherMode enum).
 */
public final class PaxDeviceImpl {

    private static final String TAG = "PaxDeviceImpl";

    // ─── Cipher mode constants (inlined from deleted EDeviceCipherMode) ──
    /** DES ECB encrypt */
    public static final int DES_ECB_ENC = 0x00;
    /** DES ECB decrypt */
    public static final int DES_ECB_DEC = 0x01;
    /** DES CBC encrypt */
    public static final int DES_CBC_ENC = 0x02;
    /** DES CBC decrypt */
    public static final int DES_CBC_DEC = 0x03;
    /** 3DES ECB encrypt */
    public static final int TDES_ECB_ENC = 0x04;
    /** 3DES ECB decrypt */
    public static final int TDES_ECB_DEC = 0x05;
    /** 3DES CBC encrypt */
    public static final int TDES_CBC_ENC = 0x06;
    /** 3DES CBC decrypt */
    public static final int TDES_CBC_DEC = 0x07;

    private static volatile PaxDeviceImpl instance;

    private final SecureRandom random = new SecureRandom();

    private PaxDeviceImpl() { }

    public static PaxDeviceImpl getInstance() {
        if (instance == null) {
            synchronized (PaxDeviceImpl.class) {
                if (instance == null) {
                    instance = new PaxDeviceImpl();
                }
            }
        }
        return instance;
    }

    // ─── ICC APDU (contact) ─────────────────────────────────────────────

    /**
     * Send APDU to contact ICC via slot.
     */
    public int iccCommand(byte slot, ApduSendL2 apduSend, ApduRespL2 apduResp) {
        IDAL dal = PaxRuntime.getDal();
        if (dal == null) return DeviceRetCode.ICC_CMD_ERR;
        try {
            IIcc icc = dal.getIcc();
            ApduSendInfo send = toApduSendInfo(apduSend);
            ApduRespInfo resp = icc.isoCommandByApdu(slot, send);
            if (resp == null) return DeviceRetCode.ICC_CMD_ERR;
            fromApduRespInfo(resp, apduResp);
            return DeviceRetCode.OK;
        } catch (Exception e) {
            LogUtils.e(TAG, "iccCommand error", e);
            return DeviceRetCode.ICC_CMD_ERR;
        }
    }

    // ─── PICC APDU (contactless) ────────────────────────────────────────

    /**
     * Send APDU to contactless PICC.
     */
    public int piccCommand(ApduSendL2 apduSend, ApduRespL2 apduResp) {
        IDAL dal = PaxRuntime.getDal();
        if (dal == null) return DeviceRetCode.ICC_CMD_ERR;
        try {
            IPicc picc = dal.getPicc(EPiccType.INTERNAL);
            byte[] cmdBytes = buildApduBytes(apduSend);
            byte[] response = picc.transfer(cmdBytes);
            if (response == null || response.length < 2) return DeviceRetCode.ICC_CMD_ERR;
            apduResp.SWA = response[response.length - 2];
            apduResp.SWB = response[response.length - 1];
            int dataLen = response.length - 2;
            if (dataLen > 0) {
                apduResp.DataOut = new byte[dataLen];
                System.arraycopy(response, 0, apduResp.DataOut, 0, dataLen);
            } else {
                apduResp.DataOut = new byte[0];
            }
            apduResp.LenOut = (short) dataLen;
            return DeviceRetCode.OK;
        } catch (Exception e) {
            LogUtils.e(TAG, "piccCommand error", e);
            return DeviceRetCode.ICC_CMD_ERR;
        }
    }

    // ─── DES / 3DES ─────────────────────────────────────────────────────

    /**
     * DES/3DES encrypt/decrypt using software crypto.
     * The EMV kernel provides raw key material, so we use javax.crypto directly
     * rather than the PED (which works with stored key indices).
     */
    public int desProcess(int mode, byte[] key, byte[] dataIn, byte[] dataOut) {
        try {
            boolean is3Des = mode >= TDES_ECB_ENC;
            boolean isEcb = (mode & 0x02) == 0;
            boolean isEncrypt = (mode & 0x01) == 0;

            String algo = is3Des ? "DESede" : "DES";
            String transform = algo + (isEcb ? "/ECB/NoPadding" : "/CBC/NoPadding");
            int cipherMode = isEncrypt ? Cipher.ENCRYPT_MODE : Cipher.DECRYPT_MODE;

            // Ensure key length: DES=8, 3DES=24 (expand 16-byte to 24 by repeating first 8)
            byte[] keyBytes;
            if (is3Des) {
                if (key.length == 16) {
                    keyBytes = new byte[24];
                    System.arraycopy(key, 0, keyBytes, 0, 16);
                    System.arraycopy(key, 0, keyBytes, 16, 8);
                } else {
                    keyBytes = key;
                }
            } else {
                keyBytes = java.util.Arrays.copyOf(key, 8);
            }

            SecretKey secretKey = new SecretKeySpec(keyBytes, algo);
            Cipher cipher = Cipher.getInstance(transform);
            if (isEcb) {
                cipher.init(cipherMode, secretKey);
            } else {
                cipher.init(cipherMode, secretKey, new IvParameterSpec(new byte[8]));
            }
            byte[] result = cipher.doFinal(dataIn);
            if (result != null && dataOut != null) {
                System.arraycopy(result, 0, dataOut, 0, Math.min(result.length, dataOut.length));
            }
            return DeviceRetCode.OK;
        } catch (Exception e) {
            LogUtils.e(TAG, "desProcess error", e);
            return DeviceRetCode.ICC_CMD_ERR;
        }
    }

    // ─── AES ────────────────────────────────────────────────────────────

    /**
     * AES encrypt/decrypt.
     */
    public int aesProcess(int mode, byte[] key, byte[] dataIn, byte[] dataOut) {
        // AES is not commonly used in EMV kernels; delegate to software impl
        // if needed. Most PAX terminals use DES/3DES for MAC.
        LogUtils.w(TAG, "aesProcess not implemented for mode=" + mode);
        return DeviceRetCode.ICC_CMD_ERR;
    }

    // ─── RSA recovery ───────────────────────────────────────────────────

    /**
     * RSA public key recovery (modular exponentiation) for ODA.
     */
    public int rsaRecover(byte[] modulus, byte[] exponent, byte[] dataIn, byte[] dataOut) {
        try {
            java.math.BigInteger mod = new java.math.BigInteger(1, modulus);
            java.math.BigInteger exp = new java.math.BigInteger(1, exponent);
            java.math.BigInteger data = new java.math.BigInteger(1, dataIn);
            byte[] result = data.modPow(exp, mod).toByteArray();
            // BigInteger may add leading 0 byte; strip it
            int srcOffset = 0;
            if (result.length > modulus.length && result[0] == 0) {
                srcOffset = result.length - modulus.length;
            }
            int copyLen = Math.min(result.length - srcOffset, dataOut.length);
            // Left-pad with zeros if result is shorter than modulus
            int dstOffset = dataOut.length - copyLen;
            if (dstOffset > 0) {
                java.util.Arrays.fill(dataOut, 0, dstOffset, (byte) 0);
            }
            System.arraycopy(result, srcOffset, dataOut, dstOffset, copyLen);
            return DeviceRetCode.OK;
        } catch (Exception e) {
            LogUtils.e(TAG, "rsaRecover error", e);
            return DeviceRetCode.ICC_CMD_ERR;
        }
    }

    // ─── SHA ────────────────────────────────────────────────────────────

    /**
     * SHA-1 hash (used by EMV for ODA certificate verification).
     */
    public int shaProcess(byte[] dataIn, int dataInLen, byte[] dataOut) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-1");
            byte[] hash = digest.digest(java.util.Arrays.copyOf(dataIn, dataInLen));
            System.arraycopy(hash, 0, dataOut, 0, Math.min(hash.length, dataOut.length));
            return DeviceRetCode.OK;
        } catch (NoSuchAlgorithmException e) {
            LogUtils.e(TAG, "shaProcess error", e);
            return DeviceRetCode.ICC_CMD_ERR;
        }
    }

    // ─── Time / Random / SN ─────────────────────────────────────────────

    /**
     * Get current date/time as BCD YYYYMMDDHHMMSS in 7 bytes.
     */
    public int getTime(byte[] dateTime) {
        try {
            Calendar cal = Calendar.getInstance();
            String ts = new SimpleDateFormat("yyyyMMddHHmmss", Locale.US).format(cal.getTime());
            byte[] bcd = hexStringToBytes(ts);
            System.arraycopy(bcd, 0, dateTime, 0, Math.min(bcd.length, dateTime.length));
            return DeviceRetCode.OK;
        } catch (Exception e) {
            LogUtils.e(TAG, "getTime error", e);
            return DeviceRetCode.ICC_CMD_ERR;
        }
    }

    /**
     * Fill random bytes.
     */
    public void getRand(byte[] randBuf, int len) {
        byte[] rand = new byte[len];
        random.nextBytes(rand);
        System.arraycopy(rand, 0, randBuf, 0, Math.min(len, randBuf.length));
    }

    /**
     * Read device serial number.
     */
    public int readSN(byte[] sn) {
        IDAL dal = PaxRuntime.getDal();
        if (dal == null) return DeviceRetCode.ICC_CMD_ERR;
        try {
            String serial = dal.getSys().getTermInfo()
                    .get(com.pax.dal.entity.ETermInfoKey.SN);
            if (serial != null) {
                byte[] snBytes = serial.getBytes(java.nio.charset.StandardCharsets.US_ASCII);
                System.arraycopy(snBytes, 0, sn, 0, Math.min(snBytes.length, sn.length));
            }
            return DeviceRetCode.OK;
        } catch (Exception e) {
            LogUtils.e(TAG, "readSN error", e);
            return DeviceRetCode.ICC_CMD_ERR;
        }
    }

    // ─── PIN input ──────────────────────────────────────────────────────

    /**
     * PIN entry — invoked by the EMV kernel during CVM.
     * Currently returns PIN bypass (no password) since PED integration is
     * specific to the host application's PIN entry UI. Override as needed.
     */
    public int inputPin(int isOnline, int pinLenMin, int pinLenMax, byte[] cardNo,
            byte[] pinBlock) {
        // PIN entry requires UI coordination with the host app.
        // Return "no password" to let the kernel proceed with PIN bypass.
        LogUtils.i(TAG, "inputPin isOnline=" + isOnline + " (bypassed — wire to host PIN UI)");
        return DeviceRetCode.OK;
    }

    // ─── Timer ──────────────────────────────────────────────────────────

    /**
     * Set a timer (milliseconds). Used by kernels for timeout management.
     */
    public int timerSet(int ms) {
        // Timer is managed by the kernel natively; this is a no-op bridge.
        return DeviceRetCode.OK;
    }

    /**
     * Check if the timer has expired.
     */
    public int timerCheck() {
        return DeviceRetCode.OK;
    }

    // ─── Helpers ─────────────────────────────────────────────────────────

    private static ApduSendInfo toApduSendInfo(ApduSendL2 l2) {
        ApduSendInfo info = new ApduSendInfo();
        info.Command = new byte[]{l2.Command[0], l2.Command[1], l2.Command[2], l2.Command[3]};
        info.Lc = (short) (l2.Lc & 0xFFFF);
        if (l2.DataIn != null && l2.Lc > 0) {
            info.DataIn = new byte[l2.Lc];
            System.arraycopy(l2.DataIn, 0, info.DataIn, 0, l2.Lc);
        }
        info.Le = (short) (l2.Le & 0xFFFF);
        return info;
    }

    private static void fromApduRespInfo(ApduRespInfo resp, ApduRespL2 l2) {
        l2.SWA = (byte) ((resp.SWA) & 0xFF);
        l2.SWB = (byte) ((resp.SWB) & 0xFF);
        if (resp.DataOut != null) {
            l2.DataOut = resp.DataOut;
            l2.LenOut = (short) resp.DataOut.length;
        } else {
            l2.DataOut = new byte[0];
            l2.LenOut = 0;
        }
    }

    private static byte[] buildApduBytes(ApduSendL2 l2) {
        int totalLen = 4 + (l2.Lc > 0 ? 1 + l2.Lc : 0) + 1;
        byte[] cmd = new byte[totalLen];
        System.arraycopy(l2.Command, 0, cmd, 0, 4);
        int offset = 4;
        if (l2.Lc > 0 && l2.DataIn != null) {
            cmd[offset++] = (byte) l2.Lc;
            System.arraycopy(l2.DataIn, 0, cmd, offset, l2.Lc);
            offset += l2.Lc;
        }
        cmd[offset] = (byte) l2.Le;
        return cmd;
    }

    private static byte[] hexStringToBytes(String hex) {
        int len = hex.length() / 2;
        byte[] result = new byte[len];
        for (int i = 0; i < len; i++) {
            result[i] = (byte) ((Character.digit(hex.charAt(i * 2), 16) << 4)
                    + Character.digit(hex.charAt(i * 2 + 1), 16));
        }
        return result;
    }
}
