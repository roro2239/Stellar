package roro.stellar.manager.compat

import android.app.Activity
import android.content.Context
import android.content.res.Resources
import android.util.TypedValue
import androidx.annotation.AttrRes
import androidx.annotation.ColorInt

@ColorInt
fun Context.resolveColor(@AttrRes attr: Int): Int {
    val typedValue = TypedValue()
    theme.resolveAttribute(attr, typedValue, true)
    return typedValue.data
}

@ColorInt
fun Resources.Theme.resolveColor(@AttrRes attr: Int): Int {
    val typedValue = TypedValue()
    resolveAttribute(attr, typedValue, true)
    return typedValue.data
}

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

