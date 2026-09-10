plugins {
    kotlin("jvm") version "2.0.21"
    application
}

kotlin { jvmToolchain(21) }

repositories { mavenCentral() }

application { mainClass.set("com.example.ais.CoreSystemEngineKt") }

sourceSets { named("main") { kotlin.srcDirs("app/src/main/java") } }

dependencies { implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.8.1") }

tasks.register<JavaExec>("runCore") {
    group = "application"
    description = "Runs Core "
    mainClass.set("com.example.core.MainKt")
    classpath = sourceSets["main"].runtimeClasspath
}
