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
import com.pax.emvbase.param.common.Capk
import com.pax.commonlib.application.BaseApplication
import com.pax.configservice.impl.ConfigInit
import com.pax.configservice.impl.EmvParamUpdater
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.lang.reflect.Modifier

/**
 * PAX's [EmvParams]: the GreenDAO parameter database, the kernel's parameter cache
 * ([PaxTerminal.kernelParams], shown apart, never loaded from here), and the host's parameter package ([EmvParamDownloadClient] +
 * [EmvParamUpdater]). Every row is rendered here, so the screen never sees a PAX type. Nothing
 * writes the database while a transaction runs.
 */
internal class PaxEmvParams(private val terminal: PaxTerminal) : EmvParams {

    override fun categories(): List<String> = Category.values().map { it.label }

    override fun rows(category: String): List<EmvParams.Row> {
        val c = Category.of(category)
        return c.loader().filterNotNull().map { PaxRow(c.renderer(it), it) }
    }

    /** Read from [PaxTerminal.kernelParams], which never loads it. */
    override fun cachedRows(category: String): List<EmvParams.Row>? {
        val cache = terminal.kernelParams() ?: return null
        val c = Category.of(category)
        val entries = c.cached(cache) ?: throw IllegalStateException(c.label + " is not in the kernel's cache")
        return entries.filterNotNull().map { PaxRow(c.cachedRenderer(it), it) }
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

    /** One entry — a database row or a cache entry — and every field of it. */
    private class PaxRow(private val title: String, private val item: Any) : EmvParams.Row {

        override fun title(): String = title

        override fun details(): String = describe(item)
    }

    /**
     * One EMV param category: how to load it from the database and render each row, and how to
     * read its entries in the kernel's parameter cache (what a transaction uses) and render them.
     * `cached` is `null` when the cache lacks the category.
     */
    private enum class Category(
        val label: String,
        val loader: () -> List<*>,
        val renderer: (Any) -> String,
        val cached: (EmvProcessParam) -> List<*>?,
        val cachedRenderer: (Any) -> String,
    ) {
        AID(
            "AID",
            { GreendaoHelper.getEmvAidHelper().loadAll() },
            { item -> row((item as EmvAid).appName, item.aid) },
            { cache -> cache.emvAidList },
            { entry -> hex((entry as com.pax.emvbase.param.contact.EmvAid).applicationID) },
        ),
        CAPK(
            "CAPK",
            { GreendaoHelper.getEmvCapkHelper().loadAll() },
            { item -> "RID " + (item as EmvCapk).rid + "   Key " + item.keyID },
            { cache -> cache.capkParam?.capkList },
            { entry -> "RID " + hex((entry as Capk).rid) + "   Key " + (entry.keyId.toInt() and 0xFF) },
        ),
        AMEX(
            "Amex",
            { AmexAidDbHelper.getInstance().loadAll() },
            { item -> row((item as AmexAidBean).appName, item.aid) },
            { cache -> clss(cache.amexParam) },
            { entry -> clssRow(entry) },
        ),
        PAYPASS(
            "PayPass",
            { PaypassAidDbHelper.getInstance().loadAll() },
            { item -> row((item as PayPassAidBean).appName, item.aid) },
            { cache -> clss(cache.payPassParam) },
            { entry -> clssRow(entry) },
        ),
        PAYWAVE(
            "PayWave",
            { PaywaveAidDbHelper.getInstance().loadAll() },
            { item -> row((item as PaywaveAidBean).appName, item.aid) },
            { cache -> clss(cache.payWaveParam) },
            { entry -> clssRow(entry) },
        ),
        DPAS(
            "Dpas",
            { DpasAidDbHelper.getInstance().loadAll() },
            { item -> row((item as DpasAidBean).appName, item.aid) },
            { cache -> clss(cache.dpasParam) },
            { entry -> clssRow(entry) },
        ),
        EFT(
            "EFT",
            { EFTAidDbHelper.getInstance().loadAll() },
            { item -> row((item as EFTAidBean).appName, item.aid) },
            { cache -> clss(cache.eftParam) },
            { entry -> clssRow(entry) },
        ),
        JCB(
            "Jcb",
            { JcbAidDbHelper.getInstance().loadAll() },
            { item -> row((item as JcbAidBean).appName, item.aid) },
            { cache -> clss(cache.jcbParam) },
            { entry -> clssRow(entry) },
        ),
        MIR(
            "Mir",
            { MirAidDbHelper.getInstance().loadAll() },
            { item -> row((item as MirAidBean).appName, item.aid) },
            { cache -> clss(cache.mirParam) },
            { entry -> clssRow(entry) },
        ),
        PBOC(
            "Pboc",
            { PBOCAidDbHelper.getInstance().loadAll() },
            { item -> row((item as PBOCAidBean).appName, item.aid) },
            { cache -> clss(cache.pbocParam) },
            { entry -> clssRow(entry) },
        ),
        PURE(
            "Pure",
            { PureAidDbHelper.getInstance().loadAll() },
            { item -> row((item as PureAidBean).appName, item.aid) },
            { cache -> clss(cache.pureParam) },
            { entry -> clssRow(entry) },
        ),
        RUPAY(
            "RuPay",
            { RupayAidDbHelper.getInstance().loadAll() },
            { item -> row((item as RupayAidBean).appName, item.aid) },
            { cache -> clss(cache.ruPayParam) },
            { entry -> clssRow(entry) },
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

        /** A contactless kernel's cached AIDs; `null` when the cache lacks the kernel. */
        private fun clss(param: BaseParam<out BaseAid>?): List<*>? = param?.aidList

        /** A cached contactless AID, the way [row] shows its database row. */
        private fun clssRow(entry: Any): String {
            val aid = entry as BaseAid
            val name = aid.appName
            return row(if (name == null) "" else String(name, Charsets.US_ASCII).trim(), hex(aid.aid))
        }

        private fun hex(bytes: ByteArray?): String = if (bytes == null) "" else ConvertUtils.bcd2Str(bytes)

        private fun row(appName: String?, aid: String?): String = "$appName   ($aid)"

        /**
         * Every field of one parameter object — a cached kernel AID, CAPK or the like — as text, the
         * way the kernel gets it: byte arrays in hex, nested objects and lists indented.
         */
        fun describe(value: Any): String {
            val text = StringBuilder()
            append(text, value, "")
            return text.toString()
        }

        private fun append(text: StringBuilder, value: Any, indent: String) {
            var type: Class<*>? = value.javaClass
            while (type != null && type != Any::class.java) {
                for (field in type.declaredFields) {
                    if (Modifier.isStatic(field.modifiers) || field.isSynthetic) {
                        continue
                    }
                    field.isAccessible = true
                    val fieldValue = try {
                        field.get(value)
                    } catch (e: IllegalAccessException) {
                        continue
                    }
                    text.append(indent).append(field.name).append(" = ")
                    appendValue(text, fieldValue, indent)
                }
                type = type.superclass
            }
        }

        private fun appendValue(text: StringBuilder, value: Any?, indent: String) {
            when {
                value == null -> text.append("null\n")
                value is ByteArray ->
                    text.append(if (value.isEmpty()) "(empty)" else ConvertUtils.bcd2Str(value)).append('\n')
                value is Byte -> text.append(String.format("%02X", value.toInt() and 0xFF)).append('\n')
                value.javaClass.isArray -> {
                    val items = StringBuilder("[")
                    for (i in 0 until java.lang.reflect.Array.getLength(value)) {
                        items.append(if (i == 0) "" else ", ").append(java.lang.reflect.Array.get(value, i))
                    }
                    text.append(items).append("]\n")
                }
                value is Collection<*> -> {
                    text.append(value.size).append(" item(s)\n")
                    for ((i, item) in value.withIndex()) {
                        text.append(indent).append("  [").append(i).append("]\n")
                        if (isPlain(item)) {
                            text.append(indent).append("    ")
                            appendValue(text, item, "$indent    ")
                        } else {
                            append(text, item!!, "$indent    ")
                        }
                    }
                }
                isPlain(value) -> text.append(value).append('\n')
                else -> {
                    text.append('\n')
                    append(text, value, "$indent  ")
                }
            }
        }

        private fun isPlain(value: Any?): Boolean =
            value == null || value is CharSequence || value is Number || value is Boolean ||
                value is Char || value is Enum<*> || value is ByteArray || value.javaClass.isArray
    }
}
