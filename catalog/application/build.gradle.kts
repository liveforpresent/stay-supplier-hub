plugins {
    kotlin("jvm")
}

dependencies {
    implementation(project(":catalog:api"))
    implementation(project(":catalog:domain"))
    implementation(project(":catalog:port"))
}
