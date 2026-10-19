plugins {
    id("com.android.application")
    id("com.google.gms.google-services") apply false
}
if (file("google-services.json").exists()) {
    apply(plugin = "com.google.gms.google-services")
}
android {
    namespace = "ge.n24.operator"
    compileSdk = 35
    defaultConfig {
        applicationId = "ge.n24.operator"
        minSdk = 23
        targetSdk = 35
        versionCode = 27
        versionName = "2.7"
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}
dependencies {
    implementation(platform("com.google.firebase:firebase-bom:33.9.0"))
    implementation("com.google.firebase:firebase-messaging")
}
