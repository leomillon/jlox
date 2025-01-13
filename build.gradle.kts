plugins {
  kotlin("jvm") version "2.0.21"
  application
}

group = "fr.leomillon.lang"
version = "1.0-SNAPSHOT"

repositories {
  mavenCentral()
}

dependencies {
  testImplementation(kotlin("test"))
  testImplementation("com.willowtreeapps.assertk:assertk:0.28.1")
}

tasks.test {
  useJUnitPlatform()
}

kotlin {
  jvmToolchain(21)
}

application {
  mainClass = "fr.leomillon.lang.lox.LoxKt"
}