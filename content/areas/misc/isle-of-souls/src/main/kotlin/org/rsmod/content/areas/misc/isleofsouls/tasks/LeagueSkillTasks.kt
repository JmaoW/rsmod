package org.rsmod.content.areas.misc.isleofsouls.tasks

import jakarta.inject.Inject
import org.rsmod.annotations.InternalApi
import org.rsmod.api.config.refs.objs
import org.rsmod.api.config.refs.stats
import org.rsmod.api.config.refs.varbits
import org.rsmod.api.player.stat.statBase
import org.rsmod.api.script.advanced.onWearposChange
import org.rsmod.api.script.onEvent
import org.rsmod.api.script.onPlayerLogin
import org.rsmod.api.spells.events.SpellCast
import org.rsmod.content.areas.misc.isleofsouls.configs.IsleOfSoulsMagicTasks
import org.rsmod.content.areas.misc.isleofsouls.configs.IsleOfSoulsTaskStructs
import org.rsmod.content.areas.misc.isleofsouls.configs.isle_objs
import org.rsmod.content.areas.misc.isleofsouls.miningguild.GoldenNuggetFound
import org.rsmod.content.areas.misc.isleofsouls.miningguild.MiningGuildEntered
import org.rsmod.content.interfaces.prayer.tab.Prayer
import org.rsmod.content.interfaces.prayer.tab.PrayerEvents
import org.rsmod.content.skills.agility.scripts.Agility
import org.rsmod.content.skills.agility.scripts.Arena
import org.rsmod.content.skills.cooking.scripts.Cooking
import org.rsmod.content.skills.crafting.configs.CraftingObjs
import org.rsmod.content.skills.crafting.scripts.Crafting
import org.rsmod.content.skills.firemaking.scripts.Firemaking
import org.rsmod.content.skills.fishing.scripts.Fishing
import org.rsmod.content.skills.fletching.scripts.Fletching
import org.rsmod.content.skills.herblore.configs.Herb
import org.rsmod.content.skills.herblore.configs.Herbs
import org.rsmod.content.skills.herblore.configs.Potion
import org.rsmod.content.skills.herblore.configs.Potions
import org.rsmod.content.skills.herblore.scripts.Drinking
import org.rsmod.content.skills.herblore.scripts.Herblore
import org.rsmod.content.skills.hunter.scripts.Birdhouses
import org.rsmod.content.skills.hunter.scripts.Falconer
import org.rsmod.content.skills.hunter.scripts.HunterCatching
import org.rsmod.content.skills.hunter.scripts.HunterTraps
import org.rsmod.content.skills.mining.configs.ProspectorObjs
import org.rsmod.content.skills.mining.scripts.Mining
import org.rsmod.content.skills.prayer.configs.AltarKind
import org.rsmod.content.skills.prayer.scripts.Altars
import org.rsmod.content.skills.prayer.scripts.Burying
import org.rsmod.content.skills.runecraft.configs.Pouch
import org.rsmod.content.skills.runecraft.configs.Pouches
import org.rsmod.content.skills.runecraft.configs.RuneAltar
import org.rsmod.content.skills.runecraft.scripts.Abyss
import org.rsmod.content.skills.runecraft.scripts.EssenceMine
import org.rsmod.content.skills.runecraft.scripts.EssencePouches
import org.rsmod.content.skills.runecraft.scripts.Runecrafting
import org.rsmod.content.skills.slayer.scripts.SlayerEvents
import org.rsmod.content.skills.smithing.scripts.Smelting
import org.rsmod.content.skills.smithing.scripts.Smithing
import org.rsmod.content.skills.thieving.configs.PickpocketTarget
import org.rsmod.content.skills.thieving.configs.Stall
import org.rsmod.content.skills.thieving.configs.ThievingObjs
import org.rsmod.content.skills.thieving.scripts.CoinPouches
import org.rsmod.content.skills.thieving.scripts.Pickpocketing
import org.rsmod.content.skills.thieving.scripts.StallThieving
import org.rsmod.content.skills.woodcutting.scripts.Woodcutting
import org.rsmod.game.entity.Player
import org.rsmod.game.inv.isType
import org.rsmod.game.type.obj.ObjType
import org.rsmod.game.type.obj.ObjTypeList
import org.rsmod.game.type.stat.StatType
import org.rsmod.game.type.varp.VarpType
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

/**
 * Completes league tasks from mining, woodcutting, fishing, cooking, firemaking, smithing,
 * fletching, prayer, herblore, crafting and runecraft. Tasks that depend on a guild, or on content
 * that does not exist yet (dark crabs, pizzas, Wintertodt, pyre logs, torches and lanterns, soft
 * clay, granite, oak planks), are left locked. Tasks that used to name a place (see
 * `IsleOfSoulsTaskEdits`) complete anywhere.
 */
