package com.emvenhance.vendor.pax

import com.emvenhance.network.EmvParamDownloadClient
import com.pax.configservice.impl.EmvParamUpdateResult
import com.pax.configservice.impl.EmvParamUpdater
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Downloads a fresh EMV/CLSS parameter package and applies it, replacing whatever `ConfigInit`'s
 * first-boot JSON-asset seed (or a previous call to this) currently has loaded — see
 * `EmvParamUpdater` (bizentity module) for exactly what gets replaced and how.
 *
 * The app layer's job here is just wiring: [EmvParamDownloadClient] (vendor-agnostic `:network`)
 * gets the raw bytes, `EmvParamUpdater` (PAX-specific `:bizentity`) unzips/parses/applies them.
 * Not wired into any automatic startup path — call this from wherever a setup/update screen wants
 * to trigger it.
 */
class PaxEmvParamUpdateService(private val downloadClient: EmvParamDownloadClient) {

    constructor() : this(EmvParamDownloadClient())

    /**
     * @return which sections applied (and their row counts); throws the first error hit. The
     *      download suspends; the unzip + database apply blocks, so it runs on `Dispatchers.IO`.
     */
    suspend fun downloadAndApply(headers: Map<String, String>, posType: String): EmvParamUpdateResult {
        val zip = downloadClient.download(headers, posType)
        return withContext(Dispatchers.IO) { EmvParamUpdater.applyFromZip(zip) }
    }
}
