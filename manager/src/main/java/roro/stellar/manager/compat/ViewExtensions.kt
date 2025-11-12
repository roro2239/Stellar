package roro.stellar.manager.compat

import android.app.Activity
import android.content.Context
import android.content.res.Resources
import android.util.TypedValue
import androidx.annotation.AttrRes
import androidx.annotation.ColorInt

/**
 * View扩展函数
 * View Extension Functions
 * 
 * 替代Rikka core功能
 * Replaces Rikka core functions
 */

/**
 * 解析颜色属性
 * Resolve color attribute
 * 
 * @param attr 颜色属性
 * @return 颜色值
 */
@ColorInt
fun Context.resolveColor(@AttrRes attr: Int): Int {
    val typedValue = TypedValue()
    theme.resolveAttribute(attr, typedValue, true)
    return typedValue.data
}

/**
 * 解析颜色属性（Theme扩展）
 * Resolve color attribute (Theme extension)
 * 
 * @param attr 颜色属性
 * @return 颜色值
 */
@ColorInt
fun Resources.Theme.resolveColor(@AttrRes attr: Int): Int {
    val typedValue = TypedValue()
    resolveAttribute(attr, typedValue, true)
    return typedValue.data
}

/**
 * 将Context转换为Activity
 * Convert Context to Activity
 */
fun Context.asActivity(): Activity? {
    var context = this
    while (context is android.content.ContextWrapper) {
        if (context is Activity) {
            return context
        }
        context = context.baseContext
    }
    return null
}

