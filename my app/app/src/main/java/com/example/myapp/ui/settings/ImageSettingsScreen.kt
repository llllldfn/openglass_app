package com.example.myapp.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Compress
import androidx.compose.material.icons.filled.CropFree
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.FlashOn
import androidx.compose.material.icons.filled.CenterFocusStrong
import androidx.compose.material.icons.filled.Grid3x3
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.myapp.ui.theme.*
import android.os.Parcel
import android.os.Parcelable

// 数据类定义
data class ImageSettings(
    val quality: Int = 80,
    val maxSize: Int = 1024,
    val format: ImageFormat = ImageFormat.JPEG,
    val maxFileSize: Int = 5,
    val autoFlash: Boolean = true,
    val autoFocus: Boolean = true,
    val showGrid: Boolean = false,
    val autoUpload: Boolean = true,
    val showPreview: Boolean = true
) : Parcelable {
    constructor(parcel: Parcel) : this(
        parcel.readInt(),
        parcel.readInt(),
        ImageFormat.valueOf(parcel.readString() ?: ImageFormat.JPEG.name),
        parcel.readInt(),
        parcel.readByte() != 0.toByte(),
        parcel.readByte() != 0.toByte(),
        parcel.readByte() != 0.toByte(),
        parcel.readByte() != 0.toByte(),
        parcel.readByte() != 0.toByte()
    )

    override fun writeToParcel(parcel: Parcel, flags: Int) {
        parcel.writeInt(quality)
        parcel.writeInt(maxSize)
        parcel.writeString(format.name)
        parcel.writeInt(maxFileSize)
        parcel.writeByte(if (autoFlash) 1 else 0)
        parcel.writeByte(if (autoFocus) 1 else 0)
        parcel.writeByte(if (showGrid) 1 else 0)
        parcel.writeByte(if (autoUpload) 1 else 0)
        parcel.writeByte(if (showPreview) 1 else 0)
    }

    override fun describeContents(): Int {
        return 0
    }

    companion object CREATOR : Parcelable.Creator<ImageSettings> {
        override fun createFromParcel(parcel: Parcel): ImageSettings {
            return ImageSettings(parcel)
        }

        override fun newArray(size: Int): Array<ImageSettings?> {
            return arrayOfNulls(size)
        }
    }
}

enum class ImageFormat(val displayName: String, val extension: String) {
    JPEG("JPEG", "jpg"),
    PNG("PNG", "png"),
    WEBP("WebP", "webp")
}

