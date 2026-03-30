package com.velobrowser.ui.isolated

import com.velobrowser.service.TabKeepAliveService
import com.velobrowser.service.TabKeepAliveService1
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class IsolatedSlot1Activity : IsolatedBrowserActivity() {
    override val slot: Int = 1
    override val keepAliveServiceClass: Class<out TabKeepAliveService> = TabKeepAliveService1::class.java
}
