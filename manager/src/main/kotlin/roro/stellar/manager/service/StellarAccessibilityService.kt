package roro.stellar.manager.service

import android.accessibilityservice.AccessibilityService
import android.view.accessibility.AccessibilityEvent

class StellarAccessibilityService : AccessibilityService() {

    override fun onAccessibilityEvent(event: AccessibilityEvent) {
        // 不处理任何事件，仅用于开机自启
    }

    override fun onInterrupt() {
        // 不需要处理中断
    }
}
