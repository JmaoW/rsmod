package org.rsmod.api.cache.enricher.npc

import com.fasterxml.jackson.core.type.TypeReference
import com.fasterxml.jackson.databind.ObjectMapper
import jakarta.inject.Inject
import kotlin.math.round
import org.rsmod.api.config.aliases.ParamCategory
import org.rsmod.api.config.aliases.ParamInt
import org.rsmod.api.config.aliases.ParamSeq
import org.rsmod.api.config.aliases.ParamSynth
import org.rsmod.api.config.constants
import org.rsmod.api.config.refs.categories
import org.rsmod.api.config.refs.params
import org.rsmod.api.parsers.toml.Toml
import org.rsmod.api.type.script.dsl.NpcPluginBuilder
import org.rsmod.api.type.symbols.name.NameMapping
import org.rsmod.api.utils.io.InputStreams
import org.rsmod.game.type.npc.UnpackedNpcType
import org.rsmod.game.type.seq.SeqTypeList
import org.rsmod.game.type.synth.SynthType

public class DefaultNpcCacheEnricher
@Inject
constructor(
    @Toml private val mapper: ObjectMapper,
    private val nameMapping: NameMapping,
    private val seqTypes: SeqTypeList,
) : NpcCacheEnricher {
    private val names: Map<String, Int>
        get() = nameMapping.npcs

    override fun generate(): List<UnpackedNpcType> {
        val external = loadExternalConfigs()
        val derived = loadDerivedAnims().toMutableMap()
        val configured =
            external.map { config ->
                // Anims set in `npcs.toml` always win over derived ones.
                val fallback = config.npc?.let(derived::remove)
                config
                    .copy(
                        attackAnim = config.attackAnim ?: fallback?.attack,
                        defendAnim = config.defendAnim ?: fallback?.defend,
                        deathAnim = config.deathAnim ?: fallback?.death,
                    )
                    .toCacheType()
            }
        val unconfigured = derived.map { (name, anims) -> animsOnlyType(name, anims) }
        val attributes = loadDerivedAttributes().map { (name, set) -> attributesType(name, set) }
        return configured + unconfigured + attributes
    }

    /**
     * The "type" params (undead, demon, ...) that the combat formulas read, found by
     * `tools/npc-attributes/derive_attributes.py`. Nothing else sets them, so without this none of
     * the bonuses that depend on them (salve amulet, demonbane weapons, ...) would apply. These are
     * kept apart from `npcs.toml` so they merge with whatever else an npc has configured.
     */
    private fun loadDerivedAttributes(): Map<String, List<ParamInt>> {
        val input = InputStreams.readAllBytes<DefaultNpcCacheEnricher>("npc_attributes.toml")
        val type = object : TypeReference<Map<String, Map<String, Int>>>() {}
        val map = mapper.readValue(input, type)
        val derived = mutableMapOf<String, MutableList<ParamInt>>()
        for ((section, npcs) in map) {
            val param = attributeParams[section] ?: error("Unknown npc attribute section: $section")
            for ((name, value) in npcs) {
                if (value != 0) {
                    derived.getOrPut(name, ::mutableListOf) += param
                }
            }
        }
        return derived
    }

    private fun attributesType(name: String, attributes: List<ParamInt>): UnpackedNpcType {
        val id = names[name] ?: error("Mapping with name not found: $name")
        val builder = NpcPluginBuilder(name)
        for (attribute in attributes) {
            builder.param[attribute] = 1
        }
        return builder.build(id)
    }

    /**
     * Attack, defend and death anims found by `tools/npc-anims/derive_anims.py`. Without one an npc
     * falls back to the param defaults, which are the human punch, block and death anims.
     */
    private fun loadDerivedAnims(): Map<String, DerivedAnims> {
        val input = InputStreams.readAllBytes<DefaultNpcCacheEnricher>("npc_anims.toml")
        val type = object : TypeReference<Map<String, Map<String, Int>>>() {}
        val map = mapper.readValue(input, type)
        val attack =
            map[DERIVED_ATTACK_KEY]
                ?: error("Could not extract `${DERIVED_ATTACK_KEY}` value from input.")
        val defend =
            map[DERIVED_DEFEND_KEY]
                ?: error("Could not extract `${DERIVED_DEFEND_KEY}` value from input.")
        val death =
            map[DERIVED_DEATH_KEY]
                ?: error("Could not extract `${DERIVED_DEATH_KEY}` value from input.")
        val names = attack.keys + defend.keys + death.keys
        return names.associateWith { DerivedAnims(attack[it], defend[it], death[it]) }
    }

    private fun animsOnlyType(name: String, anims: DerivedAnims): UnpackedNpcType {
        val id = names[name] ?: error("Mapping with name not found: $name")
        val builder = NpcPluginBuilder(name)
        builder.putSeq(anims.attack, params.attack_anim)
        builder.putSeq(anims.defend, params.defend_anim)
        builder.putSeq(anims.death, params.death_anim)
        return builder.build(id)
    }

    private fun loadExternalConfigs(): List<ExternalNpcConfig> {
        val input = InputStreams.readAllBytes<DefaultNpcCacheEnricher>("npcs.toml")
        val type = object : TypeReference<Map<String, List<ExternalNpcConfig>>>() {}
        val map = mapper.readValue(input, type)
        val configs =
            map[CONFIG_MAP_KEY] ?: error("Could not extract `${CONFIG_MAP_KEY}` value from input.")
        return configs
    }

    private fun ExternalNpcConfig.toCacheType(): UnpackedNpcType {
        val id = id ?: names[npc] ?: error("Mapping with name not found: $npc")
        val builder = NpcPluginBuilder(npc ?: "npc_${this.id}")
        return builder.apply(this).build(id)
    }

    private fun NpcPluginBuilder.apply(config: ExternalNpcConfig): NpcPluginBuilder {
        desc = config.examine
        respawnRate = config.respawnRate
        putSeq(config.attackAnim, params.attack_anim)
        putSeq(config.defendAnim, params.defend_anim)
        putSeq(config.deathAnim, params.death_anim)
        putSynth(config.attackSound, params.attack_sound)
        putSynth(config.defendSound, params.defend_sound)
        putSynth(config.deathSound, params.death_sound)
        putInt(config.attackMelee, params.attack_melee)
        putInt(config.defenceLight, params.defence_light)
        putInt(config.defenceStandard, params.defence_standard)
        putInt(config.defenceHeavy, params.defence_heavy)
        putInt(config.poisonImmunity, params.poison_immunity)
        putInt(config.cannonImmunity, params.cannon_immunity)
        putInt(config.thrallImmunity, params.thrall_immunity)
        putInt(config.burnImmunity, params.burn_immunity)
        putInt(config.slayerReq, params.slayer_levelrequire)
        putSlayerXpMod(config.slayerXp, params.slayer_experience)
        putCombatXpMod(config.bonusXp, params.npc_com_xp_multiplier)
        putAttackType(config.attackType, params.npc_attack_type)
        putElementalWeakness(config.elementalWeaknessType, config.elementalWeaknessPercent)
        putVenomImmunity(config.venomImmunity)
        return this
    }

    private fun NpcPluginBuilder.putSeq(anim: Int?, paramType: ParamSeq) {
        val id = anim ?: return
        val seq = seqTypes.getValue(id)
        param[paramType] = seq.toHashedType()
    }

    private fun NpcPluginBuilder.putSynth(sound: Int?, paramType: ParamSynth) {
        val id = sound ?: return
        val synth = SynthType(id, "synth_$id")
        param[paramType] = synth
    }

    private fun NpcPluginBuilder.putInt(value: Int?, paramType: ParamInt) {
        if (value != null) {
            param[paramType] = value
        }
    }

    private fun NpcPluginBuilder.putInt(value: Boolean?, paramType: ParamInt) {
        if (value != null) {
            param[paramType] = if (value) 1 else 0
        }
    }

    private fun NpcPluginBuilder.putCombatXpMod(value: Double?, paramType: ParamInt) {
        if (value != null) {
            // `bonusXp` in npc config file is represented as `+bonus%` (e.g., 7.5 for +7.5%).
            // We scale that percent by 10 and add 1000 to represent the multiplier.
            // Example: 7.5% becomes 1000 + 75 = 1075.
            param[paramType] = 1000 + round(value * 10.0).toInt()
        }
    }

    private fun NpcPluginBuilder.putSlayerXpMod(value: Double?, paramType: ParamInt) {
        if (value != null) {
            param[paramType] = (value * 10).toInt()
        }
    }

    private fun NpcPluginBuilder.putAttackType(value: String?, paramType: ParamCategory) {
        val category =
            when (value?.lowercase()) {
                "stab" -> categories.attacktype_stab
                "slash" -> categories.attacktype_slash
                "crush" -> categories.attacktype_crush
                else -> null
            }
        if (category != null) {
            param[paramType] = category
        }
    }

    private fun NpcPluginBuilder.putElementalWeakness(type: String?, percent: Int?) {
        if (type == null) {
            return
        }
        check(percent != null) { "Unexpected null `percent` value: '$name' (element=$type)" }
        val weaknessType =
            when (type.lowercase()) {
                "air" -> constants.elemental_weakness_wind
                "water" -> constants.elemental_weakness_water
                "earth" -> constants.elemental_weakness_earth
                "fire" -> constants.elemental_weakness_fire
                else -> error("Unexpected weakness type name: $type")
            }
        param[params.elemental_weakness_type] = weaknessType
        param[params.elemental_weakness_percent] = percent
    }

    private fun NpcPluginBuilder.putVenomImmunity(type: String?) {
        if (type == null) {
            return
        }
        val id =
            when (type.lowercase()) {
                "immune" -> constants.npc_venom_full_immunity
                "poisonsinstead" -> constants.npc_venom_partial_immunity
                else -> error("Unexpected venom immunity type name: $type")
            }
        param[params.venom_immunity] = id
    }

    private val attributeParams: Map<String, ParamInt>
        get() =
            mapOf(
                "undead" to params.undead,
                "demon" to params.demon,
                "demonbane_resistant" to params.demonbane_resistant,
                "shade" to params.shade,
                "leafy" to params.leafy,
                "xerician" to params.xerician,
                "rat" to params.rat,
                "tormented_demon" to params.tormented_demon,
                "amascutnpc" to params.amascutnpc,
            )

    private companion object {
        const val CONFIG_MAP_KEY: String = "config"
        const val DERIVED_ATTACK_KEY: String = "attack_anim"
        const val DERIVED_DEFEND_KEY: String = "defend_anim"
        const val DERIVED_DEATH_KEY: String = "death_anim"
    }
}

private data class DerivedAnims(val attack: Int?, val defend: Int?, val death: Int?)

private data class ExternalNpcConfig(
    val npc: String?,
    val id: Int?,
    val examine: String,
    val attackType: String?,
    val respawnRate: Int?,
    val bonusXp: Double?,
    val slayerReq: Int?,
    val slayerXp: Double?,
    val attackMelee: Int?,
    val defenceLight: Int?,
    val defenceStandard: Int?,
    val defenceHeavy: Int?,
    val elementalWeaknessType: String?,
    val elementalWeaknessPercent: Int?,
    val freezeResistance: Int?,
    val poisonImmunity: Boolean?,
    val cannonImmunity: Boolean?,
    val thrallImmunity: Boolean?,
    val burnImmunity: Boolean?,
    val venomImmunity: String?,
    val attackAnim: Int?,
    val attackSound: Int?,
    val defendAnim: Int?,
    val defendSound: Int?,
    val deathAnim: Int?,
    val deathSound: Int?,
)
