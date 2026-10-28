plugins {
    id("org.jetbrains.kotlin.jvm")
}

// Sof Kotlin: Vision, maket (sim), aim. Android'ga bog'liq emas — kompyuterda test qilinadi.
kotlin {
    jvmToolchain(17)
}

dependencies {
    testImplementation("junit:junit:4.13.2")
}
