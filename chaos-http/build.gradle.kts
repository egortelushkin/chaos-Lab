plugins {
    id("java-library")
}

group = "com.helloegor03"
version = "0.1.0"

repositories {
    mavenCentral()
}

dependencies {
    api(project(":chaos-core"))

    api(platform("org.springframework:spring-framework-bom:6.1.5"))
    api("org.springframework:spring-webflux")

    testImplementation(platform("org.junit:junit-bom:5.10.0"))
    testImplementation("org.junit.jupiter:junit-jupiter")
}

tasks.test {
    useJUnitPlatform()
}
