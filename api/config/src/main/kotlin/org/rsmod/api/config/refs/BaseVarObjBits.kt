@file:Suppress("SpellCheckingInspection", "unused")

package org.rsmod.api.config.refs

import org.rsmod.api.type.builders.varobjbit.VarObjBitBuilder

typealias varobjs = BaseVarObjBits

object BaseVarObjBits : VarObjBitBuilder() {
    val charges_16383 =
        build("charges_16383") {
            startBit = 0
            endBit = 13
        }

    val tumeken_charges =
        build("tumeken_charges") {
            startBit = 0
            endBit = 14
        }

    /**
     * The revenant ether stored in a revenant weapon or the bracelet of ethereum. A weapon's count
     * includes the 1,000 ether of its activation (see `RevenantEther`), so it holds at most 17,000.
     */
    val ether_charges =
        build("ether_charges") {
            startBit = 0
            endBit = 14
        }

    /** Whether a bracelet of ethereum takes in the ether that revenants it kills would drop. */
    val ether_absorb =
        build("ether_absorb") {
            startBit = 15
            endBit = 15
        }

    val stamina_charges =
        build("stamina_charges") {
            startBit = 0
            endBit = 10
        }

    val snakeboss_blowpipe_darrtype =
        build("snakeboss_blowpipe_darttype") {
            startBit = 0
            endBit = 3
        }

    val snakeboss_blowpipe_dartcount =
        build("snakeboss_blowpipe_dartcount") {
            startBit = 4
            endBit = 17
        }

    val snakeboss_blowpipe_flakes =
        build("snakeboss_blowpipe_flakes") {
            startBit = 18
            endBit = 31
        }
}
