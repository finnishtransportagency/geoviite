import com.github.jk1.license.filter.DependencyFilter
import com.github.jk1.license.filter.LicenseBundleNormalizer
import com.github.jk1.license.render.InventoryHtmlReportRenderer
import com.github.jk1.license.render.ReportRenderer
import org.gradle.api.tasks.testing.logging.TestExceptionFormat.FULL
import org.gradle.api.tasks.testing.logging.TestLogEvent.FAILED
import org.gradle.api.tasks.testing.logging.TestLogEvent.PASSED
import org.gradle.api.tasks.testing.logging.TestLogEvent.SKIPPED
import org.gradle.api.tasks.testing.logging.TestLogEvent.STANDARD_ERROR
import org.gradle.api.tasks.testing.logging.TestLogEvent.STANDARD_OUT
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

val geotoolsVersion = "31.3"
val kotlinVersion = "2.0.20"

plugins {
    id("org.springframework.boot") version "3.3.3"
    id("io.spring.dependency-management") version "1.1.6"
    id("com.github.jk1.dependency-license-report") version "2.9"
    kotlin("jvm") version "2.0.20"
    kotlin("plugin.spring") version "2.0.20"
    id("com.ncorti.ktfmt.gradle") version "0.20.1"
}

group = "fi.fta.geoviite"

version = "SNAPSHOT"

java {
    sourceCompatibility = JavaVersion.VERSION_17
    toolchain { languageVersion.set(JavaLanguageVersion.of(17)) }
}

repositories {
    maven(url = "https://repo.osgeo.org/repository/release")
    mavenCentral()
}

ktfmt {
    blockIndent = 4
    continuationIndent = 4
    maxWidth = 120
    manageTrailingCommas = true
}

configurations { all { exclude("org.springframework.boot", "spring-boot-starter-logging") } }

ext["selenium.version"] = "4.25.0"

dependencies {
    // Version overrides for transitive deps (due to known vulnerabilities)

    // Actual deps
    implementation("com.amazonaws:aws-java-sdk-cloudfront:1.12.705") { exclude("commons-logging", "commons-logging") }
    implementation("org.bouncycastle:bcpkix-jdk18on:1.78.1")
    implementation("org.springframework.boot:spring-boot-starter-actuator")
    implementation("org.springframework.boot:spring-boot-starter-jdbc")
    implementation("org.springframework.boot:spring-boot-starter-web")
    implementation("org.springframework.boot:spring-boot-starter-log4j2")
    implementation("org.springframework.boot:spring-boot-starter-cache")
    implementation("org.springframework.boot:spring-boot-starter-security")
    implementation("org.springframework.boot:spring-boot-starter-webflux")
    runtimeOnly("org.springframework.boot:spring-boot-properties-migrator")
    implementation("org.springdoc:springdoc-openapi-starter-webmvc-ui:2.6.0")
    implementation("org.jetbrains.kotlin:kotlin-reflect:$kotlinVersion")
    implementation("org.jetbrains.kotlin:kotlin-stdlib-jdk8:$kotlinVersion")
    implementation("com.fasterxml.jackson.module:jackson-module-kotlin:2.17.2")
    implementation("com.fasterxml.jackson.dataformat:jackson-dataformat-yaml:2.17.2")
    implementation("com.zaxxer:HikariCP:5.1.0")
    implementation("org.flywaydb:flyway-core:10.19.0")
    implementation("org.flywaydb:flyway-database-postgresql:10.19.0")
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
    implementation("org.apache.commons:commons-csv:1.11.0")
    implementation("commons-io:commons-io:2.16.1")
    implementation("com.auth0:jwks-rsa:0.22.1")
    implementation("com.auth0:java-jwt:4.4.0")
    implementation("io.netty:netty-resolver-dns-native-macos:4.1.109.Final:osx-aarch_64")
    implementation("org.postgresql:postgresql:42.7.4")
    implementation("jakarta.activation:jakarta.activation-api:2.1.3")
    implementation("jakarta.xml.bind:jakarta.xml.bind-api:4.0.2")
    implementation("com.github.davidmoten:rtree2:0.9.3")
    implementation("commons-validator:commons-validator:1.9.0") {
        exclude("commons-logging", "commons-logging")
        exclude("commons-collections", "commons-collections")
    }
    implementation("org.aspectj:aspectjweaver:1.9.22.1")
    compileOnly("org.springframework.boot:spring-boot-devtools")
    runtimeOnly("org.glassfish.jaxb:jaxb-runtime:4.0.5")
    annotationProcessor("org.springframework.boot:spring-boot-configuration-processor")
    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testImplementation("org.jetbrains.kotlin:kotlin-test-junit:$kotlinVersion")
    testImplementation("org.seleniumhq.selenium:selenium-java:4.25.0")
    // Do not update to version 5.15.0 as it causes StackOverflowError.
    // See: https://github.com/mock-server/mockserver/issues/1660
    testImplementation("org.mock-server:mockserver-netty:5.15.0")
    testImplementation("org.apache.httpcomponents.client5:httpclient5:5.3.1")
    testImplementation("io.projectreactor:reactor-test:3.7.2")
}

licenseReport {
    renderers = arrayOf<ReportRenderer>(InventoryHtmlReportRenderer("report.html", "Backend"))
    filters =
        arrayOf<DependencyFilter>(
            LicenseBundleNormalizer()
            // ExcludeTransitiveDependenciesFilter(),
        )
    allowedLicensesFile = File("$projectDir/allowed-licenses.json")
}

tasks.withType<KotlinCompile> {
    kotlinOptions {
        freeCompilerArgs = listOf("-Xjsr305=strict", "-Xconsistent-data-class-copy-visibility")
        jvmTarget = "17"
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
    // testLogging.events = mutableSetOf(FAILED, PASSED, SKIPPED)
    testLogging.events = mutableSetOf(FAILED, PASSED, SKIPPED, STANDARD_OUT, STANDARD_ERROR)
}

tasks.register<Test>("integrationtest") { useJUnitPlatform() }

tasks.register<Test>("integrationtest-without-cache") {
    systemProperty("geoviite.cache.enabled", false)
    useJUnitPlatform()
}

tasks.register<Test>("ui-test-selenium-local") { useJUnitPlatform() }

tasks.register<Test>("ui-test-selenium-docker") {
    //     Unfortunately not dynamically assigned from the .env file yet :(
    environment("DB_URL", "host.docker.internal:5446/geoviite")
    environment("E2E_URL_GEOVIITE", "http://host.docker.internal:9004/app/index.html")
    environment("E2E_REMOTE_SELENIUM_HUB_ENABLED", "true")
    environment("E2E_URL_REMOTE_SELENIUM_HUB", "http://host.docker.internal:4444")

    useJUnitPlatform()
}

tasks.withType<AbstractArchiveTask> {
    isPreserveFileTimestamps = false
    isReproducibleFileOrder = true
}

tasks.register("downloadDependencies") {
    doLast {
        configurations.forEach { configuration ->
            if (configuration.isCanBeResolved) {
                configuration.resolve()
            }
        }
    }
}
