import AVFoundation
import Darwin
import Foundation

nonisolated struct LensCameraInventory: Sendable {
    let lenses: [HardwareLens]
    let hardwareDevice: String
    let aliases: Set<String>

    static var localHardwareDevice: String {
#if targetEnvironment(simulator)
        if let model = ProcessInfo.processInfo.environment["SIMULATOR_MODEL_IDENTIFIER"] { return "Apple|\(model)" }
#endif
#if os(macOS)
        let key = "hw.model"
#else
        let key = "hw.machine"
#endif
        var size = 0
        guard sysctlbyname(key, nil, &size, nil, 0) == 0, size > 1, size <= 256 else { return "" }
        var bytes = [CChar](repeating: 0, count: size)
        guard sysctlbyname(key, &bytes, &size, nil, 0) == 0 else { return "" }
        return "Apple|" + String(decoding: bytes.prefix(while: { $0 != 0 }).map { UInt8(bitPattern: $0) }, as: UTF8.self)
    }

    static func scan() -> Self {
#if os(macOS)
        let types: [AVCaptureDevice.DeviceType] = [.builtInWideAngleCamera]
#else
        let types: [AVCaptureDevice.DeviceType] = [.builtInWideAngleCamera, .builtInUltraWideCamera, .builtInTelephotoCamera]
#endif
        let devices = AVCaptureDevice.DiscoverySession(deviceTypes: types, mediaType: .video, position: .unspecified).devices
        let lenses = devices.map { device -> HardwareLens in
            let facing: LensProfile.Facing = switch device.position {
            case .back: .back
            case .front: .front
            default: .unspecified
            }
#if os(macOS)
            let focal: Double? = nil
#else
            let nominal = Double(device.nominalFocalLengthIn35mmFilm)
            let focal = nominal.isFinite && nominal > 0 ? nominal : nil
#endif
            // Model-scoped camera type, never an individual device's uniqueID or serial number.
            return HardwareLens(id: "\(facing.rawValue):\(device.deviceType.rawValue)", name: device.localizedName,
                                facing: facing, equivalentFocal: focal)
        }
        let unique = Dictionary(grouping: lenses, by: \.id).values.compactMap { $0.count == 1 ? $0[0] : nil }.sorted { $0.id < $1.id }
        let model = localHardwareDevice
        return Self(lenses: unique, hardwareDevice: model, aliases: Set(devices.map(\.modelID) + [model.replacingOccurrences(of: "Apple|", with: "")]))
    }
}
