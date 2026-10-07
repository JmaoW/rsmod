plugins {
    id("base-conventions")
    id("integration-test-suite")
}

dependencies {
    implementation(projects.api.pluginCommons)
    integrationImplementation(projects.api.gameProcess)
    integrationImplementation(projects.api.player)
}
