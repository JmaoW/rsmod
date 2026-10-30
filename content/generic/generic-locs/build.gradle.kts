plugins {
    id("base-conventions")
    id("integration-test-suite")
}

dependencies {
    implementation(projects.api.pluginCommons)
    implementation(projects.content.interfaces.bank)
    integrationImplementation(projects.content.interfaces.bank)
    integrationImplementation(projects.api.invPlugin)
}
