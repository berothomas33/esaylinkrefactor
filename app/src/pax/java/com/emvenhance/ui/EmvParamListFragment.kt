package com.emvenhance.ui

import android.graphics.Typeface
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.ListView
import android.widget.ProgressBar
import android.widget.ScrollView
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.emvenhance.EmvEnhanceApp
import com.emvenhance.R
import com.emvenhance.vendor.pax.PaxTerminal
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
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Shows one EMV param category as a plain list — its own [Category] decides how to load and
 * render it. Loads only when this fragment's view is created, only its own category, never the
 * whole `EmvProcessParam` at once.
 *
 * The database reads run on `Dispatchers.IO` in the view's `lifecycleScope`: leaving the screen
 * (or switching category) cancels them, so nothing is shown on a view that's gone.
 */
class EmvParamListFragment : Fragment() {

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View = inflater.inflate(R.layout.fragment_emv_param_list, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val category = Category.valueOf(arguments?.getString(ARG_CATEGORY) ?: Category.AID.name)

        val progress = view.findViewById<ProgressBar>(R.id.progress)
        val list = view.findViewById<ListView>(R.id.list)
        val emptyText = view.findViewById<TextView>(R.id.emptyText)

        viewLifecycleOwner.lifecycleScope.launch {
            val loaded: Pair<List<Any>, List<String>>? = withContext(Dispatchers.IO) {
                try {
                    val items = category.loader().filterNotNull()
                    items to items.map(category.renderer)
                } catch (e: Exception) {
                    e.printStackTrace()
                    null
                }
            }
            progress.visibility = View.GONE
            when {
                loaded == null -> {
                    emptyText.setText(R.string.emv_param_load_failed)
                    emptyText.visibility = View.VISIBLE
                }
                loaded.second.isEmpty() -> {
                    emptyText.setText(R.string.emv_param_empty)
                    emptyText.visibility = View.VISIBLE
                }
                else -> {
                    val (items, rows) = loaded
                    list.adapter = ArrayAdapter(requireContext(), android.R.layout.simple_list_item_1, rows)
                    list.setOnItemClickListener { _, _, position, _ -> showCached(category, items[position]) }
                }
            }
        }
    }

    /**
     * What the kernel will use for this row: its entry in the terminal's parameter cache
     * (PaxTerminal#kernelParams), every field. A row that isn't cached yet — the database changed
     * since — shows its database values and says so.
     */
    private fun showCached(category: Category, item: Any) {
        val terminal = (requireActivity().application as EmvEnhanceApp).terminal as PaxTerminal
        val cachedLabel = getString(R.string.emv_param_cached)
        val notCachedLabel = getString(R.string.emv_param_not_cached)
        viewLifecycleOwner.lifecycleScope.launch {
            val title = category.renderer(item)
            val text = withContext(Dispatchers.IO) {
                try {
                    val cached = category.cache(terminal.kernelParams(), item)
                    if (cached != null) {
                        cachedLabel + "\n\n" + ParamDump.describe(cached)
                    } else {
                        notCachedLabel + "\n\n" + ParamDump.describe(item)
                    }
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    e.toString()
                }
            }
            val view = TextView(requireContext())
            view.text = text
            view.setTextIsSelectable(true)
            view.typeface = Typeface.MONOSPACE
            view.textSize = 11f
            val pad = (12 * resources.displayMetrics.density).toInt()
            view.setPadding(pad, pad, pad, pad)
            val scroll = ScrollView(requireContext())
            scroll.addView(view)
            AlertDialog.Builder(requireContext())
                .setTitle(title)
                .setView(scroll)
                .setPositiveButton(android.R.string.ok, null)
                .show()
        }
    }

    /**
     * One EMV param category: how to load it from the database, how to render each row, and how
     * to find that row's entry in the kernel's parameter cache (what a transaction uses).
     */
    enum class Category(
        @JvmField val label: String,
        internal val loader: () -> List<*>,
        internal val renderer: (Any) -> String,
        internal val cache: (EmvProcessParam, Any) -> Any?,
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
    }

    companion object {
        private const val ARG_CATEGORY = "category"

        @JvmStatic
        fun newInstance(category: Category): EmvParamListFragment {
            val fragment = EmvParamListFragment()
            val args = Bundle()
            args.putString(ARG_CATEGORY, category.name)
            fragment.arguments = args
            return fragment
        }

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
