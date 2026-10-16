package org.rsmod.content.areas.misc.isleofsouls.dungeons

import org.rsmod.api.config.refs.params
import org.rsmod.api.type.builders.hunt.HuntModeBuilder
import org.rsmod.api.type.editors.npc.NpcEditor
import org.rsmod.api.type.refs.hunt.HuntModeReferences
import org.rsmod.api.type.refs.npc.NpcReferences
import org.rsmod.api.type.script.dsl.HuntModePluginBuilder
import org.rsmod.game.entity.npc.NpcMode
import org.rsmod.game.movement.BlockWalk
import org.rsmod.game.type.hunt.HuntCheckNotTooStrong
import org.rsmod.game.type.hunt.HuntType
import org.rsmod.game.type.hunt.HuntVis
import org.rsmod.game.type.npc.NpcType

/**
 * Who in the God Wars Dungeon belongs to which god, by cache name, for aggression (the Wiki's
 * "Followers in each area"). It differs from [God.ofFollower], which is the Wiki's kill count:
 * there icefiends count for Saradomin and goraks for Bandos, but here both are Zamorakian, as in
 * the Wiki's protection table. Leaders (the four generals and their twelve bodyguards) belong to no
 * list: they are always aggressive. `godwars_dying_paladin` and `godwars_paladin_multi` are a quest
 * npc and are left alone.
 */
object GodWarsNames {
    val armadyl =
        listOf(
            "godwars_spiritual_armadyl_warrior",
            "godwars_spiritual_armadyl_ranger",
            "godwars_spiritual_armadyl_mage",
            "godwars_armadyl_male_armor01_blue",
            "godwars_armadyl_male_armor01_green",
            "godwars_armadyl_male_armor01_red",
            "godwars_armadyl_male_armor02_blue",
            "godwars_armadyl_male_armor02_green",
            "godwars_armadyl_male_armor02_red",
            "godwars_armadyl_male_armor03_green",
            "godwars_armadyl_male_armor03_red",
            "godwars_armadyl_female_armor01_blue",
            "godwars_armadyl_female_armor01_green",
            "godwars_armadyl_female_armor01_red",
            "godwars_armadyl_female_armor02_blue",
            "godwars_armadyl_female_armor02_green",
            "godwars_armadyl_female_armor02_red",
            "godwars_armadyl_female_armor03_blue",
        )

    val bandos =
        listOf(
            "godwars_spiritual_bandos_warrior",
            "godwars_spiritual_bandos_ranger",
            "godwars_spiritual_bandos_mage",
            "godwars_goblin1",
            "godwars_goblin2",
            "godwars_goblin3",
            "godwars_goblin4",
            "godwars_goblin5",
            "godwars_ancient_hobgoblin",
            "godwars_ancient_ogre",
            "godwars_ancient_jogre",
            "godwars_ancient_cyclops",
            "godwars_ancient_cyclops2",
            "godwars_ancient_ork1",
            "godwars_ancient_ork2",
            "godwars_ancient_ork3",
            "godwars_ancient_ork4",
        )

    val saradomin =
        listOf(
            "godwars_spiritual_saradomin_warrior",
            "godwars_spiritual_saradomin_ranger",
            "godwars_spiritual_saradomin_mage",
            "godwars_saradomin_knight_1",
            "godwars_saradomin_knight_2",
            "godwars_ancient_saradomin_wizard",
        )

    val zamorak =
        listOf(
            "godwars_spiritual_zamorak_warrior",
            "godwars_spiritual_zamorak_ranger",
            "godwars_spiritual_zamorak_mage",
            "godwars_ancient_imp",
            "godwars_icefiend_1",
            "godwars_pyrefiend_1",
            "godwars_ancient_vampire",
            "godwars_bloodveld",
            "godwars_ancient_werewolf1",
            "godwars_ancient_werewolf2",
            "godwars_ancient_hellhound",
            "godwars_gorak",
        )

