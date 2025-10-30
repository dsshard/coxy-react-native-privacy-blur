# Dummy CMake target to satisfy React Native Android autolinking when codegen is not actually used.
# This library exposes no sources and prevents configuration-time failures when add_subdirectory and
# target_link_libraries reference a non-existent codegen target.

# Define an interface library; it has no sources and imposes no link requirements.
add_library(react_codegen_ReactNativePrivacyBlur INTERFACE)

# No include dirs or properties are required; this target is a placeholder only.
