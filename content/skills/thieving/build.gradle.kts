plugins {
    id("base-conventions")
    id("integration-test-suite")
}

dependencies {
    implementation(projects.api.pluginCommons)
    integrationImplementation(libs.guice)
    integrationImplementation(projects.api.hitPlugin)
    integrationImplementation(projects.api.invPlugin)
    integrationImplementation(projects.api.player)
}
