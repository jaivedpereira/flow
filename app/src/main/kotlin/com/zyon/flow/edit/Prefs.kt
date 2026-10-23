/**
 * Flow — first-run flag.
 *
 * Kept in SharedPreferences so onboarding survives a restart but does not come
 * back on every launch. Cleared by "clear data", which is the right behaviour.
 */

package com.zyon.flow.edit

import android.content.Context

object Prefs {
    private const val NAME = "flow_prefs"
    private const val KEY_ONBOARDED = "onboarded"
    private const val KEY_LAUNCHES = "launches"
    private const val KEY_SHARE_HINTED = "share_hinted"

    private fun sp(context: Context) =
        context.getSharedPreferences(NAME, Context.MODE_PRIVATE)

    fun hasOnboarded(context: Context): Boolean =
        sp(context).getBoolean(KEY_ONBOARDED, false)

    fun setOnboarded(context: Context) {
        sp(context).edit().putBoolean(KEY_ONBOARDED, true).apply()
    }

    fun launches(context: Context): Int = sp(context).getInt(KEY_LAUNCHES, 0)

    fun bumpLaunch(context: Context) {
        sp(context).edit().putInt(KEY_LAUNCHES, launches(context) + 1).apply()
    }

    fun hasShareHinted(context: Context): Boolean =
        sp(context).getBoolean(KEY_SHARE_HINTED, false)

    fun setShareHinted(context: Context) {
        sp(context).edit().putBoolean(KEY_SHARE_HINTED, true).apply()
    }
}
