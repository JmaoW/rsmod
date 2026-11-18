package org.rsmod.content.areas.misc.isleofsouls.configs

import org.rsmod.api.type.editors.struct.StructEditor
import org.rsmod.api.type.refs.param.ParamReferences
import org.rsmod.api.type.refs.struct.StructReferences
import org.rsmod.game.type.struct.StructType

/** The league task struct params the client reads (see [IsleOfSoulsTaskStructs]). */
internal object IsleOfSoulsTaskParams : ParamReferences() {
    val name = find<String>("league_task_name")
    val description = find<String>("league_task_desc")
    val area = find<Int>("league_task_area")
}

/**
 * Vanilla league task structs that [IsleOfSoulsTaskEdits] rewrites. Each needs a line in
 * `.data/symbols/struct.sym` named `league_task_<task id>` with the struct's id.
 */
internal object IsleOfSoulsTaskEditStructs : StructReferences() {
    fun task(taskId: Int): StructType = find("league_task_$taskId")
}

/**
 * The new text for league tasks that named a place the isle does not have, or that asked for one:
 * "Mine Some Runite Ore in the Wilderness" is now "Mine Some Runite Ore" and completes anywhere.
 * The server names in `LeagueTaskList` must be kept in step with [Edit.name].
 */
object IsleOfSoulsTaskTexts {
    /** A rewrite of one task. A `null` [name] or [description] keeps what the cache has. */
    class Edit(val taskId: Int, val name: String?, val description: String?)

