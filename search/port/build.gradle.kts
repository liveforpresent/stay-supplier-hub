plugins {
    kotlin("jvm")
}

dependencies {
    api(project(":catalog:api"))
    api(project(":search:domain"))
}
