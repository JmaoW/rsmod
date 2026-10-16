package org.rsmod.content.skills.agility.configs

/**
 * The courses of the isle. There are none: the agility park's six courses were taken out, and only
 * the shortcuts and the Brimhaven Agility Arena are left. The course machinery in the skill still
 * reads this list, so a course added here is placed and run as before.
 */
object Courses {
    val all: List<Course> = emptyList()

    fun byKey(key: String): Course? = all.firstOrNull { it.key == key }
}
