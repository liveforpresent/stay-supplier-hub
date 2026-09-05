plugins {
    kotlin("jvm")
}

dependencies {
    api(project(":catalog:api"))
    testImplementation(kotlin("test"))
    testRuntimeOnly("org.junit.platform:junit-platform-launcher:1.12.2")
}
