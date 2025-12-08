package com.ensody.nativebuilds.loader

public interface NativeBuildsJvmLib {
    public val packageName: String

    public val libName: String

    /** Maps from the platform name to the respective library file name. */
    public val platformFileName: Map<String, String>
}
