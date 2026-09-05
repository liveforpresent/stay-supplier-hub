plugins {
    kotlin("jvm")
    kotlin("plugin.spring")
    id("org.springframework.boot")
    id("io.spring.dependency-management")
}

dependencies {
    implementation(project(":catalog:api"))
    implementation(project(":catalog:application"))
    implementation(project(":catalog:adapter:persistence"))
    implementation(project(":search:application"))
    implementation(project(":search:adapter:web"))
    implementation(project(":integration:supplier-a"))
    implementation(project(":integration:supplier-b"))
    implementation(project(":shared:infrastructure"))
    implementation("org.springframework.boot:spring-boot-starter-actuator")
    implementation("org.springframework.boot:spring-boot-starter-webmvc")
    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}
