plugins {
    kotlin("jvm")
}

dependencies {
    implementation(project(":catalog:api"))
    implementation(project(":search:domain"))
    implementation(project(":search:port"))
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.10.2")
}
