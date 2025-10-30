new_arch_enabled = ENV['RCT_NEW_ARCH_ENABLED'] == '1'
package = JSON.parse(File.read(File.join(__dir__, "package.json")))

Pod::Spec.new do |s|
  s.name         = "ReactNativePrivacyBlur"
  s.version      = package["version"]
  s.summary      = "Privacy blur overlay for iOS/Android (Swift, Turbo-ready)"
  s.homepage     = "https://github.com/dsshard"
  s.license      = { :type => "MIT" }
  s.authors      = { "Steven Green" => "github.com/dsshard" }

  s.platforms    = { :ios => "12.0" }
  s.source       = { :git => "https://github.com/dsshard/coxy-react-native-privacy-blur.git", :tag => s.version.to_s }

  s.source_files = "ios/**/*.{h,m,mm,swift}"
  s.requires_arc = true
  s.swift_version = "5.0"

  if defined?(install_modules_dependencies()) != nil
    install_modules_dependencies(s)
  else
    # Don't install the dependencies when we run `pod install` in the old architecture.
    if new_arch_enabled then
      s.compiler_flags = "-DRCT_NEW_ARCH_ENABLED=1"
      s.dependency "React-Codegen"
      s.dependency "ReactCommon/turbomodule/core"
    else
      s.dependency "React-Core"
    end
  end
end
