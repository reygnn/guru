package com.github.reygnn.guru

/**
 * Every word Guru shows or speaks besides the Amiga quote, which stays as it is. The host passes
 * its own from its resources, so TalkBack on a German device reads German; [English] is the
 * default. Read each time the alert is built or a toast shown, never kept: a language change in
 * between is picked up.
 *
 * Every member has its English text as the default, here and nowhere else, so a member added
 * later breaks no host — and [English] overrides nothing, so no default can call itself.
 */
internal interface GuruTexts {

    /** Under the alert box, and the end of what a screen reader says for it. */
    val hint: String get() = "double-tap: continue · hold: save · back: later"

    /** The screen reader's name for continuing — a double-tap (the reports go). */
    val actionContinue: String get() = "continue, discard the report"

    /** The screen reader's name for a hold (save to Download/Guru). */
    val actionSave: String get() = "save the report to Download/Guru"

    /** Under the Guru number when more reports wait behind the one shown: [count] of them. */
    fun more(count: Int): String = "+$count more"

    /** The toast after saving: [saved] of [total] in Download/Guru; the rest stays for the next start. */
    fun saved(saved: Int, total: Int): String = when {
        saved == total && total == 1 -> "Guru: report saved to Download/Guru"
        saved == total -> "Guru: $total reports saved to Download/Guru"
        saved == 0 -> "Guru: not saved, kept for the next start"
        else -> "Guru: $saved of $total saved to Download/Guru, the rest kept"
    }

    /** The defaults, as they are. */
    object English : GuruTexts
}
