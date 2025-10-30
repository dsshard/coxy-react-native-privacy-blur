import Foundation
import UIKit
import CoreImage
import React

@objc(PrivacyBlur)
final class PrivacyBlur: NSObject {

  // MARK: - Config
  private var enabled: Bool = true
  private var blurRadius: Int = 20
  private var duration: TimeInterval = 0.2
  private var bgColor: UIColor? = nil
  private var bgOpacity: CGFloat = 1.0

  // MARK: - Views
  private weak var hostWindow: UIWindow?
  private var container: UIView?
  private var imageView: UIImageView?
  private var solidView: UIView?

  // MARK: - CI
  private let ciContext = CIContext(options: nil)

  // MARK: - RN bridge
  @objc static func moduleName() -> String! { "PrivacyBlur" }
  @objc static func requiresMainQueueSetup() -> Bool { true }

  // MARK: - Public API

  @objc func configure(_ conf: NSDictionary) {
    DispatchQueue.main.async {
      if let d = conf["duration"] as? NSNumber { self.duration = max(0.0, d.doubleValue / 1000.0) }
      if let r = conf["blurRadius"] as? NSNumber { self.blurRadius = max(0, r.intValue) }
    }
  }

  @objc func enable()  { DispatchQueue.main.async { self.enabled = true } }
  @objc func disable() { DispatchQueue.main.async { self.enabled = false; self.hideOverlayImmediate() } }

  @objc func isEnabled(_ resolve: RCTPromiseResolveBlock, rejecter reject: RCTPromiseRejectBlock) {
    resolve(enabled)
  }

  @objc func showNow() { DispatchQueue.main.async { if self.enabled { self.showOverlay() } } }
  @objc func hideNow() { DispatchQueue.main.async { self.animateHide() } }
  // MARK: - Windows

