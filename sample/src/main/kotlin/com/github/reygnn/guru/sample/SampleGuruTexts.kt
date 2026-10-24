package com.github.reygnn.guru.sample

import android.content.Context
import com.github.reygnn.guru.GuruTexts

/** Guru's words from this app's resources, so TalkBack on a German device reads German. */
internal class SampleGuruTexts(private val context: Context) : GuruTexts {
    override val hint: String get() = context.getString(R.string.guru_hint)
    override val actionContinue: String get() = context.getString(R.string.guru_action_continue)
    override val actionSave: String get() = context.getString(R.string.guru_action_save)

    override fun more(count: Int): String = context.resources.getQuantityString(R.plurals.guru_more, count, count)

    override fun saved(saved: Int, total: Int): String = when {
        saved == total -> context.resources.getQuantityString(R.plurals.guru_saved_all, total, total)
        saved == 0 -> context.getString(R.string.guru_saved_none)
        else -> context.resources.getQuantityString(R.plurals.guru_saved_some, total, saved, total)
    }
}
