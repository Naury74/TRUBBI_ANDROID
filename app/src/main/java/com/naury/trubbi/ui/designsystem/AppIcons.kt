package com.naury.trubbi.ui.designsystem

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.MyLocation
import androidx.compose.material.icons.rounded.Remove
import androidx.compose.material.icons.rounded.Star as MaterialStarFilled
import androidx.compose.ui.graphics.vector.ImageVector
import com.composables.icons.lucide.ArrowDown
import com.composables.icons.lucide.ArrowDownToLine
import com.composables.icons.lucide.ArrowLeft
import com.composables.icons.lucide.ArrowUp
import com.composables.icons.lucide.ArrowUpDown
import com.composables.icons.lucide.ArrowUpFromLine
import com.composables.icons.lucide.Bold
import com.composables.icons.lucide.Calendar
import com.composables.icons.lucide.Camera
import com.composables.icons.lucide.Check
import com.composables.icons.lucide.ChevronLeft
import com.composables.icons.lucide.ChevronRight
import com.composables.icons.lucide.Circle
import com.composables.icons.lucide.CircleCheck
import com.composables.icons.lucide.CircleDot
import com.composables.icons.lucide.Cloud
import com.composables.icons.lucide.CloudFog
import com.composables.icons.lucide.CloudLightning
import com.composables.icons.lucide.CloudRain
import com.composables.icons.lucide.CloudSnow
import com.composables.icons.lucide.Contact
import com.composables.icons.lucide.Copy
import com.composables.icons.lucide.Eye
import com.composables.icons.lucide.EyeOff
import com.composables.icons.lucide.FileText
import com.composables.icons.lucide.Flag
import com.composables.icons.lucide.FlipHorizontal
import com.composables.icons.lucide.Gauge
import com.composables.icons.lucide.GripVertical
import com.composables.icons.lucide.House
import com.composables.icons.lucide.Image
import com.composables.icons.lucide.Images
import com.composables.icons.lucide.Info
import com.composables.icons.lucide.Italic
import com.composables.icons.lucide.Keyboard
import com.composables.icons.lucide.LayoutGrid
import com.composables.icons.lucide.List
import com.composables.icons.lucide.ListOrdered
import com.composables.icons.lucide.Lock
import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.Mail
import com.composables.icons.lucide.MapPin
import com.composables.icons.lucide.Maximize2
import com.composables.icons.lucide.MessageSquare
import com.composables.icons.lucide.Mic
import com.composables.icons.lucide.Moon
import com.composables.icons.lucide.Navigation
import com.composables.icons.lucide.Pause
import com.composables.icons.lucide.Pencil
import com.composables.icons.lucide.Phone
import com.composables.icons.lucide.Play
import com.composables.icons.lucide.Plus
import com.composables.icons.lucide.QrCode
import com.composables.icons.lucide.RefreshCw
import com.composables.icons.lucide.RotateCcw
import com.composables.icons.lucide.RotateCw
import com.composables.icons.lucide.Route
import com.composables.icons.lucide.Save
import com.composables.icons.lucide.ScanLine
import com.composables.icons.lucide.Scissors
import com.composables.icons.lucide.Search
import com.composables.icons.lucide.Settings
import com.composables.icons.lucide.Share2
import com.composables.icons.lucide.SlidersHorizontal
import com.composables.icons.lucide.Sparkles
import com.composables.icons.lucide.SquarePen
import com.composables.icons.lucide.Star
import com.composables.icons.lucide.Strikethrough
import com.composables.icons.lucide.Sun
import com.composables.icons.lucide.Table
import com.composables.icons.lucide.Tag
import com.composables.icons.lucide.Trash2
import com.composables.icons.lucide.TriangleAlert
import com.composables.icons.lucide.Truck
import com.composables.icons.lucide.Underline
import com.composables.icons.lucide.Undo2
import com.composables.icons.lucide.Upload
import com.composables.icons.lucide.User
import com.composables.icons.lucide.Video
import com.composables.icons.lucide.Volume2
import com.composables.icons.lucide.VolumeX
import com.composables.icons.lucide.Wallet
import com.composables.icons.lucide.X
import com.composables.icons.lucide.Zap
import com.composables.icons.lucide.ZapOff

/**
 * 앱이 쓰는 아이콘 한곳 모음.
 *
 * 화면은 전부 `AppIcons.X` 로만 참조한다. 아이콘 세트를 바꾸거나 특정 글리프가 마음에 들지
 * 않아 교체할 때 여기 한 줄만 고치면 되고, 화면 코드에는 어떤 라이브러리를 쓰는지 드러나지 않는다.
 */
object AppIcons {
    val QrCode2: ImageVector get() = Lucide.QrCode
    val Description: ImageVector get() = Lucide.FileText

