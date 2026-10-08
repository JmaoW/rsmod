plugins {
    id("base-conventions")
    id("integration-test-suite")
}

dependencies {
    implementation(projects.api.combat.combatMastery)
    implementation(projects.api.pluginCommons)
    integrationImplementation(projects.api.combat.combatMastery)
    integrationImplementation(projects.api.invPlugin)
    integrationImplementation(projects.api.player)
    integrationImplementation(projects.api.statsPlugin)
}
