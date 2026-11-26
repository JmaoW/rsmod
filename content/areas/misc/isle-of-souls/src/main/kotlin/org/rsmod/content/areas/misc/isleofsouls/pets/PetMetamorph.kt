package org.rsmod.content.areas.misc.isleofsouls.pets

import jakarta.inject.Inject
import org.rsmod.api.player.vars.VarPlayerIntMapSetter
import org.rsmod.api.random.GameRandom
import org.rsmod.game.entity.Player
import org.rsmod.game.type.obj.ObjType
import org.rsmod.game.type.obj.UnpackedObjType

/** The metamorphoses unlocked by an item or a feat, as bits of [PetVarps.unlocks]. */
enum class PetUnlock(val bit: Int) {
    BeaverLogs(0),
    BeaverPheasant(1),
    BeaverFox(2),
    SquirrelDark(3),
    SquirrelBone(4),
    HeronBlue(5),
    PhoenixBlue(6),
    PhoenixGreen(7),
    PhoenixPurple(8),
    PhoenixWhite(9),
    Olmlet(10),
    LilZik(11),
    LittleNightmare(12),
    Muphin(13),
    Nid(14),
    SrarachaOrange(15),
    SrarachaBlue(16),
    ToaAkkha(17),
    ToaBaba(18),
    ToaKephri(19),
    ToaZebak(20),
    ToaAncient(21),

    /** Beating the Corrupted Gauntlet (wiki: the Youngllef's corrupted look). */
    CorruptedGauntlet(22),

    /** All six of TzHaar-Ket-Rak's Challenges (JalRek-Jad). Not on the isle. */
    KetRakChallenges(23),
}

/**
 * What a pet's metamorphosis does, per the OSRS wiki's `Metamorphosis` page and each pet's own
 * page:
 * - Most pets cycle through their looks with the "Metamorphosis" option, some only after an item or
 *   a feat has unlocked the other looks (metamorphic dust, sanguine dust, a remnant, ...).
 * - Some change only when an item is used on them: the rock golem (ores, a rock to go back), the
 *   tangleroot (seeds, consumed), Rocky (berries), the baby mole (mole claw / mole skin), the
 *   beaver (logs, consumed, once a funky shaped log is used), the heron (3,000 spirit flakes, then
 *   one; a raw fish turns it back) and the phoenix (250 firelighters of a colour, unlocked for
 *   good).
 * - The baby chinchompa cycles red, grey and black, with a 1 in 10,000 chance to turn gold.
 * - The rift guardian takes the colour of the altar its owner crafts at (unless locked).
 */
class PetMetamorph @Inject constructor(private val random: GameRandom) {
    fun unlocked(player: Player, unlock: PetUnlock): Boolean =
        player.vars[pet_varps.unlocks] and (1 shl unlock.bit) != 0

    fun unlock(player: Player, unlock: PetUnlock) {
        VarPlayerIntMapSetter.set(
            player,
            pet_varps.unlocks,
            player.vars[pet_varps.unlocks] or (1 shl unlock.bit),
        )
    }

