import com.github.jk1.license.filter.DependencyFilter
import com.github.jk1.license.filter.LicenseBundleNormalizer
import com.github.jk1.license.render.InventoryHtmlReportRenderer
import com.github.jk1.license.render.ReportRenderer
import org.gradle.api.tasks.testing.logging.TestExceptionFormat.FULL
import org.gradle.api.tasks.testing.logging.TestLogEvent.*
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

val geotoolsVersion = "29.2"
val kotlinVersion = "1.9.10"
val springBootVersion = "2.7.16"

plugins {
    id("org.springframework.boot") version "2.7.16"
    id("io.spring.dependency-management") version "1.1.3"
    id("com.github.jk1.dependency-license-report") version "2.0"
    kotlin("jvm") version "1.9.10"
    kotlin("plugin.spring") version "1.9.10"
}

group = "fi.fta.geoviite"
version = "SNAPSHOT"
java.sourceCompatibility = JavaVersion.VERSION_11

repositories {
    maven(url = "https://repo.osgeo.org/repository/release")
    mavenCentral()
}

configurations {
    all {
        exclude("org.springframework.boot", "spring-boot-starter-logging")
    }
}

ext["selenium.version"] = "4.11.0"
dependencies {
    // Version overrides for transitive deps (due to known vulnerabilities)

    // For gt-epsg-hsql
    implementation("org.hsqldb", "hsqldb").version {
        strictly("[2.7.1,2.8.0)")
    }
    // For spring-boot-starter-actuator & aws-java-sdk-cloudfront
    implementation("org.yaml", "snakeyaml").version {
        strictly("[1.33,2.0)")
    }

    // Actual deps
    implementation("com.amazonaws:aws-java-sdk-cloudfront:1.12.560")
    implementation("org.bouncycastle:bcpkix-jdk15on:1.70")
    implementation("org.springframework.boot:spring-boot-starter-actuator:$springBootVersion")
    implementation("org.springframework.boot:spring-boot-starter-jdbc:$springBootVersion")
    implementation("org.springframework.boot:spring-boot-starter-web:$springBootVersion")
    implementation("org.springframework.boot:spring-boot-starter-log4j2:$springBootVersion")
    implementation("org.springframework.boot:spring-boot-starter-cache:$springBootVersion")
    implementation("org.springframework.boot:spring-boot-starter-security:$springBootVersion")
    implementation("org.springframework.boot:spring-boot-starter-webflux:$springBootVersion")
    implementation("org.jetbrains.kotlin:kotlin-reflect:$kotlinVersion")
    implementation("org.jetbrains.kotlin:kotlin-stdlib-jdk8:$kotlinVersion")
    implementation("com.fasterxml.jackson.module:jackson-module-kotlin:2.15.2")
    implementation("com.fasterxml.jackson.dataformat:jackson-dataformat-yaml:2.15.2")
    implementation("com.zaxxer:HikariCP:5.0.1")
    implementation("org.flywaydb:flyway-core:9.22.2")
    implementation("com.github.ben-manes.caffeine:caffeine:3.1.8")
    implementation("org.geotools:gt-main:$geotoolsVersion") {
        // Excluded as the license (JDL or JRL) compatibility is unconfirmed. We don't need this.
        exclude("javax.media", "jai_core")
        // jgridshift doesn't provide licensing information. We don't need it.
        exclude("it.geosolutions.jgridshift", "jgridshift-core")
    }
    implementation("org.geotools:gt-epsg-hsql:$geotoolsVersion") {
        // Excluded as the license (JDL or JRL) compatibility is unconfirmed. We don't need this.
        exclude("javax.media", "jai_core")
        // jgridshift doesn't provide licensing information. We don't need it.
        exclude("it.geosolutions.jgridshift", "jgridshift-core")
    }
    implementation("org.apache.commons:commons-csv:1.10.0")
    implementation("commons-io:commons-io:2.14.0")
    implementation("com.auth0:jwks-rsa:0.22.1")
    implementation("com.auth0:java-jwt:4.4.0")
    implementation("io.netty:netty-resolver-dns-native-macos:4.1.99.Final:osx-aarch_64")
    implementation("org.postgresql:postgresql:42.6.0")
    implementation("jakarta.activation:jakarta.activation-api:2.1.2")
    implementation("jakarta.xml.bind:jakarta.xml.bind-api:4.0.1")
    implementation("com.github.davidmoten:rtree2:0.9.3")
    implementation("commons-validator:commons-validator:1.7")
    compileOnly("org.springframework.boot:spring-boot-devtools:$springBootVersion")
    runtimeOnly("org.glassfish.jaxb:jaxb-runtime:4.0.3")
    annotationProcessor("org.springframework.boot:spring-boot-configuration-processor:$springBootVersion")
    testImplementation("org.springframework.boot:spring-boot-starter-test:$springBootVersion")
    testImplementation("org.jetbrains.kotlin:kotlin-test-junit:$kotlinVersion")
    testImplementation("org.seleniumhq.selenium:selenium-java:4.13.0")
    testImplementation("org.mock-server:mockserver-netty-no-dependencies:5.14.0")
    testImplementation("org.apache.httpcomponents.client5:httpclient5:5.2.1")
}

licenseReport {
    renderers = arrayOf<ReportRenderer>(InventoryHtmlReportRenderer("report.html", "Backend"))
    filters = arrayOf<DependencyFilter>(
        LicenseBundleNormalizer(),
        // ExcludeTransitiveDependenciesFilter(),
    )
    allowedLicensesFile = File("$projectDir/allowed-licenses.json")
}

tasks.withType<KotlinCompile> {
    kotlinOptions {
        freeCompilerArgs = listOf("-Xjsr305=strict")
        jvmTarget = "11"
    }
}
tasks.withType<Jar> {
    from("${rootProject.projectDir}/..") {
        include("LICENSE.txt")
        into("META-INF")
    }
}

tasks.withType<Test> {
    useJUnitPlatform()
    systemProperty("browser.name", System.getProperty("browser.name"))
    systemProperty("browser.headless", System.getProperty("browser.headless"))
    testLogging.exceptionFormat = FULL
    //testLogging.events = mutableSetOf(FAILED, PASSED, SKIPPED)
    testLogging.events = mutableSetOf(FAILED, PASSED, SKIPPED, STANDARD_OUT, STANDARD_ERROR)
}

tasks.withType<AbstractArchiveTask> {
    isPreserveFileTimestamps = false
    isReproducibleFileOrder = true
}
