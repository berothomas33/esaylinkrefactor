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
 * it; tap a row for every field. Loads only when this fragment's view is created, and only its
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

        val progress = view.findViewById<ProgressBar>(R.id.progress)
        val list = view.findViewById<ListView>(R.id.list)
        val emptyText = view.findViewById<TextView>(R.id.emptyText)

        viewLifecycleOwner.lifecycleScope.launch {
            val loaded: Pair<List<EmvParams.Row>, List<String>>? = withContext(Dispatchers.IO) {
                try {
                    val rows = params.rows(category)
                    rows to rows.map { it.title() }
                } catch (e: CancellationException) {
                    throw e
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
                    val (rows, titles) = loaded
                    list.adapter = ArrayAdapter(requireContext(), android.R.layout.simple_list_item_1, titles)
                    list.setOnItemClickListener { _, _, position, _ -> showDetails(rows[position], titles[position]) }
                }
            }
        }
    }

    /** Every field of one row, as the kernel gets it ([EmvParams.Row.details]). */
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

        @JvmStatic
        fun newInstance(category: String): EmvParamListFragment {
            val fragment = EmvParamListFragment()
            val args = Bundle()
            args.putString(ARG_CATEGORY, category)
            fragment.arguments = args
            return fragment
        }
    }
}