    /** The looks the "Metamorphosis" option cycles through for [player], in order. */
    fun cycle(player: Player, pet: Pet, current: PetForm): List<PetForm> {
        val all = pet.forms
        return when (pet.key) {
            "kalphite_princess",
            "smoke_devil",
            "dark_core",
            "snakeling",
            "venenatis_spiderling",
            "callisto_cub",
            "vetion_jr",
            "jal_nib_rek",
            "noon",
            "ikkle_hydra",
            "lil_creator",
            "bran" -> all
            "baby_chinchompa" -> all.filter { it.label != "gold" }
            "tzrek_jad" -> unlockedOr(player, PetUnlock.KetRakChallenges, all)
            "youngllef" -> unlockedOr(player, PetUnlock.CorruptedGauntlet, all)
            "olmlet" -> unlockedOr(player, PetUnlock.Olmlet, all)
            "lil_zik" -> unlockedOr(player, PetUnlock.LilZik, all)
            "little_nightmare" -> unlockedOr(player, PetUnlock.LittleNightmare, all)
            "muphin" -> unlockedOr(player, PetUnlock.Muphin, all)
            "nid" -> unlockedOr(player, PetUnlock.Nid, all)
            "sraracha" ->
                listOfNotNull(
                    pet.form("red"),
                    pet.form("orange").takeIf { unlocked(player, PetUnlock.SrarachaOrange) },
                    pet.form("blue").takeIf { unlocked(player, PetUnlock.SrarachaBlue) },
                )
            "tumekens_guardian" ->
                listOfNotNull(
                    pet.form("tumeken's guardian"),
                    pet.form("elidinis' guardian"),
                    pet.form("akkhito").takeIf { unlocked(player, PetUnlock.ToaAkkha) },
                    pet.form("babi").takeIf { unlocked(player, PetUnlock.ToaBaba) },
                    pet.form("kephriti").takeIf { unlocked(player, PetUnlock.ToaKephri) },
                    pet.form("zebo").takeIf { unlocked(player, PetUnlock.ToaZebak) },
                    pet.form("tumeken's damaged guardian").takeIf {
                        unlocked(player, PetUnlock.ToaAncient)
                    },
                    pet.form("elidinis' damaged guardian").takeIf {
                        unlocked(player, PetUnlock.ToaAncient)
                    },
                )
            "giant_squirrel" ->
                listOfNotNull(
                    pet.form("giant"),
                    pet.form("dark").takeIf { unlocked(player, PetUnlock.SquirrelDark) },
                    pet.form("bone").takeIf { unlocked(player, PetUnlock.SquirrelBone) },
                )
            "phoenix" ->
                listOfNotNull(
                    pet.form("regular"),
                    pet.form("blue").takeIf { unlocked(player, PetUnlock.PhoenixBlue) },
                    pet.form("green").takeIf { unlocked(player, PetUnlock.PhoenixGreen) },
                    pet.form("purple").takeIf { unlocked(player, PetUnlock.PhoenixPurple) },
                    pet.form("white").takeIf { unlocked(player, PetUnlock.PhoenixWhite) },
                )
            "heron" -> unlockedOr(player, PetUnlock.HeronBlue, all)
            "beaver" -> {
                // The log colours change by logs; the option switches between the current log
                // colour, the pheasant and the fox once those are unlocked (wiki: "freely").
                val log = current.takeIf { it.label != "pheasant" && it.label != "fox" }
                listOfNotNull(
                    log ?: pet.form("normal"),
                    pet.form("pheasant").takeIf { unlocked(player, PetUnlock.BeaverPheasant) },
                    pet.form("fox").takeIf { unlocked(player, PetUnlock.BeaverFox) },
                )
            }
            else -> listOf(current)
        }
    }

    private fun unlockedOr(player: Player, unlock: PetUnlock, all: List<PetForm>) =
        if (unlocked(player, unlock)) all else all.take(1)

    /**
     * The look after [current] when [player] picks "Metamorphosis", or `null` when it has no other
     * look yet. The baby chinchompa has its 1 in 10,000 chance to turn gold.
     */
    fun next(player: Player, pet: Pet, current: PetForm): PetForm? {
        if (pet.key == "baby_chinchompa" && random.of(CHINCHOMPA_GOLD_ODDS) == 0) {
            val gold = pet.form("gold")
            if (gold != current) {
                return gold
            }
        }
        val cycle = cycle(player, pet, current)
        if (cycle.size <= 1) {
            return null
        }
        val index = cycle.indexOf(current)
        return cycle[(index + 1).mod(cycle.size)]
    }

    /** What using [item] on [pet] (in [current] look) does. */
    sealed interface ItemUse {
        /** Nothing: the item does not interest the pet. */
        data object Nothing : ItemUse

        /** The pet already looks like that. */
        data object Already : ItemUse

        /** The pet needs more of the item first. */
        data class NotEnough(val message: String) : ItemUse

        /**
         * Turn into [form]; take [consume] of the item; set [unlock] for good. [message] is shown
         * to the owner.
         */
        data class Change(
            val form: PetForm?,
            val consume: Int = 0,
            val unlock: PetUnlock? = null,
            val message: String? = null,
        ) : ItemUse
    }

