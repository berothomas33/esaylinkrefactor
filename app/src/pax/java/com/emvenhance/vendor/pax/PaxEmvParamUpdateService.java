package com.emvenhance.vendor.pax;

import com.emvenhance.network.EmvParamDownloadClient;
import com.pax.configservice.impl.EmvParamUpdater;

import java.util.Map;

import androidx.annotation.NonNull;
import io.reactivex.rxjava3.core.Single;

/**
 * Downloads a fresh EMV/CLSS parameter package and applies it, replacing whatever
 * {@code ConfigInit}'s first-boot JSON-asset seed (or a previous call to this) currently has
 * loaded — see {@code EmvParamUpdater} (bizentity module) for exactly what gets replaced and how.
 *
 * <p>The app layer's job here is just wiring: {@link EmvParamDownloadClient} (vendor-agnostic
 * {@code :network}) gets the raw bytes, {@code EmvParamUpdater} (PAX-specific {@code :bizentity})
 * unzips/parses/applies them. Not wired into any automatic startup path — call this from wherever
 * a setup/update screen wants to trigger it.
 */
public final class PaxEmvParamUpdateService {

    private final EmvParamDownloadClient downloadClient;

    public PaxEmvParamUpdateService() {
        this(new EmvParamDownloadClient());
    }

    public PaxEmvParamUpdateService(EmvParamDownloadClient downloadClient) {
        this.downloadClient = downloadClient;
    }

    /** @return {@code true} if every section present in the downloaded package applied successfully. */
    public Single<Boolean> downloadAndApply(@NonNull Map<String, String> headers, @NonNull String posType) {
        return downloadClient.download(headers, posType)
                .map(EmvParamUpdater::applyFromZip);
    }
}
