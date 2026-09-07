import java.io.File
import java.net.Socket
import java.util.concurrent.atomic.AtomicReference

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
    implementation(project(":catalog:port"))
    implementation(project(":search:port"))
    implementation(project(":search:application"))
    implementation(project(":search:adapter:web"))
    implementation(project(":integration:supplier-a"))
    implementation(project(":integration:supplier-b"))
    implementation(project(":shared:infrastructure"))
    implementation("org.springframework.boot:spring-boot-starter-actuator")
    implementation("org.springframework.boot:spring-boot-starter-webmvc")
    implementation("org.springframework.boot:spring-boot-starter-webclient")
    implementation("com.fasterxml.jackson.module:jackson-module-kotlin")
    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testImplementation("org.springframework.boot:spring-boot-starter-data-jpa")
    testImplementation(project(":catalog:domain"))
    testImplementation(project(":catalog:port"))
    testImplementation("org.testcontainers:junit-jupiter")
    testImplementation("org.testcontainers:postgresql")
    testImplementation("io.kotest:kotest-runner-junit5:6.1.0")
    testImplementation("io.kotest:kotest-assertions-core:6.1.0")
    testImplementation(kotlin("test"))
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

val e2eMockSupplierPort = 19090
val e2eMockSupplierProcess = AtomicReference<Process?>()

val stopE2eMockSupplier = tasks.register("stopE2eMockSupplier") {
    doLast {
        e2eMockSupplierProcess.getAndSet(null)?.destroyForcibly()
    }
}

tasks.register<Test>("e2eTest") {
    description = "Runs the app E2E suite against a separate Mock Supplier process."
    group = "verification"
    testClassesDirs = sourceSets.test.get().output.classesDirs
    classpath = sourceSets.test.get().runtimeClasspath
    filter {
        includeTestsMatching("com.staysupplierhub.SearchEndToEndTest")
    }
    dependsOn(":mock-supplier:bootJar")
    finalizedBy(stopE2eMockSupplier)
    systemProperty("e2e.mock-supplier.base-url", "http://localhost:$e2eMockSupplierPort")

    doFirst {
        val mockSupplierJar = rootProject.project(":mock-supplier")
            .layout.buildDirectory.file("libs/mock-supplier.jar").get().asFile
        check(mockSupplierJar.isFile) { "Mock Supplier boot jar was not built" }
        val javaExecutable = File(System.getProperty("java.home"), "bin/java.exe")
        val process = ProcessBuilder(javaExecutable.absolutePath, "-jar", mockSupplierJar.absolutePath, "--server.port=$e2eMockSupplierPort")
            .redirectErrorStream(true)
            .redirectOutput(layout.buildDirectory.file("e2e-mock-supplier.log").get().asFile)
            .start()
        e2eMockSupplierProcess.set(process)
        repeat(100) {
            if (runCatching { Socket("localhost", e2eMockSupplierPort).use { } }.isSuccess) return@doFirst
            Thread.sleep(100)
        }
        error("Mock Supplier did not start on port $e2eMockSupplierPort")
    }
}

dependencyManagement {
    imports {
        mavenBom("org.testcontainers:testcontainers-bom:1.20.6")
    }
}
