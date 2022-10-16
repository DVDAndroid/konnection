plugins {
  kotlin("js")
}

kotlin {
  js(IR) {
    browser()
    binaries.executable()
  }
}

dependencies {
  implementation(Dependencies.kotlinCoroutinesCore)
  implementation(project(":konnection"))
}