package com.velobrowser.ui.menu

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import androidx.fragment.app.activityViewModels
import androidx.recyclerview.widget.RecyclerView
import androidx.viewpager2.widget.ViewPager2
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.velobrowser.R
import com.velobrowser.databinding.BottomSheetMenuBinding
import com.velobrowser.ui.browser.BookmarksBottomSheet
import com.velobrowser.ui.browser.BrowserActivity
import com.velobrowser.ui.browser.BrowserViewModel
import com.velobrowser.ui.browser.HistoryBottomSheet
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

    private var page2View: View? = null

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        _binding = BottomSheetMenuBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupViewPager()
        setupBottomButtons()
        observeBookmarkState()
    }

    private fun setupViewPager() {
        val inflater = LayoutInflater.from(requireContext())
        val page1 = inflater.inflate(R.layout.layout_menu_page1, binding.menuViewPager, false)
        val page2 = inflater.inflate(R.layout.layout_menu_page2, binding.menuViewPager, false)
        val page3 = inflater.inflate(R.layout.layout_menu_page3, binding.menuViewPager, false)
        page2View = page2

        setupPage1(page1)
        setupPage2(page2)
        setupPage3(page3)

        val pages = listOf(page1, page2, page3)
        binding.menuViewPager.adapter = MenuPagerAdapter(pages)
        binding.menuViewPager.offscreenPageLimit = 2

        binding.menuViewPager.registerOnPageChangeCallback(object : ViewPager2.OnPageChangeCallback() {
            override fun onPageSelected(position: Int) {
                updateDots(position)
                updateNavButtons(position, pages.size)
            }
        })

        updateDots(0)
        updateNavButtons(0, pages.size)

        binding.btnMenuPrev.setOnClickListener {
            val cur = binding.menuViewPager.currentItem
            if (cur > 0) binding.menuViewPager.currentItem = cur - 1
        }
        binding.btnMenuNext.setOnClickListener {
            val cur = binding.menuViewPager.currentItem
            if (cur < pages.size - 1) binding.menuViewPager.currentItem = cur + 1
        }
    }

    private fun updateDots(position: Int) {
        val dots = listOf(binding.dot1, binding.dot2, binding.dot3)
        dots.forEachIndexed { i, dot -> dot.alpha = if (i == position) 1.0f else 0.3f }
    }

    private fun updateNavButtons(position: Int, total: Int) {
        binding.btnMenuPrev.alpha = if (position > 0) 1.0f else 0.3f
        binding.btnMenuNext.alpha = if (position < total - 1) 1.0f else 0.3f
    }

    private fun setupPage1(v: View) {
        v.findViewById<View>(R.id.p1NightMode).setOnClickListener {
            val dark = !viewModel.settings.value.darkMode
            viewModel.setDarkMode(dark)
            toast(getString(if (dark) R.string.dark_mode else R.string.menu_dark_mode))
            dismissAllowingStateLoss()
        }
        v.findViewById<View>(R.id.p1Bookmarks).setOnClickListener {
            dismissAllowingStateLoss()
            activity?.supportFragmentManager?.let {
                BookmarksBottomSheet.newInstance().show(it, BookmarksBottomSheet.TAG)
            }
        }
        v.findViewById<View>(R.id.p1History).setOnClickListener {
            dismissAllowingStateLoss()
            activity?.supportFragmentManager?.let {
                HistoryBottomSheet.newInstance().show(it, HistoryBottomSheet.TAG)
            }
        }
        v.findViewById<View>(R.id.p1Downloads).setOnClickListener {
            startActivity(Intent(requireContext(), DownloadsActivity::class.java))
            dismissAllowingStateLoss()
        }
        v.findViewById<View>(R.id.p1Incognito).setOnClickListener {
            viewModel.openNewTab(incognito = true)
            dismissAllowingStateLoss()
        }
    }

    private fun setupPage2(v: View) {
        v.findViewById<View>(R.id.p2Share).setOnClickListener {
            val url = viewModel.currentUrl.value
            if (url.isNotEmpty()) {
                startActivity(Intent.createChooser(
                    Intent(Intent.ACTION_SEND).apply {
                        type = "text/plain"
                        putExtra(Intent.EXTRA_TEXT, url)
                    }, getString(R.string.share)
                ))
            }
            dismissAllowingStateLoss()
        }
        v.findViewById<View>(R.id.p2AddBookmark).setOnClickListener {
            viewModel.toggleBookmark()
            dismissAllowingStateLoss()
        }
        v.findViewById<View>(R.id.p2Desktop).setOnClickListener {
            viewModel.setDesktopMode(!viewModel.settings.value.desktopMode)
            dismissAllowingStateLoss()
        }
        v.findViewById<View>(R.id.p2Profiles).setOnClickListener {
            startActivity(Intent(requireContext(), ProfileManagerActivity::class.java))
            dismissAllowingStateLoss()
        }
        v.findViewById<View>(R.id.p2Settings).setOnClickListener {
            startActivity(Intent(requireContext(), SettingsActivity::class.java))
            dismissAllowingStateLoss()
        }
    }

    private fun setupPage3(v: View) {
        v.findViewById<View>(R.id.p3NewTab).setOnClickListener {
            viewModel.openNewTab()
            dismissAllowingStateLoss()
        }
        v.findViewById<View>(R.id.p3CopyUrl).setOnClickListener {
            val url = viewModel.currentUrl.value
            if (url.isNotEmpty()) {
                val cb = requireContext().getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                cb.setPrimaryClip(ClipData.newPlainText("url", url))
                toast(getString(R.string.url_copied))
            }
            dismissAllowingStateLoss()
        }
        v.findViewById<View>(R.id.p3Source).setOnClickListener {
            val url = viewModel.currentUrl.value
            if (url.isNotEmpty() && !url.startsWith("view-source:")) {
                viewModel.navigateTo("view-source:$url")
            }
            dismissAllowingStateLoss()
        }
        v.findViewById<View>(R.id.p3Find).setOnClickListener {
            dismissAllowingStateLoss()
            (activity as? BrowserActivity)?.findInPage()
        }
        v.findViewById<View>(R.id.p3Print).setOnClickListener {
            dismissAllowingStateLoss()
            (activity as? BrowserActivity)?.printCurrentPage()
        }
    }

    private fun setupBottomButtons() {
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
            page2View?.findViewById<ImageView>(R.id.ivBookmarkIcon)?.setImageResource(
                if (isBookmarked) R.drawable.ic_bookmark_filled else R.drawable.ic_bookmark
            )
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    private inner class MenuPagerAdapter(
        private val pages: List<View>
    ) : RecyclerView.Adapter<MenuPagerAdapter.VH>() {

        inner class VH(view: View) : RecyclerView.ViewHolder(view)

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
            val view = pages[viewType]
            view.layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
            return VH(view)
        }

        override fun onBindViewHolder(holder: VH, position: Int) {}

        override fun getItemCount() = pages.size

        override fun getItemViewType(position: Int) = position
    }
}