    /**
     * The generals, and the bodyguards that fight beside them. K'ril's three are the demons that
     * only stand in his chamber (one spawn each).
     */
    val leaders =
        listOf(
            "godwars_armadyl_avatar",
            "godwars_armadyl_bodyguard_skree",
            "godwars_armadyl_bodyguard_geerin",
            "godwars_armadyl_bodyguard_kilisa",
            "godwars_bandos_avatar",
            "godwars_sergeant_goblin1",
            "godwars_sergeant_goblin2",
            "godwars_sergeant_goblin3",
            "godwars_saradomin_avatar",
            "godwars_saradomin_unicorn",
            "godwars_saradomin_lion",
            "godwars_saradomin_centaur",
            "godwars_zamorak_avatar",
            "godwars_ancient_greater_demon",
            "godwars_ancient_lesser_demon",
            "godwars_ancient_black_demon",
        )

    /**
     * The followers that attack from a distance (ranged or magic; see [GodWarsFollowerScript]): all
     * of Armadyl's aviansies, its spiritual warrior, ranger and mage, every other god's spiritual
     * ranger and mage, and the Saradomin wizard. They hunt with [GodWarsHunt.of] `ranged = true`.
     */
    val ranged: List<String> =
        armadyl +
            listOf(
                "godwars_spiritual_bandos_ranger",
                "godwars_spiritual_bandos_mage",
                "godwars_spiritual_saradomin_ranger",
                "godwars_spiritual_saradomin_mage",
                "godwars_ancient_saradomin_wizard",
                "godwars_spiritual_zamorak_ranger",
                "godwars_spiritual_zamorak_mage",
            )

    /** How far a ranged or magic follower attacks from; a guess (the Wiki gives none). */
    const val ATTACK_RANGE = 7

    /** Followers that hit with "magical melee" from next to the player (the melee hunt mode). */
    val magicMelee = listOf("godwars_bloodveld")

    /** How often (ticks) a leader checks its chamber is still occupied (Armadyl script). */
    const val RESET_TIMER = 10

    // Kree'arra and her bodyguards (Armadyl's chamber).
    const val KREE = "godwars_armadyl_avatar"
    const val SKREE = "godwars_armadyl_bodyguard_skree"
    const val GEERIN = "godwars_armadyl_bodyguard_geerin"
    const val KILISA = "godwars_armadyl_bodyguard_kilisa"
    val armadylLeaders = listOf(KREE, SKREE, GEERIN, KILISA)

    // General Graardor and his sergeants (Bandos' chamber).
    const val GRAARDOR = "godwars_bandos_avatar"
    const val STRONGSTACK = "godwars_sergeant_goblin1"
    const val STEELWILL = "godwars_sergeant_goblin2"
    const val GRIMSPIKE = "godwars_sergeant_goblin3"
    val bandosLeaders = listOf(GRAARDOR, STRONGSTACK, STEELWILL, GRIMSPIKE)

    // Commander Zilyana and her bodyguards (Saradomin's chamber): Starlight the unicorn, Growler
    // the
    // lion and Bree the centaur.
    const val ZILYANA = "godwars_saradomin_avatar"
    const val STARLIGHT = "godwars_saradomin_unicorn"
    const val GROWLER = "godwars_saradomin_lion"
    const val BREE = "godwars_saradomin_centaur"
    val saradominLeaders = listOf(ZILYANA, STARLIGHT, GROWLER, BREE)

    // K'ril Tsutsaroth and his bodyguards (Zamorak's chamber). The three demons stand nowhere else.
    const val KRIL = "godwars_zamorak_avatar"
    const val TSTANON = "godwars_ancient_greater_demon"
    const val ZAKLN = "godwars_ancient_lesser_demon"
    const val BALFRUG = "godwars_ancient_black_demon"
    val zamorakLeaders = listOf(KRIL, TSTANON, ZAKLN, BALFRUG)

    /** Dungeon npcs that are not part of any army. */
    val bystanders = listOf("godwars_dying_paladin", "godwars_paladin_multi")

    fun followersOf(god: God): List<String> =
        when (god) {
            God.ARMADYL -> armadyl
            God.BANDOS -> bandos
            God.SARADOMIN -> saradomin
            God.ZAMORAK -> zamorak
        }

    /** The god whose follower [name] is, or `null` for a leader, a bystander or any other npc. */
    fun godOf(name: String): God? = God.entries.firstOrNull { name in followersOf(it) }
}

object GodWarsRoster : NpcReferences() {
    val followers: Map<God, List<NpcType>> =
        God.entries.associateWith { god -> GodWarsNames.followersOf(god).map { find(it) } }
    val leaders: List<NpcType> = GodWarsNames.leaders.map { find(it) }

