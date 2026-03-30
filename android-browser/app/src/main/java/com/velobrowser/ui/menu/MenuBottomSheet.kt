package com.velobrowser.ui.menu

import android.content.Intent
import android.content.pm.ActivityInfo
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.util.TypedValue
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.webkit.CookieManager
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.fragment.app.activityViewModels
import androidx.recyclerview.widget.RecyclerView
import androidx.viewpager2.widget.ViewPager2
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.velobrowser.R
import com.velobrowser.databinding.BottomSheetMenuBinding
import com.velobrowser.ui.browser.BookmarksBottomSheet
import com.velobrowser.ui.browser.BrowserViewModel
import com.velobrowser.ui.browser.HistoryBottomSheet
import com.velobrowser.ui.downloads.DownloadsActivity
import com.velobrowser.ui.profiles.ProfileManagerActivity
import com.velobrowser.ui.settings.SettingsActivity
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

    private data class MenuItem(
        val iconRes: Int,
        val label: String,
        val enabled: Boolean = true,
        val action: () -> Unit = {}
    )

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = BottomSheetMenuBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupPager()
        setupDots()
        setupBottomButtons()
    }

    private fun buildPages(): List<List<MenuItem>> {
        val isBookmarked = viewModel.isBookmarked.value
        val isDesktop = viewModel.settings.value.desktopMode
        val isDark = viewModel.settings.value.darkMode
        val isAdBlock = viewModel.settings.value.adBlockerEnabled
        val isImages = viewModel.settings.value.imagesEnabled

        return listOf(
            listOf(
                MenuItem(R.drawable.ic_night_mode,
                    if (isDark) getString(R.string.menu_light_mode) else getString(R.string.menu_dark_mode)) {
                    viewModel.setDarkMode(!isDark); dismiss()
                },
                MenuItem(R.drawable.ic_bookmark, getString(R.string.bookmarks)) {
                    dismiss()
                    activity?.supportFragmentManager?.let {
                        BookmarksBottomSheet.newInstance().show(it, BookmarksBottomSheet.TAG)
                    }
                },
                MenuItem(R.drawable.ic_history, getString(R.string.history)) {
                    dismiss()
                    activity?.supportFragmentManager?.let {
                        HistoryBottomSheet.newInstance().show(it, HistoryBottomSheet.TAG)
                    }
                },
                MenuItem(R.drawable.ic_download, getString(R.string.downloads)) {
                    startActivity(Intent(requireContext(), DownloadsActivity::class.java)); dismiss()
                },
                MenuItem(R.drawable.ic_incognito, getString(R.string.menu_incognito_label)) {
                    viewModel.openNewTab(incognito = true); dismiss()
                },
                MenuItem(R.drawable.ic_share, getString(R.string.share)) {
                    val url = viewModel.currentUrl.value
                    if (url.isNotEmpty()) {
                        val i = Intent(Intent.ACTION_SEND).apply {
                            type = "text/plain"
                            putExtra(Intent.EXTRA_TEXT, url)
                        }
                        startActivity(Intent.createChooser(i, getString(R.string.share)))
                    }
                    dismiss()
                },
                MenuItem(
                    if (isBookmarked) R.drawable.ic_bookmark_filled else R.drawable.ic_bookmark,
                    getString(R.string.menu_add_bookmark)
                ) { viewModel.toggleBookmark(); dismiss() },
                MenuItem(R.drawable.ic_desktop,
                    if (isDesktop) getString(R.string.menu_mobile_mode) else getString(R.string.desktop_mode)) {
                    viewModel.setDesktopMode(!isDesktop); dismiss()
                },
                MenuItem(R.drawable.ic_tools, getString(R.string.menu_tools_label)) {
                    startActivity(Intent(requireContext(), ProfileManagerActivity::class.java)); dismiss()
                },
                MenuItem(R.drawable.ic_settings, getString(R.string.settings)) {
                    startActivity(Intent(requireContext(), SettingsActivity::class.java)); dismiss()
                }
            ),
            listOf(
                MenuItem(R.drawable.ic_search_page, getString(R.string.menu_find_in_page)) {
                    toast(getString(R.string.menu_find_in_page)); dismiss()
                },
                MenuItem(R.drawable.ic_save, getString(R.string.menu_save), false) {},
                MenuItem(R.drawable.ic_layers, getString(R.string.menu_saved_pages), false) {},
                MenuItem(R.drawable.ic_translate, getString(R.string.menu_translate), false) {},
                MenuItem(R.drawable.ic_code, getString(R.string.menu_view_source)) {
                    val url = viewModel.currentUrl.value
                    if (url.startsWith("http")) viewModel.navigateTo("view-source:$url")
                    dismiss()
                },
                MenuItem(R.drawable.ic_fullscreen, getString(R.string.menu_fullscreen)) {
                    val act = activity ?: return@MenuItem
                    act.requestedOrientation =
                        if (act.requestedOrientation == ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE)
                            ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
                        else
                            ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
                    dismiss()
                },
                MenuItem(
                    R.drawable.ic_image_off,
                    if (isImages) getString(R.string.menu_block_images) else getString(R.string.menu_allow_images)
                ) { viewModel.setImagesEnabled(!isImages); dismiss() },
                MenuItem(R.drawable.ic_sniff, getString(R.string.menu_resource_sniffer), false) {},
                MenuItem(R.drawable.ic_user_agent, getString(R.string.menu_user_agent)) {
                    viewModel.setDesktopMode(!isDesktop); dismiss()
                },
                MenuItem(R.drawable.ic_network_log, getString(R.string.menu_network_log), false) {}
            ),
            listOf(
                MenuItem(R.drawable.ic_qr_code, getString(R.string.menu_qr_scan), false) {},
                MenuItem(R.drawable.ic_add_home, getString(R.string.menu_add_to_home), false) {},
                MenuItem(R.drawable.ic_volume_up, getString(R.string.menu_read_aloud), false) {},
                MenuItem(R.drawable.ic_ai, getString(R.string.menu_ai), false) {},
                MenuItem(R.drawable.ic_screen_rotation, getString(R.string.menu_orientation)) {
                    activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
                    dismiss()
                },
                MenuItem(
                    R.drawable.ic_ad_block,
                    if (isAdBlock) getString(R.string.menu_ad_block_on) else getString(R.string.menu_ad_block_off)
                ) { viewModel.setAdBlockerEnabled(!isAdBlock); dismiss() },
                MenuItem(R.drawable.ic_mark_ads, getString(R.string.menu_mark_ads), false) {},
                MenuItem(R.drawable.ic_text_size, getString(R.string.menu_text_size), false) {},
                MenuItem(R.drawable.ic_delete, getString(R.string.menu_clear_data)) {
                    viewModel.clearBrowsingData(); dismiss()
                },
                MenuItem(R.drawable.ic_customize, getString(R.string.menu_customize), false) {}
            )
        )
    }

    private fun setupPager() {
        val pages = buildPages()
        binding.menuPager.adapter = object : RecyclerView.Adapter<RecyclerView.ViewHolder>() {
            override fun getItemCount() = pages.size
            override fun getItemViewType(position: Int) = position
            override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
                val page = buildPageView(pages[viewType])
                page.layoutParams = ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT
                )
                return object : RecyclerView.ViewHolder(page) {}
            }
            override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {}
        }
    }

    private fun buildPageView(items: List<MenuItem>): View {
        val ctx = requireContext()
        val root = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(4.dp, 8.dp, 4.dp, 4.dp)
        }
        val row1 = buildRow(items.take(5))
        val row2 = buildRow(items.drop(5).take(5))
        root.addView(row1, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f))
        root.addView(row2, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f))
        return root
    }

    private fun buildRow(items: List<MenuItem>): LinearLayout {
        val ctx = requireContext()
        return LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL
            for (item in items) {
                val v = buildItem(item)
                addView(v, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, 1f))
            }
        }
    }

    private fun buildItem(item: MenuItem): LinearLayout {
        val ctx = requireContext()
        val enabledColor = ctx.getColor(R.color.color_icon)
        val disabledColor = Color.parseColor("#BDBDBD")
        val enabledText = ctx.getColor(R.color.color_text_primary)
        val disabledText = Color.parseColor("#BDBDBD")

        return LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            isEnabled = item.enabled
            isClickable = item.enabled
            isFocusable = item.enabled

            if (item.enabled) {
                val tv = TypedValue()
                ctx.theme.resolveAttribute(android.R.attr.selectableItemBackground, tv, true)
                setBackgroundResource(tv.resourceId)
                setOnClickListener { item.action() }
            }

            val icon = ImageView(ctx).apply {
                setImageResource(item.iconRes)
                setColorFilter(if (item.enabled) enabledColor else disabledColor)
            }
            addView(icon, LinearLayout.LayoutParams(28.dp, 28.dp).apply {
                gravity = Gravity.CENTER_HORIZONTAL
                bottomMargin = 5.dp
            })

            val label = TextView(ctx).apply {
                text = item.label
                textSize = 10f
                gravity = Gravity.CENTER
                maxLines = 2
                setTextColor(if (item.enabled) enabledText else disabledText)
            }
            addView(label, LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { gravity = Gravity.CENTER_HORIZONTAL })
        }
    }

    private fun setupDots() {
        val ctx = requireContext()
        val dotCount = 3
        val dots = Array(dotCount) { i ->
            View(ctx).apply {
                val size = 7.dp
                val shape = GradientDrawable().apply {
                    shape = GradientDrawable.OVAL
                    setColor(if (i == 0) Color.parseColor("#888888") else Color.parseColor("#CCCCCC"))
                }
                background = shape
                val lp = LinearLayout.LayoutParams(size, size).apply {
                    marginStart = 4.dp
                    marginEnd = 4.dp
                }
                binding.menuDots.addView(this, lp)
            }
        }

        binding.menuPager.registerOnPageChangeCallback(object : ViewPager2.OnPageChangeCallback() {
            override fun onPageSelected(position: Int) {
                dots.forEachIndexed { index, dot ->
                    (dot.background as GradientDrawable).setColor(
                        if (index == position) Color.parseColor("#888888")
                        else Color.parseColor("#CCCCCC")
                    )
                }
            }
        })
    }

    private fun setupBottomButtons() {
        binding.btnMenuExit.setOnClickListener {
            dismiss()
            activity?.finishAffinity()
        }
        binding.btnMenuDismiss.setOnClickListener {
            dismiss()
        }
    }

    private val Int.dp: Int
        get() = (this * resources.displayMetrics.density + 0.5f).toInt()

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
