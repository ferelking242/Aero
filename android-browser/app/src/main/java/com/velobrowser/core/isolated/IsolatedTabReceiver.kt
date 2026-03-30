package com.velobrowser.core.isolated

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import com.velobrowser.core.tabs.TabManager

class IsolatedTabReceiver(
    private val tabManager: TabManager,
    private val isolatedTabManager: IsolatedTabManager,
    private val onSlotUpdated: (Int, String, String) -> Unit,
    private val onSlotClosed: (Int) -> Unit
) : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val slot = intent.getIntExtra(IsolatedTabManager.EXTRA_SLOT, -1)
        if (slot < 1 || slot > IsolatedTabManager.MAX_SLOTS) return

        when (intent.action) {
            IsolatedTabManager.ACTION_URL_CHANGED -> {
                val url = intent.getStringExtra(IsolatedTabManager.EXTRA_URL) ?: return
                val title = intent.getStringExtra(IsolatedTabManager.EXTRA_TITLE) ?: url
                isolatedTabManager.updateSlot(slot, url, title)
                tabManager.updateIsolatedTabBySlot(slot) { it.copy(url = url, title = title) }
                onSlotUpdated(slot, url, title)
            }
            IsolatedTabManager.ACTION_TAB_CLOSED -> {
                isolatedTabManager.closeSlot(slot)
                tabManager.closeIsolatedTab(slot)
                onSlotClosed(slot)
            }
        }
    }

    companion object {
        fun createIntentFilter(): IntentFilter = IntentFilter().apply {
            addAction(IsolatedTabManager.ACTION_URL_CHANGED)
            addAction(IsolatedTabManager.ACTION_TAB_CLOSED)
        }

        fun register(
            context: Context,
            receiver: IsolatedTabReceiver
        ) {
            val filter = createIntentFilter()
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                context.registerReceiver(receiver, filter, Context.RECEIVER_NOT_EXPORTED)
            } else {
                context.registerReceiver(receiver, filter)
            }
        }
    }
}
