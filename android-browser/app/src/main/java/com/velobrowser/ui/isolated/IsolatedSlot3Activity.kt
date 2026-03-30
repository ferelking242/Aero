package com.velobrowser.ui.isolated

import com.velobrowser.service.TabKeepAliveService
import com.velobrowser.service.TabKeepAliveService3
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class IsolatedSlot3Activity : IsolatedBrowserActivity() {
    override val slot: Int = 3
    override val keepAliveServiceClass: Class<out TabKeepAliveService> = TabKeepAliveService3::class.java
}
