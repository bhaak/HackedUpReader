# CMake toolchain file for building ARM software on OI environment

# this one is important
SET(CMAKE_SYSTEM_NAME Linux)
#this one not so much
SET(CMAKE_SYSTEM_VERSION 1)

# specify the cross compiler
SET(CMAKE_C_COMPILER   /opt/kindle/gcc/arm-kindle-linux-gnueabi/bin/arm-kindle-linux-gnueabi-gcc)
SET(CMAKE_CXX_COMPILER /opt/kindle/gcc/arm-kindle-linux-gnueabi/bin/arm-kindle-linux-gnueabi-g++)
SET(CMAKE_STRIP /opt/kindle/gcc/arm-kindle-linux-gnueabi/bin/arm-kindle-linux-gnueabi-strip)

# where is the target environment 
SET(CMAKE_FIND_ROOT_PATH  /mnt/us/cr3)

# search for programs in the build host directories
SET(CMAKE_FIND_ROOT_PATH_MODE_PROGRAM NEVER)
# for libraries and headers in the target directories
SET(CMAKE_FIND_ROOT_PATH_MODE_LIBRARY ONLY)
SET(CMAKE_FIND_ROOT_PATH_MODE_INCLUDE ONLY)
