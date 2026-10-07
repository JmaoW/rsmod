package org.rsmod.api.drops

/** Parses the text forms used in drop table data files. */
public object DropChance {
    private val fraction =
        Regex("^~?\\s*([0-9][0-9,]*(?:\\.[0-9]+)?)\\s*/\\s*([0-9][0-9,]*(?:\\.[0-9]+)?)$")
    private val percent = Regex("^~?\\s*([0-9]+(?:\\.[0-9]+)?)\\s*%$")

    /** Returns `true` if [text] means "always" (a drop on every kill). */
    public fun isAlways(text: String): Boolean {
        val value = text.trim()
        return value.equals("always", ignoreCase = true) || value == "1/1" || value == "100%"
    }

    /** Parses `3/128`, `~1/5,000`, `0.25` or `12.5%` into a probability. */
    public fun parse(text: String): Double {
        val value = text.trim()
        fraction.matchEntire(value)?.let {
            val numerator = it.groupValues[1].replace(",", "").toDouble()
            val denominator = it.groupValues[2].replace(",", "").toDouble()
            require(denominator > 0.0) { "Drop chance has a zero denominator: `$text`" }
            return checked(numerator / denominator, text)
        }
        percent.matchEntire(value)?.let {
            return checked(it.groupValues[1].toDouble() / 100.0, text)
        }
        val plain = value.toDoubleOrNull()
        require(plain != null) { "Unrecognised drop chance: `$text`" }
        return checked(plain, text)
    }

    private fun checked(chance: Double, text: String): Double {
        require(chance > 0.0 && chance <= 1.0) { "Drop chance is not between 0 and 1: `$text`" }
        return chance
    }

    /** Parses `5` or `1-5` (a hyphen or an en dash) into an inclusive range. */
    public fun parseQuantity(text: String): IntRange {
        val value = text.trim().replace(",", "")
        val numbers = value.split('-', '–').map { it.trim().toIntOrNull() }
        require(numbers.size in 1..2 && numbers.all { it != null }) {
            "Unrecognised drop quantity: `$text`"
        }
        val min = numbers.first()!!
        val max = numbers.last()!!
        require(min >= 1 && max >= min) { "Invalid drop quantity: `$text`" }
        return min..max
    }
}