    fun useItem(
        player: Player,
        pet: Pet,
        current: PetForm,
        item: UnpackedObjType,
        held: Int,
    ): ItemUse {
        val o = pet_objs
        fun change(
            form: PetForm?,
            consume: Int = 0,
            unlock: PetUnlock? = null,
            mes: String? = null,
        ) =
            if (form == current && unlock == null) ItemUse.Already
            else ItemUse.Change(form, consume, unlock, mes)
        fun same(type: ObjType) = item.id == type.id
        fun unlockAndShow(unlock: PetUnlock, form: PetForm): ItemUse =
            if (unlocked(player, unlock)) {
                ItemUse.Nothing
            } else {
                ItemUse.Change(form, consume = 1, unlock = unlock, message = unlockedMessage(pet))
            }
        return when (pet.key) {
            "rock_golem" -> {
                val label = GOLEM_ORES.entries.firstOrNull { (obj, _) -> same(obj(o)) }?.value
                if (label == null) ItemUse.Nothing else change(pet.form(label))
            }
            "tangleroot" -> {
                val label = TANGLEROOT_SEEDS.entries.firstOrNull { (obj, _) -> same(obj(o)) }?.value
                if (label == null) ItemUse.Nothing else change(pet.form(label), consume = 1)
            }
            "rocky" ->
                when {
                    same(o.white_berries) -> change(pet.form("raccoon"))
                    same(o.redberries) -> change(pet.form("red panda"))
                    same(o.poison_ivy_berries) -> change(pet.form("tanuki"))
                    else -> ItemUse.Nothing
                }
            "baby_mole" ->
                when {
                    same(o.mole_claw) -> change(pet.form("mole-rat"))
                    same(o.mole_skin) -> change(pet.form("mole"))
                    else -> ItemUse.Nothing
                }
            "heron" -> heron(player, pet, current, item, held)
            "beaver" -> beaver(player, pet, current, item)
            "phoenix" -> {
                val colour = PHOENIX_FIRELIGHTERS.entries.firstOrNull { (obj, _) -> same(obj(o)) }
                when {
                    colour == null -> ItemUse.Nothing
                    unlocked(player, colour.value.second) -> change(pet.form(colour.value.first))
                    held < PHOENIX_FIRELIGHTER_COUNT ->
                        ItemUse.NotEnough(
                            "You need $PHOENIX_FIRELIGHTER_COUNT of those firelighters to change " +
                                "your phoenix's colour."
                        )
                    else ->
                        ItemUse.Change(
                            pet.form(colour.value.first),
                            consume = PHOENIX_FIRELIGHTER_COUNT,
                            unlock = colour.value.second,
                            message = unlockedMessage(pet),
                        )
                }
            }
            "giant_squirrel" ->
                when {
                    same(o.dark_acorn) -> unlockAndShow(PetUnlock.SquirrelDark, pet.form("dark"))
                    same(o.calcified_acorn) ->
                        unlockAndShow(PetUnlock.SquirrelBone, pet.form("bone"))
                    else -> ItemUse.Nothing
                }
            "olmlet" ->
                if (same(o.metamorphic_dust)) unlockAndShow(PetUnlock.Olmlet, current)
                else ItemUse.Nothing
            "lil_zik" ->
                if (same(o.sanguine_dust)) unlockAndShow(PetUnlock.LilZik, current)
                else ItemUse.Nothing
            "little_nightmare" ->
                if (same(o.parasitic_egg)) {
                    unlockAndShow(PetUnlock.LittleNightmare, pet.form("little parasite"))
                } else ItemUse.Nothing
            "muphin" ->
                if (same(o.charged_ice)) unlockAndShow(PetUnlock.Muphin, current)
                else ItemUse.Nothing
            "nid" ->
                if (same(o.coagulated_venom)) unlockAndShow(PetUnlock.Nid, pet.form("rax"))
                else ItemUse.Nothing
            "sraracha" ->
                when {
                    same(o.orange_egg_sac) ->
                        unlockAndShow(PetUnlock.SrarachaOrange, pet.form("orange"))
                    same(o.blue_egg_sac) -> unlockAndShow(PetUnlock.SrarachaBlue, pet.form("blue"))
                    else -> ItemUse.Nothing
                }
            "tumekens_guardian" ->
                when {
                    same(o.remnant_akkha) -> unlockAndShow(PetUnlock.ToaAkkha, pet.form("akkhito"))
                    same(o.remnant_baba) -> unlockAndShow(PetUnlock.ToaBaba, pet.form("babi"))
                    same(o.remnant_kephri) ->
                        unlockAndShow(PetUnlock.ToaKephri, pet.form("kephriti"))
                    same(o.remnant_zebak) -> unlockAndShow(PetUnlock.ToaZebak, pet.form("zebo"))
                    same(o.ancient_remnant) ->
                        unlockAndShow(PetUnlock.ToaAncient, pet.form("tumeken's damaged guardian"))
                    else -> ItemUse.Nothing
                }
            "rift_guardian" ->
                if (same(o.guardians_eye) && current.label != "greatish guardian") {
                    ItemUse.Change(
                        pet.form("greatish guardian"),
                        consume = 1,
                        message = "You have unlocked a new metamorphosis for your rift guardian.",
                    )
                } else ItemUse.Nothing
            else -> ItemUse.Nothing
        }
    }

