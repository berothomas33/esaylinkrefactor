package com.emvenhance.network

/**
 * Downloads this terminal's EMV/CLSS parameter package as raw bytes — see
 * [HostApiConnection.emvFileDownload]. Stays at the byte level deliberately: unzipping and
 * parsing the package is PAX-specific (`EmvParamUpdater` in the bizentity module), and this
 * module has no PAX dependency to keep it usable by every vendor flavor.
 */
class EmvParamDownloadClient(private val connection: HostApiConnection) {

    constructor() : this(HostApiClient.create())

    /** Suspends until the whole package is read; call it off the main thread's work (IO). */
    suspend fun download(headers: Map<String, String>, posType: String): ByteArray =
        connection.emvFileDownload(headers, posType).use { body -> body.bytes() }
}
