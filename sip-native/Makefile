# -------------------- VALUES TO CONFIGURE --------------------

# Path to Android NDK
# NDK version must match ndkVersion in app/build.gradle
NDK_PATH  ?= $(ANDROID_NDK_ROOT)

# Android API level (matches the minSdk of the SIP module)
API_LEVEL := 28

# Set default from following values: [armeabi-v7a, arm64-v8a, x86_64]
ANDROID_TARGET_ARCH := arm64-v8a

# Directory where libraries and include files are instelled
OUTPUT_DIR := $(CURDIR)/distribution

# -------------------- GENERATED VALUES --------------------

CPU_COUNT	:= $(shell nproc)
PWD		:= $(shell pwd)

ifeq ($(ANDROID_TARGET_ARCH), armeabi-v7a)
	TARGET       := arm-linux-androideabi
	CLANG_TARGET := armv7a-linux-androideabi
	ARCH         := arm
	OPENSSL_ARCH := android-arm
	MARCH        := armv7-a
else
ifeq ($(ANDROID_TARGET_ARCH), arm64-v8a)
	TARGET       := aarch64-linux-android
	CLANG_TARGET := $(TARGET)
	ARCH         := arm
	OPENSSL_ARCH := android-arm64
	MARCH        := armv8-a
else
ifeq ($(ANDROID_TARGET_ARCH), x86_64)
	TARGET       := x86_64-linux-android
	CLANG_TARGET := $(TARGET)
	ARCH         := x86
	OPENSSL_ARCH := android-x86_64
	MARCH        := x86-64
else
	exit 1
endif
endif
endif

PLATFORM	:= android-$(API_LEVEL)

OS		:= $(shell uname -s | tr "[A-Z]" "[a-z]")
ifeq ($(OS),linux)
	HOST_OS   := linux-x86_64
endif
ifeq ($(OS),darwin)
	HOST_OS   := darwin-x86_64
endif

# Toolchain and sysroot
TOOLCHAIN	:= $(NDK_PATH)/toolchains/llvm/prebuilt/$(HOST_OS)
CMAKE_TOOLCHAIN_FILE	:= $(NDK_PATH)/build/cmake/android.toolchain.cmake
SYSROOT		:= $(TOOLCHAIN)/sysroot
PKG_CONFIG_LIBDIR	:= $(NDK_PATH)/prebuilt/$(HOST_OS)/lib/pkgconfig

# Toolchain tools
PATH	:= $(TOOLCHAIN)/bin:/usr/bin:/bin
AR	:= llvm-ar
AS	:= $(CLANG_TARGET)$(API_LEVEL)-clang
CC	:= $(CLANG_TARGET)$(API_LEVEL)-clang
CXX	:= $(CLANG_TARGET)$(API_LEVEL)-clang++
LD	:= ld.lld
RANLIB	:= llvm-ranlib
STRIP	:= llvm-strip

# Android cmake flags
CMAKE_ANDROID_FLAGS := \
	-DANDROID=ON \
	-DANDROID_PLATFORM=$(API_LEVEL) \
	-DCMAKE_SYSTEM_NAME=Android \
	-DCMAKE_SYSTEM_VERSION=$(API_LEVEL) \
	-DCMAKE_TOOLCHAIN_FILE=$(CMAKE_TOOLCHAIN_FILE) \
	-DANDROID_ABI=$(ANDROID_TARGET_ARCH) \
	-DCMAKE_ANDROID_ARCH_ABI=$(ANDROID_TARGET_ARCH) \
	-DCMAKE_SKIP_INSTALL_RPATH=ON \
	-DCMAKE_C_COMPILER=$(CC) \
	-DCMAKE_CXX_COMPILER=$(CXX) \
	-DCMAKE_POSITION_INDEPENDENT_CODE=ON \
	-DCMAKE_BUILD_TYPE=Release

MODULES := "augain;aaudio;dtls_srtp;opus;g711;stun;turn;ice;account;natpmp;srtp;uuid;debug_cmd"

default: all

.PHONY: openssl
openssl:
	-make distclean -C openssl
	cd openssl && \
	ANDROID_NDK_ROOT=$(NDK_PATH) PATH=$(PATH) ./Configure $(OPENSSL_ARCH) no-apps no-asm no-docs no-engine no-gost no-legacy no-shared no-ssl no-tests no-zlib -U__ANDROID_API__ -D__ANDROID_API__=$(API_LEVEL) && \
	make build_libs && \
	rm -rf $(OUTPUT_DIR)/openssl/lib/$(ANDROID_TARGET_ARCH)
	mkdir -p $(OUTPUT_DIR)/openssl/lib/$(ANDROID_TARGET_ARCH)
	cp openssl/libcrypto.a $(OUTPUT_DIR)/openssl/lib/$(ANDROID_TARGET_ARCH)
	cp openssl/libssl.a $(OUTPUT_DIR)/openssl/lib/$(ANDROID_TARGET_ARCH)
	rm -rf $(OUTPUT_DIR)/openssl/include
	mkdir -p $(OUTPUT_DIR)/openssl/include
	cp -r openssl/include/openssl $(OUTPUT_DIR)/openssl/include/

