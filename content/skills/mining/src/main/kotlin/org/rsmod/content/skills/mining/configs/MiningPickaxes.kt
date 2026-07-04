package org.rsmod.content.skills.mining.configs

import org.rsmod.api.config.refs.content
import org.rsmod.api.config.refs.objs
import org.rsmod.api.config.refs.params
import org.rsmod.api.config.refs.seqs
import org.rsmod.api.type.editors.obj.ObjEditor

internal object MiningPickaxes : ObjEditor() {
    init {
        standard(objs.bronze_pickaxe, levelReq = 1, anim = seqs.human_mining_bronze_pickaxe)
        standard(objs.iron_pickaxe, levelReq = 1, anim = seqs.human_mining_iron_pickaxe)
        standard(objs.steel_pickaxe, levelReq = 6, anim = seqs.human_mining_steel_pickaxe)
        standard(objs.black_pickaxe, levelReq = 11, anim = seqs.human_mining_black_pickaxe)
        standard(objs.mithril_pickaxe, levelReq = 21, anim = seqs.human_mining_mithril_pickaxe)
        standard(objs.adamant_pickaxe, levelReq = 31, anim = seqs.human_mining_adamant_pickaxe)
        standard(objs.rune_pickaxe, levelReq = 41, anim = seqs.human_mining_rune_pickaxe)
        standard(objs.gilded_pickaxe, levelReq = 41, anim = seqs.human_mining_gilded_pickaxe)
        standard(objs.dragon_pickaxe, levelReq = 61, anim = seqs.human_mining_dragon_pickaxe)
        standard(
            objs.dragon_pickaxe_upgraded,
            levelReq = 61,
            anim = seqs.human_mining_dragon_pickaxe_pretty,
        )
        standard(
            objs.dragon_pickaxe_or_trailblazer,
            levelReq = 61,
            anim = seqs.human_mining_trailblazer_pickaxe_no_infernal,
        )
        standard(objs.infernal_pickaxe, levelReq = 61, anim = seqs.human_mining_infernal_pickaxe)
        standard(objs.infernal_pickaxe_uncharged, levelReq = 61, anim = seqs.human_mining_infernal_pickaxe)
        standard(
            objs.infernal_pickaxe_or,
            levelReq = 61,
            anim = seqs.human_mining_trailblazer_pickaxe,
        )
        standard(
            objs.infernal_pickaxe_or_uncharged,
            levelReq = 61,
            anim = seqs.human_mining_trailblazer_pickaxe_no_infernal,
        )
        standard(objs.third_age_pickaxe, levelReq = 61, anim = seqs.human_mining_3a_pickaxe)
        standard(objs.crystal_pickaxe, levelReq = 71, anim = seqs.human_mining_crystal_pickaxe)
        standard(
            objs.crystal_pickaxe_inactive,
            levelReq = 71,
            anim = seqs.human_mining_crystal_pickaxe,
        )
        standard(
            objs.trailblazer_reloaded_pickaxe,
            levelReq = 61,
            anim = seqs.human_mining_trailblazer_reloaded_pickaxe,
        )
        standard(
            objs.trailblazer_reloaded_pickaxe_empty,
            levelReq = 61,
            anim = seqs.human_mining_trailblazer_reloaded_pickaxe_no_infernal,
        )
        standard(
            objs.trailblazer_reloaded_pickaxe_no_infernal,
            levelReq = 61,
            anim = seqs.human_mining_trailblazer_reloaded_pickaxe_no_infernal,
        )
        standard(
            objs.league_trailblazer_pickaxe,
            levelReq = 61,
            anim = seqs.human_mining_league_trailblazer_pickaxe,
        )
        standard(objs.zalcano_pickaxe, levelReq = 61, anim = seqs.human_mining_zalcano_zalcano_pickaxe)
        standard(objs.gauntlet_pickaxe, levelReq = 61, anim = seqs.human_mining_gauntlet_pickaxe_hm)
        standard(objs.gauntlet_pickaxe_hm, levelReq = 61, anim = seqs.human_mining_gauntlet_pickaxe_hm)
    }

    private fun standard(type: org.rsmod.game.type.obj.ObjType, levelReq: Int, anim: org.rsmod.game.type.seq.SeqType) {
        edit(type) {
            contentGroup = content.mining_pickaxe
            param[params.levelrequire] = levelReq
            param[params.skill_anim] = anim
        }
    }
}
