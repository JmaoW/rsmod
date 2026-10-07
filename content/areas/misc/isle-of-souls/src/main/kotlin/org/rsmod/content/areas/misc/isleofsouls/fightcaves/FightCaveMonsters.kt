package org.rsmod.content.areas.misc.isleofsouls.fightcaves

import org.rsmod.api.combat.NpcAttack
import org.rsmod.api.combat.NpcAttackStyle
import org.rsmod.api.combat.commons.types.MeleeAttackType
import org.rsmod.api.config.refs.projanims
import org.rsmod.game.type.npc.NpcType
import org.rsmod.game.type.seq.SeqType

/**
 * How one Fight Cave monster fights. [apRange] is how far away it attacks from, or `null` if it
 * only ever fights in melee. Numbers are from the wiki: the max hit of each attack, and how many
 * ticks pass between attacks.
 */
class FightCaveMonsterDef(
    val kind: FightCaveMonster,
    val type: NpcType,
    val size: Int,
    val attackRate: Int,
    val apRange: Int?,
    val deathAnim: SeqType,
)

/** The attacks of the cave monsters, built once. */
object FightCaveAttacks {
    /** Tz-Kih drains this many prayer points with every attack. */
    const val TZ_KIH_PRAYER_DRAIN = 1

    val tzKih =
        NpcAttack(
            style = NpcAttackStyle.Melee,
            meleeType = MeleeAttackType.Stab,
            anim = cave_seqs.tz_kih_attack,
            maxHit = 4,
        )

    val tzKek =
        NpcAttack(
            style = NpcAttackStyle.Melee,
            meleeType = MeleeAttackType.Crush,
            anim = cave_seqs.tz_kek_attack,
            maxHit = 7,
        )

    /** The small Tz-Kek that a big one leaves behind. */
    val tzKekSmall =
        NpcAttack(
            style = NpcAttackStyle.Melee,
            meleeType = MeleeAttackType.Crush,
            anim = cave_seqs.tz_kek_attack,
            maxHit = 4,
        )

    val tokXilRanged =
        NpcAttack(
            style = NpcAttackStyle.Ranged,
            anim = cave_seqs.tok_xil_ranged,
            projectile = cave_spotanims.tok_xil_spine,
            projanim = projanims.arrow,
            maxHit = 14,
        )

    val tokXilMelee =
        NpcAttack(
            style = NpcAttackStyle.Melee,
            meleeType = MeleeAttackType.Stab,
            anim = cave_seqs.tok_xil_melee,
            maxHit = 13,
        )

    val ytMejKot =
        NpcAttack(
            style = NpcAttackStyle.Melee,
            meleeType = MeleeAttackType.Crush,
            anim = cave_seqs.yt_mejkot_attack,
            maxHit = 25,
        )

    val ketZekMagic =
        NpcAttack(
            style = NpcAttackStyle.Magic,
            anim = cave_seqs.ket_zek_magic,
            projectile = cave_spotanims.ket_zek_fireball,
            projanim = projanims.magic_spell,
            impactSpot = cave_spotanims.ket_zek_impact,
            maxHit = 52,
        )

    val ketZekMelee =
        NpcAttack(
            style = NpcAttackStyle.Melee,
            meleeType = MeleeAttackType.Crush,
            anim = cave_seqs.ket_zek_melee,
            maxHit = 55,
        )

    /**
     * The ticks between a Jad magic or ranged animation starting and the hit landing, whatever the
     * distance. This is what gives a player time to switch their protection prayer.
     */
    const val JAD_HIT_DELAY = 5

    val jadMagic =
        NpcAttack(
            style = NpcAttackStyle.Magic,
            anim = cave_seqs.jad_magic,
            castSpot = cave_spotanims.jad_magic_cast,
            projectile = cave_spotanims.jad_magic_ball,
            projanim = projanims.magic_spell,
            minHitDelay = JAD_HIT_DELAY,
            maxHit = 95,
        )

