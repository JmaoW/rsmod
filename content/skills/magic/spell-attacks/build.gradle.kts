plugins {
    id("base-conventions")
    id("integration-test-suite")
}

dependencies {
    implementation(projects.api.combat.combatManager)
    implementation(projects.api.npc)
    implementation(projects.api.pluginCommons)
    implementation(projects.api.repo)
    implementation(projects.api.spells)
    integrationImplementation(libs.guice)
    integrationImplementation(projects.api.combat.combatCommons)
    integrationImplementation(projects.api.combat.combatManager)
    integrationImplementation(projects.api.hitPlugin)
    integrationImplementation(projects.api.npc)
    integrationImplementation(projects.api.player)
    integrationImplementation(projects.api.repo)
    integrationImplementation(projects.api.spells)
    integrationImplementation(projects.api.spellsRunes)
}
