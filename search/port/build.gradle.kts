plugins {
    kotlin("jvm")
}

dependencies {
    api(project(":catalog:api"))
    api(project(":search:domain"))
    testImplementation(kotlin("test"))
    testRuntimeOnly("org.junit.platform:junit-platform-launcher:1.12.2")
}
