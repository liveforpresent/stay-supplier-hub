import org.gradle.api.artifacts.ExternalModuleDependency
import org.gradle.api.artifacts.ProjectDependency
import org.jetbrains.kotlin.gradle.dsl.KotlinJvmProjectExtension
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    base
    kotlin("jvm") version "2.3.21" apply false
    kotlin("plugin.spring") version "2.3.21" apply false
    id("org.springframework.boot") version "4.1.1" apply false
    id("io.spring.dependency-management") version "1.1.7" apply false
}

group = "com.staysupplierhub"
version = "0.0.1-SNAPSHOT"

allprojects {
    repositories {
        mavenCentral()
    }
}

subprojects {
    plugins.withId("org.jetbrains.kotlin.jvm") {
        extensions.configure<KotlinJvmProjectExtension> {
            jvmToolchain(25)
        }

        tasks.withType<KotlinCompile>().configureEach {
            compilerOptions {
                freeCompilerArgs.addAll("-Xjsr305=strict", "-Xannotation-default-target=param-property")
            }
        }

        tasks.withType<Test>().configureEach {
            useJUnitPlatform()
        }
    }
}

val frameworkFreeModules = setOf(
    ":catalog:api",
    ":catalog:domain",
    ":catalog:port",
    ":search:domain",
    ":search:port",
)

val allowedProjectDependencies = mapOf(
    ":catalog:api" to emptySet(),
    ":catalog:domain" to setOf(":catalog:api"),
    ":catalog:port" to setOf(":catalog:api", ":catalog:domain"),
    ":catalog:application" to setOf(":catalog:api", ":catalog:domain", ":catalog:port"),
    ":catalog:adapter:persistence" to setOf(":catalog:api", ":catalog:domain", ":catalog:port"),
    ":search:domain" to setOf(":catalog:api"),
    ":search:port" to setOf(":catalog:api", ":search:domain"),
    ":search:application" to setOf(":catalog:api", ":search:domain", ":search:port"),
    ":search:adapter:web" to setOf(":catalog:api", ":search:domain", ":search:port"),
    ":integration:supplier-a" to setOf(":catalog:port", ":search:port"),
    ":integration:supplier-b" to setOf(":catalog:port", ":search:port"),
    ":shared:infrastructure" to emptySet(),
    ":mock-supplier" to emptySet(),
    ":app" to setOf(
        ":catalog:api",
        ":catalog:application",
        ":catalog:adapter:persistence",
        ":search:application",
        ":search:adapter:web",
        ":integration:supplier-a",
        ":integration:supplier-b",
        ":shared:infrastructure",
    ),
)

val forbiddenFrameworkGroups = setOf(
    "org.springframework",
    "jakarta.persistence",
    "org.hibernate.orm",
    "io.projectreactor",
)

tasks.register("verifyModuleBoundaries") {
    group = "verification"
    description = "Verifies the documented direct Gradle module dependency boundaries."

    doLast {
        allowedProjectDependencies.forEach { (projectPath, allowedDependencies) ->
            val target = project(projectPath)
            val declaredDependencies = target.configurations
                .filter { it.isCanBeDeclared }
                .flatMap { it.dependencies }

            declaredDependencies.filterIsInstance<ProjectDependency>().forEach { dependency ->
                check(dependency.path in allowedDependencies) {
                    "$projectPath must not depend on ${dependency.path}"
                }
            }

            if (projectPath in frameworkFreeModules) {
                declaredDependencies.filterIsInstance<ExternalModuleDependency>().forEach { dependency ->
                    check(dependency.group !in forbiddenFrameworkGroups) {
                        "$projectPath must remain framework-free; found ${dependency.group}:${dependency.name}"
                    }
                }
            }
        }
    }
}

tasks.named("check") {
    dependsOn("verifyModuleBoundaries")
}
