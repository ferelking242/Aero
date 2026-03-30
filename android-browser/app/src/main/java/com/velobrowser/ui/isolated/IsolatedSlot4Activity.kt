package com.velobrowser.ui.isolated

import com.velobrowser.service.TabKeepAliveService
import com.velobrowser.service.TabKeepAliveService4
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class IsolatedSlot4Activity : IsolatedBrowserActivity() {
    override val slot: Int = 4
    override val keepAliveServiceClass: Class<out TabKeepAliveService> = TabKeepAliveService4::class.java
}
