plugins {
    java
    id("com.github.johnrengelman.shadow") version "8.1.1"  // creates fat JAR
}

group = "example"
version = "1.0.0"

java {
    toolchain { languageVersion.set(JavaLanguageVersion.of(17)) }
}

repositories {
    mavenCentral()
}

dependencies {
    implementation("software.amazon.awssdk:sesv2:2.25.36")
    implementation("com.amazonaws:aws-lambda-java-core:1.2.3")
}

tasks {
    // Optional: set up a fat JAR for Lambda
    shadowJar {
        archiveClassifier.set("all")
        mergeServiceFiles()
    }
}
