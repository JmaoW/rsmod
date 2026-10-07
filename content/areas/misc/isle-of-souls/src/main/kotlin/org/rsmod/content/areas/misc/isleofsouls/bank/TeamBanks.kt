package org.rsmod.content.areas.misc.isleofsouls.bank

import com.github.michaelbull.logging.InlineLogger
import jakarta.inject.Inject
import jakarta.inject.Singleton
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption.REPLACE_EXISTING
import org.rsmod.api.config.refs.interfaces
import org.rsmod.api.config.refs.invs
import org.rsmod.api.player.ui.ifClose
import org.rsmod.api.player.vars.VarPlayerIntMapSetter
import org.rsmod.content.areas.misc.isleofsouls.teams.LeagueFiles
import org.rsmod.content.areas.misc.isleofsouls.teams.LeagueTeams
import org.rsmod.content.areas.misc.isleofsouls.teams.Team
import org.rsmod.content.interfaces.bank.BankTab
import org.rsmod.content.interfaces.bank.configs.BankVarBits
import org.rsmod.events.EventBus
import org.rsmod.game.entity.Player
import org.rsmod.game.inv.InvObj
import org.rsmod.game.inv.Inventory
import org.rsmod.game.type.inv.InvTypeList
import org.rsmod.game.type.util.UncheckedType

/**
 * One bank shared by every member of a team.
 *
 * A player's bank is the inventory the engine keeps in [Player.invMap] under the `bank` type. Here
 * every member of a team is given the very same [Inventory] object, so a withdrawal by one is
 * instantly what the other sees, and everything that puts items in a bank (the bank screen, the
 * auto-banking relics) works on the team bank without knowing about it.
 * - **Saving.** The normal player save writes the bank into each player's own row, so those copies
 *   would drift apart. The team bank is saved on its own, to `team-banks.json`, and that file is
 *   what is loaded. See [save].
 * - **One at a time.** The bank screen addresses items by slot, so two people in the same bank
 *   would click each other's items by mistake. The bank module's `BankAccess` refuses to open a
 *   bank another player is already using; nothing here has to do that.
 * - **Tab layout.** How many items each tab holds is kept in per-player varbits, but it describes
 *   the shared slots. So when someone opens the bank, they are given the layout of whoever used it
 *   last (see [onOpen]).
 *
 * Players who are not on a team keep their own bank. Everything here runs on the game thread.
 */
