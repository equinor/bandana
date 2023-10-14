plugins {
    id("org.jetbrains.kotlin.jvm") version "1.8.20"

    // This plugin let us create a shadow jar using `gradle shadowjar`
    // This is needed to include kotlin standard library in a fat jar.
    // When the jar file contains all it's dependencies it is easy to
    // include it in fuseki.
    id("com.github.johnrengelman.shadow") version "7.1.0"
    `java-library`
}

repositories {
    mavenCentral()
}

dependencies {
    // Jena
    implementation("org.apache.jena", "apache-jena-libs", "4.9.0") {
        isTransitive = true
    }
    implementation("org.apache.jena", "jena-fuseki-core", "4.9.0")
    implementation("org.apache.jena", "jena-fuseki-server", "4.9.0")

    // Jwt handling using nimbusds
    implementation("com.nimbusds", "oauth2-oidc-sdk", "10.13.2")
    implementation("com.nimbusds", "nimbus-jose-jwt", "9.31")

    // Test related
    testImplementation("org.jetbrains.kotlin:kotlin-test-junit5")
    testImplementation("org.junit.jupiter:junit-jupiter-engine:5.9.2")
    testImplementation("org.junit.jupiter:junit-jupiter-params:5.9.2")
    testImplementation("org.junit.platform:junit-platform-runner:1.10.0")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
    testImplementation(kotlin("test"))
}

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(17))
    }
}

tasks.test {
    useJUnitPlatform()
    outputs.upToDateWhen {false}
    testLogging {
        showStandardStreams = true
    }
}
