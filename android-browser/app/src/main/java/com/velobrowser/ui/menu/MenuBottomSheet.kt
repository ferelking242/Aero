package com.velobrowser.ui.menu

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.activityViewModels
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.velobrowser.R
import com.velobrowser.databinding.BottomSheetMenuBinding
import com.velobrowser.ui.browser.BrowserViewModel
import com.velobrowser.ui.downloads.DownloadsActivity
import com.velobrowser.ui.profiles.ProfileManagerActivity
import com.velobrowser.ui.settings.SettingsActivity
import com.velobrowser.utils.collectFlow
import com.velobrowser.utils.toast
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MenuBottomSheet : BottomSheetDialogFragment() {

    companion object {
        const val TAG = "MenuBottomSheet"
        fun newInstance() = MenuBottomSheet()
    }

    private var _binding: BottomSheetMenuBinding? = null
    private val binding get() = _binding!!
    private val viewModel: BrowserViewModel by activityViewModels()

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = BottomSheetMenuBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        setupMenuItems()
        observeBookmarkState()
    }

    private fun setupMenuItems() {
        binding.itemNightMode.setOnClickListener {
            val newDark = !viewModel.settings.value.darkMode
            viewModel.setDarkMode(newDark)
            toast(getString(if (newDark) R.string.dark_mode else R.string.menu_dark_mode))
            dismissAllowingStateLoss()
        }

        binding.itemBookmarks.setOnClickListener {
            dismissAllowingStateLoss()
            activity?.supportFragmentManager?.let { fm ->
                com.velobrowser.ui.browser.BookmarksBottomSheet.newInstance()
                    .show(fm, com.velobrowser.ui.browser.BookmarksBottomSheet.TAG)
            }
        }

        binding.itemHistory.setOnClickListener {
            dismissAllowingStateLoss()
            activity?.supportFragmentManager?.let { fm ->
                com.velobrowser.ui.browser.HistoryBottomSheet.newInstance()
                    .show(fm, com.velobrowser.ui.browser.HistoryBottomSheet.TAG)
            }
        }

        binding.itemDownloads.setOnClickListener {
            startActivity(Intent(requireContext(), DownloadsActivity::class.java))
            dismissAllowingStateLoss()
        }

        binding.itemIncognito.setOnClickListener {
            viewModel.openNewTab(incognito = true)
            dismissAllowingStateLoss()
        }

        binding.itemShare.setOnClickListener {
            val url = viewModel.currentUrl.value
            if (url.isNotEmpty()) {
                val shareIntent = Intent(Intent.ACTION_SEND).apply {
                    type = "text/plain"
                    putExtra(Intent.EXTRA_TEXT, url)
                }
                startActivity(Intent.createChooser(shareIntent, getString(R.string.share)))
            }
            dismissAllowingStateLoss()
        }

        binding.itemAddBookmark.setOnClickListener {
            viewModel.toggleBookmark()
            dismissAllowingStateLoss()
        }

        binding.itemDesktop.setOnClickListener {
            val newDesktop = !viewModel.settings.value.desktopMode
            viewModel.setDesktopMode(newDesktop)
            dismissAllowingStateLoss()
        }

        binding.itemTools.setOnClickListener {
            startActivity(Intent(requireContext(), ProfileManagerActivity::class.java))
            dismissAllowingStateLoss()
        }

        binding.itemSettings.setOnClickListener {
            startActivity(Intent(requireContext(), SettingsActivity::class.java))
            dismissAllowingStateLoss()
        }

        binding.btnExit.setOnClickListener {
            dismissAllowingStateLoss()
            activity?.finishAffinity()
        }

        binding.btnDismissMenu.setOnClickListener {
            dismissAllowingStateLoss()
        }
    }

    private fun observeBookmarkState() {
        collectFlow(viewModel.isBookmarked) { isBookmarked ->
            binding.ivBookmarkIcon.setImageResource(
                if (isBookmarked) R.drawable.ic_bookmark_filled else R.drawable.ic_bookmark
            )
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
