plugins {
    id("base-conventions")
    id("integration-test-suite")
}

dependencies {
    implementation(projects.content.skills.slayer)
    implementation(projects.api.pluginCommons)
    integrationImplementation(projects.content.skills.slayer)
    integrationImplementation(projects.api.invPlugin)
    integrationImplementation(projects.api.player)
}
