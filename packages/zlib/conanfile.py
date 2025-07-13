from os.path import join

from conan import ConanFile
from conan.tools.files import copy


class ZlibConan(ConanFile):
    options = {
        "shared": [True, False],
    }

    def configure(self):
        pass

    def requirements(self):
        self.requires("zlib/" + str(self.version))

    def generate(self):
        for depname in ["zlib"]:
            dep = self.dependencies[depname]
            includedir = dep.cpp_info.includedirs[0]
            libdir = dep.cpp_info.libdirs[0]
            bindir = dep.cpp_info.bindirs[0]
            base_output_path = join(self.build_folder, "output", depname)

            copy(self, "*.h", includedir, join(base_output_path, "include"))

            for libName in ["libcurl", "libcrypto", "libssl", "libz"]:
                for ext in [
                    # mingw dynamic
                    "dll.a",
                    # windows dynamic
                    "lib",
                    # macos dynamic
                    "dylib", "3.dylib",
                    # linux dynamic
                    "so.3", "so",
                    # macos/linux/mingw static
                    "a"
                ]:
                    copy(self, libName + "." + ext, libdir, join(base_output_path, "lib"))

            # Windows only
            for binName in ["libcrypto-3-x64", "libssl-3-x64"]:
                for ext in ["dll"]:
                    copy(self, binName + "." + ext, bindir, join(base_output_path, "bin"))
