#!/bin/bash
source init-ndk.sh && \
NDK_DIR=`pwd`/ndk-toolchain && \
cp -a prebuilt-ndk-deps/* $NDK_DIR && \
cd libosmocore && \
autoreconf -i && \
LIBGNUTLS_CFLAGS="-I$NDK_DIR/include" LIBGNUTLS_LIBS="-L$NDK_DIR/lib -lgnutls" TALLOC_CFLAGS="-I$NDK_DIR/include" TALLOC_LIBS="-L$NDK_DIR/lib -ltalloc" ./configure --host arm-linux-androideabi --prefix=$NDK_DIR --disable-pcsc && \
make install && \
cd ../osmocom-bb/src && \
LIBOSMOCORE_CFLAGS="-I$NDK_DIR/include" LIBOSMOCORE_LIBS="-L$NDK_DIR/lib -losmocore" LIBOSMOVTY_CFLAGS="-I$NDK_DIR/include" LIBOSMOVTY_LIBS="-L$NDK_DIR/lib -losmovty" LIBOSMOGSM_CFLAGS="-I$NDK_DIR/include" LIBOSMOGSM_LIBS="-L$NDK_DIR/lib -losmogsm" LIBOSMOCODEC_CFLAGS="-I$NDK_DIR/include" LIBOSMOCODEC_LIBS="-L$NDK_DIR/lib -losmocodec" LIBOSMOCODING_CFLAGS="-I$NDK_DIR/include" LIBOSMOCODING_LIBS="-L$NDK_DIR/lib -losmocoding" HOST_CONFARGS="--host arm-linux-androideabi --prefix=$NDK_DIR" make && \
cd ../../android && \
./gradlew build -x lint --stacktrace
