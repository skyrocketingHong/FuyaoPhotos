import Foundation

nonisolated enum XiaomiPhotographicStyleReader {
    static let prefix = Data("XIAOMI_CUSTOMIZE\0".utf8)

    static func name(in packets: [Data]) -> String? {
        let matching = packets.filter { $0.starts(with: prefix) }
        guard matching.count == 1, let packet = matching.first,
              packet.count > prefix.count + 2, packet.count <= 65_533,
              packet[prefix.count] == 1, packet[prefix.count + 1] == 1 else { return nil }
        // ASVS 1.5.2/2.2.1: bounded UTF-8 dictionaries; never interpret numeric filter IDs as names.
        let bytes = Data(packet.dropFirst(prefix.count + 2))
        guard String(data: bytes, encoding: .utf8) != nil,
              let fields = (try? JSONSerialization.jsonObject(with: bytes)) as? [String: Any],
              let auxiliary = fields["889e"] as? String, auxiliary.utf8.count <= 65_533,
              let values = (try? JSONSerialization.jsonObject(with: Data(auxiliary.utf8))) as? [String: Any] else { return nil }
        return PhotographicStyleReader.readableName(values["filterName"] as? String)
    }
}
