package org.rsmod.content.areas.misc.isleofsouls.configs

import org.rsmod.api.type.builders.struct.StructBuilder
import org.rsmod.game.type.util.ParamMap

/**
 * League task structs the vanilla cache does not have, for tasks the isle adds itself. A task
 * struct is read by the client's Leagues scripts from these params (decoded from the Raging Echoes
 * task structs, for example "Obtain 20 Golden Nuggets"):
 * - `873`: the task id (the completion bit index, `LeagueTask.id`)
 * - `874`, `875`: the name and the description
 * - `1850`, `1851`, `1852`, `2044`: the difficulty (1 easy, 2 medium, 3 hard, 4 elite)
 * - `1016`: the category (`1` for every task seen), `1017`: the area (`3` is Asgarnia) and `1018`:
 *   the skill (`13` is Mining)
 *
 * Each struct name needs a line in `.data/symbols/struct.sym` giving it the id used in the task
 * enum, and the cache has to be repacked.
 *
 * _Not yet verified against a real client._
 */
internal object IsleOfSoulsTaskStructs : StructBuilder() {
    /**
     * The id `isle_task_enter_mining_guild` has in `struct.sym`. The vanilla cache ends at 6244.
     */
    const val ENTER_MINING_GUILD_STRUCT = 6245

    /** Task ids 0..1983 have a completion bit, and the vanilla ones end at 1844. */
    const val ENTER_MINING_GUILD_TASK = 1845

    private const val DIFFICULTY_MEDIUM = 2
    private const val AREA_ASGARNIA = 3
    private const val SKILL_MINING = 13
    private const val CATEGORY_GENERAL = 1

    init {
        build("isle_task_enter_mining_guild") {
            paramMap =
                task(
                    id = ENTER_MINING_GUILD_TASK,
                    name = "Enter the Mining Guild",
                    description = "Enter the Mining Guild beneath Falador",
                    difficulty = DIFFICULTY_MEDIUM,
                    area = AREA_ASGARNIA,
                    skill = SKILL_MINING,
                )
        }
    }

    private fun task(
        id: Int,
        name: String,
        description: String,
        difficulty: Int,
        area: Int,
        skill: Int,
    ): ParamMap {
        val params =
            mapOf<Int, Any>(
                873 to id,
                874 to name,
                875 to description,
                1850 to difficulty,
                1851 to difficulty,
                1852 to difficulty,
                2044 to difficulty,
                1016 to CATEGORY_GENERAL,
                1017 to area,
                1018 to skill,
            )
        return ParamMap(params, params)
    }
}
