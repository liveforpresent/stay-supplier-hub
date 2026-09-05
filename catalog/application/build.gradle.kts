plugins {
    kotlin("jvm")
}

dependencies {
    implementation(project(":catalog:api"))
    implementation(project(":catalog:domain"))
    implementation(project(":catalog:port"))
    testImplementation(kotlin("test"))
    testRuntimeOnly("org.junit.platform:junit-platform-launcher:1.12.2")
}
