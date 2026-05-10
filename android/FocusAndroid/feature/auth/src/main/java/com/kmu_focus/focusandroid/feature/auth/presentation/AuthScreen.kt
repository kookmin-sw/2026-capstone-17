package com.kmu_focus.focusandroid.feature.auth.presentation

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.kmu_focus.focusandroid.feature.auth.R
import kotlinx.coroutines.delay

@Composable
fun AuthScreen(
    onLoginSuccess: () -> Unit,
    onContinueWithoutLogin: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
    viewModel: AuthViewModel = hiltViewModel(),
) {
    val context = LocalContext.current
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    LaunchedEffect(Unit) {
        viewModel.tryAutoLogin()
    }

    when (val state = uiState) {
        AuthUiState.Loading -> {
            IntroLoadingScreen(
                title = "세션을 확인하는 중입니다.",
                modifier = modifier,
            )
        }

        AuthUiState.NeedLogin -> {
            AuthIntroScreen(
                message = "카카오 로그인 후 방송 준비와 분석 흐름을 이어서 사용할 수 있습니다.",
                onClick = { viewModel.kakaoLogin(context) },
                onContinueWithoutLogin = onContinueWithoutLogin,
                modifier = modifier,
            )
        }

        AuthUiState.Success -> {
            LaunchedEffect(Unit) {
                onLoginSuccess()
            }
            IntroLoadingScreen(
                title = "로그인 세션을 복원하는 중입니다.",
                modifier = modifier,
            )
        }

        is AuthUiState.Error -> {
            AuthIntroScreen(
                message = state.message,
                onClick = { viewModel.kakaoLogin(context) },
                onContinueWithoutLogin = onContinueWithoutLogin,
                modifier = modifier,
            )
        }
    }
}

@Composable
private fun IntroLoadingScreen(
    title: String,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(
                Brush.linearGradient(
                    colors = listOf(
                        Color(0xFFF0F9FF),
                        Color.White,
                        Color(0xFFE4F3FA),
                    ),
                ),
            ),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Text(
                text = "FOCUS",
                color = Color(0xFF0369A1),
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
            )
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                color = Color(0xFF0C4A6E),
            )
            CircularProgressIndicator(color = Color(0xFF0369A1))
        }
    }
}

