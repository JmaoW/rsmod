package org.rsmod.content.areas.misc.isleofsouls.chat

import java.text.NumberFormat
import java.util.Locale

/**
 * Colours and small text helpers for league chat messages. The colours are tuned for the parchment
 * chatbox, so [BLUE] is the darker of the two league blues (the bright one is for dark backgrounds
 * such as the task pop-up).
 */
object LeagueChat {
    /** The league's secondary colour: names, numbers and task names. */
    const val BLUE = "147194"

    /** Confirmations. */
    const val GREEN = "1a6b1a"

    /** Refusals and problems. */
    const val RED = "8b1a1a"

    /** Offline players and other quiet text. */
    const val GREY = "7a7467"

    private val command = Regex("::\\w+")
    private val wordBreaks = Regex("[ _-]+")

    fun blue(text: Any): String = colour(BLUE, text)

    fun green(text: Any): String = colour(GREEN, text)

    fun red(text: Any): String = colour(RED, text)

    fun grey(text: Any): String = colour(GREY, text)

    /** A whole number in blue, with thousands separators: `1,240`. */
    fun number(value: Int): String = blue(format(value))

    /** `340 League Points`, all in blue, with the singular for exactly one. */
    fun points(value: Int): String = blue("${format(value)} League ${plural(value, "Point")}")

    /** A blue count followed by its noun: `12 tasks`, `1 task`. */
    fun counted(value: Int, noun: String): String = "${number(value)} ${plural(value, noun)}"

    /** `Task Progress: Mine 30 Iron Ore: 15/30`, with only the label in blue. */
    fun taskProgress(taskName: String, progress: Int, goal: Int): String =
        "${blue("Task Progress:")} $taskName: ${format(progress)}/${format(goal)}"

    /** Highlights every `::command` in [text]. Only use on text that has no colour tags yet. */
    fun commands(text: String): String = command.replace(text) { blue(it.value) }

    /**
     * Turns a login name into a display name: `some_name` becomes `Some Name`. Used when a player
     * has no display name of their own.
     */
    fun titleCase(name: String): String =
        name.trim().split(wordBreaks).filter(String::isNotEmpty).joinToString(" ") { word ->
            word.lowercase().replaceFirstChar(Char::uppercaseChar)
        }

    private fun plural(value: Int, noun: String): String = if (value == 1) noun else "${noun}s"

    private fun format(value: Int): String =
        NumberFormat.getIntegerInstance(Locale.US).format(value)

    private fun colour(hex: String, text: Any): String = "<col=$hex>$text</col>"
}
