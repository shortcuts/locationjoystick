package com.locationjoystick.core.designsystem

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.DirectionsBike
import androidx.compose.material.icons.automirrored.rounded.DirectionsRun
import androidx.compose.material.icons.automirrored.rounded.DirectionsWalk
import androidx.compose.material.icons.automirrored.rounded.OpenInNew
import androidx.compose.material.icons.automirrored.rounded.Undo
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.AddLocationAlt
import androidx.compose.material.icons.rounded.ArrowDropDown
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.ContentPaste
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.DeveloperMode
import androidx.compose.material.icons.rounded.Directions
import androidx.compose.material.icons.rounded.DirectionsCar
import androidx.compose.material.icons.rounded.DragHandle
import androidx.compose.material.icons.rounded.EditNote
import androidx.compose.material.icons.rounded.ExpandLess
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.Explore
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.FavoriteBorder
import androidx.compose.material.icons.rounded.FileDownload
import androidx.compose.material.icons.rounded.FileUpload
import androidx.compose.material.icons.rounded.Forum
import androidx.compose.material.icons.rounded.Fullscreen
import androidx.compose.material.icons.rounded.FullscreenExit
import androidx.compose.material.icons.rounded.Group
import androidx.compose.material.icons.rounded.Hiking
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Layers
import androidx.compose.material.icons.rounded.LocationOff
import androidx.compose.material.icons.rounded.LocationOn
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.LockOpen
import androidx.compose.material.icons.rounded.Loop
import androidx.compose.material.icons.rounded.Map
import androidx.compose.material.icons.rounded.Menu
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.MyLocation
import androidx.compose.material.icons.rounded.NewReleases
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Route
import androidx.compose.material.icons.rounded.Save
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.SkipPrevious
import androidx.compose.material.icons.rounded.Speed
import androidx.compose.material.icons.rounded.SportsEsports
import androidx.compose.material.icons.rounded.Stop
import androidx.compose.material.icons.rounded.SwapVert
import androidx.compose.material.icons.rounded.Terrain
import androidx.compose.material.icons.rounded.Timer
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

object LjIcons {
    val NorthUp: ImageVector by lazy {
        ImageVector
            .Builder(
                name = "NorthUp",
                defaultWidth = 24.dp,
                defaultHeight = 24.dp,
                viewportWidth = 24f,
                viewportHeight = 24f,
            ).apply {
                path(fill = SolidColor(Color.Black)) {
                    moveTo(7.4f, 7f)
                    lineTo(7.4f, 1.5f)
                    lineTo(9.3f, 1.5f)
                    lineTo(14.6f, 5.1f)
                    lineTo(14.6f, 1.5f)
                    lineTo(16.6f, 1.5f)
                    lineTo(16.6f, 7f)
                    lineTo(14.7f, 7f)
                    lineTo(9.4f, 3.4f)
                    lineTo(9.4f, 7f)
                    close()
                }
                path(fill = SolidColor(Color.Black), pathFillType = PathFillType.EvenOdd) {
                    moveTo(12f, 8f)
                    lineTo(4.5f, 22.5f)
                    lineTo(12f, 18.6f)
                    lineTo(19.5f, 22.5f)
                    close()
                    moveTo(12.8f, 11.5f)
                    lineTo(12.8f, 16.9f)
                    lineTo(17f, 19.1f)
                    close()
                }
            }.build()
    }
    val Add = Icons.Rounded.Add
    val ArrowBack = Icons.AutoMirrored.Rounded.ArrowBack
    val ArrowDropDown = Icons.Rounded.ArrowDropDown
    val Check = Icons.Rounded.Check
    val CheckCircle = Icons.Rounded.CheckCircle
    val Close = Icons.Rounded.Close
    val ContentCopy = Icons.Rounded.ContentCopy
    val ContentPaste = Icons.Rounded.ContentPaste
    val Delete = Icons.Rounded.Delete
    val DeveloperMode = Icons.Rounded.DeveloperMode
    val DirectionsBike = Icons.AutoMirrored.Rounded.DirectionsBike
    val DirectionsCar = Icons.Rounded.DirectionsCar
    val DirectionsRun = Icons.AutoMirrored.Rounded.DirectionsRun
    val DirectionsWalk = Icons.AutoMirrored.Rounded.DirectionsWalk
    val Directions = Icons.Rounded.Directions
    val DragHandle = Icons.Rounded.DragHandle
    val Edit = Icons.Rounded.EditNote
    val ExpandLess = Icons.Rounded.ExpandLess
    val ExpandMore = Icons.Rounded.ExpandMore
    val Fullscreen = Icons.Rounded.Fullscreen
    val FullscreenExit = Icons.Rounded.FullscreenExit
    val Explore = Icons.Rounded.Explore
    val Favorite = Icons.Rounded.Favorite
    val FavoriteBorder = Icons.Rounded.FavoriteBorder
    val FileDownload = Icons.Rounded.FileDownload
    val FileUpload = Icons.Rounded.FileUpload
    val Forum = Icons.Rounded.Forum
    val Group = Icons.Rounded.Group
    val Hiking = Icons.Rounded.Hiking
    val Home = Icons.Rounded.Home
    val Info = Icons.Rounded.Info
    val Joystick = Icons.Rounded.SportsEsports
    val Layers = Icons.Rounded.Layers
    val Lock = Icons.Rounded.Lock
    val LockOpen = Icons.Rounded.LockOpen
    val LocationOff = Icons.Rounded.LocationOff
    val LocationOn = Icons.Rounded.LocationOn
    val Loop = Icons.Rounded.Loop
    val OpenInNew = Icons.AutoMirrored.Rounded.OpenInNew
    val Map = Icons.Rounded.Map
    val Menu = Icons.Rounded.Menu
    val MoreVert = Icons.Rounded.MoreVert
    val MyLocation = Icons.Rounded.MyLocation
    val Pause = Icons.Rounded.Pause
    val PlayArrow = Icons.Rounded.PlayArrow
    val Route = Icons.Rounded.Route
    val Save = Icons.Rounded.Save
    val Search = Icons.Rounded.Search
    val Share = Icons.Rounded.Share
    val Settings = Icons.Rounded.Settings
    val Speed = Icons.Rounded.Speed
    val SkipNext = Icons.Rounded.SkipNext
    val SkipPrevious = Icons.Rounded.SkipPrevious
    val Stop = Icons.Rounded.Stop
    val SwapVert = Icons.Rounded.SwapVert
    val Terrain = Icons.Rounded.Terrain
    val Timer = Icons.Rounded.Timer
    val Undo = Icons.AutoMirrored.Rounded.Undo
    val WhatsNew = Icons.Rounded.NewReleases
    val Visibility = Icons.Rounded.Visibility
    val AddLocationAlt = Icons.Rounded.AddLocationAlt
}
