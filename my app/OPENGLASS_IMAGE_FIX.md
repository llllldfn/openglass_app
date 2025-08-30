# OpenGlass图像数据传输修复

## 问题描述
虽然显示"已连接到: OpenGlass"，但一直显示"等待图像数据..."，没有接收到图像数据。

## 问题分析

### 1. OpenGlass固件工作原理
通过分析OpenGlass固件代码，发现：

1. **需要主动触发拍照**：OpenGlass设备不会自动发送图像数据
2. **拍照控制机制**：通过`PHOTO_CONTROL_CHAR_UUID`特征发送控制命令
   - `-1`：拍一张照片
   - `0`：停止拍照  
   - `5-300`：按指定间隔拍照（5秒间隔）

### 2. 发现的问题
1. **UUID不匹配**：音频特征UUID错误
2. **缺少手动触发**：只设置了自动拍照，没有手动触发
3. **用户交互缺失**：没有提供手动拍照的界面

## 解决方案

### 1. 修正UUID配置
```kotlin
object BleConfig {
    val SERVICE_UUID = "19B10000-E8F2-537E-4F6C-D104768A1214"
    val PHOTO_CHAR_UUID = "19B10005-E8F2-537E-4F6C-D104768A1214"
    val PHOTO_CONTROL_CHAR_UUID = "19B10006-E8F2-537E-4F6C-D104768A1214"
    val AUDIO_CHAR_UUID = "19B10001-E8F2-537E-4F6C-D104768A1214"  // 修正为OpenGlass固件中的UUID
}
```

### 2. 改进拍照触发逻辑
```kotlin
// 连接后先拍一张照片，然后启动自动拍照
val controlChar = service.getCharacteristic(UUID.fromString(BleConfig.PHOTO_CONTROL_CHAR_UUID))
if (controlChar != null) {
    // 先拍一张照片
    controlChar.value = byteArrayOf(-1) // 拍一张照片
    gatt.writeCharacteristic(controlChar)
    Log.d("BleInput", "Single photo triggered")
    
    // 然后启动自动拍照（5秒间隔）
    controlChar.value = byteArrayOf(0x05) // 5秒间隔
    gatt.writeCharacteristic(controlChar)
    Log.d("BleInput", "Auto photo capture enabled (5s interval)")
}
```

### 3. 添加手动拍照功能
```kotlin
@SuppressLint("MissingPermission")
fun takePhoto() {
    val service = gatt?.getService(UUID.fromString(BleConfig.SERVICE_UUID))
    val controlChar = service?.getCharacteristic(UUID.fromString(BleConfig.PHOTO_CONTROL_CHAR_UUID))
    if (controlChar != null) {
        controlChar.value = byteArrayOf(-1) // 拍一张照片
        gatt?.writeCharacteristic(controlChar)
        Log.d("BleInput", "Manual photo triggered")
    }
}

@SuppressLint("MissingPermission")
fun startAutoPhoto(intervalSeconds: Int = 5) {
    val service = gatt?.getService(UUID.fromString(BleConfig.SERVICE_UUID))
    val controlChar = service?.getCharacteristic(UUID.fromString(BleConfig.PHOTO_CONTROL_CHAR_UUID))
    if (controlChar != null) {
        controlChar.value = byteArrayOf(intervalSeconds.toByte()) // 设置间隔
        gatt?.writeCharacteristic(controlChar)
        Log.d("BleInput", "Auto photo started with ${intervalSeconds}s interval")
    }
}

@SuppressLint("MissingPermission")
fun stopAutoPhoto() {
    val service = gatt?.getService(UUID.fromString(BleConfig.SERVICE_UUID))
    val controlChar = service?.getCharacteristic(UUID.fromString(BleConfig.PHOTO_CONTROL_CHAR_UUID))
    if (controlChar != null) {
        controlChar.value = byteArrayOf(0) // 停止拍照
        gatt?.writeCharacteristic(controlChar)
        Log.d("BleInput", "Auto photo stopped")
    }
}
```

### 4. 在ViewModel中添加蓝牙拍照方法
```kotlin
fun takeBlePhoto() {
    if (_ui.value.bleOn && _ui.value.bleConnectionState.isConnected) {
        ble.takePhoto()
        _ui.value = _ui.value.copy(error = "已触发蓝牙设备拍照")
    }
}
```

