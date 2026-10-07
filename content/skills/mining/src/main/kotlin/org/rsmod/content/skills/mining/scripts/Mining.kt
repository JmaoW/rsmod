package org.rsmod.content.skills.mining.scripts

import jakarta.inject.Inject
import kotlin.math.max
import org.rsmod.api.config.locParam
import org.rsmod.api.config.locXpParam
import org.rsmod.api.config.objParam
import org.rsmod.api.config.refs.content
import org.rsmod.api.config.refs.objs
import org.rsmod.api.config.refs.params
import org.rsmod.api.config.refs.stats
import org.rsmod.api.config.refs.synths
import org.rsmod.api.invtx.invAdd
import org.rsmod.api.player.output.mes
import org.rsmod.api.player.output.soundSynth
import org.rsmod.api.player.output.spam
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.player.righthand
import org.rsmod.api.player.stat.miningLvl
import org.rsmod.api.player.stat.statAdvance
import org.rsmod.api.random.GameRandom
import org.rsmod.api.repo.loc.LocRepository
import org.rsmod.api.script.onOpLoc1
import org.rsmod.api.script.onOpLoc3
import org.rsmod.api.script.onOpLocU
import org.rsmod.api.stats.xpmod.XpModifiers
import org.rsmod.content.skills.mining.configs.MiningGems
import org.rsmod.content.skills.mining.configs.MiningRocks
import org.rsmod.game.MapClock
import org.rsmod.game.entity.Player
import org.rsmod.game.inv.InvObj
import org.rsmod.game.loc.BoundLocInfo
import org.rsmod.game.type.loc.LocType
import org.rsmod.game.type.loc.UnpackedLocType
import org.rsmod.game.type.obj.ObjType
import org.rsmod.game.type.obj.ObjTypeList
import org.rsmod.game.type.obj.UnpackedObjType
import org.rsmod.game.type.obj.isType
import org.rsmod.game.type.seq.SeqType
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.map.CoordGrid
import org.rsmod.plugin.scripts.ScriptContext

