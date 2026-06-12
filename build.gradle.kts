plugins {
    java
    id("com.gradleup.shadow") version "8.3.5"
}

group = "dev.aicraft"
version = findProperty("version")?.toString() ?: "1.1.0"

repositories {
    mavenCentral()
    maven("https://repo.papermc.io/repository/maven-public/")
}

dependencies {
    // Built against 1.21 API (Java 21) so the JAR runs on 1.21 and 26.x Paper servers.
    compileOnly("io.papermc.paper:paper-api:1.21.11-R0.1-SNAPSHOT")
    compileOnly("net.luckperms:api:5.4")

    implementation("com.google.code.gson:gson:2.11.0")
    implementation("com.zaxxer:HikariCP:6.2.1")
    implementation("org.xerial:sqlite-jdbc:3.49.1.0")
    implementation("org.postgresql:postgresql:42.7.5")
}

tasks.shadowJar {
    archiveClassifier.set("")
    relocate("com.google.gson", "dev.aicraft.libs.gson")
    mergeServiceFiles()
}

tasks.build {
    dependsOn(tasks.shadowJar)
}

tasks.jar {
    enabled = false
}

java {
    toolchain.languageVersion.set(JavaLanguageVersion.of(21))
}

tasks.withType<JavaCompile> {
    options.encoding = Charsets.UTF_8.name()
}

tasks.processResources {
    filteringCharset = Charsets.UTF_8.name()
    filesMatching("plugin.yml") {
        filter(
            org.apache.tools.ant.filters.ReplaceTokens::class,
            "tokens" to mapOf("version" to version.toString()),
        )
    }
}
