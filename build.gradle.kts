// Top-level build file where you can add configuration options common to all sub-projects/modules.
plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.android) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.license)
}

license {
    header = rootProject.file("LICENSE_HEADER")
    exclude("**/*.xml")
    //strictCheck = true
    mapping("java", "SLASHSTAR_STYLE")
    mapping("kt", "SLASHSTAR_STYLE")
}

// See https://github.com/hierynomus/license-gradle-plugin/issues/155
tasks.register("licenseCheckForKotlin", com.hierynomus.gradle.license.tasks.LicenseCheck::class) {
    source = fileTree(project.projectDir) { include("**/*.kt") }
}
tasks["license"].dependsOn("licenseCheckForKotlin")
tasks.register("licenseFormatForKotlin", com.hierynomus.gradle.license.tasks.LicenseFormat::class) {
    source = fileTree(project.projectDir) { include("**/*.kt") }
}
tasks["licenseFormat"].dependsOn("licenseFormatForKotlin")
