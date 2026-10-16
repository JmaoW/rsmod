package org.rsmod.content.areas.misc.isleofsouls.inferno

import org.rsmod.api.combat.NpcAttack
import org.rsmod.api.combat.NpcAttackStyle
import org.rsmod.api.combat.commons.types.MeleeAttackType
import org.rsmod.api.config.refs.projanims
import org.rsmod.content.areas.misc.isleofsouls.fightcaves.cave_seqs
import org.rsmod.content.areas.misc.isleofsouls.fightcaves.cave_spotanims
import org.rsmod.game.type.npc.NpcType
import org.rsmod.game.type.seq.SeqType

/**
 * How one Inferno monster fights. [apRange] is how far away it attacks from, or `null` if it only
 * ever fights in melee. [attackRate] is the ticks between attacks. Numbers are from the wiki.
 */
class InfernoMonsterDef(
    val kind: InfernoMonster,
    val type: NpcType,
    val size: Int,
    val attackRate: Int,
    val apRange: Int?,
    val deathAnim: SeqType,
)

/** The attacks of the ordinary Inferno monsters, built once. Max hits are the wiki's. */
object InfernoAttacks {
    /** The wiki: Jal-Nib attacks "always hit", whatever the player wears. */
    val nibbler =
        NpcAttack(
            style = NpcAttackStyle.Melee,
            meleeType = MeleeAttackType.Crush,
            anim = inferno_seqs.nibbler_attack,
            alwaysHits = true,
            maxHit = 4,
        )

    val bat =
        NpcAttack(
            style = NpcAttackStyle.Ranged,
            anim = inferno_seqs.bat_attack,
            projectile = inferno_spotanims.bat_shot,
            projanim = projanims.arrow,
            maxHit = 19,
        )

    val blobRanged =
        NpcAttack(
            style = NpcAttackStyle.Ranged,
            anim = inferno_seqs.blob_ranged,
            projectile = inferno_spotanims.blob_ranged,
            projanim = projanims.arrow,
            maxHit = 29,
        )

    val blobMagic =
        NpcAttack(
            style = NpcAttackStyle.Magic,
            anim = inferno_seqs.blob_magic,
            projectile = inferno_spotanims.blob_magic,
            projanim = projanims.magic_spell,
            maxHit = 29,
        )

    val blobMelee =
        NpcAttack(
            style = NpcAttackStyle.Melee,
            meleeType = MeleeAttackType.Crush,
            anim = inferno_seqs.blob_melee,
            maxHit = 29,
        )

    val babyRanged =
        NpcAttack(
            style = NpcAttackStyle.Ranged,
            anim = inferno_seqs.blob_ranged,
            projectile = inferno_spotanims.baby_ranged,
            projanim = projanims.arrow,
            maxHit = 18,
        )

    val babyMagic =
        NpcAttack(
            style = NpcAttackStyle.Magic,
            anim = inferno_seqs.blob_magic,
            projectile = inferno_spotanims.baby_magic,
            projanim = projanims.magic_spell,
            maxHit = 18,
        )

    val babyMelee =
        NpcAttack(
            style = NpcAttackStyle.Melee,
            meleeType = MeleeAttackType.Crush,
            anim = inferno_seqs.blob_melee,
            maxHit = 18,
        )

    val melee =
        NpcAttack(
            style = NpcAttackStyle.Melee,
            meleeType = MeleeAttackType.Slash,
            anim = inferno_seqs.melee_attack,
            maxHit = 49,
        )

    val rangerRanged =
        NpcAttack(
            style = NpcAttackStyle.Ranged,
            anim = inferno_seqs.ranger_ranged,
            projectile = inferno_spotanims.ranger_shot,
            projanim = projanims.arrow,
            maxHit = 46,
        )

    val rangerMelee =
        NpcAttack(
            style = NpcAttackStyle.Melee,
            meleeType = MeleeAttackType.Crush,
            anim = inferno_seqs.ranger_melee,
            maxHit = 19,
        )

    val magerMagic =
        NpcAttack(
            style = NpcAttackStyle.Magic,
            anim = inferno_seqs.mager_magic,
            projectile = inferno_spotanims.mager_shot,
            projanim = projanims.magic_spell,
            maxHit = 70,
        )

    /**
     * The ticks between a Jad magic or ranged animation starting and the hit landing, whatever the
     * distance, so there is time to switch protection prayer. The wiki gives no number; this is the
     * Fight Cave's, itself a guess.
     */
    const val JAD_HIT_DELAY = 5

