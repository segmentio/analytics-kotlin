plugins {
    id("java-library")
    id("kotlin")
    id("org.jetbrains.kotlin.plugin.serialization")
}

java {
    sourceCompatibility = JavaVersion.VERSION_1_8
    targetCompatibility = JavaVersion.VERSION_1_8
}

tasks.withType<Test> {
    useJUnitPlatform()
}

repositories {
    mavenCentral()
}

dependencies {
    // MAIN DEPS
    api("com.segment:sovran-kotlin:1.2.1")
    api("org.jetbrains.kotlinx:kotlinx-serialization-json:1.2.2")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.5.2")

    // TESTING

    testImplementation("io.mockk:mockk:1.10.6")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.3.2")

    testImplementation("junit:junit:4.13.2")
    testImplementation("org.junit.jupiter:junit-jupiter:5.7.2")
}

apply(from = rootProject.file("gradle/artifacts-core.gradle"))
apply(from = rootProject.file("gradle/mvn-publish.gradle"))
apply(from = rootProject.file("gradle/codecov.gradle"))