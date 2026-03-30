package com.velobrowser.ui.isolated

import com.velobrowser.service.TabKeepAliveService
import com.velobrowser.service.TabKeepAliveService2
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class IsolatedSlot2Activity : IsolatedBrowserActivity() {
    override val slot: Int = 2
    override val keepAliveServiceClass: Class<out TabKeepAliveService> = TabKeepAliveService2::class.java
}