.PHONY: opus
opus:
	-make distclean -C opus
	cd opus && \
	rm -rf include_opus && \
	./autogen.sh && \
	CC="$(CC) --sysroot $(SYSROOT)" CC=$(CC) RANLIB=$(RANLIB) AR=$(AR) PATH=$(PATH) ./configure --host=$(TARGET) --disable-shared --disable-doc --disable-extra-programs CFLAGS="-fPIC -O3" && \
	CC="$(CC) --sysroot $(SYSROOT)" RANLIB=$(RANLIB) AR=$(AR) PATH=$(PATH) make && \
	mkdir -p include_opus/opus && \
	cp include/* include_opus/opus
	rm -rf $(OUTPUT_DIR)/opus/lib/$(ANDROID_TARGET_ARCH)
	mkdir -p $(OUTPUT_DIR)/opus/lib/$(ANDROID_TARGET_ARCH)
	cp opus/.libs/libopus.a $(OUTPUT_DIR)/opus/lib/$(ANDROID_TARGET_ARCH)

libre.a: Makefile
	cd re && \
	rm -rf build && rm -rf .cache && mkdir build && cd build && \
	cmake .. \
		$(CMAKE_ANDROID_FLAGS) \
		-DCMAKE_FIND_ROOT_PATH="$(NDK_PATH);$(PWD)/openssl" \
		-DOPENSSL_VERSION_MAJOR=3 \
		-DOPENSSL_ROOT_DIR=$(PWD)/openssl && \
	cmake --build . --target re -j$(CPU_COUNT)

libbaresip: Makefile openssl opus libre.a
	cd baresip && \
	rm -rf build && rm -rf .cache && mkdir build && cd build && \
	cmake .. \
		$(CMAKE_ANDROID_FLAGS) \
		-DCMAKE_FIND_ROOT_PATH="$(PWD)/openssl" \
		-DSTATIC=ON -DCMAKE_VERBOSE_MAKEFILE:BOOL=ON \
		-DAAUDIO_INCLUDE_DIR=${TOOLCHAIN}/sysroot/usr/include \
		-DAAUDIO_LIBRARY=${TOOLCHAIN}/sysroot/usr/lib/$(TARGET)/$(API_LEVEL)/libaaudio.so \
		-Dre_DIR=$(PWD)/re/cmake \
		-DRE_LIBRARY=$(PWD)/re/build/libre.a \
		-DRE_INCLUDE_DIR=$(PWD)/re/include \
		-DOPENSSL_ROOT_DIR=$(PWD)/openssl \
		-DOPUS_INCLUDE_DIR=$(PWD)/opus/include_opus \
		-DOPUS_LIBRARY=$(OUTPUT_DIR)/opus/lib/$(ANDROID_TARGET_ARCH)/libopus.a \
		-DCMAKE_C_COMPILER="clang" \
		-DCMAKE_CXX_COMPILER="clang++" \
		-DMODULES=$(MODULES) && \
	cmake --build . --target baresip -j$(CPU_COUNT)
	rm -rf $(OUTPUT_DIR)/re/lib/$(ANDROID_TARGET_ARCH)
	mkdir -p $(OUTPUT_DIR)/re/lib/$(ANDROID_TARGET_ARCH)
	cp re/build/libre.a $(OUTPUT_DIR)/re/lib/$(ANDROID_TARGET_ARCH)
	rm -rf $(OUTPUT_DIR)/baresip/lib/$(ANDROID_TARGET_ARCH)
	mkdir -p $(OUTPUT_DIR)/baresip/lib/$(ANDROID_TARGET_ARCH)
	cp baresip/build/libbaresip.a $(OUTPUT_DIR)/baresip/lib/$(ANDROID_TARGET_ARCH)
	rm -rf $(OUTPUT_DIR)/re/include
	mkdir -p $(OUTPUT_DIR)/re/include
	cp re/include/* $(OUTPUT_DIR)/re/include
	rm -rf $(OUTPUT_DIR)/re/cmake
	mkdir -p $(OUTPUT_DIR)/re/cmake
	cp re/cmake/re-config.cmake $(OUTPUT_DIR)/re/cmake
	rm -rf $(OUTPUT_DIR)/baresip/include
	mkdir -p $(OUTPUT_DIR)/baresip/include
	cp baresip/include/baresip.h $(OUTPUT_DIR)/baresip/include

all:
	make libbaresip ANDROID_TARGET_ARCH=arm64-v8a
	make libbaresip ANDROID_TARGET_ARCH=armeabi-v7a

.PHONY: download-sources
download-sources:
	rm -fr baresip openssl opus re
	git clone --depth 1 https://github.com/baresip/baresip.git
	git clone --depth 1 https://github.com/openssl/openssl.git -b openssl-3.5 --single-branch openssl
	git clone --depth 1 https://github.com/xiph/opus.git -b v1.4 --single-branch
	git clone --depth 1 https://github.com/baresip/re.git
	patch -d re -p1 < re-patch
