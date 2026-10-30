package org.rsmod.content.other.bolt.effects

import org.rsmod.api.type.refs.spot.SpotanimReferences

/**
 * The ten gems that enchanted bolts are tipped with, and how often each one's effect works (OSRS
 * Wiki, "Bolt effects"). The chances are in tenths of a percent: `55` is 5.5%.
 *
 * @property pvm the chance against an npc, `0` for an effect that only works on players.
 * @property pvp the chance against a player.
 * @property bypassesAccuracy the effect can work on a shot that would have missed, and makes it hit
 *   when it does. The others only work on a shot that hits.
 */
enum class BoltGem(
    val pvm: Int,
    val pvp: Int,
    val bypassesAccuracy: Boolean,
    val effectName: String,
) {
    Opal(pvm = 50, pvp = 50, bypassesAccuracy = true, effectName = "Lucky Lightning"),
    Jade(pvm = 60, pvp = 60, bypassesAccuracy = true, effectName = "Earth's Fury"),
    Pearl(pvm = 60, pvp = 60, bypassesAccuracy = true, effectName = "Sea Curse"),
    Topaz(pvm = 0, pvp = 40, bypassesAccuracy = true, effectName = "Down to Earth"),
    Sapphire(pvm = 250, pvp = 50, bypassesAccuracy = false, effectName = "Clear Mind"),
    Emerald(pvm = 550, pvp = 540, bypassesAccuracy = false, effectName = "Magical Poison"),
    Ruby(pvm = 60, pvp = 110, bypassesAccuracy = true, effectName = "Blood Forfeit"),
    Diamond(pvm = 100, pvp = 50, bypassesAccuracy = true, effectName = "Armour Piercing"),
    Dragonstone(pvm = 60, pvp = 60, bypassesAccuracy = false, effectName = "Dragon's Breath"),
    Onyx(pvm = 110, pvp = 100, bypassesAccuracy = false, effectName = "Life Leech");

    companion object {
        /**
         * Both spellings of an enchanted bolt: `xbows_crossbow_bolts_runite_tipped_onyx_enchanted`
         * (and its stack variants `..._enchanted_2` to `_5`) and `dragon_bolts_enchanted_onyx`.
         */
        private val ENCHANTED =
            Regex(
                "^(?:.*_(opal|jade|pearl|redtopaz|sapphire|emerald|ruby|diamond|dragonstone|onyx)_enchanted(?:_[2-5])?" +
                    "|dragon_bolts_enchanted_(opal|jade|pearl|topaz|sapphire|emerald|ruby|diamond|dragonstone|onyx))$"
            )

        /** The gem of the enchanted bolt named [internalName], or `null` if it is not one. */
        fun of(internalName: String?): BoltGem? {
            val match = ENCHANTED.matchEntire(internalName ?: return null) ?: return null
            val key = match.groupValues[1].ifEmpty { match.groupValues[2] }
            return if (key == "redtopaz") Topaz
            else valueOf(key.replaceFirstChar { it.uppercase() })
        }
    }
}

/** The graphic each effect plays on the target. */
object BoltSpotanims : SpotanimReferences() {
    val opal = find("xbows_lucky_lightening_strike_spot_anim")
    val pearl = find("xbows_sea_curse_waterfall_spot_anim")
    val sapphire = find("xbows_clear_mind_glowing_spot_anim")
    val emerald = find("xbows_magical_poison_spot_anim")
    val onyx = find("xbows_life_leach_spot_anim")
    val ruby = find("xbows_blood_sacrifice_spot_anim")
    val jade = find("xbows_earths_fury_spot_anim")
    val dragonstone = find("xbows_dragons_breath_spot_anim")
    val topaz = find("xbows_down_to_earth_spot_anim")
    val diamond = find("xbows_diamond_tips_spotanim")

    fun of(gem: BoltGem) =
        when (gem) {
            BoltGem.Opal -> opal
            BoltGem.Jade -> jade
            BoltGem.Pearl -> pearl
            BoltGem.Topaz -> topaz
            BoltGem.Sapphire -> sapphire
            BoltGem.Emerald -> emerald
            BoltGem.Ruby -> ruby
            BoltGem.Diamond -> diamond
            BoltGem.Dragonstone -> dragonstone
            BoltGem.Onyx -> onyx
        }
}

/** What makes a target fiery, or a staff a fire or water staff, for the effects that care. */
object BoltTargets {
    /** Adult chromatic and metal dragons, lava dragons and the King Black Dragon; not babies. */
    private val FIERY_NPCS =
        Regex(
            "^(?!.*baby)(?:.*(?:red|black|green|blue|bronze|iron|steel|mithril|adamant|rune|lava|king)_?(?:\\d_)?dragon.*" +
                "|.*fire_?giant.*|.*pyrefiend.*|.*fire_elemental.*)$"
        )

    private val FIRE_STAVES =
        Regex("(?:staff_of_fire|fire_staff|fire_battlestaff|mystic_fire|fire_surge)")
    private val FIRE_CAPES =
        Regex("(?:tzhaar_cape_fire|firecape|fire_cape|tzkal_cape|infernal_cape)")
    private val WATER_STAVES =
        Regex("(?:staff_of_water|water_staff|water_battlestaff|mystic_water|water_surge)")

    fun isFieryNpc(internalName: String?): Boolean =
        internalName != null && FIERY_NPCS.matches(internalName)

    /** A player wielding a fire staff or wearing a fire cape. */
    fun isFireGear(weapon: String?, cape: String?): Boolean =
        weapon != null && FIRE_STAVES.containsMatchIn(weapon) ||
            cape != null && FIRE_CAPES.containsMatchIn(cape)

    fun isWaterStaff(weapon: String?): Boolean =
        weapon != null && WATER_STAVES.containsMatchIn(weapon)
}
