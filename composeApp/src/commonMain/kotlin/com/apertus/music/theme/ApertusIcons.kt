package com.apertus.music.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathFillType
import androidx.compose.ui.graphics.vector.PathNode
import androidx.compose.ui.unit.dp

/**
 * Apertus' own icon set.
 *
 * Why hand-authored instead of `Icons.Filled.*`:
 *  - `material-icons-extended` is frozen by the Compose plugin at 1.7.3 and the
 *    upstream project states it will receive no further updates.
 *  - The stock Material glyphs are literally the same shapes every other app
 *    ships. These are drawn for this app: heavier strokes, a wider optical
 *    weight, and flat 24dp geometry that reads cleanly at both 32dp transport
 *    sizes and 20dp in the navigation bar.
 *  - No dependency, no download, no R8 surprises — every path is right here.
 *
 * All glyphs live on a 24x24 viewport and are filled with a single solid colour;
 * [androidx.compose.material3.Icon] tints them, so the fill colour here only
 * matters when the vector is drawn untinted.
 */
object ApertusIcons {

    /** Play — a plain triangle, nudge the apex right so it optically centres. */
    val Play: ImageVector by lazy {
        vector(
            "Apertus.Play",
            listOf(
                PathNode.MoveTo(7.5f, 4.8f),
                PathNode.LineTo(19.2f, 12f),
                PathNode.LineTo(7.5f, 19.2f),
                PathNode.Close
            )
        )
    }

    /** Pause — two bars, deliberately thicker than the Material glyph. */
    val Pause: ImageVector by lazy {
        vector(
            "Apertus.Pause",
            listOf(
                PathNode.MoveTo(6.8f, 4.8f),
                PathNode.LineTo(9.9f, 4.8f),
                PathNode.LineTo(9.9f, 19.2f),
                PathNode.LineTo(6.8f, 19.2f),
                PathNode.Close,
                PathNode.MoveTo(14.1f, 4.8f),
                PathNode.LineTo(17.2f, 4.8f),
                PathNode.LineTo(17.2f, 19.2f),
                PathNode.LineTo(14.1f, 19.2f),
                PathNode.Close
            )
        )
    }

    val SkipNext: ImageVector by lazy {
        vector(
            "Apertus.SkipNext",
            listOf(
                PathNode.MoveTo(5.5f, 4.8f),
                PathNode.LineTo(15.4f, 12f),
                PathNode.LineTo(5.5f, 19.2f),
                PathNode.Close,
                PathNode.MoveTo(16.6f, 4.8f),
                PathNode.LineTo(19.2f, 4.8f),
                PathNode.LineTo(19.2f, 19.2f),
                PathNode.LineTo(16.6f, 19.2f),
                PathNode.Close
            )
        )
    }

    val SkipPrevious: ImageVector by lazy {
        vector(
            "Apertus.SkipPrevious",
            listOf(
                PathNode.MoveTo(18.5f, 4.8f),
                PathNode.LineTo(8.6f, 12f),
                PathNode.LineTo(18.5f, 19.2f),
                PathNode.Close,
                PathNode.MoveTo(4.8f, 4.8f),
                PathNode.LineTo(7.4f, 4.8f),
                PathNode.LineTo(7.4f, 19.2f),
                PathNode.LineTo(4.8f, 19.2f),
                PathNode.Close
            )
        )
    }

    /** Back — auto-mirrored so RTL locales get the arrow pointing correctly. */
    val ArrowBack: ImageVector by lazy {
        vector(
            "Apertus.ArrowBack",
            listOf(
                PathNode.MoveTo(4f, 12f),
                PathNode.LineTo(11f, 5f),
                PathNode.LineTo(12.9f, 6.9f),
                PathNode.LineTo(9.2f, 10.6f),
                PathNode.LineTo(20f, 10.6f),
                PathNode.LineTo(20f, 13.4f),
                PathNode.LineTo(9.2f, 13.4f),
                PathNode.LineTo(12.9f, 17.1f),
                PathNode.LineTo(11f, 19f),
                PathNode.Close
            ),
            autoMirror = true
        )
    }

    /** Home — a roof band sitting on a peaked body. */
    val Home: ImageVector by lazy {
        vector(
            "Apertus.Home",
            listOf(
                PathNode.MoveTo(12f, 2.8f),
                PathNode.LineTo(21.6f, 12f),
                PathNode.LineTo(18.8f, 12f),
                PathNode.LineTo(12f, 5.4f),
                PathNode.LineTo(5.2f, 12f),
                PathNode.LineTo(2.4f, 12f),
                PathNode.Close,
                PathNode.MoveTo(5.6f, 11f),
                PathNode.LineTo(12f, 5.2f),
                PathNode.LineTo(18.4f, 11f),
                PathNode.LineTo(18.4f, 21f),
                PathNode.LineTo(5.6f, 21f),
                PathNode.Close
            )
        )
    }

