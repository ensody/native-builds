package com.ensody.buildlogic

data class PkgDef(
    val pkg: String,
    val sublibDependencies: Map<String, List<String>>,
    val versionPrefix: String? = null,
    val republishVersionSuffix: Map<String, String>,
)
