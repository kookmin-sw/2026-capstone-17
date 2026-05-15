platform :ios, '15.0'

target 'focus' do
  use_frameworks!

  pod 'TensorFlowLiteSwift'
  pod 'onnxruntime-objc'

  target 'focusTests' do
    inherit! :search_paths
  end

  target 'focusUITests' do
    inherit! :search_paths
  end
end

post_install do |installer|
  installer.pods_project.targets.each do |target|
    target.build_configurations.each do |config|
      config.build_settings['EXCLUDED_ARCHS[sdk=iphonesimulator*]'] = ''
    end
  end
end