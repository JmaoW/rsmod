package org.rsmod.content.areas.misc.isleofsouls.configs

import org.rsmod.api.type.builders.area.AreaBuilder
import org.rsmod.api.type.refs.area.AreaReferences

typealias isle_areas = IsleOfSoulsAreas

object IsleOfSoulsAreas : AreaReferences() {
    val isle_of_souls = find("isle_of_souls")
}

internal object IsleOfSoulsAreaBuilder : AreaBuilder() {
    init {
        build("isle_of_souls")
    }
}
