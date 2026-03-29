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
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class TabsBottomSheet : BottomSheetDialogFragment() {

    companion object {
        const val TAG = "TabsBottomSheet"
        fun newInstance() = TabsBottomSheet()
    }

    private var _binding: BottomSheetTabsBinding? = null
    private val binding get() = _binding!!
    private val viewModel: BrowserViewModel by activityViewModels()
    private lateinit var adapter: TabsAdapter

    private var showingIncognito = false

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = BottomSheetTabsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        adapter = TabsAdapter(
            activeTabId = { viewModel.activeTabId.value },
            onTabClicked = { tab ->
                viewModel.switchToTab(tab.id)
                dismissAllowingStateLoss()
            },
            onTabClosed = { tab ->
                viewModel.closeTab(tab.id)
                val remaining = viewModel.tabs.value.filter { it.isIncognito == showingIncognito }
                if (remaining.isEmpty()) {
                    if (showingIncognito) {
                        showingIncognito = false
                        binding.tabTypeSelector.getTabAt(0)?.select()
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

        binding.btnCloseAllTabs.setOnClickListener {
            val allTabs = viewModel.tabs.value.toList()
            val toClose = allTabs.filter { it.isIncognito == showingIncognito }
            toClose.forEach { viewModel.closeTab(it.id) }
            if (showingIncognito) {
                showingIncognito = false
                binding.tabTypeSelector.getTabAt(0)?.select()
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

        val hasIncognito = viewModel.activeTab?.isIncognito == true
        if (hasIncognito) {
            showingIncognito = true
            binding.tabTypeSelector.getTabAt(1)?.select()
        }

        binding.tabTypeSelector.addOnTabSelectedListener(object : TabLayout.OnTabSelectedListener {
            override fun onTabSelected(tab: TabLayout.Tab?) {
                showingIncognito = tab?.position == 1
                updateTabList(viewModel.tabs.value)
            }
            override fun onTabUnselected(tab: TabLayout.Tab?) {}
            override fun onTabReselected(tab: TabLayout.Tab?) {}
        })
    }

    private fun updateTabSelector(tabs: List<BrowserTab>) {
        val normalCount = tabs.count { !it.isIncognito }
        val incognitoCount = tabs.count { it.isIncognito }
        binding.tabTypeSelector.getTabAt(0)?.text =
            if (normalCount > 0) "${getString(R.string.tabs)} ($normalCount)" else getString(R.string.tabs)
        binding.tabTypeSelector.getTabAt(1)?.text =
            if (incognitoCount > 0) "${getString(R.string.incognito)} ($incognitoCount)" else getString(R.string.incognito)

        val header = if (showingIncognito) {
            val cnt = tabs.count { it.isIncognito }
            "$cnt ${getString(R.string.incognito_tabs_label)}"
        } else {
            val cnt = tabs.count { !it.isIncognito }
            "$cnt ${getString(R.string.tab_count_label)}"
        }
        binding.tvTabCountHeader.text = header
    }

    private fun updateTabList(tabs: List<BrowserTab>) {
        val filtered = tabs.filter { it.isIncognito == showingIncognito }
        adapter.submitList(filtered)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