  private func keyWindow() -> UIWindow? {
    if #available(iOS 13.0, *) {
      let scenes = UIApplication.shared.connectedScenes
        .compactMap { $0 as? UIWindowScene }
        .filter { $0.activationState == .foregroundActive || $0.activationState == .foregroundInactive }
      for scene in scenes {
        if let w = scene.windows.first(where: { $0.isKeyWindow }) { return w }
        if let w = scene.windows.first { return w }
      }
      return UIApplication.shared.windows.first { $0.isKeyWindow } ?? UIApplication.shared.windows.first
    } else {
      return UIApplication.shared.keyWindow
    }
  }

  // MARK: - Overlay

  private func ensureOverlay(in win: UIWindow) {
    if let c = container, c.superview != nil { return }

    hostWindow = win

    let c = UIView(frame: win.bounds)
    c.autoresizingMask = [.flexibleWidth, .flexibleHeight]
    c.isUserInteractionEnabled = false
    c.isAccessibilityElement  = false
    c.backgroundColor = .clear
    c.alpha = 1.0

    let sv = UIView(frame: c.bounds)
    sv.autoresizingMask = [.flexibleWidth, .flexibleHeight]
    if #available(iOS 13.0, *) { sv.backgroundColor = .systemBackground } else { sv.backgroundColor = .white }
    sv.isHidden = true
    c.addSubview(sv)
    solidView = sv

    let iv = UIImageView(frame: c.bounds)
    iv.autoresizingMask = [.flexibleWidth, .flexibleHeight]
    iv.contentMode = .scaleToFill
    iv.isOpaque = true
    iv.alpha = 1.0
    c.addSubview(iv)
    imageView = iv

    win.addSubview(c)
    win.bringSubviewToFront(c)
    container = c
  }

  private func showOverlay() {
    guard let win = keyWindow() else { return }

    // Snapshot BEFORE adding overlay
    let snapshot = captureWindowImage(win)

    // Prepare blurred image with downsample → blur → upsample for softer look similar to Android
    var blurred: UIImage? = nil
    if blurRadius > 0, let snap = snapshot {
      // Safety: choose conservative downsample to hide details and reduce cost
      let downsampleFactor: Int = 4 // 1 = no downsample; 4 is a safe default similar to Android
      let down = downsample(image: snap, factor: downsampleFactor)
      if let down = down, let soft = makeSoftBlur(image: down, radius: CGFloat(blurRadius) * 2.0) {
        blurred = upscaleTo(image: soft, targetSize: snap.size, scale: snap.scale)
      } else {
        // Fallback: blur original snapshot if downsample failed
        blurred = makeSoftBlur(image: snap, radius: CGFloat(blurRadius) * 2.0)
      }
    }

    ensureOverlay(in: win)

    if blurRadius > 0, let blurred = blurred {
      solidView?.isHidden = true
      imageView?.image = blurred
      let shouldAnimate = UIApplication.shared.applicationState == .active && duration > 0
      if shouldAnimate {
        imageView?.alpha = 0.0
        UIView.animate(withDuration: duration, delay: 0, options: [.curveEaseInOut], animations: {
          self.imageView?.alpha = 1.0
        }, completion: nil)
      } else {
        imageView?.alpha = 1.0
      }
    } else {
      imageView?.image = nil
      imageView?.alpha = 0.0
      solidView?.isHidden = false
      container?.alpha = 1.0
    }
  }

  private func animateHide() {
    guard let c = container else { return }
    UIView.animate(withDuration: duration, delay: 0, options: .curveEaseOut, animations: {
      c.alpha = 0.0
    }, completion: { _ in
      self.hideOverlayImmediate()
    })
  }

  private func hideOverlayImmediate() {
    container?.removeFromSuperview()
    container = nil
    imageView = nil
    solidView = nil
    hostWindow = nil
  }

  // MARK: - Snapshot & Blur

  private func captureWindowImage(_ window: UIWindow) -> UIImage? {
    let bounds = window.bounds
    let format = UIGraphicsImageRendererFormat()
    format.scale = UIScreen.main.scale
    format.opaque = true

    let renderer = UIGraphicsImageRenderer(bounds: bounds, format: format)
    return renderer.image { _ in
      window.drawHierarchy(in: bounds, afterScreenUpdates: false)
    }
  }

  private func makeSoftBlur(image: UIImage, radius: CGFloat) -> UIImage? {
    // IMPORTANT: clamp → blur → crop to avoid edge shrink
    let colorSpace = CGColorSpaceCreateDeviceRGB()
    let options: [CIImageOption: Any] = [.colorSpace: colorSpace]
    guard let input = CIImage(image: image, options: options) else { return nil }

    let clamped = input.clampedToExtent()

    guard let blur = CIFilter(name: "CIGaussianBlur") else { return nil }
    blur.setValue(clamped, forKey: kCIInputImageKey)
    blur.setValue(radius,  forKey: kCIInputRadiusKey)
    guard var current = blur.outputImage?.cropped(to: input.extent) else { return nil }

    if let controls = CIFilter(name: "CIColorControls") {
      controls.setValue(current, forKey: kCIInputImageKey)
      controls.setValue(0.92, forKey: kCIInputSaturationKey)
      controls.setValue(1.0,  forKey: kCIInputContrastKey)
      controls.setValue(0.0,  forKey: kCIInputBrightnessKey)
      current = (controls.outputImage?.cropped(to: input.extent)) ?? current
    }

    guard let cg = ciContext.createCGImage(current, from: input.extent) else { return nil }
    return UIImage(cgImage: cg, scale: image.scale, orientation: image.imageOrientation)
  }

  // MARK: - Downsample helpers
  private func downsample(image: UIImage, factor: Int) -> UIImage? {
    // Safety: ensure factor >= 1; 1 means no downsample
    let f = max(1, factor)
    if f == 1 { return image }

    let originalSize = image.size
    let newSize = CGSize(width: max(1, Int(originalSize.width) / f), height: max(1, Int(originalSize.height) / f))

    // Use UIGraphicsImageRenderer to redraw at lower resolution; keep scale = 1 to actually reduce pixels
    let format = UIGraphicsImageRendererFormat()
    format.scale = 1.0
    format.opaque = true

    let renderer = UIGraphicsImageRenderer(size: newSize, format: format)
    let scaled = renderer.image { ctx in
      // High-quality downscale
      ctx.cgContext.interpolationQuality = .high
      image.draw(in: CGRect(origin: .zero, size: newSize))
    }
    return scaled
  }

  private func upscaleTo(image: UIImage, targetSize: CGSize, scale: CGFloat) -> UIImage? {
    // Safety: upscale blurred small image back to window size; keep device scale for crisp compositing
    let format = UIGraphicsImageRendererFormat()
    format.scale = scale
    format.opaque = true

    let renderer = UIGraphicsImageRenderer(size: targetSize, format: format)
    let up = renderer.image { ctx in
      ctx.cgContext.interpolationQuality = .high
      image.draw(in: CGRect(origin: .zero, size: targetSize))
    }
    return up
  }
}