@Composable
fun ImageSettingsScreen(
    onBack: () -> Unit,
    onSettingsChanged: (ImageSettings) -> Unit,
    currentSettings: ImageSettings
) {
    var settings by remember { mutableStateOf(currentSettings) }
    
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        MaterialTheme.colorScheme.background,
                        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
                    )
                )
            )
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // 顶部标题栏
            ModernImageSettingsHeader(onBack = onBack)
            
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // 图片质量设置
                item {
                    ModernSettingsSection(
                        title = "图片质量",
                        icon = Icons.Filled.Star
                    ) {
                        ModernSettingsCard {
                            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                                ModernSettingItem(
                                    icon = Icons.Filled.Compress,
                                    title = "压缩质量",
                                    subtitle = "设置图片压缩质量 (1-100)",
                                    trailing = {
                                        OutlinedTextField(
                                            value = settings.quality.toString(),
                                            onValueChange = { value ->
                                                val quality = value.toIntOrNull() ?: 80
                                                settings = settings.copy(quality = quality.coerceIn(1, 100))
                                            },
                                            modifier = Modifier.width(80.dp),
                                            singleLine = true,
                                            colors = OutlinedTextFieldDefaults.colors(
                                                focusedBorderColor = MaterialTheme.colorScheme.primary
                                            )
                                        )
                                    }
                                                                )
                                
                                ModernSettingItem(
                                    icon = Icons.Filled.CropFree,
                                    title = "最大尺寸",
                                    subtitle = "设置图片最大尺寸 (像素)",
                                    trailing = {
                                        OutlinedTextField(
                                            value = settings.maxSize.toString(),
                                            onValueChange = { value ->
                                                val size = value.toIntOrNull() ?: 1024
                                                settings = settings.copy(maxSize = size.coerceIn(100, 4096))
                                            },
                                            modifier = Modifier.width(80.dp),
                                            singleLine = true,
                                            colors = OutlinedTextFieldDefaults.colors(
                                                focusedBorderColor = MaterialTheme.colorScheme.primary
                                            )
                                        )
                                    }
                                )
                            }
                        }
                    }
                }
                
                // 相机设置
                item {
                                         ModernSettingsSection(
                         title = "相机设置",
                         icon = Icons.Filled.CameraAlt
                     ) {
                                                 ModernSettingsCard {
                             Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                                 ModernSettingItem(
                                     icon = Icons.Filled.FlashOn,
                                    title = "自动闪光灯",
                                    subtitle = "在光线不足时自动开启闪光灯",
                                    trailing = {
                                        Switch(
                                            checked = settings.autoFlash,
                                            onCheckedChange = { settings = settings.copy(autoFlash = it) },
                                            colors = SwitchDefaults.colors(
                                                checkedThumbColor = MaterialTheme.colorScheme.primary,
                                                checkedTrackColor = MaterialTheme.colorScheme.primaryContainer
                                            )
                                        )
                                    }
                                                                )
                                
                                ModernSettingItem(
                                    icon = Icons.Filled.CenterFocusStrong,
                                    title = "自动对焦",
                                    subtitle = "启用自动对焦功能",
                                    trailing = {
                                        Switch(
                                            checked = settings.autoFocus,
                                            onCheckedChange = { settings = settings.copy(autoFocus = it) },
                                            colors = SwitchDefaults.colors(
                                                checkedThumbColor = MaterialTheme.colorScheme.primary,
                                                checkedTrackColor = MaterialTheme.colorScheme.primaryContainer
                                            )
                                        )
                                    }
                                                                )
                                
                                ModernSettingItem(
                                    icon = Icons.Filled.Grid3x3,
                                    title = "网格线",
                                    subtitle = "显示相机网格线辅助构图",
                                    trailing = {
                                        Switch(
                                            checked = settings.showGrid,
                                            onCheckedChange = { settings = settings.copy(showGrid = it) },
                                            colors = SwitchDefaults.colors(
                                                checkedThumbColor = MaterialTheme.colorScheme.primary,
                                                checkedTrackColor = MaterialTheme.colorScheme.primaryContainer
                                            )
                                        )
                                    }
                                )
                            }
                        }
                    }
                }
                
                // 上传设置
                item {
                                         ModernSettingsSection(
                         title = "上传设置",
                         icon = Icons.Filled.CloudUpload
                     ) {
                                                 ModernSettingsCard {
                             Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                                 ModernSettingItem(
                                     icon = Icons.Filled.Speed,
                                    title = "自动上传",
                                    subtitle = "拍照后自动上传到AI",
                                    trailing = {
                                        Switch(
                                            checked = settings.autoUpload,
                                            onCheckedChange = { settings = settings.copy(autoUpload = it) },
                                            colors = SwitchDefaults.colors(
                                                checkedThumbColor = MaterialTheme.colorScheme.primary,
                                                checkedTrackColor = MaterialTheme.colorScheme.primaryContainer
                                            )
                                        )
                                    }
                                                                )
                                
                                ModernSettingItem(
                                    icon = Icons.Filled.Visibility,
                                    title = "预览确认",
                                    subtitle = "上传前显示图片预览",
                                    trailing = {
                                        Switch(
                                            checked = settings.showPreview,
                                            onCheckedChange = { settings = settings.copy(showPreview = it) },
                                            colors = SwitchDefaults.colors(
                                                checkedThumbColor = MaterialTheme.colorScheme.primary,
                                                checkedTrackColor = MaterialTheme.colorScheme.primaryContainer
                                            )
                                        )
                                    }
                                )
                            }
                        }
                    }
                }
            }
            
            // 保存按钮
            Button(
                onClick = {
                    onSettingsChanged(settings)
                    onBack()
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary
                ),
                shape = RoundedCornerShape(16.dp)
            ) {
                Text(
                    "保存设置",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }
    }
}

@Composable
private fun ModernImageSettingsHeader(onBack: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .shadow(8.dp, RoundedCornerShape(16.dp)),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.9f)
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                IconButton(onClick = onBack) {
                                    Icon(
                    Icons.Filled.ArrowBack,
                        contentDescription = "返回",
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
                Text(
                    text = "图片上传设置",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(
                        Brush.linearGradient(
                            colors = listOf(PrimaryGradientStart, PrimaryGradientEnd)
                        )
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Filled.PhotoCamera,
                    contentDescription = "图片设置",
                    tint = Color.White,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}

@Composable
private fun ModernSettingsSection(
    title: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    content: @Composable () -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Icon(
                icon,
                contentDescription = title,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(24.dp)
            )
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface
            )
        }
        content()
    }
}

@Composable
private fun ModernSettingsCard(
    content: @Composable () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .shadow(4.dp, RoundedCornerShape(12.dp)),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.9f)
        )
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            content()
        }
    }
}

@Composable
private fun ModernSettingItem(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    subtitle: String,
    trailing: @Composable () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Icon(
            icon,
            contentDescription = title,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(24.dp)
        )
        
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        
        trailing()
    }
}
