package org.rsmod.content.areas.misc.isleofsouls.configs

import org.rsmod.content.areas.misc.isleofsouls.tasks.LeagueTier

/**
 * The league tasks the isle adds for Magic, which the vanilla list has no task for. They are
 * numbered after the Mining Guild task (see [IsleOfSoulsTaskStructs]): task id `FIRST_TASK + n` has
 * the struct `isle_task_magic_<name>`, and the structs' ids in `.data/symbols/struct.sym` are
 * `FIRST_STRUCT + n`, in the order of [all].
 *
 * `LeagueMagicTasks` is what completes them.
 */
internal object IsleOfSoulsMagicTasks {
    const val FIRST_TASK = 1846
    const val FIRST_STRUCT = 6246

    /**
     * One task. [combat] puts it in the client's combat category rather than the general one.
     * [name] is also the name in `LeagueTaskList`.
     */
    class Def(
        val key: String,
        val tier: LeagueTier,
        val name: String,
        val description: String,
        val combat: Boolean = false,
    )

    val all: List<Def> =
        listOf(
            Def(
                "wind_strike",
                LeagueTier.Easy,
                "Cast Wind Strike",
                "Cast the Wind Strike spell",
                true,
            ),
            Def(
                "low_alchemy",
                LeagueTier.Easy,
                "Cast Low Level Alchemy",
                "Cast the Low Level Alchemy spell",
            ),
            Def(
                "bones_to_bananas",
                LeagueTier.Easy,
                "Cast Bones to Bananas",
                "Cast the Bones to Bananas spell",
            ),
            Def(
                "varrock_teleport",
                LeagueTier.Easy,
                "Cast Varrock Teleport",
                "Cast the Varrock Teleport spell",
            ),
            Def(
                "enchant_sapphire",
                LeagueTier.Easy,
                "Cast Lvl-1 Enchant",
                "Cast the Lvl-1 Enchant spell on a piece of sapphire jewellery",
            ),
            Def(
                "telekinetic_grab",
                LeagueTier.Medium,
                "Cast Telekinetic Grab",
                "Cast the Telekinetic Grab spell",
            ),
            Def(
                "superheat",
                LeagueTier.Medium,
                "Cast Superheat Item",
                "Cast the Superheat Item spell",
            ),
            Def(
                "smoke_rush",
                LeagueTier.Medium,
                "Cast Smoke Rush",
                "Cast the Smoke Rush spell",
                true,
            ),
            Def(
                "thrall",
                LeagueTier.Medium,
                "Summon a Thrall",
                "Cast any of the Resurrect spells to summon a thrall",
            ),
            Def(
                "mark_of_darkness",
                LeagueTier.Medium,
                "Cast Mark of Darkness",
                "Cast the Mark of Darkness spell",
            ),
            Def(
                "kills_25",
                LeagueTier.Medium,
                "Kill 25 Monsters With Magic",
                "Kill 25 monsters with combat spells",
                true,
            ),
            Def(
                "high_alchemy_100",
                LeagueTier.Hard,
                "Cast High Level Alchemy 100 Times",
                "Cast the High Level Alchemy spell 100 times",
            ),
            Def("charge", LeagueTier.Hard, "Cast Charge", "Cast the Charge spell"),
            Def("magic_imbue", LeagueTier.Hard, "Cast Magic Imbue", "Cast the Magic Imbue spell"),
            Def(
                "tele_group",
                LeagueTier.Hard,
                "Cast a Tele Group Spell",
                "Cast any of the Tele Group spells",
            ),
            Def(
                "death_charge",
                LeagueTier.Hard,
                "Cast Death Charge",
                "Cast the Death Charge spell",
            ),
            Def("vile_vigour", LeagueTier.Hard, "Cast Vile Vigour", "Cast the Vile Vigour spell"),
            Def(
                "demonic_offering",
                LeagueTier.Hard,
                "Cast Demonic Offering",
                "Cast the Demonic Offering spell",
            ),
            Def(
                "blood_barrage",
                LeagueTier.Elite,
                "Cast Blood Barrage",
                "Cast the Blood Barrage spell",
                true,
            ),
            Def("vengeance", LeagueTier.Elite, "Cast Vengeance", "Cast the Vengeance spell"),
            Def("plank_make", LeagueTier.Elite, "Cast Plank Make", "Cast the Plank Make spell"),
            Def(
                "sinister_offering",
                LeagueTier.Elite,
                "Cast Sinister Offering",
                "Cast the Sinister Offering spell",
            ),
            Def(
                "kills_250",
                LeagueTier.Elite,
                "Kill 250 Monsters With Magic",
                "Kill 250 monsters with combat spells",
                true,
            ),
            Def(
                "casts_1000",
                LeagueTier.Elite,
                "Cast 1,000 Spells",
                "Cast 1,000 spells of any kind",
            ),
            Def(
                "xp_25m",
                LeagueTier.Elite,
                "Obtain 25 Million Magic XP",
                "Obtain 25 million experience in your Magic skill",
            ),
            Def(
                "xp_35m",
                LeagueTier.Elite,
                "Obtain 35 Million Magic XP",
                "Obtain 35 million experience in your Magic skill",
            ),
        )

    fun taskId(key: String): Int = FIRST_TASK + indexOf(key)

    fun structName(def: Def): String = "isle_task_magic_${def.key}"

    fun structId(key: String): Int = FIRST_STRUCT + indexOf(key)

    private fun indexOf(key: String): Int {
        val index = all.indexOfFirst { it.key == key }
        require(index != -1) { "No magic task with key '$key'." }
        return index
    }
}
