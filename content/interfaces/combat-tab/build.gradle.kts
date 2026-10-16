plugins {
    id("base-conventions")
    id("integration-test-suite")
}

dependencies {
    implementation(libs.fastutil)
    implementation(projects.api.combat.combatWeapon)
    implementation(projects.api.pluginCommons)
    implementation(projects.api.scriptAdvanced)
    implementation(projects.api.specials)
    implementation(projects.api.spells)
    implementation(projects.api.spellsAutocast)

    integrationImplementation(libs.guice)
    integrationImplementation(projects.engine.annotations)
    integrationImplementation(projects.api.combat.combatCommons)
    integrationImplementation(projects.api.combat.combatManager)
    integrationImplementation(projects.api.player)
    integrationImplementation(projects.api.spells)
    integrationImplementation(projects.api.spellsAutocast)
    integrationImplementation(projects.api.spellsRunes)
}
