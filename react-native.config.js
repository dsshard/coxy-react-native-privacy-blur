// Disable CMake/codegen autolinking for Android because this module has no native C++/JNI part.
// This prevents RN from generating Android-autolinking.cmake entries that point to non-existent codegen jni outputs.
/** @type {import('@react-native-community/cli-types').Config} */
module.exports = {
  // Library config (for consumers' autolinking)
  dependency: {
    platforms: {
      android: {
        // Fully disable Android platform autolinking for CMake/codegen.
        // Java/Kotlin part still links via Gradle and does not require CMake.
        // Provide explicit CMakeLists path as a safe fallback for environments that still try to autolink.
        cmakeListsPath: './android/cmake/CMakeLists.txt',
      },
    },
  },
};