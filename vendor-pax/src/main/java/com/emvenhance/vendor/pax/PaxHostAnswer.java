package com.emvenhance.vendor.pax;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import com.emvenhance.core.host.AuthResult;
import com.pax.emvbase.process.entity.EOnlineResult;
import com.pax.emvbase.process.entity.IssuerRspData;
import com.pax.emvbase.process.entity.OnlineResultWrapper;
import com.pax.emvbase.process.enums.TransResultEnum;
import java.nio.charset.StandardCharsets;

/**
 * The host's {@link AuthResult} in PAX's shape ({@link OnlineResultWrapper}), what
 * {@code PaxEmvBehavior.startOnlineProcess} hands back to the kernel. A pure mapping: no kernel
 * call, no state.
 */
final class PaxHostAnswer {

    private PaxHostAnswer() { }

    /**
     * The host's answer as the kernel takes it for the 2nd GENERATE AC: none or failed →
     * FAILED; approved → APPROVE with the auth code, response code, ARPC and issuer scripts;
     * anything else → DENIAL with the response code.
     */
    @NonNull
    static OnlineResultWrapper toOnlineResult(@Nullable AuthResult auth) {
        OnlineResultWrapper wrapper = new OnlineResultWrapper();
        IssuerRspData rsp = new IssuerRspData();
        if (auth == null || auth.isFailed()) {
            wrapper.setResultCode(EOnlineResult.FAILED.getResultCode());
            wrapper.setTransResultEnum(TransResultEnum.RESULT_ONLINE_FAILED);
            rsp.setOnlineResult(EOnlineResult.FAILED.getEmvOnlineResult());
            wrapper.setIssuerRspData(rsp);
            return wrapper;
        }
        if (auth.isApproved()) {
            wrapper.setResultCode(EOnlineResult.APPROVE.getResultCode());
            wrapper.setTransResultEnum(TransResultEnum.RESULT_ONLINE_APPROVED);
            rsp.setOnlineResult(EOnlineResult.APPROVE.getEmvOnlineResult());
            if (auth.getAuthCode() != null) {
                rsp.setAuthCode(auth.getAuthCode().getBytes(StandardCharsets.US_ASCII));
            }
            if (auth.getResponseCode() != null) {
                rsp.setRespCode(auth.getResponseCode().getBytes(StandardCharsets.US_ASCII));
            }
            if (auth.getArpc() != null) {
                rsp.setAuthData(auth.getArpc());
            }
            if (auth.getIssuerScript() != null) {
                rsp.setScript(auth.getIssuerScript());
            }
        } else {
            wrapper.setResultCode(EOnlineResult.DENIAL.getResultCode());
            wrapper.setTransResultEnum(TransResultEnum.RESULT_ONLINE_DENIED);
            rsp.setOnlineResult(EOnlineResult.DENIAL.getEmvOnlineResult());
            if (auth.getResponseCode() != null) {
                rsp.setRespCode(auth.getResponseCode().getBytes(StandardCharsets.US_ASCII));
            }
        }
        wrapper.setIssuerRspData(rsp);
        return wrapper;
    }
}
