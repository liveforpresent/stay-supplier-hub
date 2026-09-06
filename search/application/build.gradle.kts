plugins {
    kotlin("jvm")
}

dependencies {
    implementation(project(":catalog:api"))
    implementation(project(":search:domain"))
    implementation(project(":search:port"))
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.10.2")
    testImplementation(kotlin("test"))
    testRuntimeOnly("org.junit.platform:junit-platform-launcher:1.12.2")
}