    /** Every listed npc by cache name, so scripts can look a type up without a second `find`. */
    val byName: Map<String, NpcType> =
        (followers.values.flatten() + leaders).associateBy { it.internalNameValue }
}

object GodWarsHunt : HuntModeReferences() {
    val armadyl = find("godwars_armadyl_hunt")
    val bandos = find("godwars_bandos_hunt")
    val saradomin = find("godwars_saradomin_hunt")
    val zamorak = find("godwars_zamorak_hunt")
    val leader = find("godwars_leader_hunt")

    /** For the generals and bodyguards that fight from a distance (Kree'arra's). */
    val leaderRanged = find("godwars_leader_ranged_hunt")

    // The same four, for followers that attack from a distance: they walk up to their attack range
    // and stop, instead of closing to melee.
    val armadylRanged = find("godwars_armadyl_ranged_hunt")
    val bandosRanged = find("godwars_bandos_ranged_hunt")
    val saradominRanged = find("godwars_saradomin_ranged_hunt")
    val zamorakRanged = find("godwars_zamorak_ranged_hunt")

    fun of(god: God, ranged: Boolean = false) =
        when (god) {
            God.ARMADYL -> if (ranged) armadylRanged else armadyl
            God.BANDOS -> if (ranged) bandosRanged else bandos
            God.SARADOMIN -> if (ranged) saradominRanged else saradomin
            God.ZAMORAK -> if (ranged) zamorakRanged else zamorak
        }
}

/**
 * The hunt modes. A follower looks for a player in line of sight every tick and walks up to melee
 * them, unless the player's switch for the follower's god ([God.aligned], see [GodItems]) is `1`:
 * then it finds nobody. A leader has no such condition.
 *
 * None of them checks whether the player or the npc was in a fight lately (the other aggressive
 * modes do): the dungeon is multi-combat and everything in it should join in. Once a monster has a
 * target it does not hunt again, so taking the item off mid-fight changes nothing for the monsters
 * already on you, and putting it back on does not make them let go, as on the Wiki.
 */
internal object GodWarsHuntBuilder : HuntModeBuilder() {
    init {
        build("godwars_armadyl_hunt") { follower(God.ARMADYL) }
        build("godwars_bandos_hunt") { follower(God.BANDOS) }
        build("godwars_saradomin_hunt") { follower(God.SARADOMIN) }
        build("godwars_zamorak_hunt") { follower(God.ZAMORAK) }
        build("godwars_armadyl_ranged_hunt") { follower(God.ARMADYL, ranged = true) }
        build("godwars_bandos_ranged_hunt") { follower(God.BANDOS, ranged = true) }
        build("godwars_saradomin_ranged_hunt") { follower(God.SARADOMIN, ranged = true) }
        build("godwars_zamorak_ranged_hunt") { follower(God.ZAMORAK, ranged = true) }
        build("godwars_leader_hunt") { common() }
        build("godwars_leader_ranged_hunt") { common(ranged = true) }
    }

    private fun HuntModePluginBuilder.common(ranged: Boolean = false) {
        type = HuntType.Player
        checkVis = HuntVis.LineOfSight
        checkNotTooStrong = HuntCheckNotTooStrong.Off
        checkNotBusy = false
        findKeepHunting = false
        findNewMode = if (ranged) NpcMode.ApPlayer2 else NpcMode.OpPlayer2
    }

    private fun HuntModePluginBuilder.follower(god: God, ranged: Boolean = false) {
        common(ranged)
        checkVar1 {
            varp = god.aligned
            operator = equals
            required = 0
        }
    }
}

/**
 * Gives every God Wars follower the hunt mode of its god and every leader the always-aggressive
 * one. The hunt ranges are guesses (the Wiki gives none): 5 tiles for followers, 8 for the generals
 * and bodyguards so a whole chamber notices a player at the door. Needs `packCache`, which now
 * packs twice the first time (see below).
 */
internal object GodWarsNpcEdits : NpcEditor() {
    private const val FOLLOWER_RANGE = 5
    private const val LEADER_RANGE = 8

    /**
     * How far from where it spawned a general or bodyguard will follow a target. The cache's
     * default would drop a player on the far side of the chamber (they are up to 19 tiles apart).
     */
    private const val LEADER_LEASH = 24

    /** Ticks before a general or bodyguard comes back (the Wiki: 150, or 90 seconds). */
    private const val LEADER_RESPAWN = 150

