package com.example.myapp.ui.settings

import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.BluetoothSearching
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Stop
import kotlinx.coroutines.delay
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.myapp.ui.theme.*
import kotlinx.coroutines.delay

@Composable
fun BluetoothManagerScreen(
    onBack: () -> Unit,
    onDeviceSelected: (BluetoothDevice) -> Unit,
    onPairDevice: (BluetoothDevice) -> Unit
) {
    val context = LocalContext.current
    val bluetoothManager = remember { context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager }
    val bluetoothAdapter = remember { bluetoothManager.adapter }
    
    var isBluetoothEnabled by remember { mutableStateOf(bluetoothAdapter.isEnabled) }
    var pairedDevices by remember { mutableStateOf<List<BluetoothDevice>>(emptyList()) }
    var discoveredDevices by remember { mutableStateOf<List<BluetoothDevice>>(emptyList()) }
    var isScanning by remember { mutableStateOf(false) }
    var showBluetoothSettings by remember { mutableStateOf(false) }
    
    // 扫描回调
    val scanCallback = remember {
        object : ScanCallback() {
            override fun onScanResult(callbackType: Int, result: ScanResult) {
                val device = result.device
                if (!discoveredDevices.any { it.address == device.address }) {
                    discoveredDevices = discoveredDevices + device
                }
            }
        }
    }
    
    // 停止扫描函数
    val stopScan: () -> Unit = {
        if (isScanning) {
            try {
                val scanner = bluetoothAdapter.bluetoothLeScanner
                scanner.stopScan(scanCallback)
            } catch (e: Exception) {
                // 忽略错误
            }
            isScanning = false
        }
    }
    
    // 开始扫描函数
    val startScan: () -> Unit = {
        if (isBluetoothEnabled && !isScanning) {
            isScanning = true
            discoveredDevices = emptyList()
            
            try {
                val scanner = bluetoothAdapter.bluetoothLeScanner
                val settings = ScanSettings.Builder()
                    .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
                    .build()
                scanner.startScan(null, settings, scanCallback)
                
                // 10秒后自动停止扫描
                CoroutineScope(Dispatchers.Main).launch {
                    delay(10000)
                    if (isScanning) {
                        stopScan()
                    }
                }
            } catch (e: Exception) {
                isScanning = false
            }
        }
    }
    
    // 请求启用蓝牙
    val enableBluetoothLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        isBluetoothEnabled = bluetoothAdapter.isEnabled
        if (isBluetoothEnabled) {
            pairedDevices = bluetoothAdapter.bondedDevices.toList()
        }
    }
    
    // 请求蓝牙权限
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val allGranted = permissions.values.all { it }
        if (allGranted) {
            isBluetoothEnabled = bluetoothAdapter.isEnabled
            if (isBluetoothEnabled) {
                pairedDevices = bluetoothAdapter.bondedDevices.toList()
            }
        }
    }
    
    LaunchedEffect(Unit) {
        // 检查蓝牙权限
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            permissionLauncher.launch(
                arrayOf(
                    android.Manifest.permission.BLUETOOTH_SCAN,
                    android.Manifest.permission.BLUETOOTH_CONNECT
                )
            )
        }
    }
    
    LaunchedEffect(isBluetoothEnabled) {
        if (isBluetoothEnabled) {
            pairedDevices = bluetoothAdapter.bondedDevices.toList()
        }
    }
    
    // 组件销毁时停止扫描
    DisposableEffect(Unit) {
        onDispose {
            stopScan()
        }
    }
    
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        MaterialTheme.colorScheme.surface,
                        MaterialTheme.colorScheme.surfaceVariant
                    )
                )
            )
    ) {
        Column(
            modifier = Modifier.fillMaxSize()
        ) {
            // 头部
            ModernBluetoothHeader(onBack = onBack)
            
            // 扫描控制区域
            if (isBluetoothEnabled) {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp)
                        .shadow(4.dp, RoundedCornerShape(12.dp)),
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.9f)
                    )
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "设备扫描",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold
                            )
                            
                            if (isScanning) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(16.dp),
                                        strokeWidth = 2.dp
                                    )
                                    Text(
                                        text = "扫描中...",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                }
                            }
                        }
                        
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Button(
                                onClick = startScan,
                                enabled = !isScanning,
                                modifier = Modifier.weight(1f),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = MaterialTheme.colorScheme.primary
                                )
                            ) {
                                Icon(
                                    Icons.Filled.BluetoothSearching,
                                    contentDescription = "开始扫描",
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("开始扫描")
                            }
                            
                            Button(
                                onClick = stopScan,
                                enabled = isScanning,
                                modifier = Modifier.weight(1f),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = MaterialTheme.colorScheme.error
                                )
                            ) {
                                Icon(
                                    Icons.Filled.Stop,
                                    contentDescription = "停止扫描",
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("停止扫描")
                            }
                        }
                    }
                }
            }
            
            // 设备列表
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // 新发现的设备
                if (isBluetoothEnabled && discoveredDevices.isNotEmpty()) {
                    item {
                        Text(
                            text = "新发现的设备",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                    
                    items(discoveredDevices) { device ->
                        ModernDeviceCard(
                            device = device,
                            onSelect = { 
                                if (pairedDevices.any { it.address == device.address }) {
                                    onDeviceSelected(device) // 已配对设备，连接
                                } else {
                                    onPairDevice(device) // 未配对设备，配对
                                }
                            },
                            isPaired = pairedDevices.any { it.address == device.address }
                        )
                    }
                }
                
                // 已配对设备列表
                if (isBluetoothEnabled && pairedDevices.isNotEmpty()) {
                    item {
                        Text(
                            text = "已配对设备",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                    
                    items(pairedDevices) { device ->
                        ModernDeviceCard(
                            device = device,
                            onSelect = { onDeviceSelected(device) }, // 已配对设备，连接
                            isPaired = true
                        )
                    }
                } else if (isBluetoothEnabled) {
                    item {
                        ModernEmptyStateCard(
                            icon = Icons.Filled.BluetoothSearching,
                            title = "暂无配对设备",
                            subtitle = "请先在系统设置中配对您的蓝牙设备"
                        )
                    }
                }
            }
        }
        
        // 蓝牙设置对话框
        if (showBluetoothSettings) {
            ModernBluetoothSettingsDialog(
                onDismiss = { showBluetoothSettings = false },
                onOpenSystemSettings = {
                    val intent = Intent(android.provider.Settings.ACTION_BLUETOOTH_SETTINGS)
                    context.startActivity(intent)
                    showBluetoothSettings = false
                }
            )
        }
    }
}

