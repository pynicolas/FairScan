import org.gradle.api.tasks.Copy
import java.net.URL

val generatedAssetsDir = layout.buildDirectory.dir("generated/assets")

fun registerModelTasks(
    modelName: String,
    version: String,
    fileName: String,
    baseUrl: String
) {
    val modelUrl = "$baseUrl/$modelName/releases/download/$version/$fileName"
    val downloadedModelPath = layout.buildDirectory.file("downloads/$fileName")

    val downloadTask = tasks.register("download${modelName.capitalize()}Model") {
        val outputFile = downloadedModelPath.get().asFile
        outputs.file(outputFile)

        doLast {
            if (!outputFile.exists()) {
                println("Downloading $fileName from $modelUrl")
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

    val copyTask = tasks.register<Copy>("copy${modelName.capitalize()}ToAssets") {
        dependsOn(downloadTask)
        from(downloadedModelPath)
        into(generatedAssetsDir)
    }

    tasks.named("preBuild") {
        dependsOn(copyTask)
    }
}

registerModelTasks(
    modelName = "fairscan-segmentation-model",
    version = "v1.1.0",
    fileName = "fairscan-segmentation-model.tflite",
    baseUrl = "https://github.com/pynicolas"
)

registerModelTasks(
    modelName = "fairscan-quadrilateral",
    version = "v0.1",
    fileName = "fairscan-quadrilateral.tflite",
    baseUrl = "https://github.com/pynicolas"
)
