// swift-tools-version: 6.2
import PackageDescription

let package = Package(
    name: "FuyaoPhotoMedia",
    platforms: [.macOS("26.0"), .iOS("26.0")],
    products: [.library(name: "PhotoRenderingCore", targets: ["PhotoRenderingCore"])],
    targets: [
        .target(name: "PhotoRenderingCore", path: "Sources/Features/PhotoInfo", exclude: ["Views", "Models/CardSession.swift", "Models/CardDocument.swift", "Models/PhotoSourceResources.swift", "Services/CardPhotoLibrary.swift", "Services/PhotoSourceLoader.swift", "Services/PhotoWorkingDirectory.swift"],
            sources: ["Models/PhotoCard.swift", "Media", "Rendering", "Services/CardImageProcessor.swift", "Services/MovieMetadataCleaner.swift"]),
        .testTarget(name: "PhotoRenderingTests", dependencies: ["PhotoRenderingCore"], path: "Tests/PhotoRenderingTests")
    ]
)
