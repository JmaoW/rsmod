plugins {
    id("base-conventions")
}

kotlin {
    explicitApi()
}

dependencies {
    implementation(libs.guice)
    implementation(projects.api.random)
    implementation(projects.engine.events)
    implementation(projects.engine.game)
    testImplementation(projects.api.testing.testFactory)
}
