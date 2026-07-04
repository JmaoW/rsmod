package org.rsmod.content.skills.mining

import org.rsmod.api.config.refs.stats
import org.rsmod.api.stats.levelmod.InvisibleLevelMod
import org.rsmod.game.entity.Player

class MiningLevelBoosts : InvisibleLevelMod(stats.mining) {
    override fun Player.calculateBoost(): Int {
        // TODO: Add prospector/Varrock armour bonuses once mining gear support is wired up.
        return 0
    }
}
