package roro.stellar.manager.compat

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context

/**
 * 剪贴板工具类
 * Clipboard Utility Class
 * 
 * 替代Rikka ClipboardUtils
 * Replaces Rikka ClipboardUtils
 */
object ClipboardUtils {
    
    /**
     * 将文本放入剪贴板
     * Put text into clipboard
     * 
     * @param context Context
     * @param text 要复制的文本
     * @return 是否成功
     */
    fun put(context: Context, text: String): Boolean {
        return try {
            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
            val clip = ClipData.newPlainText("text", text)
            clipboard?.setPrimaryClip(clip)
            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }
}

