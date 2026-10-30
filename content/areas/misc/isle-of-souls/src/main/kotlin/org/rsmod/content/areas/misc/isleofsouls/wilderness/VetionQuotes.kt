package org.rsmod.content.areas.misc.isleofsouls.wilderness

/**
 * What Vet'ion and Calvar'ion shout (OSRS Wiki, "Transcript:Vet'ion", taken from the game). Each
 * kind of attack has its own lines, which is the cue for which attack is coming; the enraged form
 * shouts the same lines in capitals, plus a few of its own. The wiki has no transcript for
 * Calvar'ion, who says "quotes whenever he attacks" the same way, so he shares these.
 */
object VetionQuotes {
    enum class Kind {
        /** The lightning, his ordinary attack. */
        Lightning,
        BashShield,
        HoundsCalled,
        HoundsDown,
        Enrage,
        Death,
    }

    private val lightning =
        listOf(
            "I will smite you!",
            "I've got you now!",
            "Stand still, rat!",
            "You can't escape!",
            "For the lord!",
            "You call that a weapon?!",
            "You are weak! You are feeble!",
            "Hold still so I can smite you!",
            "Dodge this!",
            "You are powerless to me!",
            "I will cut you down!",
            "I will end you!",
            "You're mine now!",
            "Time to die, mortal!",
            "Raaargh!",
            "Die, rodent!",
            "Die!",
            "Perish, fool!",
        )

    private val bash =
        listOf(
            "Now I've got you!",
            "Hands off, wretch!",
            "Grrrah!",
            "Take this!",
            "You're not blocking this one!",
            "Defend yourself!",
            "Back off, mutt!",
        )

    private val houndsCalled =
        listOf(
            "Time to feast, hounds!",
            "Go forth, my hounds, and destroy them!",
            "Hounds! Dispose of these tresspassers!",
            "I've had enough of this! Hounds!",
            "Hounds! Get rid of these interlopers!",
        )

    private val houndsDown =
        listOf("I'll kill you for killing my pets!", "Must I do everything around here?!")

    private val enrage = listOf("Now... DO IT AGAIN!!!")

    private val death =
        listOf(
            "Urgh... not... again...",
            "Urk! I... failed...",
            "I'll... be... back...",
            "This isn't... the last... of me...",
            "My lord... I'm... sorry...",
            "I'll get you... next... time...",
        )

    // The enraged form's own lines, besides the normal ones in capitals.
    private val enragedLightning = listOf("FILTHY WHELPS!")
    private val enragedHoundsDown =
        listOf(
            "MY HOUNDS! I'LL MAKE YOU PAY FOR THAT!",
            "GAH! HOUNDS! GET THEM!",
            "FINE! I'LL DEAL WITH YOU MYSELF!",
        )

    /** The lines for [kind] in the normal form, or in capitals when [enraged]. */
    fun lines(kind: Kind, enraged: Boolean): List<String> {
        val normal =
            when (kind) {
                Kind.Lightning -> lightning
                Kind.BashShield -> bash
                Kind.HoundsCalled -> houndsCalled
                Kind.HoundsDown -> houndsDown
                Kind.Enrage -> enrage
                Kind.Death -> death
            }
        // The line said as he rises is already in its enraged form.
        if (!enraged || kind == Kind.Enrage) {
            return normal
        }
        val extra =
            when (kind) {
                Kind.Lightning -> enragedLightning
                Kind.HoundsDown -> enragedHoundsDown
                else -> emptyList()
            }
        return normal.map { it.uppercase() } + extra
    }
}