    // 즐겨찾기 켜짐은 채운 별, 꺼짐은 외곽선.
    val Star: ImageVector get() = Icons.Rounded.MaterialStarFilled
    val StarBorder: ImageVector get() = Lucide.Star
    val PlayArrow: ImageVector get() = Lucide.Play
    val Pause: ImageVector get() = Lucide.Pause
    val Trim: ImageVector get() = Lucide.Scissors
    val VolumeOn: ImageVector get() = Lucide.Volume2
    val VolumeOff: ImageVector get() = Lucide.VolumeX
    val CheckCircle: ImageVector get() = Lucide.CircleCheck
    val RadioButtonUnchecked: ImageVector get() = Lucide.Circle
    val PhotoCamera: ImageVector get() = Lucide.Camera
    val CameraAlt: ImageVector get() = Lucide.Camera
    val ArrowBackIos: ImageVector get() = Lucide.ArrowLeft
    val KeyboardArrowRight: ImageVector get() = Lucide.ChevronRight
    val Home: ImageVector get() = Lucide.House
    val PhotoLibrary: ImageVector get() = Lucide.Images
    val Settings: ImageVector get() = Lucide.Settings
    val Close: ImageVector get() = Lucide.X
    val Edit: ImageVector get() = Lucide.Pencil
    val EditNote: ImageVector get() = Lucide.SquarePen
    val Share: ImageVector get() = Lucide.Share2
    val Info: ImageVector get() = Lucide.Info
    val Delete: ImageVector get() = Lucide.Trash2
    val ContentCopy: ImageVector get() = Lucide.Copy
    val DarkMode: ImageVector get() = Lucide.Moon
    val AutoAwesome: ImageVector get() = Lucide.Sparkles
    val LocationOn: ImageVector get() = Lucide.MapPin
    val Save: ImageVector get() = Lucide.Save
    val Label: ImageVector get() = Lucide.Tag
    val HighQuality: ImageVector get() = Lucide.Gauge
    val Backup: ImageVector get() = Lucide.Upload
    val Restore: ImageVector get() = Lucide.RotateCcw
    val Contacts: ImageVector get() = Lucide.Contact
    val Phone: ImageVector get() = Lucide.Phone
    val Message: ImageVector get() = Lucide.MessageSquare
    val Widgets: ImageVector get() = Lucide.LayoutGrid
    val DragHandle: ImageVector get() = Lucide.GripVertical
    val Add: ImageVector get() = Lucide.Plus
    val Remove: ImageVector get() = Icons.Rounded.Remove
    val MyLocation: ImageVector get() = Icons.Rounded.MyLocation
    val QrCodeScanner: ImageVector get() = Lucide.ScanLine
    val Search: ImageVector get() = Lucide.Search
    val Person: ImageVector get() = Lucide.User
    val RotateLeft: ImageVector get() = Lucide.RotateCcw
    val RotateRight: ImageVector get() = Lucide.RotateCw
    val Flip: ImageVector get() = Lucide.FlipHorizontal
    val Refresh: ImageVector get() = Lucide.RefreshCw
    val Undo: ImageVector get() = Lucide.Undo2
    val FlashOn: ImageVector get() = Lucide.Zap
    val FlashOff: ImageVector get() = Lucide.ZapOff
    val Check: ImageVector get() = Lucide.Check
    val Videocam: ImageVector get() = Lucide.Video
    val FormatBold: ImageVector get() = Lucide.Bold
    val FormatItalic: ImageVector get() = Lucide.Italic
    val FormatUnderline: ImageVector get() = Lucide.Underline
    val FormatStrike: ImageVector get() = Lucide.Strikethrough
    val ListBullet: ImageVector get() = Lucide.List
    val ListNumber: ImageVector get() = Lucide.ListOrdered
    val ChevronLeft: ImageVector get() = Lucide.ChevronLeft
    val ChevronRight: ImageVector get() = Lucide.ChevronRight
    val CalendarMonth: ImageVector get() = Lucide.Calendar
    val Tune: ImageVector get() = Lucide.SlidersHorizontal
    val Keyboard: ImageVector get() = Lucide.Keyboard
    val ArrowUpward: ImageVector get() = Lucide.ArrowUp
    val ArrowDownward: ImageVector get() = Lucide.ArrowDown
    val ImageIcon: ImageVector get() = Lucide.Image
    val Fullscreen: ImageVector get() = Lucide.Maximize2
    val TableIcon: ImageVector get() = Lucide.Table
    val Mic: ImageVector get() = Lucide.Mic
    val Mail: ImageVector get() = Lucide.Mail
    val Lock: ImageVector get() = Lucide.Lock
    val Eye: ImageVector get() = Lucide.Eye
    val EyeOff: ImageVector get() = Lucide.EyeOff
    val Truck: ImageVector get() = Lucide.Truck
    val Flag: ImageVector get() = Lucide.Flag
    val SwapVert: ImageVector get() = Lucide.ArrowUpDown
    val Route: ImageVector get() = Lucide.Route
    val Navigation: ImageVector get() = Lucide.Navigation
    val OriginDot: ImageVector get() = Lucide.CircleDot
    val Wallet: ImageVector get() = Lucide.Wallet
    val Warning: ImageVector get() = Lucide.TriangleAlert
    val Pickup: ImageVector get() = Lucide.ArrowUpFromLine
    val Dropoff: ImageVector get() = Lucide.ArrowDownToLine
    val WeatherClear: ImageVector get() = Lucide.Sun
    val WeatherClouds: ImageVector get() = Lucide.Cloud
    val WeatherRain: ImageVector get() = Lucide.CloudRain
    val WeatherSnow: ImageVector get() = Lucide.CloudSnow
    val WeatherThunder: ImageVector get() = Lucide.CloudLightning
    val WeatherFog: ImageVector get() = Lucide.CloudFog
}
