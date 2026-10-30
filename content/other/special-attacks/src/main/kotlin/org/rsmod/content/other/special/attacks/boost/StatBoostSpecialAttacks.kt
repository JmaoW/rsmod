package org.rsmod.content.other.special.attacks.boost

import jakarta.inject.Inject
import org.rsmod.api.config.constants
import org.rsmod.api.config.refs.objs
import org.rsmod.api.config.refs.spotanims
import org.rsmod.api.config.refs.stats
import org.rsmod.api.config.refs.synths
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.repo.world.WorldRepository
import org.rsmod.api.specials.SpecialAttackManager
import org.rsmod.api.specials.SpecialAttackMap
import org.rsmod.api.specials.SpecialAttackRepository
import org.rsmod.content.other.special.attacks.configs.sobjs
import org.rsmod.content.other.special.attacks.configs.special_seqs
import org.rsmod.content.other.special.attacks.configs.special_spots
import org.rsmod.game.type.obj.ObjType
import org.rsmod.game.type.seq.SeqType
import org.rsmod.game.type.spot.SpotanimType

class StatBoostSpecialAttacks @Inject constructor(private val worldRepo: WorldRepository) :
    SpecialAttackMap {
    override fun SpecialAttackRepository.register(manager: SpecialAttackManager) {
        registerInstant(objs.dragon_axe, ::lumberUpRed)
        registerInstant(objs.dragon_axe_or, ::lumberUpRed)
        registerInstant(objs.third_age_axe, ::lumberUpSilver)
        registerInstant(objs.infernal_axe, ::lumberUpRed)
        registerInstant(objs.infernal_axe_or, ::lumberUpRed)
        registerInstant(objs.crystal_axe, ::lumberUpSilver)

        registerInstant(objs.dragon_harpoon, ::fishstabberDragonHarpoon)
        registerInstant(objs.dragon_harpoon_or, ::fishstabberDragonHarpoonOr)
        registerInstant(objs.infernal_harpoon, ::fishstabberInfernalHarpoon)
        registerInstant(objs.infernal_harpoon_or, ::fishstabberInfernalHarpoonOr)
        registerInstant(objs.infernal_harpoon_or_uncharged, ::fishstabberInfernalHarpoonOr)
        registerInstant(objs.crystal_harpoon, ::fishstabberCrystalHarpoon)

        registerInstant(objs.dragon_pickaxe, ::rockKnockerDragonPickaxe)
        registerInstant(objs.dragon_pickaxe_or_zalcano, ::rockKnockerDragonPickaxeOrZalcano)
        registerInstant(objs.dragon_pickaxe_or_trailblazer, ::rockKnockerDragonPickaxeOrTrailblazer)
        registerInstant(objs.dragon_pickaxe_upgraded, ::rockKnockerDragonPickaxeUpgraded)
        registerInstant(objs.infernal_pickaxe, ::rockKnockerInfernalPickaxe)
        registerInstant(objs.infernal_pickaxe_uncharged, ::rockKnockerInfernalPickaxe)
        registerInstant(objs.infernal_pickaxe_or, ::rockKnockerInfernalPickaxeOr)
        registerInstant(objs.infernal_pickaxe_or_uncharged, ::rockKnockerInfernalPickaxeOr)
        registerInstant(objs.third_age_pickaxe, ::rockKnockerThirdAgePickaxe)
        registerInstant(objs.crystal_pickaxe, ::rockKnockerCrystalPickaxe)

        registerVariants()
    }

    /**
     * The tools that share the special attack of a tool above: the two-handed axes, the empty and
     * inactive versions, and the Trailblazer tools (which boost like their infernal originals).
     */
    private fun SpecialAttackRepository.registerVariants() {
        // Some of these objs are already registered above under the names the rest of the game
        // calls them (`dragon_axe_or` is `trailblazer_axe_no_infernal`), so a second registration
        // is skipped.
        fun register(obj: ObjType, special: (ProtectedAccess) -> Boolean) {
            try {
                registerInstant(obj) { special(this) }
            } catch (_: IllegalStateException) {
                // Already registered.
            }
        }

        register(sobjs.dragon_axe_2h, ::lumberUpRed)
        register(sobjs.infernal_axe_empty, ::lumberUpRed)
        register(sobjs.crystal_axe_inactive, ::lumberUpSilver)
        register(sobjs.crystal_axe_2h, ::lumberUpSilver)
        register(sobjs.crystal_axe_2h_inactive, ::lumberUpSilver)
        register(sobjs._3a_axe_2h, ::lumberUpSilver)

        register(sobjs.infernal_harpoon_empty, ::fishstabberInfernalHarpoon)
        register(sobjs.crystal_harpoon_inactive, ::fishstabberCrystalHarpoon)
        register(sobjs.crystal_pickaxe_inactive, ::rockKnockerCrystalPickaxe)

        for (axe in
            listOf(
                sobjs.league_trailblazer_axe,
                sobjs.trailblazer_axe,
                sobjs.trailblazer_axe_empty,
                sobjs.trailblazer_axe_no_infernal,
                sobjs.trailblazer_reloaded_axe,
                sobjs.trailblazer_reloaded_axe_empty,
                sobjs.trailblazer_reloaded_axe_no_infernal,
            )) {
            register(axe, ::lumberUpRed)
        }
        for (harpoon in
            listOf(
                sobjs.league_trailblazer_harpoon,
                sobjs.trailblazer_harpoon,
                sobjs.trailblazer_harpoon_empty,
                sobjs.trailblazer_harpoon_no_infernal,
                sobjs.trailblazer_reloaded_harpoon,
                sobjs.trailblazer_reloaded_harpoon_empty,
                sobjs.trailblazer_reloaded_harpoon_no_infernal,
            )) {
            register(harpoon, ::fishstabberInfernalHarpoonOr)
        }
        for (pickaxe in
            listOf(
                sobjs.league_trailblazer_pickaxe,
                sobjs.trailblazer_pickaxe,
                sobjs.trailblazer_pickaxe_empty,
                sobjs.trailblazer_pickaxe_no_infernal,
                sobjs.trailblazer_reloaded_pickaxe,
                sobjs.trailblazer_reloaded_pickaxe_empty,
                sobjs.trailblazer_reloaded_pickaxe_no_infernal,
            )) {
            register(pickaxe, ::rockKnockerInfernalPickaxeOr)
        }
    }

    private fun lumberUpRed(access: ProtectedAccess): Boolean {
        return access.lumberUp(special_spots.lumber_up_red)
    }

    private fun lumberUpSilver(access: ProtectedAccess): Boolean {
        return access.lumberUp(special_spots.lumber_up_silver)
    }

    private fun ProtectedAccess.lumberUp(spot: SpotanimType): Boolean {
        statBoost(stats.woodcutting, constant = 3, percent = 0)
        say("Chop chop!")
        anim(special_seqs.lumber_up)
        spotanim(spot, height = 96, slot = constants.spotanim_slot_combat)
        soundArea(worldRepo, coords, synths.clobber, radius = 1)
        return true
    }

    private fun fishstabberDragonHarpoon(access: ProtectedAccess): Boolean {
        return access.fishstabber(
            special_seqs.fishstabber_dragon_harpoon,
            spotanims.sp_attackglow_red,
        )
    }

    private fun fishstabberDragonHarpoonOr(access: ProtectedAccess): Boolean {
        // Uses the same seq as Infernal harpoon (or).
        return access.fishstabber(
            special_seqs.fishstabber_infernal_harpoon_or,
            spotanims.sp_attackglow_red,
        )
    }

    private fun fishstabberInfernalHarpoon(access: ProtectedAccess): Boolean {
        return access.fishstabber(
            special_seqs.fishstabber_infernal_harpoon,
            spotanims.sp_attackglow_red,
        )
    }

    private fun fishstabberInfernalHarpoonOr(access: ProtectedAccess): Boolean {
        return access.fishstabber(
            special_seqs.fishstabber_infernal_harpoon_or,
            spotanims.sp_attackglow_red,
        )
    }

    private fun fishstabberCrystalHarpoon(access: ProtectedAccess): Boolean {
        return access.fishstabber(
            special_seqs.fishstabber_crystal_harpoon,
            special_spots.fishstabber_silver,
        )
    }

    private fun ProtectedAccess.fishstabber(seq: SeqType, spot: SpotanimType): Boolean {
        statBoost(stats.fishing, constant = 3, percent = 0)
        say("Here fishy fishies!")
        anim(seq)
        spotanim(spot)
        soundArea(worldRepo, coords, synths.rampage, radius = 1)
        return true
    }

    private fun rockKnockerDragonPickaxe(access: ProtectedAccess): Boolean {
        return access.rockKnocker(special_seqs.rock_knocker_dragon_pickaxe)
    }

    private fun rockKnockerDragonPickaxeOrTrailblazer(access: ProtectedAccess): Boolean {
        return access.rockKnocker(special_seqs.rock_knocker_dragon_pickaxe_or_trailblazer)
    }

    private fun rockKnockerDragonPickaxeOrZalcano(access: ProtectedAccess): Boolean {
        return access.rockKnocker(special_seqs.rock_knocker_dragon_pickaxe_or_zalcano)
    }

    private fun rockKnockerDragonPickaxeUpgraded(access: ProtectedAccess): Boolean {
        return access.rockKnocker(special_seqs.rock_knocker_dragon_pickaxe_upgraded)
    }

    private fun rockKnockerInfernalPickaxe(access: ProtectedAccess): Boolean {
        return access.rockKnocker(special_seqs.rock_knocker_infernal_pickaxe)
    }

    private fun rockKnockerInfernalPickaxeOr(access: ProtectedAccess): Boolean {
        // Uses same seq as Dragon pickaxe (or).
        return access.rockKnocker(special_seqs.rock_knocker_dragon_pickaxe_or_trailblazer)
    }

    private fun rockKnockerThirdAgePickaxe(access: ProtectedAccess): Boolean {
        return access.rockKnocker(special_seqs.rock_knocker_3rd_age_pickaxe)
    }

    private fun rockKnockerCrystalPickaxe(access: ProtectedAccess): Boolean {
        return access.rockKnocker(special_seqs.rock_knocker_crystal_pickaxe)
    }

    private fun ProtectedAccess.rockKnocker(seq: SeqType): Boolean {
        statBoost(stats.mining, constant = 3, percent = 0)
        say("Smashing!")
        anim(seq)
        soundArea(worldRepo, coords, synths.found_gem, radius = 1)
        return true
    }
}