    /** Kree'arra sees and shoots across the whole chamber (19 by 13). */
    private const val KREE_RANGE = 16
    private const val BODYGUARD_RANGE = 8

    init {
        // The first `packCache` pass runs before the hunt modes above are in the cache, and a
        // mode with no id cannot be set on an npc. Skip then: the packer sees the new modes, adds
        // them and runs again, and this time they have ids.
        if (runCatching { GodWarsHunt.leader.id }.isSuccess) {
            apply()
        }
    }

    private fun apply() {
        for ((god, types) in GodWarsRoster.followers) {
            for (type in types) {
                val ranged = type.internalNameValue in GodWarsNames.ranged
                edit(type) {
                    huntMode = GodWarsHunt.of(god, ranged)
                    huntRange = FOLLOWER_RANGE
                    if (ranged) {
                        attackRange = GodWarsNames.ATTACK_RANGE
                    }
                }
            }
        }
        for (type in GodWarsRoster.leaders) {
            val name = type.internalNameValue
            edit(type) {
                huntMode = GodWarsHunt.leader
                huntRange = LEADER_RANGE
                maxRange = LEADER_LEASH
                // No collision, so a player cannot safespot a general behind its bodyguards (or
                // behind another general) and the whole chamber can walk through each other.
                blockWalk = BlockWalk.None
                respawnRate = LEADER_RESPAWN
                when (name) {
                    GodWarsNames.KREE -> {
                        huntMode = GodWarsHunt.leaderRanged
                        huntRange = KREE_RANGE
                        attackRange = KREE_RANGE
                        timer = GodWarsNames.RESET_TIMER
                        param[params.defend_anim] = GodWarsSeqs.kree_defend
                        param[params.death_anim] = GodWarsSeqs.kree_death
                        param[params.death_sound] = GodWarsSynths.kree_death
                    }
                    GodWarsNames.SKREE,
                    GodWarsNames.GEERIN -> {
                        huntMode = GodWarsHunt.leaderRanged
                        attackRange = BODYGUARD_RANGE
                        timer = GodWarsNames.RESET_TIMER
                    }
                    GodWarsNames.KILISA,
                    GodWarsNames.STARLIGHT,
                    GodWarsNames.TSTANON -> timer = GodWarsNames.RESET_TIMER
                    GodWarsNames.STRONGSTACK -> {
                        timer = GodWarsNames.RESET_TIMER
                        param[params.death_sound] = GodWarsSynths.sergeant_death
                    }
                    GodWarsNames.ZILYANA -> {
                        timer = GodWarsNames.RESET_TIMER
                        param[params.death_sound] = GodWarsSynths.zilyana_death
                    }
                    GodWarsNames.KRIL -> {
                        timer = GodWarsNames.RESET_TIMER
                        param[params.death_sound] = GodWarsSynths.kril_death
                    }
                    GodWarsNames.ZAKLN,
                    GodWarsNames.BALFRUG -> {
                        huntMode = GodWarsHunt.leaderRanged
                        attackRange = BODYGUARD_RANGE
                        timer = GodWarsNames.RESET_TIMER
                    }
                    GodWarsNames.GROWLER -> {
                        huntMode = GodWarsHunt.leaderRanged
                        attackRange = BODYGUARD_RANGE
                        timer = GodWarsNames.RESET_TIMER
                    }
                    GodWarsNames.BREE -> {
                        huntMode = GodWarsHunt.leaderRanged
                        attackRange = BODYGUARD_RANGE
                        timer = GodWarsNames.RESET_TIMER
                        param[params.defend_anim] = GodWarsSeqs.centaur_defend
                        param[params.death_anim] = GodWarsSeqs.centaur_death
                    }
                    GodWarsNames.GRAARDOR -> {
                        timer = GodWarsNames.RESET_TIMER
                        param[params.death_anim] = GodWarsSeqs.graardor_death
                        param[params.death_sound] = GodWarsSynths.graardor_death
                    }
                    GodWarsNames.STEELWILL,
                    GodWarsNames.GRIMSPIKE -> {
                        huntMode = GodWarsHunt.leaderRanged
                        attackRange = BODYGUARD_RANGE
                        timer = GodWarsNames.RESET_TIMER
                        param[params.death_sound] = GodWarsSynths.sergeant_death
                    }
                }
            }
        }
    }
}
