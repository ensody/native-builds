package com.ensody.nativebuilds

import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFiles

public interface CommonBuildTask {
    @get:InputFiles
    public val includeDirs: ConfigurableFileCollection

    @get:InputFiles
    public val inputFiles: ConfigurableFileCollection

    @get:Input
    public val outputLibraryName: Property<String>
}