class LeagueSkillTasks
@Inject
constructor(
    private val tracker: LeagueTaskTracker,
    private val objTypes: ObjTypeList,
    private val levelTasks: LeagueLevelTasks,
    private val hunterTasks: LeagueHunterTasks,
    private val agilityTasks: LeagueAgilityTasks,
) : PluginScript() {
    override fun ScriptContext.startup() {
        onEvent<SpellCast> { checkMilestones(player, stats.magic) }
        onEvent<SlayerEvents.Kill> { checkMilestones(player, stats.slayer) }
        onEvent<Mining.MineOre> { onMine(player, product) }
        onEvent<Mining.GemFound> { tracker.complete(player, TASK_MINE_GEM) }
        onEvent<MiningGuildEntered> { tracker.complete(player, TASK_ENTER_MINING_GUILD) }
        onEvent<GoldenNuggetFound> {
            tracker.addProgress(player, TASK_GOLDEN_NUGGETS, league_progress.golden_nuggets, 20)
        }
        onEvent<Woodcutting.CutLogs> { onChop(player, product) }
        onEvent<Fishing.FishCaught> { onCatch(player, fish) }
        onEvent<Cooking.Cooked> { onCook(player, cooked, onRange) }
        onEvent<Cooking.Burnt> { onBurn(player, raw) }
        onEvent<Firemaking.LogLit> { onLight(player, log) }
        onEvent<Smelting.BarSmelted> { onSmelt(player, bar) }
        onEvent<Smithing.ItemSmithed> { onSmith(player, obj, bar, quantity) }
        onEvent<Fletching.ItemFletched> { onFletch(player, product, quantity) }
        onEvent<Burying.BoneBuried> { onBury(player, bone) }
        onEvent<Herblore.HerbCleaned> { onClean(player, herb) }
        onEvent<Herblore.PotionMade> { onMake(player, potion, bonusDose) }
        onEvent<Drinking.PotionDrunk> { onDrink(player, potion) }
        onEvent<Crafting.ItemCrafted> { onCraft(player, product, quantity) }
        onEvent<Pickpocketing.Pickpocketed> { onPickpocket(player, target, loot, victims) }
        onEvent<StallThieving.Stolen> { onSteal(player, stall, obj) }
        onEvent<CoinPouches.PouchesOpened> { onPouchesOpened(player, count, coins) }
        onEvent<Agility.LapDone> { checkMilestones(player, stats.agility) }
        onEvent<Agility.ObstacleDone> { checkMilestones(player, stats.agility) }
        onEvent<Agility.MarkTaken> { agilityTasks.onMarkTaken(player) }
        onEvent<Arena.Tagged> { agilityTasks.onTagged(player, streak) }
        onEvent<Agility.ShortcutUsed> { checkMilestones(player, stats.agility) }
        onEvent<HunterTraps.Trapped> {
            hunterTasks.onTrapped(player, creature, catches)
            checkMilestones(player, stats.hunter)
        }
        onEvent<HunterCatching.Caught> {
            hunterTasks.onCaught(player, name, jarred)
            checkMilestones(player, stats.hunter)
        }
        onEvent<HunterCatching.Looted> { hunterTasks.onLooted(player, name, obj) }
        onEvent<Falconer.Hunted> { checkMilestones(player, stats.hunter) }
        onEvent<Birdhouses.Built> { hunterTasks.onBirdhouseBuilt(player, house) }
        onEvent<Birdhouses.Collected> {
            hunterTasks.onBirdhouseCollected(player)
            checkMilestones(player, stats.hunter)
        }
        onEvent<EssenceMine.MineVisited> { tracker.complete(player, TASK_VISIT_ESSENCE_MINE) }
        onEvent<EssenceMine.EssenceMined> { onEssenceMined(player, pure) }
        onEvent<Runecrafting.RunesCrafted> { onRunesCrafted(player, altar, essence, runes) }
        onEvent<Runecrafting.ComboRunesCrafted> { onComboCrafted(player, altar, combo.rune) }
        onEvent<Runecrafting.TalismanLocated> { tracker.complete(player, TASK_LOCATE_ALTAR) }
        onEvent<Abyss.AltarEntered> { tracker.complete(player, TASK_USE_ABYSS) }
        onEvent<EssencePouches.PouchFilled> { onPouchFilled(player, pouch) }
        onEvent<Altars.BoneOffered> { onOffer(player, bone, altar) }
        onEvent<Altars.PrayerRestored> { onRestore(player, points) }
        onEvent<PrayerEvents.Enabled> { onPray(player, prayer) }
        onWearposChange {
            checkProspectorOutfit(player)
            hunterTasks.checkGear(player)
            agilityTasks.checkGear(player)
        }
        onPlayerLogin {
            checkMilestones(player)
            checkProspectorOutfit(player)
            hunterTasks.checkGear(player)
            agilityTasks.checkGear(player)
            levelTasks.check(player)
        }
    }

    private fun onMine(player: Player, product: ObjType) {
        if (product.id == objs.copper_ore.id) {
            tracker.complete(player, TASK_MINE_COPPER)
        }

        for (rule in mineOnce) {
            if (product.id == rule.item.id) {
                tracker.complete(player, rule.taskId)
            }
        }
        for (rule in miningCounts) {
            if (product.id == rule.product.id) {
                tracker.addProgress(player, rule.taskId, rule.counter, rule.goal)
            }
        }

        val pickaxe = Mining.findPickaxe(player, objTypes)
        if (pickaxe.isType(objs.steel_pickaxe)) {
            tracker.complete(player, TASK_MINE_STEEL_PICKAXE)
        }
        if (pickaxe.isType(objs.rune_pickaxe)) {
            tracker.complete(player, TASK_MINE_RUNE_PICKAXE)
        }
        checkMilestones(player, stats.mining)
    }

    /** "Equip a Full Prospector Outfit": helmet, jacket, legs and boots worn at once. */
    private fun checkProspectorOutfit(player: Player) {
        if (tracker.isComplete(player, TASK_FULL_PROSPECTOR)) {
            return
        }
        if (prospectorPieces.all { it in player.worn }) {
            tracker.complete(player, TASK_FULL_PROSPECTOR)
        }
    }

    private fun onChop(player: Player, product: ObjType) {
        tracker.complete(player, TASK_CHOP_ANY_LOGS)

        for (rule in woodcuttingCounts) {
            if (product.id == rule.product.id) {
                tracker.addProgress(player, rule.taskId, rule.counter, rule.goal)
            }
        }

        val axe = Woodcutting.findAxe(player, objTypes)
        if (axe.isType(objs.steel_axe)) {
            tracker.complete(player, TASK_CHOP_STEEL_AXE)
        }
        if (axe.isType(objs.rune_axe)) {
            tracker.complete(player, TASK_CHOP_RUNE_AXE)
        }
        checkMilestones(player, stats.woodcutting)
    }

    private fun onCatch(player: Player, fish: ObjType) {
        for (rule in catchOnce) {
            if (fish.id == rule.item.id) {
                tracker.complete(player, rule.taskId)
            }
        }
        applyCounts(player, fish, catchCounts)
        checkMilestones(player, stats.fishing)
    }

    private fun onCook(player: Player, cooked: ObjType, onRange: Boolean) {
        if (onRange) {
            tracker.complete(player, TASK_COOK_ON_RANGE)
        }
        for (rule in cookOnce) {
            if (cooked.id == rule.item.id) {
                tracker.complete(player, rule.taskId)
            }
        }
        tracker.addProgress(player, TASK_COOK_FIVE_FOODS, league_progress.cook_food, 5)
        applyCounts(player, cooked, cookCounts)
        checkMilestones(player, stats.cooking)
    }

    private fun onBurn(player: Player, raw: ObjType) {
        tracker.complete(player, TASK_BURN_FOOD)
        if (raw.id == isle_objs.raw_karambwan.id) {
            // "In a row" means no burnt karambwan in between.
            tracker.resetProgress(
                player,
                TASK_COOK_KARAMBWAN_ROW,
                league_progress.cook_karambwan_row,
            )
        }
    }

    private fun onLight(player: Player, log: ObjType) {
        for (rule in burnOnce) {
            if (log.id == rule.item.id) {
                tracker.complete(player, rule.taskId)
            }
        }
        for (rule in burnCounts) {
            if (log.id == rule.product.id) {
                tracker.addProgress(player, rule.taskId, rule.counter, rule.goal)
            }
        }
        checkMilestones(player, stats.firemaking)
    }

    private fun onSmelt(player: Player, bar: ObjType) {
        for (rule in smeltOnce) {
            if (bar.id == rule.item.id) {
                tracker.complete(player, rule.taskId)
            }
        }
        checkMilestones(player, stats.smithing)
    }

    private fun onSmith(player: Player, obj: ObjType, bar: ObjType, quantity: Int) {
        for (rule in smithOnce) {
            if (obj.id == rule.item.id) {
                tracker.complete(player, rule.taskId)
            }
        }
        if (bar.id == isle_objs.runite_bar.id) {
            tracker.complete(player, TASK_SMITH_RUNE_ITEM)
        }
        // A smith can give a whole stack, and each item counts.
        for (rule in smithCounts) {
            if (obj.id == rule.product.id) {
                repeat(quantity) {
                    tracker.addProgress(player, rule.taskId, rule.counter, rule.goal)
                }
            }
        }
        checkMilestones(player, stats.smithing)
    }

    private fun onFletch(player: Player, product: ObjType, quantity: Int) {
        for (rule in fletchOnce) {
            if (product.id == rule.item.id) {
                tracker.complete(player, rule.taskId)
            }
        }
        // A batch gives many items, and each one counts.
        for (rule in fletchCounts) {
            if (product.id == rule.product.id) {
                repeat(quantity) {
                    tracker.addProgress(player, rule.taskId, rule.counter, rule.goal)
                }
            }
        }
        checkMilestones(player, stats.fletching)
    }

    private fun onClean(player: Player, herb: Herb) {
        for (rule in cleanOnce) {
            if (herb === rule.herb) {
                tracker.complete(player, rule.taskId)
            }
        }
        for (rule in cleanCounts) {
            if (herb === rule.herb) {
                tracker.addProgress(player, rule.taskId, rule.counter, rule.goal)
            }
        }
        checkMilestones(player, stats.herblore)
    }

    private fun onMake(player: Player, potion: Potion, bonusDose: Boolean) {
        for (rule in makeOnce) {
            if (potion === rule.potion) {
                tracker.complete(player, rule.taskId)
            }
        }
        for (rule in makeCounts) {
            if (potion === rule.potion) {
                tracker.addProgress(player, rule.taskId, rule.counter, rule.goal)
            }
        }
        if (bonusDose) {
            tracker.complete(player, TASK_FOUR_DOSE_POTION)
        }
        checkMilestones(player, stats.herblore)
    }

    private fun onDrink(player: Player, potion: Potion) {
        if (potion === Potions.strength) {
            tracker.complete(player, TASK_DRINK_STRENGTH)
        }
    }

    private fun onCraft(player: Player, product: ObjType, quantity: Int) {
        for (rule in craftOnce) {
            if (product.id == rule.item.id) {
                tracker.complete(player, rule.taskId)
            }
        }
        // A batch can give many items, and each one counts.
        for (rule in craftCounts) {
            if (product.id == rule.product.id) {
                repeat(quantity) {
                    tracker.addProgress(player, rule.taskId, rule.counter, rule.goal)
                }
            }
        }
        checkMilestones(player, stats.crafting)
    }

    private fun onPickpocket(
        player: Player,
        target: PickpocketTarget,
        loot: List<Pickpocketing.Loot>,
        victims: Int,
    ) {
        when (target.key) {
            "man" -> tracker.complete(player, TASK_PICKPOCKET_CITIZEN)
            "master_farmer" -> tracker.complete(player, TASK_PICKPOCKET_MASTER_FARMER)
            "ham" -> tracker.complete(player, TASK_PICKPOCKET_HAM)
            "guard" -> tracker.complete(player, TASK_PICKPOCKET_GUARD)
            "desert_bandit" -> tracker.complete(player, TASK_PICKPOCKET_BANDIT)
            "hero" -> tracker.complete(player, TASK_PICKPOCKET_HERO)
            "knight" ->
                countVictims(player, TASK_KNIGHT_50, league_progress.pickpocket_knight_50, victims)
            "elf_lletya",
            "elf_prifddinas" ->
                countVictims(player, TASK_ELF_50, league_progress.pickpocket_elf_50, victims)
            "vyre" ->
                countVictims(player, TASK_VYRE_50, league_progress.pickpocket_vyre_50, victims)
            "menaphite_thug" ->
                countVictims(player, TASK_THUG_50, league_progress.pickpocket_thug_50, victims)
        }
        for (item in loot) {
            when (item.obj.id) {
                ThievingObjs.bullseye_lantern_unlit.id ->
                    tracker.complete(player, TASK_BULLSEYE_LANTERN)
                ThievingObjs.king_worm.id -> tracker.complete(player, TASK_KING_WORM)
                ThievingObjs.varlamore_thieving_house_key.id -> {
                    tracker.complete(player, TASK_HOUSE_KEY)
                    repeat(item.count) {
                        tracker.addProgress(
                            player,
                            TASK_HOUSE_KEYS_15,
                            league_progress.house_keys_15,
                            15,
                        )
                    }
                }
            }
        }
        checkMilestones(player, stats.thieving)
    }

    /** One count for each npc robbed, so the Dodgy Deals relic's neighbours count too. */
    private fun countVictims(player: Player, taskId: Int, counter: VarpType, victims: Int) {
        repeat(victims) { tracker.addProgress(player, taskId, counter, 50) }
    }

    private fun onSteal(player: Player, stall: Stall, obj: ObjType) {
        val o = ThievingObjs
        when (stall.key) {
            "bakery" ->
                if (obj.id == o.chocolate_slice.id) {
                    tracker.complete(player, TASK_STEAL_CHOCOLATE_SLICE)
                }
            "tea" -> tracker.complete(player, TASK_STEAL_TEA)
            "silk" -> tracker.complete(player, TASK_STEAL_SILK)
            "fruit" ->
                if (obj.id == o.golovanova_top.id) {
                    tracker.complete(player, TASK_STEAL_GOLOVANOVA)
                }
            "fur" -> tracker.complete(player, TASK_STEAL_FUR)
            "silver" -> {
                tracker.complete(player, TASK_STEAL_SILVER)
                if (obj.id == o.tiara.id) {
                    tracker.complete(player, TASK_STEAL_TIARA)
                }
            }
            "spice" -> tracker.complete(player, TASK_STEAL_SPICE)
            "fish" -> tracker.complete(player, TASK_STEAL_FISH)
            "crossbow" ->
                if (obj.id == o.xbows_crossbow_stock_wood.id) {
                    tracker.complete(player, TASK_STEAL_WOODEN_STOCK)
                }
            "crafting" ->
                if (obj.id == o.chisel.id) {
                    tracker.complete(player, TASK_STEAL_CHISEL)
                }
            "gem",
            "mor_ul_rek_gem" -> {
                tracker.complete(player, TASK_STEAL_GEM)
                if (obj.id == o.uncut_diamond.id || obj.id == o.diamond.id) {
                    tracker.complete(player, TASK_STEAL_DIAMOND)
                }
            }
        }
        checkMilestones(player, stats.thieving)
    }

    private fun onPouchesOpened(player: Player, count: Int, coins: Int) {
        if (count >= 28) {
            tracker.complete(player, TASK_OPEN_28_POUCHES)
        }
        if (coins >= 800) {
            tracker.complete(player, TASK_800_COINS_FROM_POUCHES)
        }
    }

    private fun onEssenceMined(player: Player, pure: Boolean) {
        tracker.complete(player, TASK_MINE_ESSENCE)
        if (pure) {
            tracker.addProgress(player, TASK_MINE_PURE_ESSENCE, league_progress.pure_essence_25, 25)
        }
    }

    /** The altar's runes and essence count for the tasks that name one; air, fire and body once. */
    private fun onRunesCrafted(player: Player, altar: RuneAltar, essence: Int, runes: Int) {
        when (altar.name) {
            "air" -> tracker.complete(player, TASK_CRAFT_AIR_RUNE)
            "fire" -> tracker.complete(player, TASK_CRAFT_FIRE_RUNE)
            "body" -> tracker.complete(player, TASK_CRAFT_BODY_RUNE)
        }
        if (essence == 1 && runes >= 4) {
            tracker.complete(player, TASK_FOUR_RUNES_ONE_ESSENCE)
        }
        repeat(essence) {
            tracker.addProgress(player, TASK_ESSENCE_200, league_progress.runes_essence_200, 200)
            tracker.addProgress(player, TASK_ESSENCE_2500, league_progress.runes_essence_2500, 2500)
        }
        for (rule in runeCounts) {
            if (rule.altar == altar.name) {
                repeat(runes) { tracker.addProgress(player, rule.taskId, rule.counter, rule.goal) }
            }
        }
        checkMilestones(player, stats.runecrafting)
    }

    private fun onComboCrafted(player: Player, altar: RuneAltar, rune: ObjType) {
        tracker.complete(player, TASK_COMBINATION_RUNE)
        if (altar.name == "fire" && rune.internalNameValue == "lavarune") {
            tracker.complete(player, TASK_LAVA_RUNE_AT_FIRE_ALTAR)
        }
        checkMilestones(player, stats.runecrafting)
    }

    private fun onPouchFilled(player: Player, pouch: Pouch) {
        when {
            pouch === Pouches.large -> tracker.complete(player, TASK_FILL_LARGE_POUCH)
            pouch === Pouches.giant -> tracker.complete(player, TASK_FILL_GIANT_POUCH)
        }
    }

    private fun onBury(player: Player, bone: ObjType) {
        tracker.complete(player, TASK_BURY_BONES)
        if (isDragonOrWyvern(bone)) {
            tracker.complete(player, TASK_BURY_DRAGON_BONES)
        }
        checkMilestones(player, stats.prayer)
    }

    private fun onOffer(player: Player, bone: ObjType, altar: AltarKind) {
        if (altar == AltarKind.Chaos) {
            tracker.complete(player, TASK_SACRIFICE_BONES)
            if (bone.id == isle_objs.dragon_bones.id) {
                tracker.complete(player, TASK_SACRIFICE_DRAGON_BONES)
            }
        }
        checkMilestones(player, stats.prayer)
    }

    private fun onRestore(player: Player, points: Int) {
        if (points >= 5) {
            tracker.complete(player, TASK_RESTORE_5_PRAYER)
        }
        if (points >= 75) {
            tracker.complete(player, TASK_RESTORE_75_PRAYER)
        }
    }

    private fun onPray(player: Player, prayer: Prayer) {
        val enabled = prayer.enabled.id
        when (enabled) {
            varbits.protect_from_melee.id -> tracker.complete(player, TASK_PROTECT_FROM_MELEE)
            varbits.chivalry.id -> tracker.complete(player, TASK_CHIVALRY)
            varbits.piety.id -> tracker.complete(player, TASK_PIETY)
        }
        if (
            player.vars[varbits.superhuman_strength] == 1 &&
                player.vars[varbits.improved_reflexes] == 1
        ) {
            tracker.complete(player, TASK_STRENGTH_AND_REFLEXES)
        }
    }

    private fun isDragonOrWyvern(bone: ObjType) =
        bone.id == isle_objs.dragon_bones.id || bone.id == isle_objs.wyvern_bones.id

    private fun applyCounts(player: Player, item: ObjType, rules: List<ItemCount>) {
        for (rule in rules) {
            if (rule.items.any { item.id == it.id }) {
                tracker.addProgress(player, rule.taskId, rule.counter, rule.goal)
            }
        }
    }

    private fun checkMilestones(player: Player) {
        for (milestone in milestones) {
            checkMilestone(player, milestone)
        }
    }

    private fun checkMilestones(player: Player, stat: StatType) {
        for (milestone in milestones) {
            if (milestone.stat.id == stat.id) {
                checkMilestone(player, milestone)
            }
        }
    }

    @OptIn(InternalApi::class)
    private fun checkMilestone(player: Player, milestone: Milestone) {
        if (tracker.isComplete(player, milestone.taskId)) {
            return
        }
        val reached =
            when (milestone) {
                is Milestone.Level -> player.statBase(milestone.stat) >= milestone.level
                is Milestone.Xp -> player.statMap.getXP(milestone.stat) >= milestone.xp
            }
        if (reached) {
            tracker.complete(player, milestone.taskId)
        }
    }

    /** Mine, chop or burn [goal] of [product]; [counter] persists the running total. */
    private class CountRule(
        val taskId: Int,
        val product: ObjType,
        val goal: Int,
        val counter: org.rsmod.game.type.varp.VarpType,
    )

    /** Craft [goal] runes at the altar called [altar]; [counter] persists the running total. */
    private class RuneCount(
        val taskId: Int,
        val altar: String,
        val goal: Int,
        val counter: org.rsmod.game.type.varp.VarpType,
    )

    /** Complete [taskId] the first time [item] is caught or cooked. */
    private class Once(val taskId: Int, val item: ObjType)

    /** Catch or cook [goal] of any of [items]; [counter] persists the running total. */
    private class ItemCount(
        val taskId: Int,
        val items: List<ObjType>,
        val goal: Int,
        val counter: org.rsmod.game.type.varp.VarpType,
    )

    /** Complete [taskId] the first time [herb] is cleaned. */
    private class CleanOnce(val taskId: Int, val herb: Herb)

    /** Clean [goal] of [herb]; [counter] persists the running total. */
    private class CleanCount(
        val taskId: Int,
        val herb: Herb,
        val goal: Int,
        val counter: org.rsmod.game.type.varp.VarpType,
    )

    /** Complete [taskId] the first time [potion] is made. */
    private class MakeOnce(val taskId: Int, val potion: Potion)

    /** Make [goal] of [potion]; [counter] persists the running total. */
    private class MakeCount(
        val taskId: Int,
        val potion: Potion,
        val goal: Int,
        val counter: org.rsmod.game.type.varp.VarpType,
    )

    private sealed class Milestone(val taskId: Int, val stat: StatType) {
        class Level(taskId: Int, stat: StatType, val level: Int) : Milestone(taskId, stat)

        class Xp(taskId: Int, stat: StatType, val xp: Int) : Milestone(taskId, stat)
    }

    private val mineOnce = listOf(Once(985, objs.runite_ore))

    private val miningCounts =
        listOf(
            CountRule(1371, objs.tin_ore, 5, league_progress.tin_ore),
            CountRule(306, objs.iron_ore, 50, league_progress.iron_ore),
            CountRule(1372, objs.coal, 15, league_progress.coal),
            CountRule(1823, objs.mithril_ore, 50, league_progress.mithril_ore),
            CountRule(309, objs.adamantite_ore, 50, league_progress.adamantite_ore),
            CountRule(1379, objs.runite_ore, 200, league_progress.runite_ore),
            CountRule(1578, objs.gold_ore, 25, league_progress.gold_ore),
            CountRule(1694, objs.mithril_ore, 20, league_progress.mithril_ore_20),
        )

    private val prospectorPieces =
        listOf(
            ProspectorObjs.helmet,
            ProspectorObjs.jacket,
            ProspectorObjs.legs,
            ProspectorObjs.boots,
        )

    private val woodcuttingCounts =
        listOf(
            CountRule(292, objs.willow_logs, 100, league_progress.willow_logs),
            CountRule(1820, objs.maple_logs, 50, league_progress.maple_logs),
            CountRule(1822, objs.yew_logs, 100, league_progress.yew_logs),
            CountRule(295, objs.magic_logs, 75, league_progress.magic_logs),
            CountRule(1385, objs.teak_logs, 100, league_progress.teak_logs),
            CountRule(1579, objs.maple_logs, 100, league_progress.maple_logs_100),
            CountRule(1607, objs.yew_logs, 15, league_progress.yew_logs_15),
            CountRule(1765, objs.magic_logs, 50, league_progress.magic_logs_50),
            CountRule(1719, objs.magic_logs, 20, league_progress.magic_logs_20),
        )

    private val catchOnce =
        listOf(
            Once(340, isle_objs.raw_shrimp),
            Once(341, isle_objs.raw_herring),
            Once(342, isle_objs.raw_anchovies),
            Once(1548, isle_objs.raw_salmon),
            Once(644, isle_objs.raw_karambwanji),
            Once(696, isle_objs.raw_monkfish),
        )

    private val catchCounts =
        listOf(
            ItemCount(343, listOf(isle_objs.raw_lobster), 100, league_progress.catch_lobster),
            ItemCount(344, listOf(isle_objs.raw_swordfish), 50, league_progress.catch_swordfish),
            ItemCount(345, listOf(isle_objs.raw_salmon), 50, league_progress.catch_salmon),
            ItemCount(346, listOf(isle_objs.raw_shark), 100, league_progress.catch_shark),
            ItemCount(646, listOf(isle_objs.raw_karambwan), 50, league_progress.catch_karambwan),
            ItemCount(1825, listOf(isle_objs.raw_sardine), 25, league_progress.catch_sardine),
            ItemCount(1827, listOf(isle_objs.raw_pike), 10, league_progress.catch_pike),
            ItemCount(1609, listOf(isle_objs.raw_anglerfish), 150, league_progress.catch_anglerfish),
        )

    private val burnOnce =
        listOf(Once(371, objs.logs), Once(372, objs.oak_logs), Once(378, objs.magic_logs))

    private val burnCounts =
        listOf(
            CountRule(374, objs.willow_logs, 100, league_progress.burn_willow),
            CountRule(1821, objs.maple_logs, 25, league_progress.burn_maple),
            CountRule(377, objs.yew_logs, 100, league_progress.burn_yew),
        )

    private val smeltOnce =
        listOf(
            Once(347, isle_objs.bronze_bar),
            Once(348, isle_objs.iron_bar),
            Once(352, isle_objs.steel_bar),
            Once(354, isle_objs.runite_bar),
        )

    private val smithOnce =
        listOf(
            Once(1373, isle_objs.bronze_full_helm),
            Once(1374, isle_objs.bronze_plateskirt),
            Once(351, isle_objs.steel_platebody),
        )

    private val smithCounts =
        listOf(
            CountRule(349, isle_objs.iron_arrowheads, 150, league_progress.smith_iron_arrowtips),
            CountRule(1375, isle_objs.steel_bolts_unf, 10, league_progress.smith_steel_bolts),
            CountRule(1824, isle_objs.mithril_bolts_unf, 250, league_progress.smith_mithril_bolts),
        )

    private val fletchOnce =
        listOf(
            Once(310, isle_objs.arrow_shaft),
            Once(311, isle_objs.oak_shortbow),
            Once(313, isle_objs.willow_shortbow_u),
            Once(317, isle_objs.rune_crossbow),
            Once(1361, isle_objs.magic_shield),
            Once(1362, isle_objs.redwood_shield),
            // Either kind of broad ammo, not the amethyst ones.
            Once(315, isle_objs.broad_arrow),
            Once(315, isle_objs.broad_bolts),
        )

    private val fletchCounts =
        listOf(
            CountRule(312, isle_objs.iron_arrow, 150, league_progress.fletch_iron_arrows),
            CountRule(1357, isle_objs.oak_stock, 25, league_progress.fletch_oak_stocks),
            CountRule(1358, isle_objs.arrow_shaft, 1000, league_progress.fletch_arrow_shafts),
            CountRule(1360, isle_objs.magic_longbow_u, 200, league_progress.fletch_magic_longbows),
            CountRule(700, isle_objs.maple_longbow_u, 100, league_progress.fletch_maple_longbows),
            CountRule(1364, isle_objs.dragon_javelin, 100, league_progress.fletch_dragon_javelins),
            CountRule(1365, isle_objs.dragon_dart, 200, league_progress.fletch_dragon_darts),
        )

    private val cleanOnce = listOf(CleanOnce(391, Herbs.guam), CleanOnce(395, Herbs.avantoe))

    private val cleanCounts =
        listOf(
            CleanCount(1784, Herbs.guam, 25, league_progress.clean_guam),
            CleanCount(1785, Herbs.tarromin, 15, league_progress.clean_tarromin),
            CleanCount(1786, Herbs.ranarr, 50, league_progress.clean_ranarr),
            CleanCount(1788, Herbs.cadantine, 50, league_progress.clean_cadantine),
            CleanCount(1787, Herbs.avantoe, 100, league_progress.clean_avantoe),
            CleanCount(1789, Herbs.lantadyme, 50, league_progress.clean_lantadyme),
        )

    private val makeOnce =
        listOf(
            MakeOnce(392, Potions.attack),
            MakeOnce(1366, Potions.antipoison),
            MakeOnce(1046, Potions.combat),
            MakeOnce(397, Potions.saradomin_brew),
            MakeOnce(399, Potions.super_combat),
            MakeOnce(974, Potions.extended_antifire),
        )

    private val makeCounts =
        listOf(
            MakeCount(394, Potions.prayer, 30, league_progress.make_prayer),
            MakeCount(778, Potions.stamina, 20, league_progress.make_stamina),
            MakeCount(777, Potions.ranging, 20, league_progress.make_ranging),
            MakeCount(856, Potions.magic, 20, league_progress.make_magic),
            MakeCount(1738, Potions.goading, 50, league_progress.make_goading),
            MakeCount(1739, Potions.prayer_regeneration, 100, league_progress.make_prayer_regen),
            MakeCount(1762, Potions.divine_super_attack, 25, league_progress.make_divine_attack),
            MakeCount(
                1763,
                Potions.divine_super_strength,
                25,
                league_progress.make_divine_strength,
            ),
            MakeCount(1380, Potions.divine_super_combat, 100, league_progress.make_divine_combat),
            MakeCount(1381, Potions.divine_ranging, 100, league_progress.make_divine_ranging),
        )

    private val cookOnce =
        listOf(Once(296, isle_objs.cooked_shrimp), Once(514, isle_objs.cooked_rabbit))

    private val cookCounts =
        listOf(
            ItemCount(300, listOf(isle_objs.cooked_lobster), 100, league_progress.cook_lobster),
            ItemCount(302, listOf(isle_objs.cooked_shark), 100, league_progress.cook_shark),
            ItemCount(1587, listOf(isle_objs.cooked_shark), 20, league_progress.cook_shark_20),
            ItemCount(637, listOf(isle_objs.cooked_karambwan), 100, league_progress.cook_karambwan),
            ItemCount(
                TASK_COOK_KARAMBWAN_ROW,
                listOf(isle_objs.cooked_karambwan),
                20,
                league_progress.cook_karambwan_row,
            ),
            ItemCount(
                1347,
                listOf(isle_objs.redberry_pie, isle_objs.meat_pie, isle_objs.apple_pie),
                100,
                league_progress.cook_pies,
            ),
            ItemCount(1348, listOf(isle_objs.redberry_pie), 20, league_progress.cook_redberry_pies),
            ItemCount(
                1349,
                listOf(isle_objs.cooked_anglerfish),
                100,
                league_progress.cook_anglerfish,
            ),
            ItemCount(1826, listOf(isle_objs.cooked_sardine), 10, league_progress.cook_sardine),
            ItemCount(1828, listOf(isle_objs.cooked_pike), 20, league_progress.cook_pike),
        )

    private val craftOnce =
        listOf(
            Once(331, CraftingObjs.ball_of_wool),
            Once(332, CraftingObjs.sapphire),
            Once(333, CraftingObjs.leather_body),
            Once(1350, CraftingObjs.red_topaz),
            Once(335, CraftingObjs.emerald_ring),
            // Either the unstrung or the strung amulet counts.
            Once(336, CraftingObjs.ruby_amulet_u),
            Once(336, CraftingObjs.ruby_amulet),
            Once(337, CraftingObjs.dragonstone_amulet_u),
            Once(337, CraftingObjs.dragonstone_amulet),
            Once(339, CraftingObjs.onyx_amulet_u),
            Once(339, CraftingObjs.onyx_amulet),
            Once(1351, CraftingObjs.green_dhide_shield),
            Once(1352, CraftingObjs.black_dhide_shield),
            Once(981, CraftingObjs.air_battlestaff),
            // Any snelm.
            Once(913, CraftingObjs.red_snelm_pointed),
            Once(913, CraftingObjs.red_snelm_round),
            Once(913, CraftingObjs.bark_snelm),
            Once(913, CraftingObjs.blue_snelm_pointed),
            Once(913, CraftingObjs.blue_snelm_round),
            Once(913, CraftingObjs.myre_snelm_pointed),
            Once(913, CraftingObjs.myre_snelm_round),
            Once(913, CraftingObjs.ochre_snelm_pointed),
            Once(913, CraftingObjs.ochre_snelm_round),
            // Any pottery that came out of the oven whole.
            Once(1572, CraftingObjs.pot),
            Once(1572, CraftingObjs.cup),
            Once(1572, CraftingObjs.pie_dish),
            Once(1572, CraftingObjs.bowl),
            Once(1572, CraftingObjs.plant_pot),
            Once(1572, CraftingObjs.pot_lid),
        )

    private val craftCounts =
        listOf(
            CountRule(334, CraftingObjs.unpowered_orb, 100, league_progress.craft_unpowered_orbs),
            CountRule(
                338,
                CraftingObjs.blue_dhide_body,
                30,
                league_progress.craft_blue_dhide_bodies,
            ),
            CountRule(1353, CraftingObjs.empty_light_orb, 100, league_progress.blow_light_orbs),
        )

    private val runeCounts =
        listOf(
            RuneCount(462, "water", 50, league_progress.water_runes_50),
            RuneCount(463, "cosmic", 50, league_progress.cosmic_runes_50),
            RuneCount(573, "astral", 50, league_progress.astral_runes_50),
            RuneCount(648, "nature", 50, league_progress.nature_runes_50),
            RuneCount(525, "death", 50, league_progress.death_runes_50),
            RuneCount(776, "law", 50, league_progress.law_runes_50),
            RuneCount(1603, "blood", 100, league_progress.blood_runes_100),
        )

    private val milestones =
        listOf(
            Milestone.Level(258, stats.mining, 99),
            Milestone.Xp(259, stats.mining, 25_000_000),
            Milestone.Xp(1476, stats.mining, 35_000_000),
            Milestone.Xp(1499, stats.mining, 50_000_000),
            Milestone.Level(268, stats.woodcutting, 99),
            Milestone.Xp(269, stats.woodcutting, 25_000_000),
            Milestone.Xp(1481, stats.woodcutting, 35_000_000),
            Milestone.Xp(1504, stats.woodcutting, 50_000_000),
            Milestone.Level(262, stats.fishing, 99),
            Milestone.Xp(263, stats.fishing, 25_000_000),
            Milestone.Xp(1478, stats.fishing, 35_000_000),
            Milestone.Xp(1501, stats.fishing, 50_000_000),
            Milestone.Level(264, stats.cooking, 99),
            Milestone.Xp(265, stats.cooking, 25_000_000),
            Milestone.Xp(1479, stats.cooking, 35_000_000),
            Milestone.Xp(1502, stats.cooking, 50_000_000),
            Milestone.Level(266, stats.firemaking, 99),
            Milestone.Xp(267, stats.firemaking, 25_000_000),
            Milestone.Xp(1480, stats.firemaking, 35_000_000),
            Milestone.Xp(1503, stats.firemaking, 50_000_000),
            Milestone.Level(260, stats.smithing, 99),
            Milestone.Xp(261, stats.smithing, 25_000_000),
            Milestone.Xp(1477, stats.smithing, 35_000_000),
            Milestone.Xp(1500, stats.smithing, 50_000_000),
            Milestone.Level(252, stats.fletching, 99),
            Milestone.Xp(253, stats.fletching, 25_000_000),
            Milestone.Xp(1473, stats.fletching, 35_000_000),
            Milestone.Xp(1496, stats.fletching, 50_000_000),
            Milestone.Level(234, stats.prayer, 99),
            Milestone.Xp(235, stats.prayer, 25_000_000),
            Milestone.Xp(1464, stats.prayer, 35_000_000),
            Milestone.Xp(1487, stats.prayer, 50_000_000),
            Milestone.Level(246, stats.herblore, 99),
            Milestone.Xp(247, stats.herblore, 25_000_000),
            Milestone.Xp(1470, stats.herblore, 35_000_000),
            Milestone.Xp(1493, stats.herblore, 50_000_000),
            Milestone.Level(250, stats.crafting, 99),
            Milestone.Xp(251, stats.crafting, 25_000_000),
            Milestone.Xp(1472, stats.crafting, 35_000_000),
            Milestone.Xp(1495, stats.crafting, 50_000_000),
            Milestone.Level(256, stats.hunter, 99),
            Milestone.Xp(257, stats.hunter, 25_000_000),
            Milestone.Xp(1475, stats.hunter, 35_000_000),
            Milestone.Xp(1498, stats.hunter, 50_000_000),
            Milestone.Level(248, stats.thieving, 99),
            Milestone.Xp(249, stats.thieving, 25_000_000),
            Milestone.Xp(1471, stats.thieving, 35_000_000),
            Milestone.Xp(1494, stats.thieving, 50_000_000),
            Milestone.Level(244, stats.agility, 99),
            Milestone.Xp(245, stats.agility, 25_000_000),
            Milestone.Xp(1469, stats.agility, 35_000_000),
            Milestone.Xp(1492, stats.agility, 50_000_000),
            Milestone.Level(238, stats.runecrafting, 99),
            Milestone.Xp(239, stats.runecrafting, 25_000_000),
            Milestone.Xp(1466, stats.runecrafting, 35_000_000),
            Milestone.Xp(1489, stats.runecrafting, 50_000_000),
            Milestone.Level(254, stats.slayer, 99),
            Milestone.Xp(255, stats.slayer, 25_000_000),
            Milestone.Xp(1474, stats.slayer, 35_000_000),
            Milestone.Xp(1497, stats.slayer, 50_000_000),
            Milestone.Level(236, stats.magic, 99),
            Milestone.Xp(IsleOfSoulsMagicTasks.taskId("xp_25m"), stats.magic, 25_000_000),
            Milestone.Xp(IsleOfSoulsMagicTasks.taskId("xp_35m"), stats.magic, 35_000_000),
            Milestone.Xp(1488, stats.magic, 50_000_000),
        )

    private companion object {
        const val TASK_MINE_COPPER = 303
        const val TASK_MINE_GEM = 305
        const val TASK_MINE_STEEL_PICKAXE = 304
        const val TASK_MINE_RUNE_PICKAXE = 307
        const val TASK_GOLDEN_NUGGETS = 771
        const val TASK_FULL_PROSPECTOR = 772
        const val TASK_ENTER_MINING_GUILD = IsleOfSoulsTaskStructs.ENTER_MINING_GUILD_TASK
        const val TASK_CHOP_ANY_LOGS = 289
        const val TASK_CHOP_STEEL_AXE = 290
        const val TASK_CHOP_RUNE_AXE = 293
        const val TASK_BURN_FOOD = 297
        const val TASK_COOK_FIVE_FOODS = 298
        const val TASK_COOK_KARAMBWAN_ROW = 1395
        const val TASK_COOK_ON_RANGE = 450
        const val TASK_SMITH_RUNE_ITEM = 1265
        const val TASK_RESTORE_5_PRAYER = 94
        const val TASK_STRENGTH_AND_REFLEXES = 95
        const val TASK_BURY_BONES = 97
        const val TASK_BURY_DRAGON_BONES = 188
        const val TASK_PROTECT_FROM_MELEE = 387
        const val TASK_SACRIFICE_BONES = 969
        const val TASK_RESTORE_75_PRAYER = 65
        const val TASK_CHIVALRY = 726
        const val TASK_SACRIFICE_DRAGON_BONES = 970
        const val TASK_PIETY = 727
        const val TASK_DRINK_STRENGTH = 393
        const val TASK_FOUR_DOSE_POTION = 396
        const val TASK_VISIT_ESSENCE_MINE = 364
        const val TASK_LOCATE_ALTAR = 366
        const val TASK_CRAFT_AIR_RUNE = 774
        const val TASK_CRAFT_FIRE_RUNE = 854
        const val TASK_CRAFT_BODY_RUNE = 775
        const val TASK_MINE_ESSENCE = 1801
        const val TASK_MINE_PURE_ESSENCE = 1613
        const val TASK_ESSENCE_200 = 367
        const val TASK_ESSENCE_2500 = 370
        const val TASK_FOUR_RUNES_ONE_ESSENCE = 368
        const val TASK_COMBINATION_RUNE = 369
        const val TASK_LAVA_RUNE_AT_FIRE_ALTAR = 855
        const val TASK_USE_ABYSS = 982
        const val TASK_FILL_LARGE_POUCH = 983
        const val TASK_FILL_GIANT_POUCH = 984
        const val TASK_PICKPOCKET_CITIZEN = 273
        const val TASK_STEAL_CHOCOLATE_SLICE = 275
        const val TASK_STEAL_TEA = 457
        const val TASK_STEAL_SILK = 1071
        const val TASK_STEAL_GOLOVANOVA = 76
        const val TASK_PICKPOCKET_MASTER_FARMER = 86
        const val TASK_OPEN_28_POUCHES = 274
        const val TASK_800_COINS_FROM_POUCHES = 277
        const val TASK_PICKPOCKET_HAM = 454
        const val TASK_PICKPOCKET_GUARD = 455
        const val TASK_BULLSEYE_LANTERN = 458
        const val TASK_KNIGHT_50 = 684
        const val TASK_PICKPOCKET_BANDIT = 850
        const val TASK_HOUSE_KEY = 1699
        const val TASK_HOUSE_KEYS_15 = 1700
        const val TASK_STEAL_DIAMOND = 1768
        const val TASK_STEAL_TIARA = 1769
        const val TASK_STEAL_FUR = 1817
        const val TASK_STEAL_SILVER = 1818
        const val TASK_STEAL_SPICE = 1819
        const val TASK_STEAL_FISH = 1834
        const val TASK_STEAL_WOODEN_STOCK = 1835
        const val TASK_STEAL_CHISEL = 1844
        const val TASK_STEAL_GEM = 279
        const val TASK_ELF_50 = 526
        const val TASK_PICKPOCKET_HERO = 685
        const val TASK_THUG_50 = 851
        const val TASK_VYRE_50 = 911
        const val TASK_KING_WORM = 1541
    }
}
