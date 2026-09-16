package com.emvenhance.network;

import com.emvenhance.core.card.EntryMethod;
import com.emvenhance.core.card.TransactionConfig;
import com.emvenhance.core.host.AuthResult;
import com.emvenhance.core.host.CommunicationBehavior;
import com.emvenhance.network.model.PurchaseRequest;
import com.emvenhance.network.model.PurchaseResponse;
import com.emvenhance.network.tlv.BerTlv;

import io.reactivex.rxjava3.core.Single;

/**
 * Real {@link CommunicationBehavior} — calls the host's online purchase endpoint and maps the
 * response into {@link AuthResult}, following the old project's {@code EmvApiClient#purchase}
 * behavior. Replaces {@code HostDefaults.declineUntilWired()} once a vendor terminal is ready to
 * go online for real.
 *
 * <p>The response's EMV tags (ARPC / issuer scripts) arrive bundled inside one {@code chipData}
 * hex BER-TLV blob rather than as separate JSON fields — same shape the old project used. See
 * {@link BerTlv} for the extraction and the caveat on the exact tag set an issuer bundles there.
 */
public final class RetrofitCommunicationBehavior implements CommunicationBehavior {

    private static final String TAG_ARPC = "91";
    private static final String TAG_ISSUER_SCRIPT_1 = "71";
    private static final String TAG_ISSUER_SCRIPT_2 = "72";

    private final HostApiConnection connection;

    public RetrofitCommunicationBehavior() {
        this(HostApiClient.create());
    }

    public RetrofitCommunicationBehavior(HostApiConnection connection) {
        this.connection = connection;
    }

    @Override
    public Single<AuthResult> authorize(TransactionConfig config) {
        PurchaseRequest request = new PurchaseRequest(
                config.getAmountMinor(),
                config.getProcCode(),
                posEntryMode(config.getMode()),
                config.getIccData(),
                config.getPan(),
                config.getOnlinePinBlock(),
                config.getOnlinePinKeyEncrypted());

        return connection.purchase(request).map(RetrofitCommunicationBehavior::toAuthResult);
    }

    private static AuthResult toAuthResult(PurchaseResponse response) {
        String responseCode = response.getResponseCode() != null
                ? String.format("%02d", response.getResponseCode())
                : "96";

        boolean approved = "00".equals(responseCode);
        if (!approved) {
            String message = response.getResponseMessage() != null
                    ? response.getResponseMessage() : "Declined";
            return AuthResult.declined(responseCode, message);
        }

        String chipData = response.getChipData();
        byte[] arpc = BerTlv.findTag(chipData, TAG_ARPC);
        byte[] issuerScript = concat(
                BerTlv.findTag(chipData, TAG_ISSUER_SCRIPT_1),
                BerTlv.findTag(chipData, TAG_ISSUER_SCRIPT_2));

        String authCode = response.getAuthCode() != null ? response.getAuthCode() : "";
        return AuthResult.approved(authCode, responseCode, arpc, issuerScript);
    }

    private static byte[] concat(byte[] a, byte[] b) {
        if (a == null) {
            return b;
        }
        if (b == null) {
            return a;
        }
        byte[] result = new byte[a.length + b.length];
        System.arraycopy(a, 0, result, 0, a.length);
        System.arraycopy(b, 0, result, a.length, b.length);
        return result;
    }

    /** ISO 8583-style POS entry mode codes — same values the old project's SaleRequest expected. */
    private static String posEntryMode(EntryMethod mode) {
        switch (mode) {
            case CHIP:
                return "05";
            case CONTACTLESS:
                return "07";
            case MAGSTRIPE:
                return "90";
            case MANUAL:
                return "01";
            default:
                return "00";
        }
    }
}
