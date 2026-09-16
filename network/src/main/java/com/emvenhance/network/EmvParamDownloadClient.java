package com.emvenhance.network;

import java.util.Map;

import io.reactivex.rxjava3.core.Single;

/**
 * Downloads this terminal's EMV/CLSS parameter package as raw bytes — see
 * {@link HostApiConnection#emvFileDownload}. Stays at the byte level deliberately: unzipping and
 * parsing the package is PAX-specific ({@code EmvParamUpdater} in the bizentity module), and this
 * module has no PAX dependency to keep it usable by every vendor flavor.
 */
public final class EmvParamDownloadClient {

    private final HostApiConnection connection;

    public EmvParamDownloadClient() {
        this(HostApiClient.create());
    }

    public EmvParamDownloadClient(HostApiConnection connection) {
        this.connection = connection;
    }

    public Single<byte[]> download(Map<String, String> headers, String posType) {
        return connection.emvFileDownload(headers, posType)
                .map(body -> {
                    try {
                        return body.bytes();
                    } finally {
                        body.close();
                    }
                });
    }
}
