package com.emvenhance.vendor.pax;

import android.content.Context;
import android.content.SharedPreferences;
import androidx.annotation.Nullable;
import com.emvenhance.core.host.MasterKey;
import com.pax.bizlib.ped.PedHelper;
import com.pax.commonlib.utils.ConvertUtils;
import com.pax.commonlib.utils.LogUtils;
import com.pax.dal.IPed;
import com.pax.dal.entity.EAesCheckMode;
import com.pax.dal.entity.ECryptOperate;
import com.pax.dal.entity.ECryptOpt;
import com.pax.dal.entity.EPedKeyType;
import com.pax.dal.exceptions.PedDevException;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/**
 * The POS master key, as in the old app (PaxUtils.writeAESPedKey / encryptByAESPedKey /
 * decryptByAESPedKey, MomknKeysUtils, KeyConstants): an AES-192 key made once and written into
 * the PED at index 0x20; AES-CBC inside the PED with the old app's fixed IV, the data padded
 * with '@' to 16 bytes and the padding count kept beside it. No rotation.
 */
public final class PaxMasterKey implements MasterKey {

    private static final String TAG = "PaxMasterKey";
    private static final byte MASTER_KEY_INDEX = 0x20;
    private static final byte[] IV = {0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 0, 0x0A, 0x0B, 1, 2, 3};
    private static final int MASTER_KEY_LENGTH_BYTES = 24;
    private static final int BLOCK = 16;
    private static final String PREFS_NAME = "pos_master_key";
    private static final String KEY_GENERATION_DATE = "generation_date";

    /** Once: when no generation date is stored, a new master key is written at 0x20. */
    public static void generatePOSMasterKey(Context context) throws PedDevException {
        SharedPreferences prefs = context.getApplicationContext()
                .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        if (prefs.getString(KEY_GENERATION_DATE, null) != null) {
            return;
        }
        IPed ped = PedHelper.getPed();
        ped.eraseKey(EPedKeyType.TMK.getPedkeyType(), MASTER_KEY_INDEX);
        byte[] aesKey = new byte[MASTER_KEY_LENGTH_BYTES];
        new SecureRandom().nextBytes(aesKey);
        ped.writeAesKey(EPedKeyType.TMK, (byte) 0, MASTER_KEY_INDEX, aesKey,
                EAesCheckMode.KCV_NONE, null);
        prefs.edit().putString(KEY_GENERATION_DATE,
                new SimpleDateFormat("dd-MM-yyyy", Locale.US).format(new Date())).apply();
        LogUtils.i(TAG, "POS master key written at index 0x20");
    }

    @Override
    public EncryptorMessage encryptDataByMasterKey(String plainData) {
        int addedPadding = addedPadding(plainData);
        byte[] dataIn = padded(plainData, addedPadding).getBytes(StandardCharsets.UTF_8);
        try {
            byte[] enc = PedHelper.getPed().calcAes(MASTER_KEY_INDEX, IV, dataIn,
                    ECryptOperate.ENCRYPT, ECryptOpt.CBC);
            return new EncryptorMessage(ConvertUtils.bcd2Str(enc), addedPadding);
        } catch (PedDevException e) {
            throw new IllegalStateException("Encrypt by master key (0x20) failed", e);
        }
    }

    @Override
    @Nullable
    public String decryptDataByMasterKey(EncryptorMessage message) {
        try {
            byte[] enc = ConvertUtils.strToBcdPaddingLeft(message.encrypted);
            byte[] dec = PedHelper.getPed().calcAes(MASTER_KEY_INDEX, IV, enc,
                    ECryptOperate.DECRYPT, ECryptOpt.CBC);
            return unpadded(new String(dec, StandardCharsets.UTF_8), message.addedPadding);
        } catch (Exception e) {
            LogUtils.e(TAG, "Decrypt by master key (0x20) failed: " + e.getClass().getSimpleName());
            return null;
        }
    }

    /** How many '@' the old app added: 0 when the length is already a multiple of 16. */
    static int addedPadding(String plainData) {
        int rest = plainData.length() % BLOCK;
        return rest == 0 ? 0 : BLOCK - rest;
    }

    static String padded(String plainData, int addedPadding) {
        StringBuilder data = new StringBuilder(plainData);
        for (int i = 0; i < addedPadding; i++) {
            data.append('@');
        }
        return data.toString();
    }

    static String unpadded(String decrypted, int addedPadding) {
        return decrypted.substring(0, decrypted.length() - addedPadding);
    }
}
