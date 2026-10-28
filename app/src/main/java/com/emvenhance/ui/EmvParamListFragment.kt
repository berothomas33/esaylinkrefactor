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
import com.emvenhance.core.param.EmvParams
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Shows one EMV param category as a plain list of the rows the terminal's [EmvParams] gives for
 * it — from the kernel's cache ([EmvParams.cachedRows]) or the database ([EmvParams.rows]); tap a
 * row for every field. Loads only when this fragment's view is created, and only its
 * own category.
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

        val params = (requireActivity().application as EmvEnhanceApp).terminal.emvParams() ?: return
        val category = arguments?.getString(ARG_CATEGORY) ?: params.categories()[0]
        val fromCache = arguments?.getBoolean(ARG_FROM_CACHE) == true

        val progress = view.findViewById<ProgressBar>(R.id.progress)
        val list = view.findViewById<ListView>(R.id.list)
        val emptyText = view.findViewById<TextView>(R.id.emptyText)

        viewLifecycleOwner.lifecycleScope.launch {
            val loaded: Loaded = withContext(Dispatchers.IO) {
                try {
                    val rows = if (fromCache) params.cachedRows(category) else params.rows(category)
                    if (rows == null) Loaded.CacheEmpty else Loaded.Rows(rows, rows.map { it.title() })
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    e.printStackTrace()
                    Loaded.Failed(e.message ?: e.toString())
                }
            }
            progress.visibility = View.GONE
            when (loaded) {
                is Loaded.Failed -> {
                    emptyText.text = getString(R.string.emv_param_load_failed) + "\n" + loaded.message
                    emptyText.visibility = View.VISIBLE
                }
                Loaded.CacheEmpty -> {
                    emptyText.setText(R.string.emv_param_cache_empty) // nothing loaded it yet
                    emptyText.visibility = View.VISIBLE
                }
                is Loaded.Rows -> if (loaded.titles.isEmpty()) {
                    emptyText.setText(R.string.emv_param_empty)
                    emptyText.visibility = View.VISIBLE
                } else {
                    val titles = loaded.titles
                    list.adapter = ArrayAdapter(requireContext(), android.R.layout.simple_list_item_1, titles)
                    list.setOnItemClickListener { _, _, position, _ -> showDetails(loaded.rows[position], titles[position]) }
                }
            }
        }
    }

    /** What a category load gave: its rows, an empty cache, or a failure. */
    private sealed class Loaded {
        class Rows(val rows: List<EmvParams.Row>, val titles: List<String>) : Loaded()
        object CacheEmpty : Loaded()
        class Failed(val message: String) : Loaded()
    }

    /** Every field of one row, from its source ([EmvParams.Row.details]). */
    private fun showDetails(row: EmvParams.Row, title: String) {
        viewLifecycleOwner.lifecycleScope.launch {
            val text = withContext(Dispatchers.IO) {
                try {
                    row.details()
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

    companion object {
        private const val ARG_CATEGORY = "category"
        private const val ARG_FROM_CACHE = "fromCache"

        /** @param fromCache the kernel's cache; `false`: the database */
        @JvmStatic
        fun newInstance(category: String, fromCache: Boolean): EmvParamListFragment {
            val fragment = EmvParamListFragment()
            val args = Bundle()
            args.putString(ARG_CATEGORY, category)
            args.putBoolean(ARG_FROM_CACHE, fromCache)
            fragment.arguments = args
            return fragment
        }
    }
}