@Singleton
class TeamBanks
@Inject
constructor(
    private val invTypes: InvTypeList,
    private val teams: LeagueTeams,
    private val eventBus: EventBus,
) {
    private class Live(val inv: Inventory, var layout: List<Int>)

    private val live = hashMapOf<Int, Live>()
    private val lastUser = hashMapOf<Int, String>()
    private var store = TeamBankStore()
    private var file: Path? = null
    private var lastText: String? = null
    private var lastBackupAt = 0L
    private var lastCheckAt = 0L

    /**
     * Starts saving to `team-banks.json` in [dir] and reads whatever is there. A file that exists
     * but cannot be read stops the server on purpose: carrying on with empty banks would quietly
     * overwrite everyone's items on the next save.
     */
    fun load(dir: Path) {
        Files.createDirectories(dir)
        val path = dir.resolve(FILE)
        file = path
        if (!Files.exists(path)) {
            return
        }
        try {
            val text = Files.readString(path)
            store = TeamBankStore.parse(text)
            lastText = text
        } catch (e: Exception) {
            throw IllegalStateException(
                "Could not read ${path.toAbsolutePath()}: ${e.message}. " +
                    "Fix or move the file and start the server again.",
                e,
            )
        }
        logger.info { "Loaded the banks of ${store.teamIds.size} league teams." }
    }

    /** The shared bank of [team], loading it from the save file the first time it is needed. */
    fun bankOf(team: Team): Inventory = liveOf(team).inv

    /** Whether [inventory] is a team's shared bank. */
    fun isShared(inventory: Inventory): Boolean = live.values.any { it.inv === inventory }

    /**
     * Makes [player]'s bank match their team: the team's shared bank if they are on one, their own
     * otherwise. Call on login and whenever their team may have changed.
     *
     * If their team has no saved bank yet and they already own a bank with items in it, that bank
     * becomes the team bank rather than being thrown away. Anyone joining later who has a personal
     * bank with items keeps those items in the database, but they are not reachable in game.
     */
    fun attach(player: Player) {
        val type = invTypes[invs.bank]
        val current = player.invMap[type]
        val team = teams.teamOf(player)

        if (team == null) {
            // Left their team: give back an empty bank of their own, never the team's.
            if (current != null && isShared(current)) {
                closeBankScreen(player)
                player.invMap[type] = Inventory.create(type)
            }
            return
        }

        val bank = liveOf(team, adopt = current?.takeIf { !isShared(it) }, owner = player).inv
        if (current === bank) {
            return
        }

        if (current != null && current.isNotEmpty() && !isShared(current)) {
            logger.warn {
                "${player.username} had ${current.occupiedSpace()} items in a personal bank; " +
                    "they are not reachable while they share team ${team.id}'s bank."
            }
        }
        closeBankScreen(player)
        player.invMap[type] = bank

        // Someone added to a team mid-session needs the timer too. At login the script starts it.
        if (player.processedMapClock > 0) {
            player.softTimer(team_bank_timers.sync, SYNC_TICKS)
        }
    }

    /**
     * Called as [player] opens the bank screen. Gives them the tab layout of whoever used the bank
     * last: that player's live layout if they are online, otherwise the saved one.
     */
    fun onOpen(player: Player) {
        val team = teams.teamOf(player) ?: return
        val live = this.live[team.id] ?: return
        if (player.invMap[invTypes[invs.bank]] !== live.inv) {
            return
        }

        val previous = lastUser[team.id]
        if (previous == player.username) {
            return
        }
        val layout = previous?.let(teams::onlinePlayer)?.let(::readLayout) ?: live.layout
        writeLayout(player, layout)
    }

    /** Called as [player]'s bank screen closes (which also happens when they log out). */
    fun onClose(player: Player) {
        val team = teams.teamOf(player) ?: return
        val live = this.live[team.id] ?: return
        if (player.invMap[invTypes[invs.bank]] !== live.inv) {
            return
        }
        lastUser[team.id] = player.username
        live.layout = readLayout(player)
        save()
    }

    /**
     * Runs every few seconds for each online team member: keeps the saved layout current once the
     * bank has tidied itself after closing, and saves anything that changed while nobody had the
     * bank open (the auto-banking relics add items without a bank screen).
     */
    fun sync(player: Player) {
        val team = teams.teamOf(player) ?: return
        refreshLayout(team)
        val now = System.currentTimeMillis()
        if (now - lastCheckAt < SYNC_MILLIS) {
            return
        }
        lastCheckAt = now
        save()
    }

    /**
     * Writes every loaded team bank to disk if anything changed. Returns whether it wrote. A failed
     * write is logged and retried on the next call.
     */
    fun save(): Boolean {
        val path = file ?: return false
        for ((teamId, bank) in live) {
            teams.team(teamId)?.let(::refreshLayout)
            store[teamId] = dataOf(bank)
        }
        val text = store.toJson()
        if (text == lastText) {
            return false
        }
        return try {
            backupIfDue(path)
            LeagueFiles.writeAtomically(path, text)
            lastText = text
            true
        } catch (e: IOException) {
            logger.error(e) { "Could not save the team banks to $path." }
            false
        }
    }

    /** Takes the layout from whoever has the bank open, or else from whoever used it last. */
    private fun refreshLayout(team: Team) {
        val bank = live[team.id] ?: return
        val viewer =
            teams.onlineMembers(team).firstOrNull { it.ui.containsModal(interfaces.bank_main) }
        val source = viewer ?: lastUser[team.id]?.let(teams::onlinePlayer)
        if (source != null && source.invMap[invTypes[invs.bank]] === bank.inv) {
            bank.layout = readLayout(source)
        }
    }

    private fun liveOf(team: Team, adopt: Inventory? = null, owner: Player? = null): Live {
        live[team.id]?.let {
            return it
        }
        val type = invTypes[invs.bank]
        val saved = store[team.id]
        val created =
            when {
                saved != null -> Live(inventoryOf(saved), saved.layout.padded())
                adopt != null && adopt.isNotEmpty() && owner != null -> {
                    logger.info { "${owner.username}'s bank became team ${team.id}'s bank." }
                    Live(adopt, readLayout(owner))
                }
                else -> Live(Inventory.create(type), List(TABS) { 0 })
            }
        live[team.id] = created
        return created
    }

    @OptIn(UncheckedType::class)
    private fun inventoryOf(saved: TeamBankData): Inventory {
        val inventory = Inventory.create(invTypes[invs.bank])
        for ((slot, obj) in saved.objs) {
            if (slot !in inventory.indices) {
                logger.error { "Saved team bank has an item in slot $slot, which does not exist." }
                continue
            }
            inventory[slot] = InvObj(obj.id, obj.count, vars = obj.vars)
        }
        inventory.clearModifiedSlots()
        return inventory
    }

    private fun dataOf(bank: Live): TeamBankData {
        val objs = linkedMapOf<Int, StoredObj>()
        for (slot in bank.inv.indices) {
            val obj = bank.inv[slot] ?: continue
            objs[slot] = StoredObj(obj.id, obj.count, obj.vars)
        }
        return TeamBankData(bank.layout, objs)
    }

    private fun readLayout(player: Player): List<Int> =
        BankTab.entries.map { player.vars[it.sizeVarBit] }

    /**
     * Sets [player]'s tab sizes to [layout]. A tab that no longer exists cannot stay selected, or
     * the bank would open on an empty tab.
     */
    private fun writeLayout(player: Player, layout: List<Int>) {
        for (tab in BankTab.entries) {
            VarPlayerIntMapSetter.set(player, tab.sizeVarBit, layout.getOrElse(tab.index) { 0 })
        }
        val selected = player.vars[BankVarBits.selected_tab]
        val tab = BankTab.entries.firstOrNull { it.varValue == selected }
        if (tab != null && !tab.isMainTab && layout.getOrElse(tab.index) { 0 } == 0) {
            VarPlayerIntMapSetter.set(player, BankVarBits.selected_tab, BankTab.Main.varValue)
        }
    }

    private fun List<Int>.padded(): List<Int> = List(TABS) { getOrElse(it) { 0 } }

    private fun closeBankScreen(player: Player) {
        if (player.ui.containsModal(interfaces.bank_main)) {
            player.ifClose(eventBus)
        }
    }

    private fun backupIfDue(path: Path) {
        val now = System.currentTimeMillis()
        if (!Files.exists(path) || now - lastBackupAt < BACKUP_MILLIS) {
            return
        }
        Files.copy(path, path.resolveSibling(BACKUP_FILE), REPLACE_EXISTING)
        lastBackupAt = now
    }

    companion object {
        const val FILE = "team-banks.json"
        const val BACKUP_FILE = "team-banks.bak.json"

        /** How often the sync timer runs for each team member, in game ticks. */
        const val SYNC_TICKS = 10

        private const val SYNC_MILLIS = 2_000L
        private const val BACKUP_MILLIS = 10 * 60_000L
        private val TABS = BankTab.entries.size
        private val logger = InlineLogger()
    }
}