    val edits =
        listOf(
            // Tasks that already complete anywhere, whose text still named a place.
            Edit(700, "Fletch 100 Maple Longbow (u)", "Fletch 100 Maple Longbow (u)"),
            Edit(981, "Craft an Air Battlestaff", "Craft an Air Battlestaff"),
            Edit(1572, "Craft some Pottery", "Craft some Pottery"),
            Edit(644, null, "Catch a Karambwanji"),
            Edit(646, null, "Catch 50 Karambwan"),
            Edit(696, null, "Catch a Monkfish"),
            Edit(1548, null, "Catch a Salmon"),
            Edit(1362, null, "Fletch a Redwood Shield"),
            // Kill tasks that named a place.
            // Slayer tasks that named a place.
            Edit(917, null, null),
            Edit(466, "Defeat the Lesser Demon", "Defeat the Lesser Demon"),
            Edit(528, "Defeat a Waterfiend", "Defeat a Waterfiend"),
            Edit(583, "Defeat a Rock Crab", "Defeat a Rock Crab"),
            Edit(588, "Defeat a Cockatrice", "Defeat a Cockatrice"),
            Edit(592, "Defeat a Pyrefiend", "Defeat a Pyrefiend"),
            Edit(721, "Defeat a Bloodveld", "Defeat a Bloodveld"),
            Edit(724, "Defeat a Fire Giant", "Defeat a Fire Giant"),
            Edit(920, "Defeat a Werewolf", "Defeat a Werewolf"),
            Edit(994, "Defeat a Chaos Dwarf", "Defeat a Chaos Dwarf"),
            Edit(1594, "Kill a Barbarian", "Kill a Barbarian"),
            Edit(1715, "Kill a Guard", "Kill a Guard"),
            Edit(1718, "Kill an Icefiend", "Kill an Icefiend"),
            Edit(1798, "Kill a Snake", "Kill a Snake"),
            Edit(537, "Defeat a Kurask", "Defeat a Kurask"),
            Edit(598, "Defeat a Dagannoth", "Defeat a Dagannoth"),
            Edit(606, "Defeat a Turoth", "Defeat a Turoth"),
            Edit(611, "Defeat a Jelly", "Defeat a Jelly"),
            Edit(658, "Defeat a Greater Demon", "Defeat a Greater Demon"),
            Edit(670, "Defeat a Steel Dragon", "Defeat a Steel Dragon"),
            Edit(803, "Defeat a Blue Dragon", "Defeat a Blue Dragon"),
            Edit(805, "Defeat a Black Demon", "Defeat a Black Demon"),
            Edit(918, "Defeat a Gargoyle", "Defeat a Gargoyle"),
            Edit(926, "Defeat an Abyssal Demon", "Defeat an Abyssal Demon"),
            Edit(986, "Defeat a Lava Dragon", "Defeat a Lava Dragon"),
            Edit(998, "Defeat a Green Dragon", "Defeat a Green Dragon"),
            Edit(1387, "Kill a Black Dragon", "Kill a Black Dragon"),
            Edit(542, "Defeat a Dark Beast", "Defeat a Dark Beast"),
            Edit(555, "Defeat a Nechryael", "Defeat a Nechryael"),
            // Tasks that were locked behind a place.
            Edit(985, "Mine Some Runite Ore", "Mine some Runite Ore"),
            Edit(1379, "Mine 200 Runite Ore", "Mine 200 Runite Ore"),
            Edit(1378, "Mine 200 Soft Clay", "Mine 200 Soft Clay"),
            Edit(1578, "Mine 25 Gold Rocks", "Mine 25 gold rocks"),
            Edit(1571, "Mine 15 Granite", "Mine 15 Granite"),
            Edit(860, null, "Mine 30 chunks of Granite"),
            Edit(1694, "Mine 20 Mithril Ore", "Mine 20 Mithril Ore"),
            Edit(1385, "Chop 100 Teak Logs", "Chop 100 Teak Logs"),
            Edit(1579, "Chop 100 Maple Logs", "Chop 100 Maple Logs"),
            Edit(1765, "Chop 50 Magic Logs", "Chop 50 Magic Logs"),
            Edit(1719, "Chop 20 Magic Logs", "Chop 20 Magic Logs"),
            Edit(1607, "Chop 15 Yew Logs", "Chop 15 Yew Logs"),
            Edit(1587, "Cook 20 Sharks", "Cook 20 Sharks"),
            Edit(514, "Cook a Rabbit", "Cook a Rabbit"),
            Edit(450, "Cook Some Food on a Range", "Cook some food on a Range"),
            Edit(1592, "Make 50 Oak Planks", "Make 50 Oak Planks"),
            Edit(972, null, "Catch 100 Dark Crabs"),
            // Runecraft tasks that named an altar's region.
            Edit(774, null, null),
            Edit(854, null, null),
            Edit(462, null, null),
            Edit(463, null, "Craft 50 Cosmic Runes"),
            Edit(573, null, null),
            Edit(648, null, null),
            Edit(775, null, null),
            Edit(855, null, null),
            Edit(982, null, null),
            Edit(1613, null, null),
            Edit(525, null, null),
            Edit(776, null, null),
            Edit(1603, "Craft 100 Blood Runes", "Craft 100 Blood Runes"),
            // Magic tasks that named a place or an unlock the isle does not have.
            Edit(565, null, null),
            Edit(178, null, "Cast the Kourend Castle Teleport spell"),
            Edit(1654, null, null),
            Edit(868, null, null),
            Edit(866, null, null),
            Edit(869, null, null),
            Edit(872, null, null),
            Edit(1023, null, "Cast the Saradomin Strike spell"),
            Edit(1024, null, "Cast the Claws of Guthix spell"),
            Edit(1025, null, "Cast the Flames of Zamorak spell"),
            Edit(1124, null, null),
            Edit(566, null, null),
            Edit(568, null, null),
            // Thieving tasks that named a place.
            Edit(457, "Steal From a Tea Stall", "Steal from a Tea Stall"),
            Edit(76, null, "Steal a Golovanova fruit top from a fruit stall"),
            Edit(454, null, "Pickpocket any HAM member"),
            Edit(455, "Pickpocket a Guard", "Pickpocket any Guard"),
            Edit(458, null, null),
            Edit(684, "Pickpocket a Knight 50 Times", "Pickpocket a Knight 50 times"),
            Edit(850, "Pickpocket a Bandit", "Pickpocket a Bandit"),
            Edit(1699, null, null),
            Edit(1700, null, null),
            Edit(1768, "Thieve a Diamond from a Gem Stall", "Thieve a Diamond from a Gem Stall"),
            Edit(1769, "Thieve a Tiara from a Silver Stall", "Thieve a Tiara from a Silver Stall"),
            Edit(1817, null, "Steal from a Fur Stall"),
            Edit(1818, null, "Steal from a Silver Stall"),
            Edit(1819, null, "Steal from a Spice Stall"),
            Edit(1834, null, null),
            Edit(1835, null, null),
            Edit(1844, null, "Steal a Chisel from a crafting stall"),
            Edit(526, null, null),
            Edit(685, null, "Pickpocket any Hero"),
            Edit(851, null, "Pickpocket a Menaphite Thug 50 times"),
            Edit(911, null, null),
            Edit(1541, null, null),
            // Hunter tasks that named a place.
            Edit(1369, null, null),
            Edit(439, "Dismantle 20 Filled Bird Houses", "Dismantle 20 filled Bird Houses"),
            Edit(445, "Catch 50 Implings", "Catch 50 Implings"),
            Edit(519, null, "Catch a Crystal Impling"),
            Edit(691, "Trap a Spined Larupia", "Trap a Spined Larupia"),
            Edit(694, null, "Catch a Red Salamander"),
            Edit(859, null, "Catch 30 Orange Salamanders"),
            Edit(912, "Catch a Swamp Lizard", "Catch a Swamp Lizard"),
            Edit(976, null, "Catch a Black Salamander"),
            Edit(977, null, "Catch a Black Chinchompa"),
            Edit(1833, null, null),
            Edit(440, "Set up a Magic Bird House", "Set up a Magic Bird House"),
            Edit(578, null, null),
            Edit(978, null, null),
            Edit(979, null, null),
            Edit(980, null, null),
            Edit(1697, null, null),
            Edit(1513, null, "Receive a Dragonstone Amulet from a Crystal Impling"),
            Edit(692, "Catch 150 Red Chinchompas", "Catch 150 Red Chinchompas"),
            Edit(1377, "Catch 300 Red Chinchompas", "Catch 300 Red Chinchompas"),
            Edit(1696, null, null),
            Edit(1720, null, null),
            Edit(1722, null, null),
            // Agility tasks that named a place.
            Edit(769, null, null),
            Edit(638, null, "Receive an Agility Arena Ticket from the Agility Arena"),
            Edit(641, null, null),
            Edit(1576, null, "Equip a Pirate Hook from the Agility Arena"),
            // Zulrah's tasks named the Poison Waste.
            Edit(540, null, "Defeat Zulrah"),
            Edit(553, null, "Defeat Zulrah 50 times"),
            Edit(554, null, "Defeat Zulrah 150 times"),
            Edit(1397, null, "Defeat Zulrah 300 times"),
            Edit(1390, null, null),
            // Scurrius's tasks named Misthalin.
            Edit(1648, null, null),
            Edit(1649, null, null),
            Edit(1650, null, null),
            // The crystal tasks were set in Tirannwn; they complete anywhere.
            Edit(530, null, null),
            Edit(531, null, null),
            Edit(546, null, null),
            Edit(549, null, null),
            Edit(1512, null, null),
            Edit(1396, null, null),
            // The Gauntlet's tasks named Prifddinas.
            Edit(534, null, "Complete the Gauntlet"),
            Edit(535, null, "Complete the Corrupted Gauntlet"),
            Edit(547, null, "Complete the Corrupted Gauntlet 50 times"),
            Edit(548, null, "Complete the Corrupted Gauntlet 150 times"),
            // The Kalphite Queen's tasks named the Kalphite Lair and the Kharidian Desert.
            Edit(864, null, "Defeat the Kalphite Queen"),
            Edit(874, null, "Defeat the Kalphite Queen 50 times"),
            Edit(875, null, "Defeat the Kalphite Queen 150 times"),
            // The Wilderness bosses' tasks named the Wilderness.
            Edit(988, null, "Defeat Venenatis"),
            Edit(992, null, "Defeat Callisto"),
            // The King Black Dragon's task named the Wilderness.
            Edit(1000, null, "Defeat the King Black Dragon"),
            // The Tombs of Amascut tasks named the Kharidian Desert.
            Edit(1186, null, null),
            Edit(1187, null, null),
            Edit(1188, null, null),
            Edit(1189, null, null),
            Edit(1228, null, null),
            Edit(1230, null, null),
            Edit(1231, null, null),
            Edit(1232, null, null),
            Edit(1233, null, null),
            Edit(1236, null, null),
            // The Theatre of Blood's tasks named Morytania.
            Edit(940, null, null),
            Edit(941, null, null),
            Edit(942, null, null),
            Edit(943, null, null),
            Edit(944, null, null),
            Edit(945, null, null),
            Edit(946, null, null),
            Edit(947, null, null),
            Edit(948, null, null),
            Edit(1135, null, null),
            Edit(1136, null, null),
            Edit(996, null, "Defeat Vet'ion"),
            Edit(1003, "Equip a Dragon 2-Handed Sword", "Equip a Dragon 2-Handed Sword"),
            Edit(989, null, null),
            Edit(993, null, null),
            Edit(997, null, null),
            Edit(1256, null, null),
            // The revenant weapons' tasks named the Wilderness.
            Edit(1014, null, null),
            Edit(697, null, null),
            Edit(910, null, null),
            Edit(1018, null, null),
            Edit(1257, null, null),
            Edit(1258, null, null),
            Edit(1259, null, null),
            Edit(1019, null, null),
            Edit(1398, null, null),
            Edit(1399, null, null),
            Edit(1400, null, null),
            Edit(1401, null, null),
            Edit(1402, null, null),
            Edit(1403, null, null),
            Edit(1404, null, null),
            Edit(1405, null, null),
            Edit(1406, null, null),
            // Obor and Bryophyta named the Edgeville Dungeon and the Varrock Sewers.
            Edit(472, "Defeat Obor", "Defeat Obor"),
            Edit(476, "Defeat Bryophyta", "Defeat Bryophyta"),
            Edit(477, null, null),
            // The Dagannoth Kings' tasks named the Waterbirth Island Dungeon and the Fremennik
            // Province; their rings' tasks were tied to the province.
            Edit(596, null, "Defeat all three of the Dagannoth Kings without leaving their lair"),
            Edit(613, null, "Defeat all three of the Dagannoth Kings 50 times"),
            Edit(614, null, "Defeat all three of the Dagannoth Kings 150 times"),
            Edit(615, null, "Defeat all three of the Dagannoth Kings 300 times"),
            Edit(597, null, null),
            Edit(599, null, null),
            Edit(601, null, null),
            Edit(604, null, null),
            Edit(607, null, null),
            // Vorkath's tasks named Ungael and the Fremennik Province.
            Edit(585, null, "Defeat Vorkath"),
            Edit(608, null, "Defeat Vorkath 50 times"),
            Edit(609, null, "Defeat Vorkath 150 times"),
            Edit(610, null, "Defeat Vorkath 300 times"),
            Edit(
                602,
                null,
                "Defeat Vorkath 5 times in a row without taking any damage from his special " +
                    "attacks and without leaving his crater. Failing this task requires you to " +
                    "leave the crater to try again",
            ),
            Edit(605, null, "Defeat Vorkath 15 times without leaving his crater"),
        )
}

/**
 * Rewrites the vanilla league task structs listed in [IsleOfSoulsTaskTexts]: the name and
 * description, and the area (param `1017`), which is set to `0` as it is for tasks that are not
 * tied to an area.
 *
 * The client reads these from the cache, so this needs `./gradlew packCache` to reach it.
 */
internal object IsleOfSoulsTaskEdits : StructEditor() {
    private const val AREA_GLOBAL = 0

    init {
        for (task in IsleOfSoulsTaskTexts.edits) {
            edit(IsleOfSoulsTaskEditStructs.task(task.taskId)) {
                task.name?.let { param[IsleOfSoulsTaskParams.name] = it }
                task.description?.let { param[IsleOfSoulsTaskParams.description] = it }
                param[IsleOfSoulsTaskParams.area] = AREA_GLOBAL
            }
        }
    }
}
