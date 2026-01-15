package com.ensody.buildlogic

data class PkgDef(
    val pkg: String,
    val sublibDependencies: Map<String, List<String>>,
    val republishVersionSuffix: Map<String, String>,
)
