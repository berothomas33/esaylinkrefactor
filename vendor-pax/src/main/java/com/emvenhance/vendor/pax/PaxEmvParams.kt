package com.emvenhance.vendor.pax

import com.emvenhance.core.param.EmvParams
import com.emvenhance.network.EmvParamDownloadClient
import com.emvenhance.network.HostHeaders
import com.emvenhance.network.onboarding.OnboardingClient
import com.emvenhance.network.onboarding.OnboardingState
import com.pax.bizentity.db.helper.AmexAidDbHelper
import com.pax.bizentity.db.helper.DpasAidDbHelper
import com.pax.bizentity.db.helper.EFTAidDbHelper
import com.pax.bizentity.db.helper.GreendaoHelper
import com.pax.bizentity.db.helper.JcbAidDbHelper
import com.pax.bizentity.db.helper.MirAidDbHelper
import com.pax.bizentity.db.helper.PBOCAidDbHelper
import com.pax.bizentity.db.helper.PaypassAidDbHelper
import com.pax.bizentity.db.helper.PaywaveAidDbHelper
import com.pax.bizentity.db.helper.PureAidDbHelper
import com.pax.bizentity.db.helper.RupayAidDbHelper
import com.pax.bizentity.entity.EmvAid
import com.pax.bizentity.entity.EmvCapk
import com.pax.bizentity.entity.clss.amex.AmexAidBean
import com.pax.bizentity.entity.clss.dpas.DpasAidBean
import com.pax.bizentity.entity.clss.eft.EFTAidBean
import com.pax.bizentity.entity.clss.jcb.JcbAidBean
import com.pax.bizentity.entity.clss.mir.MirAidBean
import com.pax.bizentity.entity.clss.paypass.PayPassAidBean
import com.pax.bizentity.entity.clss.paywave.PaywaveAidBean
import com.pax.bizentity.entity.clss.pboc.PBOCAidBean
import com.pax.bizentity.entity.clss.pure.PureAidBean
import com.pax.bizentity.entity.clss.rupay.RupayAidBean
import com.pax.commonlib.utils.ConvertUtils
import com.pax.emvbase.param.EmvProcessParam
import com.pax.emvbase.param.clss.BaseAid
import com.pax.emvbase.param.clss.BaseParam
import com.pax.commonlib.application.BaseApplication
import com.pax.configservice.impl.ConfigInit
import com.pax.configservice.impl.EmvParamUpdater
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * PAX's [EmvParams]: the GreenDAO parameter database, the kernel's parameter cache
 * ([PaxTerminal.kernelParams]), and the host's parameter package ([EmvParamDownloadClient] +
 * [EmvParamUpdater]). Every row is rendered here, so the screen never sees a PAX type. Nothing
 * writes the database while a transaction runs.
 */
internal class PaxEmvParams(private val terminal: PaxTerminal) : EmvParams {

    override fun categories(): List<String> = Category.values().map { it.label }

    override fun rows(category: String): List<EmvParams.Row> {
        val c = Category.of(category)
        return c.loader().filterNotNull().map { PaxRow(c, it) }
    }

    /**
     * Refreshes the saved access token first if it is near or past expiry (rather than handing
     * the host whatever token onboarding first minted), then downloads with the authenticated
     * headers — the confirmed post-onboarding contract — and applies the package. Refused while
     * a transaction runs (checked again right before the apply): the apply deletes and rewrites
     * the tables that transaction may read. A changed database reloads at the next transaction
     * ([EmvParamUpdater] invalidates the cache). Main-safe: runs on `Dispatchers.IO`.
     */
    override suspend fun download(): String = withContext(Dispatchers.IO) {
        refuseWhileBusy()
        val state = OnboardingState(BaseApplication.getAppContext())
        val sn = terminal.serialNumber()
        val accessToken = OnboardingClient().ensureValidAccessToken(HostHeaders.build(sn), state)
        val zip = EmvParamDownloadClient().download(HostHeaders.buildAuthenticated(sn, accessToken), POS_TYPE)
        refuseWhileBusy()
        EmvParamUpdater.applyFromZip(zip).summarize()
    }

    /** Seeds the database from the bundled JSON; refused while a transaction runs. Blocking. */
    override fun seedDefaults() {
        refuseWhileBusy()
        ConfigInit().init() // synchronous; reloads the cache when done
    }

    private fun refuseWhileBusy() {
        check(!terminal.isBusy()) { "A transaction is running — try again when it ends" }
    }

    override fun downloadedFiles(): List<String> =
        listOf(EmvParamUpdater.CONTACT_FILE, EmvParamUpdater.CONTACTLESS_FILE)

    override fun downloadedFile(name: String): File? {
        val dir = EmvParamUpdater.diagnosisDir() ?: return null
        return File(dir, name).takeIf { it.isFile }
    }

    /** One database row; its details are its entry in the kernel's cache, looked up on demand. */
    private inner class PaxRow(private val category: Category, private val item: Any) : EmvParams.Row {

        override fun title(): String = category.renderer(item)

        override fun details(): String {
            val cached = category.cache(terminal.kernelParams(), item)
            return if (cached != null) {
                "In the kernel's cache — what the next transaction uses:\n\n" + ParamDump.describe(cached)
            } else {
                "Not in the kernel's cache yet (it reloads at the next transaction after a download). " +
                    "Database values:\n\n" + ParamDump.describe(item)
            }
        }
    }

