/*
 *     This file is part of Lawnchair Launcher.
 *
 *     Lawnchair Launcher is free software: you can redistribute it and/or modify
 *     it under the terms of the GNU General Public License as published by
 *     the Free Software Foundation, either version 3 of the License, or
 *     (at your option) any later version.
 *
 *     Lawnchair Launcher is distributed in the hope that it will be useful,
 *     but WITHOUT ANY WARRANTY; without even the implied warranty of
 *     MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *     GNU General Public License for more details.
 *
 *     You should have received a copy of the GNU General Public License
 *     along with Lawnchair Launcher.  If not, see <https://www.gnu.org/licenses/>.
 */

package ch.deletescape.lawnchair.settings.ui.search

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.support.v7.widget.LinearLayoutManager
import android.support.v7.widget.RecyclerView
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.InputMethodManager
import android.widget.ImageView
import android.widget.SearchView
import android.widget.TextView
import ch.deletescape.lawnchair.isVisible
import com.android.launcher3.R
import kotlinx.android.synthetic.main.activity_settings_search.*
import android.support.v7.util.DiffUtil
import android.text.TextUtils
import android.view.MenuItem
import ch.deletescape.lawnchair.settings.ui.SettingsActivity
import ch.deletescape.lawnchair.settings.ui.SettingsActivity.EXTRA_FRAGMENT_ARG_KEY
import ch.deletescape.lawnchair.settings.ui.SettingsActivity.SubSettingsFragment.*
import ch.deletescape.lawnchair.settings.ui.SettingsBaseActivity


class SettingsSearchActivity : SettingsBaseActivity(), SearchView.OnQueryTextListener {

    private val searchIndex by lazy { SearchIndex(this) }
    private val searchAdapter by lazy { SearchAdapter(this) }
    private var currentQuery = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        decorLayout.hideToolbar = true

        setContentView(R.layout.activity_settings_search)

        list_results.adapter = searchAdapter
        list_results.layoutManager = LinearLayoutManager(this)
        list_results.addOnScrollListener(object : RecyclerView.OnScrollListener() {

            override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
                if (dy != 0) {
                    hideKeyboard()
                }
            }
        })

        setSupportActionBar(search_toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        search_view.setOnQueryTextListener(this)
        search_view.requestFocus()
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (item.itemId == android.R.id.home) {
            onBackPressed()
            return true
        }
        return super.onOptionsItemSelected(item)
    }

    private fun hideKeyboard() {
        val view = currentFocus
        val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        imm.hideSoftInputFromWindow(view!!.windowToken, 0)

        list_results.requestFocus()
    }

    override fun onQueryTextChange(newText: String): Boolean {
        doSearch(newText)
        return true
    }

    override fun onQueryTextSubmit(query: String): Boolean {
        return true
    }

    private fun doSearch(query: String) {
        if (query == currentQuery) return
        currentQuery = query
        val matches = if (query.isEmpty())
            emptyList()
        else
            searchIndex.entries.filter { it.title.toLowerCase().contains(query.toLowerCase()) }
        list_results.isVisible = matches.isNotEmpty() || query.isEmpty()
        no_results_layout.isVisible = matches.isEmpty() && !query.isEmpty()
        searchAdapter.postSearchResults(matches)
    }

    class SearchAdapter(private val activity: SettingsSearchActivity) : RecyclerView.Adapter<SearchAdapter.Holder>() {

        private val searchResults = ArrayList<SearchIndex.SettingsEntry>()

        init {
            setHasStableIds(true)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Holder {
            return SettingsEntryHolder(LayoutInflater.from(parent.context).inflate(
                    R.layout.search_intent_item, parent, false))
        }

        override fun onBindViewHolder(holder: Holder, position: Int) {
            holder.onBind(activity, searchResults[position])
        }

        override fun getItemId(position: Int): Long {
            return searchResults[position].getId()
        }

        override fun getItemCount(): Int {
            return searchResults.size
        }

        fun postSearchResults(newResults: List<SearchIndex.SettingsEntry>) {
            val diffResult = DiffUtil.calculateDiff(SearchResultDiffCallback(searchResults, newResults))
            searchResults.clear()
            searchResults.addAll(newResults)
            diffResult.dispatchUpdatesTo(this)
        }

        open class Holder(itemView: View) : RecyclerView.ViewHolder(itemView) {

            protected val titleView = itemView.findViewById(android.R.id.title) as TextView
            protected val summaryView = itemView.findViewById(android.R.id.summary) as TextView
            protected val iconView = itemView.findViewById(android.R.id.icon) as ImageView
            protected val breadcrumbView = itemView.findViewById(R.id.breadcrumb) as TextView

            open fun onBind(activity: SettingsSearchActivity, entry: SearchIndex.SettingsEntry) {
                titleView.text = entry.title
                if (TextUtils.isEmpty(entry.summary) || entry.summary == "%s") {
                    summaryView.visibility = View.GONE
                } else {
                    summaryView.text = entry.summary
                    summaryView.visibility = View.VISIBLE
                }
                // TODO: implement displaying icons
                // Valid even when result.icon is null.
                // iconView.setImageDrawable(result.icon)
                bindBreadcrumbView(entry)
            }

            private fun bindBreadcrumbView(entry: SearchIndex.SettingsEntry) {
                if (entry.breadcrumbs.isEmpty()) {
                    breadcrumbView.visibility = View.GONE
                    return
                }
                breadcrumbView.text = entry.breadcrumbs
                breadcrumbView.visibility = View.VISIBLE
            }
        }

        class SettingsEntryHolder(itemView: View) : Holder(itemView) {

            override fun onBind(activity: SettingsSearchActivity, entry: SearchIndex.SettingsEntry) {
                super.onBind(activity, entry)

                itemView.setOnClickListener {
                    val context = itemView.context
                    val intent = Intent(context, SettingsActivity::class.java)
                    intent.putExtra(EXTRA_FRAGMENT_ARG_KEY, entry.key)
                    if (entry.parent != null) {
                        intent.putExtra(TITLE, entry.parent.title)
                        intent.putExtra(CONTENT_RES_ID, entry.parent.contentRes)
                        intent.putExtra(HAS_PREVIEW, entry.parent.hasPreview)
                    }
                    context.startActivity(intent)
                }
            }
        }
    }

    class SearchResultDiffCallback(
            private val oldList: List<SearchIndex.SettingsEntry>,
            private val newList: List<SearchIndex.SettingsEntry>) : DiffUtil.Callback() {

        override fun getOldListSize() = oldList.size

        override fun getNewListSize() = newList.size

        override fun areItemsTheSame(oldItemPosition: Int, newItemPosition: Int): Boolean {
            return oldList[oldItemPosition] == newList[newItemPosition]
        }

        override fun areContentsTheSame(oldItemPosition: Int, newItemPosition: Int): Boolean {
            return oldList[oldItemPosition] == newList[newItemPosition]
        }
    }
}
