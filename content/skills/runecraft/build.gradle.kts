plugins {
    id("base-conventions")
    id("integration-test-suite")
}

dependencies {
    implementation(projects.api.pluginCommons)
    implementation(projects.content.skills.mining)
    integrationImplementation(libs.guice)
    integrationImplementation(projects.api.invPlugin)
    integrationImplementation(projects.api.player)
    integrationImplementation(projects.api.shops)
    integrationImplementation(projects.content.skills.mining)
}
