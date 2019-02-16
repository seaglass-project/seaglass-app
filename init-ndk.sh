export PATH=$PATH:`pwd`/ndk-toolchain/bin

export AR=arm-linux-androideabi-ar
export AS=arm-linux-androideabi-gcc
export CC=arm-linux-androideabi-gcc
export CXX=arm-linux-androideabi-g++
export LD=arm-linux-androideabi-ld
export STRIP=arm-linux-androideabi-strip

export CFLAGS="-fPIE -fPIC"
export LDFLAGS="-pie"
