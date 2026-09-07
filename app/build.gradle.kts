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

tasks.test {
    filter {
        excludeTestsMatching("com.staysupplierhub.*EndToEndTest")
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

tasks.register<Test>("e2ePartialSupplierFailureTest") {
    description = "Runs the partial Supplier failure E2E suite against a separate Mock Supplier process."
    group = "verification"
    testClassesDirs = sourceSets.test.get().output.classesDirs
    classpath = sourceSets.test.get().runtimeClasspath
    filter {
        includeTestsMatching("com.staysupplierhub.SearchPartialSupplierFailureEndToEndTest")
    }
    dependsOn(":mock-supplier:bootJar")
    finalizedBy(stopE2eMockSupplier)
    systemProperty("e2e.mock-supplier.base-url", "http://localhost:$e2eMockSupplierPort")

    doFirst {
        val mockSupplierJar = rootProject.project(":mock-supplier")
            .layout.buildDirectory.file("libs/mock-supplier.jar").get().asFile
        check(mockSupplierJar.isFile) { "Mock Supplier boot jar was not built" }
        val javaExecutable = File(System.getProperty("java.home"), "bin/java.exe")
        val process = ProcessBuilder(
            javaExecutable.absolutePath,
            "-jar",
            mockSupplierJar.absolutePath,
            "--server.port=$e2eMockSupplierPort",
            "--mock-supplier.a-availability-mode=SUPPLIER_ERROR",
        )
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

tasks.register<Test>("e2eSupplierBProtocolFailureTest") {
    description = "Runs the Supplier B body-level failure E2E suite against a separate Mock Supplier process."
    group = "verification"
    testClassesDirs = sourceSets.test.get().output.classesDirs
    classpath = sourceSets.test.get().runtimeClasspath
    filter {
        includeTestsMatching("com.staysupplierhub.SearchSupplierBProtocolFailureEndToEndTest")
    }
    dependsOn(":mock-supplier:bootJar")
    finalizedBy(stopE2eMockSupplier)
    systemProperty("e2e.mock-supplier.base-url", "http://localhost:$e2eMockSupplierPort")

    doFirst {
        val mockSupplierJar = rootProject.project(":mock-supplier")
            .layout.buildDirectory.file("libs/mock-supplier.jar").get().asFile
        check(mockSupplierJar.isFile) { "Mock Supplier boot jar was not built" }
        val javaExecutable = File(System.getProperty("java.home"), "bin/java.exe")
        val process = ProcessBuilder(
            javaExecutable.absolutePath,
            "-jar",
            mockSupplierJar.absolutePath,
            "--server.port=$e2eMockSupplierPort",
            "--mock-supplier.b-search-mode=SUPPLIER_ERROR",
        )
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

tasks.register<Test>("e2eSupplierATimeoutTest") {
    description = "Runs the Supplier A response-timeout E2E suite against a separate Mock Supplier process."
    group = "verification"
    testClassesDirs = sourceSets.test.get().output.classesDirs
    classpath = sourceSets.test.get().runtimeClasspath
    filter {
        includeTestsMatching("com.staysupplierhub.SearchSupplierATimeoutEndToEndTest")
    }
    dependsOn(":mock-supplier:bootJar")
    finalizedBy(stopE2eMockSupplier)
    systemProperty("e2e.mock-supplier.base-url", "http://localhost:$e2eMockSupplierPort")

    doFirst {
        val mockSupplierJar = rootProject.project(":mock-supplier")
            .layout.buildDirectory.file("libs/mock-supplier.jar").get().asFile
        check(mockSupplierJar.isFile) { "Mock Supplier boot jar was not built" }
        val javaExecutable = File(System.getProperty("java.home"), "bin/java.exe")
        val process = ProcessBuilder(
            javaExecutable.absolutePath,
            "-jar",
            mockSupplierJar.absolutePath,
            "--server.port=$e2eMockSupplierPort",
            "--mock-supplier.a-availability-mode=NO_RESPONSE",
        )
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

tasks.register<Test>("e2eAllSupplierFailureTest") {
    description = "Runs the all-Supplier failure E2E suite against a separate Mock Supplier process."
    group = "verification"
    testClassesDirs = sourceSets.test.get().output.classesDirs
    classpath = sourceSets.test.get().runtimeClasspath
    filter {
        includeTestsMatching("com.staysupplierhub.SearchAllSupplierFailureEndToEndTest")
    }
    dependsOn(":mock-supplier:bootJar")
    finalizedBy(stopE2eMockSupplier)
    systemProperty("e2e.mock-supplier.base-url", "http://localhost:$e2eMockSupplierPort")

    doFirst {
        val mockSupplierJar = rootProject.project(":mock-supplier")
            .layout.buildDirectory.file("libs/mock-supplier.jar").get().asFile
        check(mockSupplierJar.isFile) { "Mock Supplier boot jar was not built" }
        val javaExecutable = File(System.getProperty("java.home"), "bin/java.exe")
        val process = ProcessBuilder(
            javaExecutable.absolutePath,
            "-jar",
            mockSupplierJar.absolutePath,
            "--server.port=$e2eMockSupplierPort",
            "--mock-supplier.a-availability-mode=SUPPLIER_ERROR",
            "--mock-supplier.b-search-mode=SUPPLIER_ERROR",
        )
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

tasks.register<Test>("e2eZeroInventoryTest") {
    description = "Runs the zero-inventory E2E suite against a separate Mock Supplier process."
    group = "verification"
    testClassesDirs = sourceSets.test.get().output.classesDirs
    classpath = sourceSets.test.get().runtimeClasspath
    filter {
        includeTestsMatching("com.staysupplierhub.SearchZeroInventoryEndToEndTest")
    }
    dependsOn(":mock-supplier:bootJar")
    finalizedBy(stopE2eMockSupplier)
    systemProperty("e2e.mock-supplier.base-url", "http://localhost:$e2eMockSupplierPort")

    doFirst {
        val mockSupplierJar = rootProject.project(":mock-supplier")
            .layout.buildDirectory.file("libs/mock-supplier.jar").get().asFile
        check(mockSupplierJar.isFile) { "Mock Supplier boot jar was not built" }
        val javaExecutable = File(System.getProperty("java.home"), "bin/java.exe")
        val process = ProcessBuilder(
            javaExecutable.absolutePath,
            "-jar",
            mockSupplierJar.absolutePath,
            "--server.port=$e2eMockSupplierPort",
            "--mock-supplier.a-availability-mode=ZERO_INVENTORY",
        )
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

tasks.register<Test>("e2eSupplierABatchingTest") {
    description = "Runs the Supplier A 50-property batching E2E suite against a separate Mock Supplier process."
    group = "verification"
    testClassesDirs = sourceSets.test.get().output.classesDirs
    classpath = sourceSets.test.get().runtimeClasspath
    filter {
        includeTestsMatching("com.staysupplierhub.SearchSupplierABatchingEndToEndTest")
    }
    dependsOn(":mock-supplier:bootJar")
    finalizedBy(stopE2eMockSupplier)
    systemProperty("e2e.mock-supplier.base-url", "http://localhost:$e2eMockSupplierPort")

    doFirst {
        val mockSupplierJar = rootProject.project(":mock-supplier")
            .layout.buildDirectory.file("libs/mock-supplier.jar").get().asFile
        check(mockSupplierJar.isFile) { "Mock Supplier boot jar was not built" }
        val javaExecutable = File(System.getProperty("java.home"), "bin/java.exe")
        val process = ProcessBuilder(
            javaExecutable.absolutePath,
            "-jar",
            mockSupplierJar.absolutePath,
            "--server.port=$e2eMockSupplierPort",
            "--mock-supplier.a-catalog-property-count=51",
        )
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

tasks.register<Test>("e2eCatalogIdStabilityTest") {
    description = "Runs the repeated Catalog synchronization E2E suite against a separate Mock Supplier process."
    group = "verification"
    testClassesDirs = sourceSets.test.get().output.classesDirs
    classpath = sourceSets.test.get().runtimeClasspath
    filter {
        includeTestsMatching("com.staysupplierhub.CatalogIdStabilityEndToEndTest")
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

tasks.register<Test>("e2eCatalogBaselineFailureTest") {
    description = "Runs the fresh Catalog baseline failure E2E suite against a separate Mock Supplier process."
    group = "verification"
    testClassesDirs = sourceSets.test.get().output.classesDirs
    classpath = sourceSets.test.get().runtimeClasspath
    filter {
        includeTestsMatching("com.staysupplierhub.SearchCatalogBaselineFailureEndToEndTest")
    }
    dependsOn(":mock-supplier:bootJar")
    finalizedBy(stopE2eMockSupplier)
    systemProperty("e2e.mock-supplier.base-url", "http://localhost:$e2eMockSupplierPort")

    doFirst {
        val mockSupplierJar = rootProject.project(":mock-supplier")
            .layout.buildDirectory.file("libs/mock-supplier.jar").get().asFile
        check(mockSupplierJar.isFile) { "Mock Supplier boot jar was not built" }
        val javaExecutable = File(System.getProperty("java.home"), "bin/java.exe")
        val process = ProcessBuilder(
            javaExecutable.absolutePath,
            "-jar",
            mockSupplierJar.absolutePath,
            "--server.port=$e2eMockSupplierPort",
            "--mock-supplier.a-catalog-mode=SUPPLIER_ERROR",
        )
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

tasks.register<Test>("e2eStaleCatalogBaselineTest") {
    description = "Runs the stale Catalog baseline E2E suite against a separate Mock Supplier process."
    group = "verification"
    testClassesDirs = sourceSets.test.get().output.classesDirs
    classpath = sourceSets.test.get().runtimeClasspath
    filter {
        includeTestsMatching("com.staysupplierhub.SearchStaleCatalogBaselineEndToEndTest")
    }
    dependsOn(":mock-supplier:bootJar")
    finalizedBy(stopE2eMockSupplier)
    systemProperty("e2e.mock-supplier.base-url", "http://localhost:$e2eMockSupplierPort")

    doFirst {
        val mockSupplierJar = rootProject.project(":mock-supplier")
            .layout.buildDirectory.file("libs/mock-supplier.jar").get().asFile
        check(mockSupplierJar.isFile) { "Mock Supplier boot jar was not built" }
        val javaExecutable = File(System.getProperty("java.home"), "bin/java.exe")
        val process = ProcessBuilder(
            javaExecutable.absolutePath,
            "-jar",
            mockSupplierJar.absolutePath,
            "--server.port=$e2eMockSupplierPort",
            "--mock-supplier.a-catalog-mode=SUPPLIER_ERROR_AFTER_FIRST_REQUEST",
        )
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