    val jadRanged =
        NpcAttack(
            style = NpcAttackStyle.Ranged,
            anim = cave_seqs.jad_ranged,
            impactSpot = cave_spotanims.jad_ranged_rock,
            hitDelay = JAD_HIT_DELAY,
            maxHit = 97,
        )

    val jadMelee =
        NpcAttack(
            style = NpcAttackStyle.Melee,
            meleeType = MeleeAttackType.Crush,
            anim = cave_seqs.jad_melee,
            maxHit = 97,
        )

    val ytHurKot =
        NpcAttack(
            style = NpcAttackStyle.Melee,
            meleeType = MeleeAttackType.Crush,
            anim = cave_seqs.yt_mejkot_attack,
            maxHit = 14,
        )
}

/** Every cave monster's definition, built when the first script needs them. */
object FightCaveMonsterDefs {
    val tzKih =
        FightCaveMonsterDef(
            FightCaveMonster.TzKih,
            cave_npcs.tz_kih,
            size = 1,
            attackRate = 4,
            apRange = null,
            deathAnim = cave_seqs.tz_kih_death,
        )
    val tzKek =
        FightCaveMonsterDef(
            FightCaveMonster.TzKek,
            cave_npcs.tz_kek,
            size = 2,
            attackRate = 4,
            apRange = null,
            deathAnim = cave_seqs.tz_kek_death,
        )
    val tzKekSpawn =
        FightCaveMonsterDef(
            FightCaveMonster.TzKek,
            cave_npcs.tz_kek_spawn,
            size = 1,
            attackRate = 4,
            apRange = null,
            deathAnim = cave_seqs.tz_kek_death,
        )
    val tokXil =
        FightCaveMonsterDef(
            FightCaveMonster.TokXil,
            cave_npcs.tok_xil,
            size = 3,
            attackRate = 4,
            apRange = FightCaveNpcEdits.TOK_XIL_RANGE,
            deathAnim = cave_seqs.tok_xil_death,
        )
    val ytMejKot =
        FightCaveMonsterDef(
            FightCaveMonster.YtMejKot,
            cave_npcs.yt_mejkot,
            size = 4,
            attackRate = 4,
            apRange = null,
            deathAnim = cave_seqs.yt_mejkot_death,
        )
    val ketZek =
        FightCaveMonsterDef(
            FightCaveMonster.KetZek,
            cave_npcs.ket_zek,
            size = 5,
            attackRate = 4,
            apRange = FightCaveNpcEdits.KET_ZEK_RANGE,
            deathAnim = cave_seqs.ket_zek_death,
        )
    val jad =
        FightCaveMonsterDef(
            FightCaveMonster.Jad,
            cave_npcs.jad,
            size = 5,
            attackRate = 8,
            apRange = FightCaveNpcEdits.JAD_RANGE,
            deathAnim = cave_seqs.jad_death,
        )
    val ytHurKot =
        FightCaveMonsterDef(
            FightCaveMonster.YtMejKot,
            cave_npcs.yt_hurkot,
            size = 1,
            attackRate = 4,
            apRange = null,
            deathAnim = cave_seqs.yt_mejkot_death,
        )

    /** The definition used for a wave's [monster]. */
    fun of(monster: FightCaveMonster): FightCaveMonsterDef =
        when (monster) {
            FightCaveMonster.TzKih -> tzKih
            FightCaveMonster.TzKek -> tzKek
            FightCaveMonster.TokXil -> tokXil
            FightCaveMonster.YtMejKot -> ytMejKot
            FightCaveMonster.KetZek -> ketZek
            FightCaveMonster.Jad -> jad
        }

    val all: List<FightCaveMonsterDef> =
        listOf(tzKih, tzKek, tzKekSpawn, tokXil, ytMejKot, ketZek, jad, ytHurKot)

    private val byType: Map<Int, FightCaveMonsterDef> by lazy { all.associateBy { it.type.id } }

    fun of(type: NpcType): FightCaveMonsterDef? = byType[type.id]
}