    /**
     * One EMV param category: how to load it from the database, how to render each row, and how
     * to find that row's entry in the kernel's parameter cache (what a transaction uses).
     */
    private enum class Category(
        val label: String,
        val loader: () -> List<*>,
        val renderer: (Any) -> String,
        val cache: (EmvProcessParam, Any) -> Any?,
    ) {
        AID(
            "AID",
            { GreendaoHelper.getEmvAidHelper().loadAll() },
            { item -> row((item as EmvAid).appName, item.aid) },
            { cache, item -> find(cache.emvAidList, { aid -> hex(aid.applicationID) }, (item as EmvAid).aid) },
        ),
        CAPK(
            "CAPK",
            { GreendaoHelper.getEmvCapkHelper().loadAll() },
            { item -> "RID " + (item as EmvCapk).rid + "   Key " + item.keyID },
            { cache, item ->
                cache.capkParam?.let { capkParam ->
                    find(
                        capkParam.capkList,
                        { capk -> hex(capk.rid) + "/" + (capk.keyId.toInt() and 0xFF) },
                        (item as EmvCapk).rid + "/" + item.keyID,
                    )
                }
            },
        ),
        AMEX(
            "Amex",
            { AmexAidDbHelper.getInstance().loadAll() },
            { item -> row((item as AmexAidBean).appName, item.aid) },
            { cache, item -> clss(cache.amexParam, (item as AmexAidBean).aid) },
        ),
        PAYPASS(
            "PayPass",
            { PaypassAidDbHelper.getInstance().loadAll() },
            { item -> row((item as PayPassAidBean).appName, item.aid) },
            { cache, item -> clss(cache.payPassParam, (item as PayPassAidBean).aid) },
        ),
        PAYWAVE(
            "PayWave",
            { PaywaveAidDbHelper.getInstance().loadAll() },
            { item -> row((item as PaywaveAidBean).appName, item.aid) },
            { cache, item -> clss(cache.payWaveParam, (item as PaywaveAidBean).aid) },
        ),
        DPAS(
            "Dpas",
            { DpasAidDbHelper.getInstance().loadAll() },
            { item -> row((item as DpasAidBean).appName, item.aid) },
            { cache, item -> clss(cache.dpasParam, (item as DpasAidBean).aid) },
        ),
        EFT(
            "EFT",
            { EFTAidDbHelper.getInstance().loadAll() },
            { item -> row((item as EFTAidBean).appName, item.aid) },
            { cache, item -> clss(cache.eftParam, (item as EFTAidBean).aid) },
        ),
        JCB(
            "Jcb",
            { JcbAidDbHelper.getInstance().loadAll() },
            { item -> row((item as JcbAidBean).appName, item.aid) },
            { cache, item -> clss(cache.jcbParam, (item as JcbAidBean).aid) },
        ),
        MIR(
            "Mir",
            { MirAidDbHelper.getInstance().loadAll() },
            { item -> row((item as MirAidBean).appName, item.aid) },
            { cache, item -> clss(cache.mirParam, (item as MirAidBean).aid) },
        ),
        PBOC(
            "Pboc",
            { PBOCAidDbHelper.getInstance().loadAll() },
            { item -> row((item as PBOCAidBean).appName, item.aid) },
            { cache, item -> clss(cache.pbocParam, (item as PBOCAidBean).aid) },
        ),
        PURE(
            "Pure",
            { PureAidDbHelper.getInstance().loadAll() },
            { item -> row((item as PureAidBean).appName, item.aid) },
            { cache, item -> clss(cache.pureParam, (item as PureAidBean).aid) },
        ),
        RUPAY(
            "RuPay",
            { RupayAidDbHelper.getInstance().loadAll() },
            { item -> row((item as RupayAidBean).appName, item.aid) },
            { cache, item -> clss(cache.ruPayParam, (item as RupayAidBean).aid) },
        ),
        ;

        companion object {
            fun of(label: String): Category =
                values().firstOrNull { it.label == label }
                    ?: throw IllegalArgumentException("No EMV param category $label")
        }
    }

    private companion object {
        /**
         * The tmsFileDownload POS type: always "pax" for this vendor. Not
         * ModelInfo#getTerminalModel() — a captured old-app call hit foundation/tmsFileDownload/pax
         * (200, real zip back) whatever the model; the per-device model ("A920Pro" etc.) 500'd on
         * the host.
         */
        const val POS_TYPE = "pax"

        /** The cached entry whose key matches `key`; null when the cache doesn't have it. */
        private fun <T> find(cached: List<T>?, keyOf: (T) -> String, key: String?): T? {
            if (cached == null || key == null) {
                return null
            }
            return cached.firstOrNull { key.equals(keyOf(it), ignoreCase = true) }
        }

        /** A contactless kernel's cached AID entry, by AID. */
        private fun clss(param: BaseParam<out BaseAid>?, aid: String?): Any? =
            if (param == null) null else find(param.aidList, { entry -> hex(entry.aid) }, aid)

        private fun hex(bytes: ByteArray?): String = if (bytes == null) "" else ConvertUtils.bcd2Str(bytes)

        private fun row(appName: String?, aid: String?): String = "$appName   ($aid)"
    }
}