    val jadMagic =
        NpcAttack(
            style = NpcAttackStyle.Magic,
            anim = inferno_seqs.jad_magic,
            castSpot = cave_spotanims.jad_magic_cast,
            projectile = cave_spotanims.jad_magic_ball,
            projanim = projanims.magic_spell,
            minHitDelay = JAD_HIT_DELAY,
            maxHit = 113,
        )

    val jadRanged =
        NpcAttack(
            style = NpcAttackStyle.Ranged,
            anim = inferno_seqs.jad_ranged,
            impactSpot = cave_spotanims.jad_ranged_rock,
            hitDelay = JAD_HIT_DELAY,
            maxHit = 113,
        )

    val jadMelee =
        NpcAttack(
            style = NpcAttackStyle.Melee,
            meleeType = MeleeAttackType.Stab,
            anim = inferno_seqs.jad_melee,
            maxHit = 113,
        )

    val healer =
        NpcAttack(
            style = NpcAttackStyle.Melee,
            meleeType = MeleeAttackType.Crush,
            anim = cave_seqs.yt_mejkot_attack,
            maxHit = 18,
        )

    val magerMelee =
        NpcAttack(
            style = NpcAttackStyle.Melee,
            meleeType = MeleeAttackType.Stab,
            anim = inferno_seqs.mager_melee,
            maxHit = 52,
        )
}

/** Every ordinary Inferno monster's definition, built when the first script needs them. */
object InfernoMonsterDefs {
    val nibbler =
        InfernoMonsterDef(
            InfernoMonster.Nibbler,
            inferno_npcs.nibbler,
            size = 1,
            attackRate = 4,
            apRange = null,
            deathAnim = inferno_seqs.nibbler_death,
        )
    val bat =
        InfernoMonsterDef(
            InfernoMonster.Bat,
            inferno_npcs.bat,
            size = 2,
            attackRate = 3,
            apRange = InfernoNpcEdits.BAT_RANGE,
            deathAnim = inferno_seqs.bat_death,
        )
    val blob =
        InfernoMonsterDef(
            InfernoMonster.Blob,
            inferno_npcs.blob,
            size = 3,
            attackRate = 6,
            apRange = InfernoNpcEdits.BLOB_RANGE,
            deathAnim = inferno_seqs.blob_death,
        )
    val babyRanged =
        InfernoMonsterDef(
            InfernoMonster.Blob,
            inferno_npcs.blob_range,
            size = 1,
            attackRate = 4,
            apRange = InfernoNpcEdits.BABY_BLOB_RANGE,
            deathAnim = inferno_seqs.blob_death,
        )
    val babyMagic =
        InfernoMonsterDef(
            InfernoMonster.Blob,
            inferno_npcs.blob_mage,
            size = 1,
            attackRate = 4,
            apRange = InfernoNpcEdits.BABY_BLOB_RANGE,
            deathAnim = inferno_seqs.blob_death,
        )
    val babyMelee =
        InfernoMonsterDef(
            InfernoMonster.Blob,
            inferno_npcs.blob_melee,
            size = 1,
            attackRate = 4,
            apRange = null,
            deathAnim = inferno_seqs.blob_death,
        )
    val melee =
        InfernoMonsterDef(
            InfernoMonster.Melee,
            inferno_npcs.melee,
            size = 4,
            attackRate = 4,
            apRange = null,
            deathAnim = inferno_seqs.melee_death,
        )
    val ranger =
        InfernoMonsterDef(
            InfernoMonster.Ranger,
            inferno_npcs.ranger,
            size = 3,
            attackRate = 4,
            apRange = InfernoNpcEdits.RANGER_RANGE,
            deathAnim = inferno_seqs.ranger_death,
        )
    val rangerFinal =
        InfernoMonsterDef(
            InfernoMonster.Ranger,
            inferno_npcs.ranger_final,
            size = 3,
            attackRate = 4,
            apRange = InfernoNpcEdits.RANGER_RANGE,
            deathAnim = inferno_seqs.ranger_death,
        )
    val mager =
        InfernoMonsterDef(
            InfernoMonster.Mager,
            inferno_npcs.mager,
            size = 4,
            attackRate = 4,
            apRange = InfernoNpcEdits.MAGER_RANGE,
            deathAnim = inferno_seqs.mager_death,
        )
    val magerFinal =
        InfernoMonsterDef(
            InfernoMonster.Mager,
            inferno_npcs.mager_final,
            size = 4,
            attackRate = 4,
            apRange = InfernoNpcEdits.MAGER_RANGE,
            deathAnim = inferno_seqs.mager_death,
        )

