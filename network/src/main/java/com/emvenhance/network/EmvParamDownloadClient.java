package com.emvenhance.network;

import java.util.Map;

import io.reactivex.rxjava3.core.Single;

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
