package roro.stellar.manager.compat

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context

object ClipboardUtils {
    
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

