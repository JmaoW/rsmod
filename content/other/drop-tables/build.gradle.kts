plugins {
    id("base-conventions")
    id("integration-test-suite")
}

dependencies {
    implementation(libs.jackson.databind)
    implementation(libs.jackson.dataformat.toml)
    implementation(libs.jackson.module.kotlin)
    implementation(projects.api.config)
    implementation(projects.api.drops)
    implementation(projects.api.parsers.toml)
    implementation(projects.api.pluginCommons)
    implementation(projects.content.skills.slayer)
    testImplementation(projects.api.testing.testFactory)
    integrationImplementation(projects.api.death)
    integrationImplementation(projects.api.deathPlugin)
    integrationImplementation(projects.api.drops)
    integrationImplementation(projects.api.hitPlugin)
    integrationImplementation(projects.api.pluginCommons)
}
