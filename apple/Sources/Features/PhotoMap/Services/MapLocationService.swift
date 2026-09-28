import CoreLocation
import Observation

@MainActor @Observable final class MapLocationService: NSObject, CLLocationManagerDelegate {
    private(set) var locating = false
    private(set) var authorized = false
    var errorMessage: String?
    @ObservationIgnored private let manager = CLLocationManager()
    @ObservationIgnored private var completion: ((CLLocationCoordinate2D) -> Void)?

    override init() {
        super.init()
        manager.delegate = self
        manager.desiredAccuracy = kCLLocationAccuracyHundredMeters
        refreshAuthorization()
    }

    func request(_ completion: @escaping (CLLocationCoordinate2D) -> Void) {
        self.completion = completion
        errorMessage = nil
        locating = true
        refreshAuthorization()
        if authorized { manager.requestLocation() }
        else if manager.authorizationStatus == .notDetermined { manager.requestWhenInUseAuthorization() }
        else { fail("map.location.permission") }
    }

    func cancel() {
        manager.stopUpdatingLocation()
        locating = false
        completion = nil
    }

    func locationManagerDidChangeAuthorization(_ manager: CLLocationManager) {
        refreshAuthorization()
        guard locating else { return }
        if authorized { manager.requestLocation() }
        else if manager.authorizationStatus != .notDetermined { fail("map.location.permission") }
    }

    func locationManager(_ manager: CLLocationManager, didUpdateLocations locations: [CLLocation]) {
        guard locating, let location = locations.last(where: { $0.horizontalAccuracy >= 0 }) else { return }
        let action = completion
        cancel()
        action?(location.coordinate)
    }

    func locationManager(_ manager: CLLocationManager, didFailWithError error: any Error) {
        guard locating else { return }
        fail((error as? CLError)?.code == .denied ? "map.location.permission" : "map.location.failed")
    }

    private func refreshAuthorization() {
#if os(macOS)
        authorized = manager.authorizationStatus == .authorizedAlways
#else
        authorized = manager.authorizationStatus == .authorizedAlways || manager.authorizationStatus == .authorizedWhenInUse
#endif
    }
    private func fail(_ key: String) {
        cancel()
        errorMessage = .localized(key)
    }
}