    private fun heron(
        player: Player,
        pet: Pet,
        current: PetForm,
        item: UnpackedObjType,
        held: Int,
    ): ItemUse {
        val blue = pet.form("great blue heron")
        if (item.id == pet_objs.spirit_flakes.id) {
            if (current == blue) {
                return ItemUse.Already
            }
            if (unlocked(player, PetUnlock.HeronBlue)) {
                return ItemUse.Change(
                    blue,
                    consume = 1,
                    message = "Your pet Heron gobbles up a Spirit flake and turns royal blue.",
                )
            }
            if (held < HERON_FLAKES) {
                return ItemUse.NotEnough(
                    "Your heron would need $HERON_FLAKES_TEXT spirit flakes to change colour."
                )
            }
            return ItemUse.Change(
                blue,
                consume = HERON_FLAKES,
                unlock = PetUnlock.HeronBlue,
                message = "Your pet Heron gobbles up the Spirit flakes and turns royal blue.",
            )
        }
        if (isRawFish(item)) {
            if (current != blue) {
                return ItemUse.Already
            }
            return ItemUse.Change(
                pet.form("heron"),
                consume = 1,
                message =
                    "Your pet Heron gobbles up your ${item.name.lowercase()} and turns a " +
                        "magnificent white.",
            )
        }
        return ItemUse.Nothing
    }

    private fun beaver(player: Player, pet: Pet, current: PetForm, item: UnpackedObjType): ItemUse {
        val o = pet_objs
        if (item.id == o.funky_shaped_log.id) {
            return if (unlocked(player, PetUnlock.BeaverLogs)) {
                ItemUse.Nothing
            } else {
                ItemUse.Change(
                    null,
                    consume = 1,
                    unlock = PetUnlock.BeaverLogs,
                    message = unlockedMessage(pet),
                )
            }
        }
        if (item.id == o.golden_pheasant_egg.id) {
            return if (unlocked(player, PetUnlock.BeaverPheasant)) ItemUse.Nothing
            else
                ItemUse.Change(
                    pet.form("pheasant"),
                    1,
                    PetUnlock.BeaverPheasant,
                    unlockedMessage(pet),
                )
        }
        if (item.id == o.fox_whistle.id) {
            return if (unlocked(player, PetUnlock.BeaverFox)) ItemUse.Nothing
            else ItemUse.Change(pet.form("fox"), 1, PetUnlock.BeaverFox, unlockedMessage(pet))
        }
        val label = BEAVER_LOGS.entries.firstOrNull { (obj, _) -> item.id == obj(o).id }?.value
        if (label == null || !unlocked(player, PetUnlock.BeaverLogs)) {
            return ItemUse.Nothing
        }
        val form = pet.form(label)
        return if (form == current) ItemUse.Already else ItemUse.Change(form, consume = 1)
    }

    private fun isRawFish(item: UnpackedObjType): Boolean {
        val name = item.name.lowercase()
        return name.startsWith("raw ") && RAW_FISH.any { name.removePrefix("raw ").startsWith(it) }
    }

    private fun unlockedMessage(pet: Pet): String =
        "You have unlocked a new metamorphosis for your ${pet.name.lowercase()}."

