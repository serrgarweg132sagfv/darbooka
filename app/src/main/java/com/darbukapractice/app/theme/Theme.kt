package com.darbukapractice.app.theme
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
private val Light=lightColorScheme(primary=Color(0xFF7A4B2A),secondary=Color(0xFF5D6B73),surface=Color(0xFFFFFBF7),background=Color(0xFFFFFBF7))
@Composable fun DarbukaTheme(content:@Composable()->Unit){MaterialTheme(colorScheme=Light,content=content)}