### 5. 在UI中添加手动拍照按钮
```kotlin
// 在蓝牙相机模式中，已连接状态下显示拍照按钮
if (bleConnectionState.isConnected) {
    // ... 显示连接状态
    Spacer(modifier = Modifier.height(16.dp))
    // 手动拍照按钮
    Button(
        onClick = { onTakeBlePhoto?.invoke() },
        colors = ButtonDefaults.buttonColors(
            containerColor = Color.Green
        ),
        shape = RoundedCornerShape(20.dp)
    ) {
        Icon(
            Icons.Filled.CameraAlt,
            contentDescription = "拍照",
            tint = Color.White,
            modifier = Modifier.size(20.dp)
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = "手动拍照",
            color = Color.White,
            style = MaterialTheme.typography.bodyMedium
        )
    }
}
```

## 修复后的工作流程

### 1. 连接流程
1. 扫描并连接OpenGlass设备
2. 发现服务并设置通知
3. **自动触发第一张照片**
4. **启动5秒间隔自动拍照**

### 2. 图像数据接收
1. OpenGlass设备拍照
2. 通过`PHOTO_CHAR_UUID`特征发送图像数据
3. 客户端接收并重组图像数据
4. 发送给AI处理

### 3. 用户交互
1. **自动模式**：连接后每5秒自动拍照
2. **手动模式**：点击"手动拍照"按钮立即拍照
3. **停止模式**：可以调用`stopAutoPhoto()`停止自动拍照

## 测试建议

### 1. 连接测试
1. 确保OpenGlass设备已开启
2. 连接后观察是否立即触发拍照
3. 检查是否每5秒自动拍照

### 2. 手动拍照测试
1. 在蓝牙相机模式下点击"手动拍照"按钮
2. 观察是否立即触发拍照
3. 检查图像数据是否正确接收

### 3. 数据接收测试
1. 观察日志中的"Photo received: X bytes"
2. 检查AI是否正确处理图像数据
3. 验证图像数据完整性

## 预期效果

- ✅ **连接后立即拍照**：不再显示"等待图像数据..."
- ✅ **自动连续拍照**：每5秒自动拍照
- ✅ **手动拍照**：用户可以随时手动触发拍照
- ✅ **实时图像处理**：接收到的图像立即发送给AI处理
- ✅ **状态同步**：UI正确显示连接状态和拍照状态

## 技术细节

### OpenGlass固件关键代码
```cpp
// 拍照控制处理
void handlePhotoControl(int8_t controlValue) {
    if (controlValue == -1) {
        // 拍一张照片
        isCapturingPhotos = true;
        captureInterval = 0;
    } else if (controlValue == 0) {
        // 停止拍照
        isCapturingPhotos = false;
        captureInterval = 0;
    } else if (controlValue >= 5 && controlValue <= 300) {
        // 按指定间隔拍照
        captureInterval = (controlValue / 5) * 5000;
        isCapturingPhotos = true;
        lastCaptureTime = millis() - captureInterval;
    }
}

// 图像数据传输
if (photoDataUploading) {
    size_t remaining = fb->len - sent_photo_bytes;
    if (remaining > 0) {
        // 发送图像数据块
        s_compressed_frame_2[0] = sent_photo_frames & 0xFF;
        s_compressed_frame_2[1] = (sent_photo_frames >> 8) & 0xFF;
        size_t bytes_to_copy = remaining;
        if (bytes_to_copy > 200) {
            bytes_to_copy = 200;
        }
        memcpy(&s_compressed_frame_2[2], &fb->buf[sent_photo_bytes], bytes_to_copy);
        
        photoDataCharacteristic->setValue(s_compressed_frame_2, bytes_to_copy + 2);
        photoDataCharacteristic->notify();
        sent_photo_bytes += bytes_to_copy;
        sent_photo_frames++;
    } else {
        // 发送结束标记
        s_compressed_frame_2[0] = 0xFF;
        s_compressed_frame_2[1] = 0xFF;
        photoDataCharacteristic->setValue(s_compressed_frame_2, 2);
        photoDataCharacteristic->notify();
        photoDataUploading = false;
    }
}
```