    val jad =
        InfernoMonsterDef(
            InfernoMonster.Jad,
            inferno_npcs.jad,
            size = 5,
            attackRate = 8,
            apRange = InfernoNpcEdits.JAD_RANGE,
            deathAnim = inferno_seqs.jad_death,
        )
    val jadFinal =
        InfernoMonsterDef(
            InfernoMonster.Jad,
            inferno_npcs.jad_final,
            size = 5,
            attackRate = 8,
            apRange = InfernoNpcEdits.JAD_RANGE,
            deathAnim = inferno_seqs.jad_death,
        )

    /** A Yt-HurKot. Its kind is [InfernoMonster.Jad]: it belongs to a Jad. */
    val healer =
        InfernoMonsterDef(
            InfernoMonster.Jad,
            inferno_npcs.jad_healer,
            size = 1,
            attackRate = 4,
            apRange = null,
            deathAnim = cave_seqs.yt_mejkot_death,
        )
    val healerFinal =
        InfernoMonsterDef(
            InfernoMonster.Jad,
            inferno_npcs.jad_healer_final,
            size = 1,
            attackRate = 4,
            apRange = null,
            deathAnim = cave_seqs.yt_mejkot_death,
        )

    /** TzKal-Zuk. He is placed by the fight, not by a wave list entry. */
    val zuk =
        InfernoMonsterDef(
            InfernoMonster.Zuk,
            inferno_npcs.zuk,
            size = 7,
            attackRate = 10,
            apRange = null,
            deathAnim = inferno_seqs.zuk_death,
        )

    /** A Jal-MejJak, one of the four healers of the enrage. */
    val mejjak =
        InfernoMonsterDef(
            InfernoMonster.Zuk,
            inferno_npcs.zuk_healer,
            size = 1,
            attackRate = 3,
            apRange = null,
            deathAnim = inferno_seqs.mejjak_death,
        )

    /** Whether [def] is a Jad (as opposed to one of his healers). */
    fun isJad(def: InfernoMonsterDef): Boolean = def === jad || def === jadFinal

    /** Whether [def] is a Yt-HurKot. */
    fun isHealer(def: InfernoMonsterDef): Boolean = def === healer || def === healerFinal

    /** The definition used for a wave's [monster], or `null` for the ones not built yet. */
    fun of(monster: InfernoMonster): InfernoMonsterDef? =
        when (monster) {
            InfernoMonster.Nibbler -> nibbler
            InfernoMonster.Bat -> bat
            InfernoMonster.Blob -> blob
            InfernoMonster.Melee -> melee
            InfernoMonster.Ranger -> ranger
            InfernoMonster.Mager -> mager
            InfernoMonster.Jad -> jad
            InfernoMonster.Zuk -> null
        }

    val all: List<InfernoMonsterDef> =
        listOf(
            nibbler,
            bat,
            blob,
            babyRanged,
            babyMagic,
            babyMelee,
            melee,
            ranger,
            rangerFinal,
            mager,
            magerFinal,
            jad,
            jadFinal,
            healer,
            healerFinal,
            zuk,
            mejjak,
        )

    private val byType: Map<Int, InfernoMonsterDef> by lazy { all.associateBy { it.type.id } }

    fun of(type: NpcType): InfernoMonsterDef? = byType[type.id]
}

/** Which protection prayer a player has on. */
enum class Protection {
    None,
    Melee,
    Magic,
    Missiles,
}

/** What the blob (Jal-Ak) does: it reads the player's prayer and attacks around it. */
object InfernoBlobAi {
    /** The blob decides its style this many ticks before it attacks. */
    const val READ_AHEAD = 3

    /** Next to a player with Protect from Melee off, it melees one time in this many. */
    const val MELEE_ODDS = 3

    /**
     * The style of the blob's next attack. Next to a player who is not protected from melee it
     * sometimes bites. Otherwise it uses a style the prayer does not cover: ranged against Protect
     * from Magic, magic against Protect from Missiles, and either one against anything else. [roll]
     * gives a number from 0 until its argument.
     */
    fun chooseStyle(protection: Protection, adjacent: Boolean, roll: (Int) -> Int): NpcAttackStyle {
        if (adjacent && protection != Protection.Melee && roll(MELEE_ODDS) == 0) {
            return NpcAttackStyle.Melee
        }
        return when (protection) {
            Protection.Magic -> NpcAttackStyle.Ranged
            Protection.Missiles -> NpcAttackStyle.Magic
            else -> if (roll(2) == 0) NpcAttackStyle.Ranged else NpcAttackStyle.Magic
        }
    }
}
