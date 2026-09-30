package ing.fuyaoskyrocket.photoinfo.domain.media

import java.io.File
import java.io.FileOutputStream

/** Reopens the package as one private motion-photo source for the existing Android editor. */
internal object FuyaoPackageMedia {
    fun assemble(contents: FuyaoPhotosPackage.Contents, destination: File) {
        MotionPhoto.validateVideo(contents.movie, 0, contents.movie.length())
        if (contents.photo.extension == "jpg") {
            val layout = JpegContainer.inspect(contents.photo)
            val source = MotionPhoto.inspect(contents.photo, "image/jpeg")
            require(source.motion == null && !source.blocked)
            val motion = MotionVideo(0, contents.movie.length(), contents.stillImageTimeUs, "video/quicktime")
            JpegContainer.rewrite(contents.photo, destination, JpegContainer.exif(layout), MotionPhoto.xmp(layout, motion))
            FileOutputStream(destination, true).use { output ->
                source.portraitTail?.let { JpegContainer.copyRange(contents.photo, it.offset, it.length, output) }
                contents.movie.inputStream().use { it.copyTo(output) }
            }
            val result = requireNotNull(MotionPhoto.inspect(destination, "image/jpeg").motion)
            require(MotionPhoto.digest(destination, result.offset, result.length)
                .contentEquals(MotionPhoto.digest(contents.movie, 0, contents.movie.length())))
        } else {
            val graph = HeifImageContainer.read(contents.photo)
            graph.withMotionDirectory(contents.stillImageTimeUs, "video/quicktime", contents.movie.length()).write(destination)
            FileOutputStream(destination, true).use { output ->
                require(contents.movie.length() <= Int.MAX_VALUE - 8)
                output.write(IsoBmff.data { writeInt(contents.movie.length().toInt() + 8); writeBytes("mpvd") })
                contents.movie.inputStream().use { it.copyTo(output) }
            }
        }
    }
}
