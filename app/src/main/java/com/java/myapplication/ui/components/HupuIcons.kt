package com.java.myapplication.ui.components

import androidx.compose.material.icons.materialIcon
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.addPathNodes

/**
 * 按需 Material 圆角图标：直接嵌入 Google 官方 24px SVG path 数据（addPathNodes 解析）。
 * 不引入 material-icons-extended 全量包（debug 不做 shrink，全量包会把 dex 拖大十几 MB），
 * 每个图标仅 ~0.5KB path 字符串，真正按需。
 */
object HupuIcons {
    val History: ImageVector = materialIcon(name = "HupuIcons.History") {
        addPath(fill = SolidColor(Color.Black), pathData = addPathNodes("M13.26 3C8.17 2.86 4 6.95 4 12H2.21c-.45 0-.67.54-.35.85l2.79 2.8c.2.2.51.2.71 0l2.79-2.8c.31-.31.09-.85-.36-.85H6c0-3.9 3.18-7.05 7.1-7 3.72.05 6.85 3.18 6.9 6.9.05 3.91-3.1 7.1-7 7.1-1.61 0-3.1-.55-4.28-1.48-.4-.31-.96-.28-1.32.08-.42.42-.39 1.13.08 1.49C9 20.29 10.91 21 13 21c5.05 0 9.14-4.17 9-9.26-.13-4.69-4.05-8.61-8.74-8.74zm-.51 5c-.41 0-.75.34-.75.75v3.68c0 .35.19.68.49.86l3.12 1.85c.36.21.82.09 1.03-.26.21-.36.09-.82-.26-1.03l-2.88-1.71v-3.4c0-.4-.34-.74-.75-.74z"))
    }
    val Tune: ImageVector = materialIcon(name = "HupuIcons.Tune") {
        addPath(fill = SolidColor(Color.Black), pathData = addPathNodes("M3 18c0 .55.45 1 1 1h5v-2H4c-.55 0-1 .45-1 1zM3 6c0 .55.45 1 1 1h9V5H4c-.55 0-1 .45-1 1zm10 14v-1h7c.55 0 1-.45 1-1s-.45-1-1-1h-7v-1c0-.55-.45-1-1-1s-1 .45-1 1v4c0 .55.45 1 1 1s1-.45 1-1zM7 10v1H4c-.55 0-1 .45-1 1s.45 1 1 1h3v1c0 .55.45 1 1 1s1-.45 1-1v-4c0-.55-.45-1-1-1s-1 .45-1 1zm14 2c0-.55-.45-1-1-1h-9v2h9c.55 0 1-.45 1-1zm-5-3c.55 0 1-.45 1-1V7h3c.55 0 1-.45 1-1s-.45-1-1-1h-3V4c0-.55-.45-1-1-1s-1 .45-1 1v4c0 .55.45 1 1 1z"))
    }
    val FilterAlt: ImageVector = materialIcon(name = "HupuIcons.FilterAlt") {
        addPath(fill = SolidColor(Color.Black), pathData = addPathNodes("M4.25,5.61C6.57,8.59,10,13,10,13v5c0,1.1,0.9,2,2,2h0c1.1,0,2-0.9,2-2v-5c0,0,3.43-4.41,5.75-7.39 C20.26,4.95,19.79,4,18.95,4H5.04C4.21,4,3.74,4.95,4.25,5.61z"))
    }
    val FormatSize: ImageVector = materialIcon(name = "HupuIcons.FormatSize") {
        addPath(fill = SolidColor(Color.Black), pathData = addPathNodes("M9 5.5c0 .83.67 1.5 1.5 1.5H14v10.5c0 .83.67 1.5 1.5 1.5s1.5-.67 1.5-1.5V7h3.5c.83 0 1.5-.67 1.5-1.5S21.33 4 20.5 4h-10C9.67 4 9 4.67 9 5.5zM4.5 12H6v5.5c0 .83.67 1.5 1.5 1.5S9 18.33 9 17.5V12h1.5c.83 0 1.5-.67 1.5-1.5S11.33 9 10.5 9h-6C3.67 9 3 9.67 3 10.5S3.67 12 4.5 12z"))
    }
    val Logout: ImageVector = materialIcon(name = "HupuIcons.Logout") {
        addPath(fill = SolidColor(Color.Black), pathData = addPathNodes("M5,5h6c0.55,0,1-0.45,1-1v0c0-0.55-0.45-1-1-1H5C3.9,3,3,3.9,3,5v14c0,1.1,0.9,2,2,2h6c0.55,0,1-0.45,1-1v0 c0-0.55-0.45-1-1-1H5V5z"))
        addPath(fill = SolidColor(Color.Black), pathData = addPathNodes("M20.65,11.65l-2.79-2.79C17.54,8.54,17,8.76,17,9.21V11h-7c-0.55,0-1,0.45-1,1v0c0,0.55,0.45,1,1,1h7v1.79 c0,0.45,0.54,0.67,0.85,0.35l2.79-2.79C20.84,12.16,20.84,11.84,20.65,11.65z"))
    }
    val Speed: ImageVector = materialIcon(name = "HupuIcons.Speed") {
        addPath(fill = SolidColor(Color.Black), pathData = addPathNodes("M19.46 10a1 1 0 0 0-.07 1 7.55 7.55 0 0 1 .52 1.81 8 8 0 0 1-.69 4.73 1 1 0 0 1-.89.53H5.68a1 1 0 0 1-.89-.54A8 8 0 0 1 13 6.06a7.69 7.69 0 0 1 2.11.56 1 1 0 0 0 1-.07 1 1 0 0 0-.17-1.76A10 10 0 0 0 3.35 19a2 2 0 0 0 1.72 1h13.85a2 2 0 0 0 1.74-1 10 10 0 0 0 .55-8.89 1 1 0 0 0-1.75-.11z"))
        addPath(fill = SolidColor(Color.Black), pathData = addPathNodes("M10.59 12.59a2 2 0 0 0 2.83 2.83l5.66-8.49z"))
    }
    val ManageSearch: ImageVector = materialIcon(name = "HupuIcons.ManageSearch") {
        addPath(fill = SolidColor(Color.Black), pathData = addPathNodes("M6,9H3C2.45,9,2,8.55,2,8v0c0-0.55,0.45-1,1-1h3c0.55,0,1,0.45,1,1v0C7,8.55,6.55,9,6,9z M6,12H3c-0.55,0-1,0.45-1,1v0 c0,0.55,0.45,1,1,1h3c0.55,0,1-0.45,1-1v0C7,12.45,6.55,12,6,12z M19.88,18.29l-3.12-3.12c-0.86,0.56-1.89,0.88-3,0.82 c-2.37-0.11-4.4-1.96-4.72-4.31C8.6,8.33,11.49,5.5,14.87,6.07c1.95,0.33,3.57,1.85,4,3.78c0.33,1.46,0.01,2.82-0.7,3.9l3.13,3.13 c0.39,0.39,0.39,1.02,0,1.41l0,0C20.91,18.68,20.27,18.68,19.88,18.29z M17,11c0-1.65-1.35-3-3-3s-3,1.35-3,3s1.35,3,3,3 S17,12.65,17,11z M3,19h8c0.55,0,1-0.45,1-1v0c0-0.55-0.45-1-1-1H3c-0.55,0-1,0.45-1,1v0C2,18.55,2.45,19,3,19z"))
    }
    val CopyAll: ImageVector = materialIcon(name = "HupuIcons.CopyAll") {
        addPath(fill = SolidColor(Color.Black), pathData = addPathNodes("M18,2H9C7.9,2,7,2.9,7,4v12c0,1.1,0.9,2,2,2h9c1.1,0,2-0.9,2-2V4C20,2.9,19.1,2,18,2z M18,16H9V4h9V16z M3,15v-2h2v2H3z M3,9.5h2v2H3V9.5z M10,20h2v2h-2V20z M3,18.5v-2h2v2H3z M5,22c-1.1,0-2-0.9-2-2h2V22z M8.5,22h-2v-2h2V22z M13.5,22L13.5,22l0-2h2 v0C15.5,21.1,14.6,22,13.5,22z M5,6L5,6l0,2H3v0C3,6.9,3.9,6,5,6z"))
    }
    val StarRate: ImageVector = materialIcon(name = "HupuIcons.StarRate") {
        addPath(fill = SolidColor(Color.Black), pathData = addPathNodes("M14.43,10l-1.47-4.84c-0.29-0.95-1.63-0.95-1.91,0L9.57,10H5.12c-0.97,0-1.37,1.25-0.58,1.81l3.64,2.6l-1.43,4.61 c-0.29,0.93,0.79,1.68,1.56,1.09L12,17.31l3.69,2.81c0.77,0.59,1.85-0.16,1.56-1.09l-1.43-4.61l3.64-2.6 c0.79-0.57,0.39-1.81-0.58-1.81H14.43z"))
    }
    /** 表情按钮（Material mood 24px） */
    val EmojiMood: ImageVector = materialIcon(name = "HupuIcons.EmojiMood") {
        addPath(fill = SolidColor(Color.Black), pathData = addPathNodes("M11.99,2C6.47,2 2,6.48 2,12s4.47,10 9.99,10C17.52,22 22,17.52 22,12S17.52,2 11.99,2zM12,20c-4.42,0 -8,-3.58 -8,-8s3.58,-8 8,-8 8,3.58 8,8 -3.58,8 -8,8zM15.5,9c0.83,0 1.5,-0.67 1.5,-1.5S16.33,6 15.5,6 14,6.67 14,7.5 14.67,9 15.5,9zM8.5,9C9.33,9 10,8.33 10,7.5S9.33,6 8.5,6 7,6.67 7,7.5 7.67,9 8.5,9zM12,17.5c2.33,0 4.31,-1.46 5.11,-3.5H6.89c0.8,2.04 2.78,3.5 5.11,3.5z"))
    }
    /** 图片按钮（Material image 24px） */
    val ImageIcon: ImageVector = materialIcon(name = "HupuIcons.ImageIcon") {
        addPath(fill = SolidColor(Color.Black), pathData = addPathNodes("M21,19V5c0,-1.1 -0.9,-2 -2,-2H5c-1.1,0 -2,0.9 -2,2v14c0,1.1 0.9,2 2,2h14c1.1,0 2,-0.9 2,-2zM8.5,13.5l2.5,3.01L14.5,12l4.5,6H5l3.5,-4.5z"))
    }
    /** 转发箭头：弧线向右拐（Material reply 水平镜像，无方框，官方 24px path 变换） */
    val IosShare: ImageVector = materialIcon(name = "HupuIcons.IosShare") {
        addPath(fill = SolidColor(Color.Black), pathData = addPathNodes("M14,9L14,5l7,7l-7,7v-4.1c-5,0,-8.5,1.6,-11,5.1C4,15,7,10,14,9z"))
    }
    /** @提及（Material alternate_email 24px） */
    val At: ImageVector = materialIcon(name = "HupuIcons.At") {
        addPath(fill = SolidColor(Color.Black), pathData = addPathNodes("M12,2C6.48,2 2,6.48 2,12c0,5.52 4.48,10 10,10h5v-2h-5c-4.34,0 -8,-3.66 -8,-8s3.66,-8 8,-8 8,3.66 8,8v1.43c0,0.79 -0.71,1.57 -1.5,1.57S17,14.22 17,13.43V12c0,-2.76 -2.24,-5 -5,-5s-5,2.24 -5,5 2.24,5 5,5c1.38,0 2.64,-0.56 3.54,-1.47 0.65,0.89 1.77,1.47 2.96,1.47 1.97,0 3.5,-1.6 3.5,-3.57V12c0,-5.52 -4.48,-10 -10,-10zM12,15c-1.66,0 -3,-1.34 -3,-3s1.34,-3 3,-3 3,1.34 3,3 -1.34,3 -3,3z"))
    }
    /** 评论气泡（Material chat_bubble 24px） */
    val Comment: ImageVector = materialIcon(name = "HupuIcons.Comment") {
        addPath(fill = SolidColor(Color.Black), pathData = addPathNodes("M20,2L4,2c-1.1,0 -1.99,0.9 -1.99,2L2,22l4,-4h14c1.1,0 2,-0.9 2,-2L22,4c0,-1.1 -0.9,-2 -2,-2z"))
    }
    /** 点亮（Material thumb_up 24px） */
    val Light: ImageVector = materialIcon(name = "HupuIcons.Light") {
        addPath(fill = SolidColor(Color.Black), pathData = addPathNodes("M1,21h4L5,9L1,9v12zM23,10c0,-1.1 -0.9,-2 -2,-2h-6.31l0.95,-4.57 0.03,-0.32c0,-0.41 -0.17,-0.79 -0.44,-1.06L14.17,1 7.59,7.59C7.22,7.95 7,8.45 7,9v10c0,1.1 0.9,2 2,2h9c0.83,0 1.54,-0.5 1.84,-1.22l3.02,-7.05c0.09,-0.23 0.14,-0.47 0.14,-0.73v-2z"))
    }
    /** 消息铃铛（Material notifications 24px） */
    val Bell: ImageVector = materialIcon(name = "HupuIcons.Bell") {
        addPath(fill = SolidColor(Color.Black), pathData = addPathNodes("M12,22c1.1,0 2,-0.9 2,-2h-4c0,1.1 0.9,2 2,2zM18,16v-5c0,-3.07 -1.63,-5.64 -4.5,-6.32L13.5,4c0,-0.83 -0.67,-1.5 -1.5,-1.5s-1.5,0.67 -1.5,1.5v0.68C7.64,5.36 6,7.92 6,11v5l-2,2v1h16v-1l-2,-2z"))
    }
    /** 发帖（Material edit 24px 铅笔） */
    val Edit: ImageVector = materialIcon(name = "HupuIcons.Edit") {
        addPath(fill = SolidColor(Color.Black), pathData = addPathNodes("M3,17.25V21h3.75L17.81,9.94l-3.75,-3.75L3,17.25zM20.71,7.04c0.39,-0.39 0.39,-1.02 0,-1.41l-2.34,-2.34c-0.39,-0.39 -1.02,-0.39 -1.41,0l-1.83,1.83 3.75,3.75 1.83,-1.83z"))
    }
    /** 视频（Material videocam 24px），用于发帖页添加视频 */
    val Videocam: ImageVector = materialIcon(name = "HupuIcons.Videocam") {
        addPath(fill = SolidColor(Color.Black), pathData = addPathNodes("M17,10.5V7c0,-0.55 -0.45,-1 -1,-1H4c-0.55,0 -1,0.45 -1,1v10c0,0.55 0.45,1 1,1h12c0.55,0 1,-0.45 1,-1v-3.5l4,4v-11l-4,4z"))
    }
    /** 播放三角（Material play_arrow 24px） */
    val PlayArrow: ImageVector = materialIcon(name = "HupuIcons.PlayArrow") {
        addPath(fill = SolidColor(Color.Black), pathData = addPathNodes("M8,5v14l11,-7z"))
    }
    /** 投票（Material poll 24px），用于发帖页添加投票 */
    val Poll: ImageVector = materialIcon(name = "HupuIcons.Poll") {
        addPath(fill = SolidColor(Color.Black), pathData = addPathNodes("M19,3H5c-1.1,0 -2,0.9 -2,2v14c0,1.1 0.9,2 2,2h14c1.1,0 2,-0.9 2,-2V5c0,-1.1 -0.9,-2 -2,-2zM9,17H7v-7h2v7zM13,17h-2V7h2v10zM17,17h-2v-4h2v4z"))
    }
    /** 1.126 关注（Material add 24px，圆形加号） */
    val Plus: ImageVector = materialIcon(name = "HupuIcons.Plus") {
        addPath(fill = SolidColor(Color.Black), pathData = addPathNodes("M19,13h-6v6h-2v-6H5v-2h6V5h2v6h6v2z"))
    }
    /** 1.130 主题模式（Material dark_mode 24px） */
    val DarkMode: ImageVector = materialIcon(name = "HupuIcons.DarkMode") {
        addPath(fill = SolidColor(Color.Black), pathData = addPathNodes("M12,3c-4.97,0 -9,4.03 -9,9s4.03,9 9,9c0.46,0 0.92,-0.04 1.36,-0.1c-0.98,-1.37 -1.56,-3.05 -1.56,-4.87c0,-4.54 3.69,-8.23 8.23,-8.23c0.18,0 0.36,0.01 0.54,0.02C19.15,4.75 15.86,3 12,3z"))
    }
    /** 1.130 私信（Material mail 24px，小图标按钮用） */
    val Mail: ImageVector = materialIcon(name = "HupuIcons.Mail") {
        addPath(fill = SolidColor(Color.Black), pathData = addPathNodes("M20,4H4C2.9,4 2.01,4.9 2.01,6L2,18c0,1.1 0.9,2 2,2h16c1.1,0 2,-0.9 2,-2V6C22,4.9 21.1,4 20,4zM20,8l-8,5l-8,-5V6l8,5l8,-5V8z"))
    }
    /** 1.134 关于（Material info 24px） */
    val Info: ImageVector = materialIcon(name = "HupuIcons.Info") {
        addPath(fill = SolidColor(Color.Black), pathData = addPathNodes("M12,2C6.48,2 2,6.48 2,12s4.48,10 10,10 10,-4.48 10,-10S17.52,2 12,2zM13,17h-2v-6h2v6zM13,9h-2V7h2v2z"))
    }
    /** 1.134 官网（Material public 24px，地球） */
    val Globe: ImageVector = materialIcon(name = "HupuIcons.Globe") {
        addPath(fill = SolidColor(Color.Black), pathData = addPathNodes("M12,2C6.48,2 2,6.48 2,12s4.48,10 10,10 10,-4.48 10,-10S17.52,2 12,2zM11,19.93c-3.95,-0.49 -7,-3.85 -7,-7.93 0,-0.62 0.08,-1.21 0.21,-1.79L9,15v1c0,1.1 0.9,2 2,2v1.93zM17.9,17.39c-0.26,-0.81 -1,-1.39 -1.9,-1.39h-1v-3c0,-0.55 -0.45,-1 -1,-1H8v-2h2c0.55,0 1,-0.45 1,-1V7h2c1.1,0 2,-0.9 2,-2v-0.41c2.93,1.19 5,4.06 5,7.41 0,2.08 -0.8,3.97 -2.1,5.39z"))
    }
    /** 1.134 源码/开源许可（Material code 24px） */
    val Code: ImageVector = materialIcon(name = "HupuIcons.Code") {
        addPath(fill = SolidColor(Color.Black), pathData = addPathNodes("M9.4,16.6L4.8,12l4.6,-4.6L8,6l-6,6 6,6 1.4,-1.4zM14.6,16.6l4.6,-4.6 -4.6,-4.6L16,6l6,6 -6,6 -1.4,-1.4z"))
    }
    /** 1.134 检查更新（Material cloud_download 24px） */
    val CloudDownload: ImageVector = materialIcon(name = "HupuIcons.CloudDownload") {
        addPath(fill = SolidColor(Color.Black), pathData = addPathNodes("M19.35,10.04C18.67,6.59 15.64,4 12,4 9.11,4 6.6,5.64 5.35,8.04 2.34,8.36 0,10.91 0,14c0,3.31 2.69,6 6,6h13c2.76,0 5,-2.24 5,-5 0,-2.64 -2.05,-4.78 -4.65,-4.96zM17,13l-5,5 -5,-5h3V9h4v4h3z"))
    }
    /** 1.158 复制图片地址（Material content_copy 24px）——core 图标集里没有，按项目惯例自绘嵌入 */
    val ContentCopy: ImageVector = materialIcon(name = "HupuIcons.ContentCopy") {
        addPath(fill = SolidColor(Color.Black), pathData = addPathNodes("M16,1H4C2.9,1 2,1.9 2,3v14h2V3h12V1zM19,5H8C6.9,5 6,5.9 6,7v14c0,1.1 0.9,2 2,2h11c1.1,0 2,-0.9 2,-2V7C21,5.9 20.1,5 19,5zM19,21H8V7h11V21z"))
    }
}