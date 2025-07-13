from os.path import join

from conan import ConanFile
from conan.tools.files import copy


class CombinedConan(ConanFile):
    options = {
        "shared": [True, False],
    }

    def configure(self):
        self.options["zstd"].build_programs = False

    def requirements(self):
        self.requires("zstd/" + str(self.version))

    def generate(self):
        for depname in ["zstd"]:
            dep = self.dependencies[depname]
            includedir = dep.cpp_info.includedirs[0]
            libdir = dep.cpp_info.libdirs[0]
            base_output_path = join(self.build_folder, "output", depname)

            copy(self, "*.h", includedir, join(base_output_path, "include"))

            for libName in ["libzstd"]:
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
