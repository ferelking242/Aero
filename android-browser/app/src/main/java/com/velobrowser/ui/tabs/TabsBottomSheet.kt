package com.velobrowser.ui.tabs

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.activityViewModels
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.viewpager2.widget.ViewPager2
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.google.android.material.tabs.TabLayoutMediator
import com.velobrowser.R
import com.velobrowser.databinding.BottomSheetTabsBinding
import com.velobrowser.domain.model.BrowserTab
import com.velobrowser.ui.browser.BrowserViewModel
import com.velobrowser.ui.isolated.IsolatedBrowserActivity
import com.velobrowser.utils.collectFlow
import com.velobrowser.utils.toast
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class TabsBottomSheet : BottomSheetDialogFragment() {

    companion object {
        const val TAG = "TabsBottomSheet"
        private const val TAB_POS_NORMAL = 0
        private const val TAB_POS_INCOGNITO = 1
        private const val TAB_POS_ISOLATED = 2

        fun newInstance() = TabsBottomSheet()
    }

    private var _binding: BottomSheetTabsBinding? = null
    private val binding get() = _binding!!
    private val viewModel: BrowserViewModel by activityViewModels()
    private lateinit var pagerAdapter: TabsPagerAdapter

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = BottomSheetTabsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        setupPager()
        setupButtons()

        collectFlow(viewModel.tabs) { tabs ->
            pagerAdapter.submitSection(TAB_POS_NORMAL, tabs.filter { !it.isIncognito && !it.isIsolated })
            pagerAdapter.submitSection(TAB_POS_INCOGNITO, tabs.filter { it.isIncognito })
            pagerAdapter.submitSection(TAB_POS_ISOLATED, tabs.filter { it.isIsolated })
            updateSectionLabels(tabs)
            updateHeader(tabs)
        }
    }

    private fun setupPager() {
        pagerAdapter = TabsPagerAdapter()
        binding.viewPagerTabs.adapter = pagerAdapter
        binding.viewPagerTabs.offscreenPageLimit = 2

        TabLayoutMediator(binding.tabTypeSelector, binding.viewPagerTabs) { tab, pos ->
            tab.text = when (pos) {
                TAB_POS_NORMAL -> getString(R.string.tabs)
                TAB_POS_INCOGNITO -> getString(R.string.incognito)
                TAB_POS_ISOLATED -> getString(R.string.isolated_tabs)
                else -> ""
            }
        }.attach()

        val activeTab = viewModel.activeTab
        val initialPos = when {
            activeTab?.isIsolated == true -> TAB_POS_ISOLATED
            activeTab?.isIncognito == true -> TAB_POS_INCOGNITO
            else -> TAB_POS_NORMAL
        }
        binding.viewPagerTabs.setCurrentItem(initialPos, false)

        binding.viewPagerTabs.registerOnPageChangeCallback(object : ViewPager2.OnPageChangeCallback() {
            override fun onPageSelected(position: Int) {
                updateHeader(viewModel.tabs.value)
            }
        })
    }

    private fun setupButtons() {
        binding.btnNewTab.setOnClickListener {
            when (binding.viewPagerTabs.currentItem) {
                TAB_POS_INCOGNITO -> viewModel.openNewTab(incognito = true)
                TAB_POS_ISOLATED -> {
                    if (viewModel.canOpenIsolatedTab()) {
                        viewModel.openIsolatedTab()
                    } else {
                        toast(getString(R.string.max_isolated_tabs_reached))
                        return@setOnClickListener
                    }
                }
                else -> viewModel.openNewTab()
            }
            dismissAllowingStateLoss()
        }

        binding.btnCloseAllTabs.setOnClickListener {
            val section = binding.viewPagerTabs.currentItem
            val toClose = getTabsForSection(section, viewModel.tabs.value)
            toClose.forEach { viewModel.closeTab(it.id) }
            if (section != TAB_POS_NORMAL) {
                binding.viewPagerTabs.setCurrentItem(TAB_POS_NORMAL, true)
            } else {
                dismissAllowingStateLoss()
            }
        }
    }

    private fun updateSectionLabels(tabs: List<BrowserTab>) {
        val normalCount = tabs.count { !it.isIncognito && !it.isIsolated }
        val incognitoCount = tabs.count { it.isIncognito }
        val isolatedCount = tabs.count { it.isIsolated }

        binding.tabTypeSelector.getTabAt(TAB_POS_NORMAL)?.text =
            if (normalCount > 0) "${getString(R.string.tabs)} ($normalCount)" else getString(R.string.tabs)
        binding.tabTypeSelector.getTabAt(TAB_POS_INCOGNITO)?.text =
            if (incognitoCount > 0) "${getString(R.string.incognito)} ($incognitoCount)" else getString(R.string.incognito)
        binding.tabTypeSelector.getTabAt(TAB_POS_ISOLATED)?.text =
            if (isolatedCount > 0) "${getString(R.string.isolated_tabs)} ($isolatedCount)" else getString(R.string.isolated_tabs)
    }

    private fun updateHeader(tabs: List<BrowserTab>) {
        val section = binding.viewPagerTabs.currentItem
        val normalCount = tabs.count { !it.isIncognito && !it.isIsolated }
        val incognitoCount = tabs.count { it.isIncognito }
        val isolatedCount = tabs.count { it.isIsolated }

        val headerText = when (section) {
            TAB_POS_INCOGNITO -> "$incognitoCount ${getString(R.string.incognito_tabs_label)}"
            TAB_POS_ISOLATED -> "$isolatedCount ${getString(R.string.isolated_tabs_label)}"
            else -> "$normalCount ${getString(R.string.tab_count_label)}"
        }
        binding.tvTabCountHeader.text = headerText
    }

    private fun getTabsForSection(section: Int, tabs: List<BrowserTab>): List<BrowserTab> = when (section) {
        TAB_POS_INCOGNITO -> tabs.filter { it.isIncognito }
        TAB_POS_ISOLATED -> tabs.filter { it.isIsolated }
        else -> tabs.filter { !it.isIncognito && !it.isIsolated }
    }

    private fun onTabClicked(tab: BrowserTab) {
        if (tab.isIsolated) {
            val intent = IsolatedBrowserActivity.createIntent(requireContext(), tab.isolatedSlot, tab.url)
            intent.addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT)
            startActivity(intent)
        } else {
            viewModel.switchToTab(tab.id)
        }
        dismissAllowingStateLoss()
    }

    private fun onTabClosed(tab: BrowserTab) {
        viewModel.closeTab(tab.id)
        val section = binding.viewPagerTabs.currentItem
        val remaining = getTabsForSection(section, viewModel.tabs.value)
        if (remaining.isEmpty() && section != TAB_POS_NORMAL) {
            binding.viewPagerTabs.setCurrentItem(TAB_POS_NORMAL, true)
        }
    }

    inner class TabsPagerAdapter : RecyclerView.Adapter<TabsPagerAdapter.PageHolder>() {

        private val tabAdapters = Array(3) {
            TabsAdapter(
                activeTabId = { viewModel.activeTabId.value },
                onTabClicked = ::onTabClicked,
                onTabClosed = ::onTabClosed
            )
        }

        fun submitSection(index: Int, tabs: List<BrowserTab>) {
            tabAdapters[index].submitList(tabs.toList())
        }

        inner class PageHolder(val rv: RecyclerView) : RecyclerView.ViewHolder(rv)

        override fun getItemCount() = 3

        override fun getItemViewType(position: Int) = position

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): PageHolder {
            val rv = RecyclerView(parent.context).apply {
                layoutParams = ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT
                )
                layoutManager = GridLayoutManager(context, 2)
                isNestedScrollingEnabled = false
            }
            return PageHolder(rv)
        }

        override fun onBindViewHolder(holder: PageHolder, position: Int) {
            holder.rv.adapter = tabAdapters[position]
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