@Composable
private fun ModernBluetoothHeader(onBack: () -> Unit) {
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
                    text = "蓝牙设备管理",
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
                            colors = listOf(MaterialTheme.colorScheme.primary, MaterialTheme.colorScheme.primaryContainer)
                        )
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Filled.Bluetooth,
                    contentDescription = "蓝牙",
                    tint = Color.White,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}

@Composable
private fun ModernBluetoothStatusCard(
    isEnabled: Boolean,
    onEnableBluetooth: () -> Unit,
    onOpenSettings: () -> Unit
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
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(12.dp)
                        .clip(CircleShape)
                        .background(
                            if (isEnabled) MaterialTheme.colorScheme.primary 
                            else MaterialTheme.colorScheme.error
                        )
                )
                Text(
                    text = if (isEnabled) "蓝牙已启用" else "蓝牙已禁用",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
            
            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                if (!isEnabled) {
                                         Button(
                         onClick = onEnableBluetooth,
                         colors = ButtonDefaults.buttonColors(
                             containerColor = MaterialTheme.colorScheme.primary
                         ),
                         modifier = Modifier.weight(1f)
                     ) {
                         Icon(
                             Icons.Filled.Bluetooth,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("启用蓝牙")
                    }
                }
                
                                 OutlinedButton(
                     onClick = onOpenSettings,
                     modifier = Modifier.weight(1f)
                 ) {
                     Icon(
                         Icons.Filled.Settings,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("蓝牙设置")
                }
            }
        }
    }
}

@Composable
private fun ModernDeviceCard(
    device: BluetoothDevice,
    onSelect: () -> Unit,
    isPaired: Boolean = false
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .shadow(2.dp, RoundedCornerShape(8.dp)),
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.9f)
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Icon(
                Icons.Filled.Bluetooth,
                contentDescription = "蓝牙设备",
                tint = if (isPaired) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.secondary,
                modifier = Modifier.size(24.dp)
            )
            
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = device.name ?: "未知设备",
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    if (isPaired) {
                        Text(
                            text = "已配对",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier
                                .background(
                                    MaterialTheme.colorScheme.primary.copy(alpha = 0.1f),
                                    RoundedCornerShape(4.dp)
                                )
                                .padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                }
                Text(
                    text = device.address,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            
            Button(
                onClick = onSelect,
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (isPaired) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.secondary
                )
            ) {
                Text(if (isPaired) "连接" else "配对")
            }
        }
    }
}

@Composable
private fun ModernEmptyStateCard(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    subtitle: String
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
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Icon(
                icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(48.dp)
            )
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 16.dp)
            )
        }
    }
}

@Composable
private fun ModernBluetoothSettingsDialog(
    onDismiss: () -> Unit,
    onOpenSystemSettings: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                "蓝牙设置",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Text(
                "您需要打开系统蓝牙设置来管理设备配对。是否现在打开？",
                style = MaterialTheme.typography.bodyMedium
            )
        },
        confirmButton = {
            Button(
                onClick = onOpenSystemSettings,
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary
                )
            ) {
                Text("打开设置")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("取消")
            }
        }
    )
}