    /** Settings — a "tune" mark; sliders read better than a gear at small sizes. */
    val Tune: ImageVector by lazy {
        vector(
            "Apertus.Tune",
            listOf(
                PathNode.MoveTo(3f, 5f),
                PathNode.LineTo(21f, 5f),
                PathNode.LineTo(21f, 7f),
                PathNode.LineTo(3f, 7f),
                PathNode.Close,
                PathNode.MoveTo(7.6f, 3f),
                PathNode.LineTo(10.4f, 3f),
                PathNode.LineTo(10.4f, 9f),
                PathNode.LineTo(7.6f, 9f),
                PathNode.Close,
                PathNode.MoveTo(3f, 11f),
                PathNode.LineTo(21f, 11f),
                PathNode.LineTo(21f, 13f),
                PathNode.LineTo(3f, 13f),
                PathNode.Close,
                PathNode.MoveTo(13.6f, 9f),
                PathNode.LineTo(16.4f, 9f),
                PathNode.LineTo(16.4f, 15f),
                PathNode.LineTo(13.6f, 15f),
                PathNode.Close,
                PathNode.MoveTo(3f, 17f),
                PathNode.LineTo(21f, 17f),
                PathNode.LineTo(21f, 19f),
                PathNode.LineTo(3f, 19f),
                PathNode.Close,
                PathNode.MoveTo(7.6f, 15f),
                PathNode.LineTo(10.4f, 15f),
                PathNode.LineTo(10.4f, 21f),
                PathNode.LineTo(7.6f, 21f),
                PathNode.Close
            )
        )
    }

    /** Library — a stack of rows, the last one short. */
    val Library: ImageVector by lazy {
        vector(
            "Apertus.Library",
            listOf(
                PathNode.MoveTo(3f, 4.5f),
                PathNode.LineTo(21f, 4.5f),
                PathNode.LineTo(21f, 6.7f),
                PathNode.LineTo(3f, 6.7f),
                PathNode.Close,
                PathNode.MoveTo(3f, 10.9f),
                PathNode.LineTo(21f, 10.9f),
                PathNode.LineTo(21f, 13.1f),
                PathNode.LineTo(3f, 13.1f),
                PathNode.Close,
                PathNode.MoveTo(3f, 17.3f),
                PathNode.LineTo(14f, 17.3f),
                PathNode.LineTo(14f, 19.5f),
                PathNode.LineTo(3f, 19.5f),
                PathNode.Close
            )
        )
    }

    /** Check — used for the selected row in the settings list. */
    val Check: ImageVector by lazy {
        vector(
            "Apertus.Check",
            listOf(
                PathNode.MoveTo(9.2f, 16.2f),
                PathNode.LineTo(4.8f, 11.8f),
                PathNode.LineTo(3.4f, 13.2f),
                PathNode.LineTo(9.2f, 19f),
                PathNode.LineTo(20.6f, 7.6f),
                PathNode.LineTo(19.2f, 6.2f),
                PathNode.Close
            )
        )
    }

    /** Equaliser bars — the "this track is playing" mark in lists. */
    val Equalizer: ImageVector by lazy {
        vector(
            "Apertus.Equalizer",
            listOf(
                PathNode.MoveTo(3.6f, 12.5f),
                PathNode.LineTo(6.4f, 12.5f),
                PathNode.LineTo(6.4f, 20.5f),
                PathNode.LineTo(3.6f, 20.5f),
                PathNode.Close,
                PathNode.MoveTo(10.6f, 6.5f),
                PathNode.LineTo(13.4f, 6.5f),
                PathNode.LineTo(13.4f, 20.5f),
                PathNode.LineTo(10.6f, 20.5f),
                PathNode.Close,
                PathNode.MoveTo(17.6f, 9.5f),
                PathNode.LineTo(20.4f, 9.5f),
                PathNode.LineTo(20.4f, 20.5f),
                PathNode.LineTo(17.6f, 20.5f),
                PathNode.Close
            )
        )
    }

    /**
     * App mark — a hexagonal aperture with a play triangle floating in the
     * opening. Even-odd fill carves the ring, then fills the triangle again
     * because it sits one level deeper.
     */
    val Logo: ImageVector by lazy {
        vector(
            "Apertus.Logo",
            listOf(
                // outer hexagon, r = 10 about (12, 12)
                PathNode.MoveTo(22f, 12f),
                PathNode.LineTo(17f, 20.66f),
                PathNode.LineTo(7f, 20.66f),
                PathNode.LineTo(2f, 12f),
                PathNode.LineTo(7f, 3.34f),
                PathNode.LineTo(17f, 3.34f),
                PathNode.Close,
                // inner hexagon, r = 7
                PathNode.MoveTo(19f, 12f),
                PathNode.LineTo(15.5f, 18.06f),
                PathNode.LineTo(8.5f, 18.06f),
                PathNode.LineTo(5f, 12f),
                PathNode.LineTo(8.5f, 5.94f),
                PathNode.LineTo(15.5f, 5.94f),
                PathNode.Close,
                // play triangle inside the opening
                PathNode.MoveTo(10.2f, 8.4f),
                PathNode.LineTo(16f, 12f),
                PathNode.LineTo(10.2f, 15.6f),
                PathNode.Close
            ),
            fillType = PathFillType.EvenOdd
        )
    }
}

private fun vector(
    name: String,
    nodes: List<PathNode>,
    fillType: PathFillType = PathFillType.NonZero,
    autoMirror: Boolean = false
): ImageVector = ImageVector.Builder(
    name = name,
    defaultWidth = 24.dp,
    defaultHeight = 24.dp,
    viewportWidth = 24f,
    viewportHeight = 24f,
    autoMirror = autoMirror
).addPath(
    pathData = nodes,
    pathFillType = fillType,
    fill = SolidColor(Color.Black)
).build()