@Composable
private fun AuthIntroScreen(
    message: String,
    onClick: () -> Unit,
    onContinueWithoutLogin: (() -> Unit)?,
    modifier: Modifier = Modifier,
) {
    val slides = remember {
        listOf(
            IntroSlide(
                title = "스트리머는 그대로, 배경 인물은 안전하게",
                subtitle = "초상권 걱정 없는 스마트 라이브 스트리밍",
            ),
            IntroSlide(
                title = "방송중 스쳐가는 사람까지",
                subtitle = "자동으로 감지해 아바타로 전환해요",
            ),
            IntroSlide(
                title = "복잡한 설정 없이 바로 시작하는",
                subtitle = "안전한 라이브 스트리밍",
            ),
        )
    }
    var selectedSlide by rememberSaveable { mutableIntStateOf(0) }
    val scrollState = rememberScrollState()

    LaunchedEffect(slides.size) {
        while (true) {
            delay(3_200L)
            selectedSlide = (selectedSlide + 1) % slides.size
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(
                Brush.linearGradient(
                    colors = listOf(
                        Color(0xFFF0F9FF),
                        Color.White,
                        Color(0xFFE4F3FA),
                    ),
                ),
            ),
    ) {
        Box(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .size(280.dp)
                .background(Color(0xFF0369A1).copy(alpha = 0.08f), CircleShape)
                .padding(18.dp),
        )
        Box(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(start = 8.dp, bottom = 140.dp)
                .size(220.dp)
                .background(Color(0xFF0EA5E9).copy(alpha = 0.10f), CircleShape),
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .windowInsetsPadding(
                    WindowInsets.safeDrawing.only(
                        WindowInsetsSides.Top + WindowInsetsSides.Bottom,
                    ),
                )
                .verticalScroll(scrollState)
                .padding(horizontal = 24.dp, vertical = 24.dp),
            verticalArrangement = Arrangement.spacedBy(28.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "FOCUS",
                    color = Color(0xFF0369A1).copy(alpha = 0.82f),
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                )
                Spacer(modifier = Modifier.weight(1f))
                Surface(
                    shape = RoundedCornerShape(999.dp),
                    color = Color.White.copy(alpha = 0.82f),
                    tonalElevation = 0.dp,
                ) {
                    Text(
                        text = "Avatar 기본",
                        modifier = Modifier.padding(horizontal = 11.dp, vertical = 7.dp),
                        style = MaterialTheme.typography.labelSmall,
                        color = Color(0xFF0369A1),
                    )
                }
            }

            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Box(
                    modifier = Modifier.size(334.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Surface(
                        modifier = Modifier.size(334.dp),
                        color = Color.White.copy(alpha = 0.82f),
                        shape = CircleShape,
                        shadowElevation = 18.dp,
                    ) {}
                    Surface(
                        modifier = Modifier.size(300.dp),
                        color = Color.Transparent,
                        shape = CircleShape,
                    ) {}
                    Image(
                        painter = painterResource(id = R.drawable.focus_intro_illustration),
                        contentDescription = null,
                        modifier = Modifier.size(286.dp),
                    )
                }

                Spacer(modifier = Modifier.height(28.dp))

                Text(
                    text = slides[selectedSlide].title,
                    style = MaterialTheme.typography.headlineLarge,
                    color = Color(0xFF0C4A6E),
                    fontWeight = FontWeight.Bold,
                )
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = slides[selectedSlide].subtitle,
                    style = MaterialTheme.typography.bodyLarge,
                    color = Color(0xFF0C4A6E).copy(alpha = 0.68f),
                )
                Spacer(modifier = Modifier.height(18.dp))
                PageIndicator(selectedPage = selectedSlide, pageCount = slides.size)
            }

            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(
                    text = message,
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color(0xFF0C4A6E).copy(alpha = 0.70f),
                )
                KakaoLoginButton(
                    onClick = onClick,
                    modifier = Modifier.fillMaxWidth(),
                )
                if (onContinueWithoutLogin != null) {
                    GuestVideoButton(
                        onClick = onContinueWithoutLogin,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        }
    }
}

@Composable
private fun PageIndicator(
    selectedPage: Int,
    pageCount: Int,
) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        repeat(pageCount) { index ->
            Box(
                modifier = Modifier
                    .size(width = if (index == selectedPage) 28.dp else 8.dp, height = 8.dp)
                    .background(
                        color = if (index == selectedPage) Color(0xFF0369A1) else Color(0xFFD7EAF5),
                        shape = RoundedCornerShape(999.dp),
                    ),
            )
        }
    }
}

@Composable
private fun KakaoLoginButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        onClick = onClick,
        modifier = modifier.height(58.dp),
        shape = RoundedCornerShape(18.dp),
        color = KakaoYellow,
        contentColor = KakaoLabelColor,
        shadowElevation = 8.dp,
    ) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center,
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 18.dp),
                contentAlignment = Alignment.CenterStart,
            ) {
                Canvas(modifier = Modifier.size(18.dp)) {
                    drawKakaoSymbol()
                }
                Text(
                    text = "시작하기",
                    modifier = Modifier.align(Alignment.Center),
                    color = KakaoLabelColor,
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold,
                )
            }
        }
    }
}

@Composable
private fun GuestVideoButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        onClick = onClick,
        modifier = modifier.height(58.dp),
        shape = RoundedCornerShape(18.dp),
        color = Color.White.copy(alpha = 0.96f),
        contentColor = Color(0xFF0C4A6E),
        border = BorderStroke(1.dp, Color(0xFFB6D7E8)),
        shadowElevation = 4.dp,
    ) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = "로그인 없이 동영상 처리",
                color = Color(0xFF0C4A6E),
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold,
            )
        }
    }
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawKakaoSymbol() {
    val bubbleWidth = size.width * 0.82f
    val bubbleHeight = size.height * 0.74f
    val bubbleTop = size.height * 0.06f
    val bubbleLeft = size.width * 0.08f

    drawOval(
        color = Color.Black,
        topLeft = Offset(bubbleLeft, bubbleTop),
        size = Size(bubbleWidth, bubbleHeight),
    )

    val tail = Path().apply {
        moveTo(size.width * 0.30f, size.height * 0.62f)
        lineTo(size.width * 0.20f, size.height * 0.98f)
        lineTo(size.width * 0.47f, size.height * 0.76f)
        close()
    }
    drawPath(
        path = tail,
        color = Color.Black,
    )
}

private data class IntroSlide(
    val title: String,
    val subtitle: String,
)

private val KakaoYellow = Color(0xFFFEE500)
private val KakaoLabelColor = Color.Black.copy(alpha = 0.85f)
