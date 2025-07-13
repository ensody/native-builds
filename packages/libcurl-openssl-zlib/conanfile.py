from os.path import join

from conan import ConanFile
from conan.tools.files import copy


class LibCurlOpenSslZlibConan(ConanFile):
    options = {
        "shared": [True, False],
    }

    def configure(self):
        self.options["libcurl"].with_ssl = "openssl"
        self.options["libcurl"].with_ftp = False
        self.options["libcurl"].with_file = False
        self.options["libcurl"].with_ldap = False
        self.options["libcurl"].with_rtsp = False
        self.options["libcurl"].with_dict = False
        self.options["libcurl"].with_telnet = False
        self.options["libcurl"].with_tftp = False
        self.options["libcurl"].with_pop3 = False
        self.options["libcurl"].with_imap = False
        self.options["libcurl"].with_smb = False
        self.options["libcurl"].with_smtp = False
        self.options["libcurl"].with_gopher = False
        self.options["libcurl"].with_mqtt = False
        self.options["libcurl"].with_libssh2 = False
        self.options["libcurl"].with_libidn = False
        self.options["libcurl"].with_librtmp = False
        self.options["libcurl"].with_libgsasl = False
        self.options["libcurl"].with_libpsl = False
        # self.options["libcurl"].with_nghttp2 = True
        self.options["libcurl"].with_zlib = True
        # self.options["libcurl"].with_brotli = True
        # self.options["libcurl"].with_zstd = True
        self.options["libcurl"].with_c_ares = False
        self.options["libcurl"].with_threaded_resolver = True
        # self.options["libcurl"].with_proxy = True
        self.options["libcurl"].with_ntlm = False
        self.options["libcurl"].with_ntlm_wb = False
        self.options["libcurl"].with_cookies = True
        self.options["libcurl"].with_ipv6 = True
        self.options["libcurl"].with_unix_sockets = True
        self.options["libcurl"].with_ca_fallback = False
        self.options["libcurl"].with_websockets = True

        self.options["openssl"].enable_weak_ssl_ciphers = False
        self.options["openssl"].enable_capieng = False
        self.options["openssl"].no_aria = True
        self.options["openssl"].no_apps = True
        self.options["openssl"].no_autoload_config = True
        self.options["openssl"].no_asm = False
        self.options["openssl"].no_async = True
        self.options["openssl"].no_blake2 = True
        self.options["openssl"].no_bf = True
        self.options["openssl"].no_camellia = True
        self.options["openssl"].no_chacha = False
        self.options["openssl"].no_cms = True
        self.options["openssl"].no_comp = True
        self.options["openssl"].no_ct = True
        self.options["openssl"].no_deprecated = True
        self.options["openssl"].no_des = True
        self.options["openssl"].no_dgram = False
        self.options["openssl"].no_dh = False
        self.options["openssl"].no_dsa = True
        self.options["openssl"].no_dso = True
        self.options["openssl"].no_ec = False
        self.options["openssl"].no_ecdh = False
        self.options["openssl"].no_ecdsa = False
        self.options["openssl"].no_engine = False
        # self.options["openssl"].no_filenames = True
        # self.options["openssl"].no_fips = False
        self.options["openssl"].no_gost = True
        self.options["openssl"].no_idea = True
        self.options["openssl"].no_legacy = True
        self.options["openssl"].no_md2 = True
        self.options["openssl"].no_md4 = True
        self.options["openssl"].no_mdc2 = True
        self.options["openssl"].no_module = True
        self.options["openssl"].no_ocsp = False
        # self.options["openssl"].no_pinshared = False
        self.options["openssl"].no_rc2 = True
        self.options["openssl"].no_rc4 = True
        self.options["openssl"].no_rc5 = True
        # self.options["openssl"].no_rfc3779 = False
        self.options["openssl"].no_rmd160 = True
        self.options["openssl"].no_sm2 = True
        self.options["openssl"].no_sm3 = True
        self.options["openssl"].no_sm4 = True
        self.options["openssl"].no_srp = True
        self.options["openssl"].no_srtp = True
        # self.options["openssl"].no_sse2 = False
        self.options["openssl"].no_ssl = True
        self.options["openssl"].no_stdio = True
        self.options["openssl"].no_seed = True
        # self.options["openssl"].no_sock = False
        self.options["openssl"].no_ssl3 = True
        self.options["openssl"].no_threads = False
        self.options["openssl"].no_tls1 = True
        self.options["openssl"].no_ts = True
        self.options["openssl"].no_whirlpool = True
        self.options["openssl"].no_zlib = True
        self.options["openssl"].tls_security_level = 3

    def requirements(self):
        self.requires("libcurl/" + str(self.version))

    def generate(self):
        for depname in ["openssl", "libcurl", "zlib"]:
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