    companion object {
        const val CHINCHOMPA_GOLD_ODDS = 10_000
        const val HERON_FLAKES = 3_000
        const val HERON_FLAKES_TEXT = "3,000"
        const val PHOENIX_FIRELIGHTER_COUNT = 250

        val GOLEM_ORES: Map<(PetMiscObjs) -> ObjType, String> =
            mapOf(
                { o: PetMiscObjs -> o.rock } to "rock",
                { o: PetMiscObjs -> o.tin_ore } to "tin",
                { o: PetMiscObjs -> o.copper_ore } to "copper",
                { o: PetMiscObjs -> o.iron_ore } to "iron",
                { o: PetMiscObjs -> o.blurite_ore } to "blurite",
                { o: PetMiscObjs -> o.silver_ore } to "silver",
                { o: PetMiscObjs -> o.coal } to "coal",
                { o: PetMiscObjs -> o.gold_ore } to "gold",
                { o: PetMiscObjs -> o.mithril_ore } to "mithril",
                { o: PetMiscObjs -> o.granite_500g } to "granite",
                { o: PetMiscObjs -> o.granite_2kg } to "granite",
                { o: PetMiscObjs -> o.granite_5kg } to "granite",
                { o: PetMiscObjs -> o.adamantite_ore } to "adamantite",
                { o: PetMiscObjs -> o.runite_ore } to "runite",
                { o: PetMiscObjs -> o.amethyst } to "amethyst",
                { o: PetMiscObjs -> o.lovakite_ore } to "lovakite",
                { o: PetMiscObjs -> o.elemental_ore } to "elemental",
                { o: PetMiscObjs -> o.daeyalt_ore } to "daeyalt",
            )

        val TANGLEROOT_SEEDS: Map<(PetMiscObjs) -> ObjType, String> =
            mapOf(
                { o: PetMiscObjs -> o.acorn } to "normal",
                { o: PetMiscObjs -> o.crystal_acorn } to "crystal",
                { o: PetMiscObjs -> o.dragonfruit_tree_seed } to "dragonfruit",
                { o: PetMiscObjs -> o.guam_seed } to "herb",
                { o: PetMiscObjs -> o.white_lily_seed } to "white lily",
                { o: PetMiscObjs -> o.redwood_tree_seed } to "redwood",
            )

        val BEAVER_LOGS: Map<(PetMiscObjs) -> ObjType, String> =
            mapOf(
                { o: PetMiscObjs -> o.logs } to "normal",
                { o: PetMiscObjs -> o.oak_logs } to "oak",
                { o: PetMiscObjs -> o.willow_logs } to "willow",
                { o: PetMiscObjs -> o.teak_logs } to "teak",
                { o: PetMiscObjs -> o.maple_logs } to "maple",
                { o: PetMiscObjs -> o.mahogany_logs } to "mahogany",
                { o: PetMiscObjs -> o.arctic_pine_logs } to "arctic pine",
                { o: PetMiscObjs -> o.yew_logs } to "yew",
                { o: PetMiscObjs -> o.magic_logs } to "magic",
                { o: PetMiscObjs -> o.redwood_logs } to "redwood",
            )

        val PHOENIX_FIRELIGHTERS: Map<(PetMiscObjs) -> ObjType, Pair<String, PetUnlock>> =
            mapOf(
                { o: PetMiscObjs -> o.blue_firelighter } to ("blue" to PetUnlock.PhoenixBlue),
                { o: PetMiscObjs -> o.green_firelighter } to ("green" to PetUnlock.PhoenixGreen),
                { o: PetMiscObjs -> o.purple_firelighter } to ("purple" to PetUnlock.PhoenixPurple),
                { o: PetMiscObjs -> o.white_firelighter } to ("white" to PetUnlock.PhoenixWhite),
            )

        /** The fish whose raw form turns a great blue heron white again (by name). */
        val RAW_FISH =
            listOf(
                "shrimp",
                "anchovies",
                "sardine",
                "herring",
                "mackerel",
                "trout",
                "cod",
                "pike",
                "salmon",
                "tuna",
                "lobster",
                "bass",
                "swordfish",
                "monkfish",
                "shark",
                "sea turtle",
                "manta ray",
                "anglerfish",
                "dark crab",
                "karambwan",
                "rainbow fish",
                "cave eel",
                "lava eel",
                "slimy eel",
                "sacred eel",
                "infernal eel",
                "giant carp",
                "leaping",
                "sturgeon",
                "cavefish",
                "tetra",
                "catfish",
                "guppy",
                "karambwanji",
                "minnow",
            )
    }
}
