package com.example.myapp.ui.splash

import androidx.compose.animation.core.*
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Chat
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay

@Composable
fun SplashScreen(
	onSplashComplete: () -> Unit
) {
	var startAnimation by remember { mutableStateOf(false) }
	var showLogo by remember { mutableStateOf(false) }
	var showText by remember { mutableStateOf(false) }
	var showLoading by remember { mutableStateOf(false) }
	
	// 呼吸效果 - 文字明暗变化
	val breathingAlpha by animateFloatAsState(
		targetValue = if (startAnimation) 1f else 0.3f,
		animationSpec = infiniteRepeatable(
			animation = tween(2000, easing = EaseInOut),
			repeatMode = RepeatMode.Reverse
		),
		label = "breathing"
	)
	
	// Logo缩放动画
	val logoScale by animateFloatAsState(
		targetValue = if (showLogo) 1f else 0f,
		animationSpec = tween(800, easing = EaseOutBack),
		label = "logo_scale"
	)
	
	// 文字透明度动画
	val textAlpha by animateFloatAsState(
		targetValue = if (showText) 1f else 0f,
		animationSpec = tween(600, delayMillis = 400),
		label = "text_alpha"
	)
	
	// 加载动画
	val loadingRotation by rememberInfiniteTransition(label = "loading").animateFloat(
		initialValue = 0f,
		targetValue = 360f,
		animationSpec = infiniteRepeatable(
			animation = tween(2000, easing = LinearEasing)
		),
		label = "loading_rotation"
	)

	LaunchedEffect(Unit) {
		startAnimation = true
		delay(300)
		showLogo = true
		delay(600)
		showText = true
		delay(800)
		showLoading = true
		delay(3000)
		onSplashComplete()
	}

	Box(
		modifier = Modifier
			.fillMaxSize()
			.background(
				Brush.verticalGradient(
					colors = listOf(
						Color(0xFF1A1A2E),  // 深蓝黑色
						Color(0xFF16213E),  // 深蓝色
						Color(0xFF0F3460)   // 深蓝紫色
					)
				)
			),
		contentAlignment = Alignment.Center
	) {
		// 背景装饰圆圈
		Box(
			modifier = Modifier
				.size(300.dp)
				.clip(CircleShape)
				.background(Color.White.copy(alpha = 0.05f))
		)
		
		// 主要内容
		Column(
			horizontalAlignment = Alignment.CenterHorizontally,
			verticalArrangement = Arrangement.spacedBy(24.dp)
		) {
			// Logo区域
			AnimatedVisibility(
				visible = showLogo,
				enter = fadeIn() + scaleIn()
			) {
				Box(
					modifier = Modifier
						.size(120.dp)
						.clip(CircleShape)
						.background(
							Brush.radialGradient(
								colors = listOf(
									Color(0xFF4A90E2),  // 蓝色
									Color(0xFF357ABD)   // 深蓝色
								)
							)
						)
						.scale(logoScale),
					contentAlignment = Alignment.Center
				) {
					// 图标组合
					Row(
						horizontalArrangement = Arrangement.spacedBy(8.dp),
						verticalAlignment = Alignment.CenterVertically
					) {
						Icon(
							Icons.Filled.CameraAlt,
							contentDescription = "相机",
							tint = Color.White,
							modifier = Modifier.size(32.dp)
						)
						Icon(
							Icons.Filled.Chat,
							contentDescription = "聊天",
							tint = Color.White,
							modifier = Modifier.size(32.dp)
						)
						Icon(
							Icons.Filled.Mic,
							contentDescription = "语音",
							tint = Color.White,
							modifier = Modifier.size(32.dp)
						)
					}
				}
			}
			
			// 标题文字
			AnimatedVisibility(
				visible = showText,
				enter = fadeIn() + slideInVertically()
			) {
				Column(
					horizontalAlignment = Alignment.CenterHorizontally,
					verticalArrangement = Arrangement.spacedBy(8.dp)
				) {
					Text(
						text = "智能聊天助手",
						fontSize = 32.sp,
						fontWeight = FontWeight.Bold,
						color = Color.White.copy(alpha = textAlpha)
					)
					Text(
						text = "AI驱动的智能交互体验",
						fontSize = 16.sp,
						color = Color.White.copy(alpha = 0.7f * textAlpha)
					)
				}
			}
			
			// 加载指示器
			AnimatedVisibility(
				visible = showLoading,
				enter = fadeIn() + scaleIn()
			) {
				Box(
					modifier = Modifier
						.size(40.dp)
						.clip(CircleShape)
						.background(Color.White.copy(alpha = 0.1f)),
					contentAlignment = Alignment.Center
				) {
					CircularProgressIndicator(
						color = Color(0xFF4A90E2),
						modifier = Modifier.size(24.dp),
						strokeWidth = 2.dp
					)
				}
			}
		}
		
		// 底部版本信息
		Box(
			modifier = Modifier
				.align(Alignment.BottomCenter)
				.padding(bottom = 48.dp)
		) {
			Text(
				text = "Version 2.0.0",
				fontSize = 12.sp,
				color = Color.White.copy(alpha = 0.5f)
			)
		}
	}
}
