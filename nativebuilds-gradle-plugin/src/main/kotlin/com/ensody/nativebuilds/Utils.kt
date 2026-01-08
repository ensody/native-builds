package com.ensody.nativebuilds

public fun String.quoted(): String =
    "\"${
        replace("\\", "\\\\")
            .replace("\"", "\\\"")
            .replace("\t", "\\\t")
            .replace("\r", "\\\r")
            .replace("\n", "\\\n")
    }\""
