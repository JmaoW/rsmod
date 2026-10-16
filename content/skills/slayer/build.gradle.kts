plugins {
    id("base-conventions")
    id("integration-test-suite")
}

dependencies {
    implementation(projects.api.combat.combatFormulas)
    implementation(projects.api.combat.combatScripts)
    implementation(projects.api.pluginCommons)
    integrationImplementation(libs.guice)
    integrationImplementation(projects.api.combat.combatCommons)
    integrationImplementation(projects.api.combat.combatFormulas)
    integrationImplementation(projects.api.combat.combatScripts)
    integrationImplementation(projects.api.combat.combatWeapon)
    integrationImplementation(projects.api.death)
    integrationImplementation(projects.api.hitPlugin)
    integrationImplementation(projects.api.invPlugin)
    integrationImplementation(projects.api.invtx)
    integrationImplementation(projects.api.player)
}
