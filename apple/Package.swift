// swift-tools-version: 6.2
import PackageDescription

let package = Package(
    name: "FuyaoPhotoMedia",
    platforms: [.macOS("26.0"), .iOS("26.0")],
    products: [.library(name: "PhotoRenderingCore", targets: ["PhotoRenderingCore"])],
    targets: [
        .target(name: "PhotoColorsCore", path: "Sources/Features/Colors/Models", resources: [.process("Resources")]),
        .target(name: "PhotoRenderingCore", path: "Sources/Features/PhotoInfo", exclude: ["Views", "Models/CardSession.swift", "Models/MetadataState.swift", "Services/MetadataPhotoLibrary.swift", "Services/CardPhotoLibrary.swift", "Services/PhotoSourceLoader.swift"],
            sources: ["Models/PhotoCard.swift", "Models/CardDocument.swift", "Models/PhotoSourceResources.swift", "Models/LensProfile.swift", "Models/LensProfileStore.swift", "Media", "Rendering", "Services/CardImageProcessor.swift", "Services/MovieMetadataCleaner.swift", "Services/PhotoWorkingDirectory.swift"]),
        .testTarget(name: "PhotoRenderingTests", dependencies: ["PhotoRenderingCore", "PhotoColorsCore"], path: "Tests/PhotoRenderingTests")
    ]
)
