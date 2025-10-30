# Safe placeholder CMakeLists for Android autolinking.
# It defines the codegen target name that RN autolinking expects when codegen is (incorrectly) assumed.
# This prevents configuration errors without introducing native code.

cmake_minimum_required(VERSION 3.13)

# Define an interface library with the expected target name.
add_library(react_codegen_ReactNativePrivacyBlur INTERFACE)
