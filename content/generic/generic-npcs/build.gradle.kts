plugins {
    id("base-conventions")
    id("integration-test-suite")
}

dependencies {
    implementation(projects.api.pluginCommons)
    implementation(projects.content.interfaces.bank)
    integrationImplementation(libs.guice)
    integrationImplementation(projects.api.hitPlugin)
    integrationImplementation(projects.api.player)
    integrationImplementation(projects.api.repo)
}
