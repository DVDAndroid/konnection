package scripts

plugins {
  kotlin("multiplatform") apply false
}

kotlin {
  js(IR) {
    browser()
  }
}