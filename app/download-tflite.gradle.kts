import java.net.URL
import org.gradle.api.tasks.Copy

val modelVersion = "v0.2"
val modelFileName = "document-segmentation-model.tflite"
val modelUrl = "https://github.com/pynicolas/document-segmentation-model/releases/download/$modelVersion/$modelFileName"

val downloadedModelPath = layout.buildDirectory.file("downloads/$modelFileName")
val generatedAssetsDir = layout.buildDirectory.dir("generated/assets")

val downloadTFLiteModel = tasks.register("downloadTFLiteModel") {
    val outputFile = downloadedModelPath.get().asFile
    outputs.file(outputFile)

    doLast {
        if (!outputFile.exists()) {
            println("Downloading $modelFileName from $modelUrl")
            outputFile.parentFile.mkdirs()
            URL(modelUrl).openStream().use { input ->
                outputFile.outputStream().use { output ->
                    input.copyTo(output)
                }
            }
        } else {
            println("Model already downloaded: ${outputFile.absolutePath}")
        }
    }
}

val copyTFLiteToAssets = tasks.register<Copy>("copyTFLiteToAssets") {
    dependsOn(downloadTFLiteModel)
    from(downloadedModelPath)
    into(generatedAssetsDir)
}

tasks.named("preBuild") {
    dependsOn(copyTFLiteToAssets)
}
