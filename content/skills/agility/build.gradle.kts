plugins {
    id("base-conventions")
    id("integration-test-suite")
}

dependencies {
    implementation(libs.fastutil)
    implementation(projects.api.pluginCommons)
    integrationImplementation(libs.fastutil)
    integrationImplementation(libs.guice)
    integrationImplementation(projects.api.hitPlugin)
    integrationImplementation(projects.api.invPlugin)
    integrationImplementation(projects.api.player)
    integrationImplementation(projects.api.pluginCommons)
    integrationImplementation(projects.api.registry)
    integrationImplementation(projects.api.shops)
    integrationImplementation(projects.api.statsPlugin)
}