// TODO:
// - rock depletion/respawn for the remaining rock variants
// - sandstone, granite, and other special mining content
// - expert mining gloves (one extra amethyst per crystal); the item is not in this cache
class Mining
@Inject
constructor(
    private val objTypes: ObjTypeList,
    private val locRepo: LocRepository,
    private val mapClock: MapClock,
    private val xpMods: XpModifiers,
) : PluginScript() {
    /** Ores left in each amethyst crystal that has been mined from but not yet depleted. */
    private val oresLeft = HashMap<CoordGrid, Int>()

    override fun ScriptContext.startup() {
        onOpLoc1(content.ore) { attempt(it.loc, it.type) }
        onOpLoc3(content.ore) { mine(it.loc, it.type) }
        onOpLocU(content.ore, content.mining_pickaxe) { mine(it.loc, it.type) }
    }

    private fun ProtectedAccess.attempt(rock: BoundLocInfo, type: UnpackedLocType) {
        if (player.miningLvl < type.miningLevelReq) {
            mes("You need a Mining level of ${type.miningLevelReq} to mine this rock.")
            return
        }

        if (inv.isFull() && !banksOre()) {
            val product = objTypes[type.miningProduct]
            mes("Your inventory is too full to hold any more ${product.name.lowercase()}.")
            soundSynth(synths.pillory_wrong)
            return
        }

        val pickaxe = findPickaxe(player, objTypes) ?: run {
            mes("You need a pickaxe to mine this rock.")
            mes("You do not have a pickaxe which you have the Mining level to use.")
            return
        }

        if (actionDelay < mapClock) {
            actionDelay = mapClock + objTypes[pickaxe].miningRollDelay
            skillAnimDelay = mapClock + 3
            opLoc1(rock)
        } else {
            anim(objTypes[pickaxe].miningAnim)
            spam("You swing your pickaxe at the rock.")
            mine(rock, type)
        }
    }

    private fun ProtectedAccess.mine(rock: BoundLocInfo, type: UnpackedLocType) {
        val pickaxe = findPickaxe(player, objTypes) ?: run {
            mes("You need a pickaxe to mine this rock.")
            mes("You do not have a pickaxe which you have the Mining level to use.")
            return
        }

        if (player.miningLvl < type.miningLevelReq) {
            mes("You need a Mining level of ${type.miningLevelReq} to mine this rock.")
            return
        }

        if (inv.isFull() && !banksOre()) {
            val product = objTypes[type.miningProduct]
            mes("Your inventory is too full to hold any more ${product.name.lowercase()}.")
            soundSynth(synths.pillory_wrong)
            return
        }

        if (skillAnimDelay <= mapClock) {
            skillAnimDelay = mapClock + 4
            anim(objTypes[pickaxe].miningAnim)
        }

        if (actionDelay < mapClock) {
            actionDelay = mapClock + objTypes[pickaxe].miningRollDelay
        } else if (actionDelay == mapClock) {
            val roll = MineRoll(player, rock, mineSuccess(player.miningLvl, type.miningLevelReq, objTypes[pickaxe], random))
            roll.ore = objTypes[type.miningProduct]
            roll.gem = MiningGems.roll(random).takeUnless { MiningGems.isGem(roll.ore!!) }
            publish(roll)
            if (roll.success) {
                val product = objTypes[type.miningProduct]
                val xp = type.miningXp * xpMods.get(player, stats.mining)
                spam("You mine some ${product.name.lowercase()}.")
                statAdvance(stats.mining, xp)
                val given = roll.product ?: product
                if (!roll.bank || !invAdd(bank, given).success) {
                    invAdd(inv, given)
                }
                val ore = MineOre(player, rock, product)
                publish(ore)

                val gem = roll.gem
                if (gem != null) {
                    val gemName = objTypes[gem].name.lowercase()
                    spam("You find ${if (gemName.first() in "aeiou") "an" else "a"} $gemName!")
                    val gemGiven = roll.gemProduct ?: gem
                    if (!roll.bank || !invAdd(bank, gemGiven).success) {
                        invAdd(inv, gemGiven)
                    }
                    publish(GemFound(player, gem))
                }

                if (type.hasDepletedStage && !ore.keepRock && rockDepletes(rock, type)) {
                    val respawnTime = type.resolveRespawnTime(random)
                    locRepo.change(rock, type.miningDepletedStage, respawnTime)
                }
            }
        }

        opLoc3(rock)
    }

    /**
     * Whether the rock depletes after the ore just mined from it. Most rocks have a chance per ore;
     * amethyst crystals instead hold a fixed number of ores, picked when the first one is mined.
     */
    private fun ProtectedAccess.rockDepletes(rock: BoundLocInfo, type: UnpackedLocType): Boolean {
        if (!MiningRocks.isAmethyst(rock)) {
            return random.of(1, 255) > type.miningDepleteChance
        }
        val left = (oresLeft[rock.coords] ?: random.of(AMETHYST_MIN_ORES, AMETHYST_MAX_ORES)) - 1
        if (left > 0) {
            oresLeft[rock.coords] = left
            return false
        }
        oresLeft.remove(rock.coords)
        return true
    }

    /** Whether the player's ore goes straight to the bank, so a full inventory is no problem. */
    private fun ProtectedAccess.banksOre(): Boolean {
        val check = BankOreCheck(player)
        publish(check)
        return check.bank
    }

    /**
     * Published on every roll to mine a rock, after the normal roll. Listeners can turn a failed
     * roll into a [success], ask for the ore to be sent to the [bank] instead of the inventory,
     * and give a different [product] in place of the [ore] (the ore still counts as mined).
     */
    class MineRoll(val player: Player, val rock: BoundLocInfo, var success: Boolean) :
        org.rsmod.events.UnboundEvent {
        var bank: Boolean = false

        /** The ore the rock gives. Set by the mining script before the event is published. */
        var ore: ObjType? = null

        /** What the player gets instead of the [ore], or `null` to get the ore itself. */
        var product: ObjType? = null

        /** The uncut gem this ore comes with, or `null` for none. Only given on a success. */
        var gem: ObjType? = null

        /** What the player gets instead of the [gem], or `null` to get the gem itself. */
        var gemProduct: ObjType? = null
    }

    /**
     * Published before mining checks the inventory is full. Listeners set [bank] when the ore
     * will go straight to the bank, so a full inventory does not stop the player mining.
     */
    class BankOreCheck(val player: Player) : org.rsmod.events.UnboundEvent {
        var bank: Boolean = false
    }

    /** Published when a gem turns up with a mined ore, whatever is done with it afterwards. */
    class GemFound(val player: Player, val gem: ObjType) : org.rsmod.events.UnboundEvent

    /** Published once an ore has been mined. Listeners can set [keepRock] to stop it depleting. */
    class MineOre(val player: Player, val rock: BoundLocInfo, val product: ObjType) :
        org.rsmod.events.UnboundEvent {
        var keepRock: Boolean = false
    }

    companion object {
        private const val AMETHYST_MIN_ORES = 2
        private const val AMETHYST_MAX_ORES = 3

        val UnpackedLocType.miningLevelReq: Int by locParam(params.levelrequire)
        val UnpackedLocType.miningProduct: ObjType by locParam(params.skill_productitem)
        val UnpackedLocType.miningXp: Double by locXpParam(params.skill_xp)
        val UnpackedLocType.miningDepletedStage: LocType by locParam(params.next_loc_stage)
        val UnpackedLocType.miningDepleteChance: Int by locParam(params.deplete_chance)
        val UnpackedLocType.miningRespawnTime: Int by locParam(params.respawn_time)
        val UnpackedLocType.miningRespawnTimeLow: Int by locParam(params.respawn_time_low)
        val UnpackedLocType.miningRespawnTimeHigh: Int by locParam(params.respawn_time_high)

        private val UnpackedLocType.hasDepletedStage: Boolean
            get() = hasParam(params.next_loc_stage)

        val UnpackedObjType.pickaxeLevelReq: Int by objParam(params.levelrequire)
        val UnpackedObjType.miningAnim: SeqType by objParam(params.skill_anim)

        val UnpackedObjType.miningRollDelay: Int
            get() = when (this) {
                objs.bronze_pickaxe -> 8
                objs.iron_pickaxe -> 7
                objs.steel_pickaxe -> 6
                objs.black_pickaxe -> 5
                objs.mithril_pickaxe -> 5
                objs.adamant_pickaxe -> 4
                objs.rune_pickaxe, objs.gilded_pickaxe -> 3
                objs.dragon_pickaxe -> 2
                objs.dragon_pickaxe_upgraded -> 2
                objs.dragon_pickaxe_or_trailblazer -> 2
                objs.dragon_pickaxe_or_zalcano -> 2
                objs.infernal_pickaxe -> 2
                objs.infernal_pickaxe_uncharged -> 2
                objs.infernal_pickaxe_or -> 2
                objs.infernal_pickaxe_or_uncharged -> 2
                objs.third_age_pickaxe -> 1
                objs.crystal_pickaxe -> 1
                objs.crystal_pickaxe_inactive -> 1
                objs.trailblazer_reloaded_pickaxe -> 2
                objs.trailblazer_reloaded_pickaxe_empty -> 2
                objs.trailblazer_reloaded_pickaxe_no_infernal -> 2
                objs.league_trailblazer_pickaxe -> 1
                objs.zalcano_pickaxe -> 2
                objs.gauntlet_pickaxe -> 3
                objs.gauntlet_pickaxe_hm -> 3
                else -> 8
            }

        val UnpackedObjType.miningPower: Int
            get() = when (this) {
                objs.bronze_pickaxe -> 1
                objs.iron_pickaxe -> 2
                objs.steel_pickaxe -> 3
                objs.black_pickaxe -> 4
                objs.mithril_pickaxe -> 5
                objs.adamant_pickaxe -> 6
                objs.rune_pickaxe, objs.gilded_pickaxe -> 7
                objs.dragon_pickaxe -> 10
                objs.dragon_pickaxe_upgraded -> 10
                objs.dragon_pickaxe_or_trailblazer -> 10
                objs.dragon_pickaxe_or_zalcano -> 10
                objs.infernal_pickaxe -> 15
                objs.infernal_pickaxe_uncharged -> 15
                objs.infernal_pickaxe_or -> 15
                objs.infernal_pickaxe_or_uncharged -> 15
                objs.third_age_pickaxe -> 20
                objs.trailblazer_reloaded_pickaxe -> 15
                objs.trailblazer_reloaded_pickaxe_empty -> 15
                objs.trailblazer_reloaded_pickaxe_no_infernal -> 15
                objs.league_trailblazer_pickaxe -> 15
                objs.zalcano_pickaxe -> 10
                objs.gauntlet_pickaxe -> 15
                objs.gauntlet_pickaxe_hm -> 15
                objs.crystal_pickaxe, objs.crystal_pickaxe_inactive -> 15
                else -> 1
            }

        fun findPickaxe(player: Player, objTypes: ObjTypeList): InvObj? {
            val worn = player.wornPickaxe(objTypes)
            val carried = player.carriedPickaxe(objTypes)
            if (worn != null && carried != null) {
                if (objTypes[worn].pickaxeLevelReq >= objTypes[carried].pickaxeLevelReq) {
                    return worn
                }
                return carried
            }
            return worn ?: carried
        }

        private fun Player.wornPickaxe(objTypes: ObjTypeList): InvObj? {
            val wornPickaxe = righthand ?: return null
            return wornPickaxe.takeIf { objTypes[it].isUsablePickaxe(miningLvl) }
        }

        private fun Player.carriedPickaxe(objTypes: ObjTypeList): InvObj? {
            return inv.filterNotNull { objTypes[it].isUsablePickaxe(miningLvl) }
                .maxByOrNull { objTypes[it].pickaxeLevelReq }
        }

        private fun UnpackedObjType.isUsablePickaxe(miningLevel: Int): Boolean =
            isContentType(content.mining_pickaxe) &&
                (miningLevel >= pickaxeLevelReq || isType(objs.league_trailblazer_pickaxe))

        private fun UnpackedLocType.resolveRespawnTime(random: GameRandom): Int {
            val fixed = miningRespawnTime
            if (fixed > 0) {
                return fixed
            }
            return random.of(max(1, miningRespawnTimeLow), max(miningRespawnTimeLow, miningRespawnTimeHigh))
        }

        private fun mineSuccess(miningLevel: Int, req: Int, pickaxe: UnpackedObjType, random: GameRandom): Boolean {
            val progressDenominator = max(1, 99 - req)
            val progress = (miningLevel - req).coerceAtLeast(0).toDouble() / progressDenominator
            val chance = (0.2 + (progress * 0.65) + (pickaxe.miningPower * 0.01)).coerceIn(0.05, 0.95)
            return random.randomDouble() < chance
        }
    }
}
