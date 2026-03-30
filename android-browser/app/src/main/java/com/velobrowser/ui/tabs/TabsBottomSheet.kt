package com.velobrowser.ui.tabs

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.activityViewModels
import androidx.recyclerview.widget.GridLayoutManager
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.google.android.material.tabs.TabLayout
import com.velobrowser.R
import com.velobrowser.databinding.BottomSheetTabsBinding
import com.velobrowser.domain.model.BrowserTab
import com.velobrowser.ui.browser.BrowserViewModel
import com.velobrowser.utils.collectFlow
import com.velobrowser.utils.gone
import com.velobrowser.utils.toast
import com.velobrowser.utils.visible
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
    private lateinit var adapter: TabsAdapter

    private var currentSection = TAB_POS_NORMAL

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = BottomSheetTabsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        adapter = TabsAdapter(
            activeTabId = { viewModel.activeTabId.value },
            onTabClicked = { tab ->
                if (tab.isIsolated) {
                    val intent = com.velobrowser.ui.isolated.IsolatedBrowserActivity.createIntent(
                        requireContext(), tab.isolatedSlot, tab.url
                    )
                    startActivity(intent)
                    dismissAllowingStateLoss()
                } else {
                    viewModel.switchToTab(tab.id)
                    dismissAllowingStateLoss()
                }
            },
            onTabClosed = { tab ->
                viewModel.closeTab(tab.id)
                val remainingInSection = getTabsForSection(currentSection, viewModel.tabs.value)
                if (remainingInSection.isEmpty()) {
                    if (currentSection != TAB_POS_NORMAL) {
                        currentSection = TAB_POS_NORMAL
                        binding.tabTypeSelector.getTabAt(TAB_POS_NORMAL)?.select()
                    } else {
                        dismissAllowingStateLoss()
                    }
                }
            }
        )

        binding.recyclerTabs.layoutManager = GridLayoutManager(requireContext(), 2)
        binding.recyclerTabs.adapter = adapter

        setupTabSelector()

        binding.btnNewTab.setOnClickListener {
            viewModel.openNewTab()
            dismissAllowingStateLoss()
        }

        binding.btnNewIncognito.setOnClickListener {
            viewModel.openNewTab(incognito = true)
            dismissAllowingStateLoss()
        }

        binding.btnNewIsolated.setOnClickListener {
            if (viewModel.canOpenIsolatedTab()) {
                viewModel.openIsolatedTab()
                dismissAllowingStateLoss()
            } else {
                toast(getString(R.string.max_isolated_tabs_reached))
            }
        }

        binding.btnCloseAllTabs.setOnClickListener {
            val toClose = getTabsForSection(currentSection, viewModel.tabs.value)
            toClose.forEach { viewModel.closeTab(it.id) }
            if (currentSection != TAB_POS_NORMAL) {
                currentSection = TAB_POS_NORMAL
                binding.tabTypeSelector.getTabAt(TAB_POS_NORMAL)?.select()
            } else {
                dismissAllowingStateLoss()
            }
        }

        collectFlow(viewModel.tabs) { tabs ->
            updateTabList(tabs)
            updateTabSelector(tabs)
        }
    }

    private fun setupTabSelector() {
        binding.tabTypeSelector.addTab(
            binding.tabTypeSelector.newTab().setText(getString(R.string.tabs))
        )
        binding.tabTypeSelector.addTab(
            binding.tabTypeSelector.newTab().setText(getString(R.string.incognito))
        )
        binding.tabTypeSelector.addTab(
            binding.tabTypeSelector.newTab().setText(getString(R.string.isolated_tabs))
        )

        val activeTab = viewModel.activeTab
        when {
            activeTab?.isIncognito == true -> {
                currentSection = TAB_POS_INCOGNITO
                binding.tabTypeSelector.getTabAt(TAB_POS_INCOGNITO)?.select()
            }
            activeTab?.isIsolated == true -> {
                currentSection = TAB_POS_ISOLATED
                binding.tabTypeSelector.getTabAt(TAB_POS_ISOLATED)?.select()
            }
        }

        binding.tabTypeSelector.addOnTabSelectedListener(object : TabLayout.OnTabSelectedListener {
            override fun onTabSelected(tab: TabLayout.Tab?) {
                currentSection = tab?.position ?: TAB_POS_NORMAL
                updateTabList(viewModel.tabs.value)
                updateIsolatedInfoVisibility()
            }
            override fun onTabUnselected(tab: TabLayout.Tab?) {}
            override fun onTabReselected(tab: TabLayout.Tab?) {}
        })
    }

    private fun updateIsolatedInfoVisibility() {
        if (currentSection == TAB_POS_ISOLATED) {
            binding.tvIsolatedInfo.visible()
        } else {
            binding.tvIsolatedInfo.gone()
        }
    }

    private fun updateTabSelector(tabs: List<BrowserTab>) {
        val normalCount = tabs.count { !it.isIncognito && !it.isIsolated }
        val incognitoCount = tabs.count { it.isIncognito }
        val isolatedCount = tabs.count { it.isIsolated }

        binding.tabTypeSelector.getTabAt(TAB_POS_NORMAL)?.text =
            if (normalCount > 0) "${getString(R.string.tabs)} ($normalCount)" else getString(R.string.tabs)
        binding.tabTypeSelector.getTabAt(TAB_POS_INCOGNITO)?.text =
            if (incognitoCount > 0) "${getString(R.string.incognito)} ($incognitoCount)" else getString(R.string.incognito)
        binding.tabTypeSelector.getTabAt(TAB_POS_ISOLATED)?.text =
            if (isolatedCount > 0) "${getString(R.string.isolated_tabs)} ($isolatedCount)" else getString(R.string.isolated_tabs)

        val headerText = when (currentSection) {
            TAB_POS_INCOGNITO -> "$incognitoCount ${getString(R.string.incognito_tabs_label)}"
            TAB_POS_ISOLATED -> "$isolatedCount ${getString(R.string.isolated_tabs_label)}"
            else -> "$normalCount ${getString(R.string.tab_count_label)}"
        }
        binding.tvTabCountHeader.text = headerText
    }

    private fun updateTabList(tabs: List<BrowserTab>) {
        val filtered = getTabsForSection(currentSection, tabs)
        adapter.submitList(filtered)
    }

    private fun getTabsForSection(section: Int, tabs: List<BrowserTab>): List<BrowserTab> {
        return when (section) {
            TAB_POS_INCOGNITO -> tabs.filter { it.isIncognito }
            TAB_POS_ISOLATED -> tabs.filter { it.isIsolated }
            else -> tabs.filter { !it.isIncognito && !it.isIsolated }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
