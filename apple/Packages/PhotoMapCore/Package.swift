// swift-tools-version: 6.2
import PackageDescription

let package = Package(
    name: "PhotoMapCore",
    platforms: [.macOS("26.0"), .iOS("26.0")],
    products: [
        .library(name: "PhotoMapCore", targets: ["PhotoMapCore"]),
        .executable(name: "PhotoMapBenchmarks", targets: ["PhotoMapBenchmarks"])
    ],
    targets: [
        .target(name: "PhotoMapCore"),
        .executableTarget(name: "PhotoMapBenchmarks", dependencies: ["PhotoMapCore"]),
        .testTarget(name: "PhotoMapCoreTests", dependencies: ["PhotoMapCore"])
    ]
)
