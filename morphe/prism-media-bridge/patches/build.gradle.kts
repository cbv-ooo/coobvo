group = "dev.coobvo"

patches {
    about {
        name = "Coobvo Prism Media Bridge"
        description = "Exports Prism's local media-session diagnostics for coobvo Dynamic Island."
        source = "https://github.com/cbv-ooo/coobvo/tree/main/morphe/prism-media-bridge"
        author = "cbv-ooo"
        contact = "GitHub repository issues"
        website = "https://github.com/cbv-ooo/coobvo"
        license = "GPLv3"
    }
}

val patchListGeneratorClasspath = configurations.create("patchListGeneratorClasspath")

dependencies {
    compileOnly(libs.gson)
    patchListGeneratorClasspath(libs.gson)
}

tasks {
    register<JavaExec>("generatePatchesList") {
        description = "Build patch with patch list"
        dependsOn(build)
        classpath = sourceSets["main"].runtimeClasspath + patchListGeneratorClasspath
        mainClass.set("util.PatchListGeneratorKt")
    }

    publish {
        dependsOn("generatePatchesList")
    }
}
