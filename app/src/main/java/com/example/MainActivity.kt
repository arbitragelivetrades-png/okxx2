package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import android.app.Activity
import android.app.KeyguardManager
import android.content.Context
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.CompareArrows
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.SubcomposeAsyncImage
import coil.request.ImageRequest
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.animation.core.*

class MainActivity : ComponentActivity() {
    companion object {
        var isSplashAlreadyShown = false
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MainAppContainer()
        }
    }
}

// Bottom tab options
enum class BottomTab {
    OKX, Explore, Trade, Orbit, Assets
}

// ------------------------------------------------------------------------
// DEPOSIT FLOW MODELS, DATA, AND SCREEN IMPLEMENTATIONS
// ------------------------------------------------------------------------

enum class DepositFlowStep {
    SelectAsset,
    SelectNetwork,
    DepositAddress
}

enum class WithdrawFlowStep {
    SelectAsset,
    SelectNetwork,
    EnterAddress,
    WithdrawDetails,
    Processing,
    P2pTrading
}

data class WithdrawNetwork(
    val name: String,
    val feeAmount: Double,
    val badge: String? = null,
    val arrivalTime: String? = null,
    val minDeposit: String? = null
)

data class CryptoAsset(
    val symbol: String,
    val name: String,
    val apr: String? = null
)

val CryptoAssetsList = listOf(
    CryptoAsset("USDT", "USDT"),
    CryptoAsset("USDG", "USDG", apr = "3.5% APR"),
    CryptoAsset("USDC", "USDC"),
    CryptoAsset("BTC", "Bitcoin"),
    CryptoAsset("ETH", "Ethereum"),
    CryptoAsset("XAUT", "Tether Gold"),
    CryptoAsset("SOL", "Solana"),
    CryptoAsset("TRX", "TRON"),
    CryptoAsset("1INCH", "1INCH")
)

val RecentDepositsList = listOf(
    CryptoAsset("BTC", "Bitcoin"),
    CryptoAsset("BNB", "BNB"),
    CryptoAsset("SOL", "Solana")
)

data class NetworkOption(
    val name: String,
    val subtext: String,
    val arrivalTime: String,
    val minDeposit: String
)

val NetworkOptionsList = listOf(
    NetworkOption("X Layer (USDT&USDT0)", "X Layer", "~ 1 minute", "0.01 USDT"),
    NetworkOption("Tron (TRC20)", "Tron", "~ 1 minute", "0.01 USDT"),
    NetworkOption("Ethereum (ERC20)", "Ethereum", "~ 7 minutes", "0.01 USDT"),
    NetworkOption("Aptos", "Aptos", "~ 1 minute", "0.01 USDT"),
    NetworkOption("Arbitrum One (USDT0)", "Arbitrum One", "~ 18 minutes", "0.01 USDT"),
    NetworkOption("Avalanche C-Chain", "Avalanche", "~ 1 minute", "0.01 USDT"),
    NetworkOption("Berachain (USDT0)", "Berachain", "~ 1 minute", "0.01 USDT"),
    NetworkOption("Monad (USDT0)", "Monad", "~ 1 minute", "0.00000001 USDT"),
    NetworkOption("Optimism (USDT&USDT0)", "Optimism", "~ 23 minutes", "0.01 USDT")
)

fun getNetworksForAsset(symbol: String): List<NetworkOption> {
    return if (symbol.uppercase() == "SOL") {
        listOf(
            NetworkOption("X Layer", "X Layer", "~ 1 minute", "0.00000001 SOL"),
            NetworkOption("Solana", "Solana", "~ 1 minute", "0.005 SOL")
        )
    } else {
        NetworkOptionsList
    }
}


@Composable
fun ChevronLeftIcon(color: Color, modifier: Modifier = Modifier) {
    Canvas(modifier = modifier.size(24.dp)) {
        val w = size.width
        val h = size.height
        val strokeW = w * 0.085f
        val path = Path().apply {
            moveTo(w * 0.62f, h * 0.25f)
            lineTo(w * 0.35f, h * 0.5f)
            lineTo(w * 0.62f, h * 0.75f)
        }
        drawPath(
            path = path,
            color = color,
            style = Stroke(width = strokeW, cap = StrokeCap.Round, join = StrokeJoin.Round)
        )
    }
}

@Composable
fun ChevronRightIcon(color: Color, modifier: Modifier = Modifier) {
    Canvas(modifier = modifier.size(24.dp)) {
        val w = size.width
        val h = size.height
        val strokeW = w * 0.085f
        val path = Path().apply {
            moveTo(w * 0.38f, h * 0.25f)
            lineTo(w * 0.65f, h * 0.5f)
            lineTo(w * 0.38f, h * 0.75f)
        }
        drawPath(
            path = path,
            color = color,
            style = Stroke(width = strokeW, cap = StrokeCap.Round, join = StrokeJoin.Round)
        )
    }
}

@Composable
fun HelpOutlineIcon(color: Color, modifier: Modifier = Modifier) {
    Canvas(modifier = modifier.size(24.dp)) {
        val w = size.width
        val h = size.height
        val strokeW = w * 0.085f
        
        // Outer circle
        drawCircle(
            color = color,
            radius = w * 0.42f,
            style = Stroke(width = strokeW)
        )
        
        // Question mark path
        val path = Path().apply {
            moveTo(w * 0.35f, h * 0.36f)
            cubicTo(w * 0.35f, h * 0.25f, w * 0.65f, h * 0.25f, w * 0.65f, h * 0.38f)
            cubicTo(w * 0.65f, h * 0.46f, w * 0.5f, h * 0.48f, w * 0.5f, h * 0.58f)
        }
        drawPath(path, color = color, style = Stroke(width = strokeW, cap = StrokeCap.Round))
        
        // Dot at bottom
        drawCircle(
            color = color,
            radius = strokeW * 0.6f,
            center = Offset(w * 0.5f, h * 0.70f)
        )
    }
}

@Composable
fun InfoIcon(color: Color, modifier: Modifier = Modifier) {
    Canvas(modifier = modifier.size(24.dp)) {
        val w = size.width
        val h = size.height
        val strokeW = w * 0.085f
        
        // Outer circle
        drawCircle(
            color = color,
            radius = w * 0.42f,
            style = Stroke(width = strokeW)
        )
        
        // Dot at top
        drawCircle(
            color = color,
            radius = strokeW * 0.8f,
            center = Offset(w * 0.5f, h * 0.32f)
        )
        
        // Stem
        drawLine(
            color = color,
            start = Offset(w * 0.5f, h * 0.44f),
            end = Offset(w * 0.5f, h * 0.72f),
            strokeWidth = strokeW,
            cap = StrokeCap.Round
        )
    }
}

@Composable
fun ContentCopyIcon(color: Color, modifier: Modifier = Modifier) {
    Canvas(modifier = modifier.size(24.dp)) {
        val w = size.width
        val h = size.height
        val strokeW = w * 0.085f
        
        // Back card outline
        drawRect(
            color = color,
            topLeft = Offset(w * 0.12f, h * 0.12f),
            size = Size(w * 0.48f, h * 0.48f),
            style = Stroke(width = strokeW, cap = StrokeCap.Round, join = StrokeJoin.Round)
        )
        
        // Front card outline
        val frontLeft = w * 0.32f
        val frontTop = w * 0.32f
        val frontW = w * 0.48f
        val frontH = h * 0.48f
        
        drawRect(
            color = PureBlack,
            topLeft = Offset(frontLeft - strokeW, frontTop - strokeW),
            size = Size(frontW + 2 * strokeW, frontH + 2 * strokeW)
        )
        
        drawRect(
            color = color,
            topLeft = Offset(frontLeft, frontTop),
            size = Size(frontW, frontH),
            style = Stroke(width = strokeW, cap = StrokeCap.Round, join = StrokeJoin.Round)
        )
    }
}

@Composable
fun MoreHorizIcon(color: Color, modifier: Modifier = Modifier) {
    Canvas(modifier = modifier.size(24.dp)) {
        val w = size.width
        val h = size.height
        val r = w * 0.08f
        
        drawCircle(color, radius = r, center = Offset(w * 0.22f, h * 0.5f))
        drawCircle(color, radius = r, center = Offset(w * 0.5f, h * 0.5f))
        drawCircle(color, radius = r, center = Offset(w * 0.78f, h * 0.5f))
    }
}

@Composable
fun CryptoIcon(symbol: String, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val url = when (symbol.uppercase()) {
        "USDG" -> "https://assets.coingecko.com/coins/images/51356/large/global_dollar.png"
        "USDC" -> "https://raw.githubusercontent.com/spothq/cryptocurrency-icons/master/128/color/usdc.png"
        "BTC" -> "https://raw.githubusercontent.com/spothq/cryptocurrency-icons/master/128/color/btc.png"
        "ETH" -> "https://raw.githubusercontent.com/spothq/cryptocurrency-icons/master/128/color/eth.png"
        "XAUT" -> "https://assets.coingecko.com/coins/images/10981/large/tether-gold.png"
        "TRX" -> "https://raw.githubusercontent.com/spothq/cryptocurrency-icons/master/128/color/trx.png"
        "1INCH" -> "https://raw.githubusercontent.com/spothq/cryptocurrency-icons/master/128/color/1inch.png"
        "BNB" -> "https://raw.githubusercontent.com/spothq/cryptocurrency-icons/master/128/color/bnb.png"
        else -> null
    }

    if (url != null) {
        SubcomposeAsyncImage(
            model = ImageRequest.Builder(context)
                .data(url)
                .crossfade(true)
                .build(),
            contentDescription = symbol,
            modifier = modifier,
            loading = {
                CryptoIconFallback(symbol = symbol, modifier = Modifier.fillMaxSize())
            },
            error = {
                CryptoIconFallback(symbol = symbol, modifier = Modifier.fillMaxSize())
            }
        )
    } else {
        CryptoIconFallback(symbol = symbol, modifier = modifier)
    }
}

fun androidx.compose.ui.graphics.drawscope.DrawScope.drawRealSolanaLogo(
    w: Float,
    h: Float,
    radius: Float
) {
    drawCircle(color = PureBlack, radius = radius)
    val gradient = Brush.linearGradient(
        colors = listOf(
            Color(0xFF14F195), // Vibrant Cyan/Green
            Color(0xFF00C2FF), // Vivid Sky Blue
            Color(0xFF9945FF)  // Rich Purple
        ),
        start = Offset(w * 0.78f, h * 0.25f),
        end = Offset(w * 0.22f, h * 0.75f)
    )
    val x1 = w * 0.21f
    val x2 = w * 0.79f
    val skew = w * 0.145f

    // Band 1: topY1 to botY1
    val topY1 = h * 0.28f
    val botY1 = h * 0.39f
    val path1 = Path().apply {
        moveTo(x1, topY1)
        lineTo(x2 - skew, topY1)
        lineTo(x2, botY1)
        lineTo(x1 + skew, botY1)
        close()
    }
    drawPath(path1, brush = gradient)

    // Band 2: topY2 to botY2
    val topY2 = h * 0.445f
    val botY2 = h * 0.555f
    val path2 = Path().apply {
        moveTo(x1 + skew, topY2)
        lineTo(x2, topY2)
        lineTo(x2 - skew, botY2)
        lineTo(x1, botY2)
        close()
    }
    drawPath(path2, brush = gradient)

    // Band 3: topY3 to botY3
    val topY3 = h * 0.61f
    val botY3 = h * 0.72f
    val path3 = Path().apply {
        moveTo(x1, topY3)
        lineTo(x2 - skew, topY3)
        lineTo(x2, botY3)
        lineTo(x1 + skew, botY3)
        close()
    }
    drawPath(path3, brush = gradient)
}

fun androidx.compose.ui.graphics.drawscope.DrawScope.drawRealLitecoinLogo(
    w: Float,
    h: Float,
    radius: Float
) {
    // 1. Blue circle background matching reference image
    drawCircle(color = Color(0xFF345C9C), radius = radius)

    // 2. Main L shape (slanted/italicized filled path)
    val pathL = Path().apply {
        moveTo(w * 0.47f, h * 0.155f)
        lineTo(w * 0.61f, h * 0.155f)
        lineTo(w * 0.455f, h * 0.690f)
        lineTo(w * 0.750f, h * 0.690f)
        lineTo(w * 0.710f, h * 0.805f)
        lineTo(w * 0.280f, h * 0.805f)
        close()
    }
    drawPath(pathL, color = Color.White)

    // 3. Crossbar shape passing through stem
    val pathCross = Path().apply {
        moveTo(w * 0.270f, h * 0.585f)
        lineTo(w * 0.290f, h * 0.530f)
        lineTo(w * 0.605f, h * 0.435f)
        lineTo(w * 0.585f, h * 0.495f)
        close()
    }
    drawPath(pathCross, color = Color.White)
}

fun androidx.compose.ui.graphics.drawscope.DrawScope.drawRealUsdtLogo(
    w: Float,
    h: Float,
    radius: Float
) {
    // 1. Teal/Green Pentagon emblem matching reference image exactly
    val pentagonPath = Path().apply {
        moveTo(w * 0.185f, h * 0.065f) // Top-left
        lineTo(w * 0.815f, h * 0.065f) // Top-right
        lineTo(w * 1.000f, h * 0.450f) // Mid-right
        lineTo(w * 0.500f, h * 0.935f) // Bottom-center point
        lineTo(w * 0.000f, h * 0.450f) // Mid-left
        close()
    }
    drawPath(pentagonPath, color = Color(0xFF50AF95))

    // 2. White 'T' shape inside emblem
    val tPath = Path().apply {
        moveTo(w * 0.265f, h * 0.185f)
        lineTo(w * 0.735f, h * 0.185f)
        lineTo(w * 0.735f, h * 0.300f)
        lineTo(w * 0.565f, h * 0.300f)
        lineTo(w * 0.565f, h * 0.755f)
        lineTo(w * 0.435f, h * 0.755f)
        lineTo(w * 0.435f, h * 0.300f)
        lineTo(w * 0.265f, h * 0.300f)
        close()
    }
    drawPath(tPath, color = Color.White)

    // 3. Elliptical ring around T stem
    val ringPath = Path().apply {
        addOval(
            Rect(
                left = w * 0.190f,
                top = h * 0.380f,
                right = w * 0.810f,
                bottom = h * 0.500f
            )
        )
    }
    drawPath(
        path = ringPath,
        color = Color.White,
        style = Stroke(width = w * 0.035f)
    )
}

@Composable
fun CryptoIconFallback(symbol: String, modifier: Modifier = Modifier) {
    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        val radius = w / 2f
        
        when (symbol) {
            "USDT" -> {
                drawRealUsdtLogo(w, h, radius)
            }
            "USDG" -> {
                drawCircle(color = Color(0xFF43A047), radius = radius)
                val strokeW = w * 0.09f
                drawArc(
                    color = Color.White,
                    startAngle = 45f,
                    sweepAngle = 270f,
                    useCenter = false,
                    topLeft = Offset(w * 0.28f, h * 0.28f),
                    size = Size(w * 0.44f, h * 0.44f),
                    style = Stroke(width = strokeW, cap = StrokeCap.Round)
                )
                drawLine(
                    color = Color.White,
                    start = Offset(w * 0.5f, h * 0.5f),
                    end = Offset(w * 0.72f, h * 0.5f),
                    strokeWidth = strokeW,
                    cap = StrokeCap.Round
                )
            }
            "USDC" -> {
                drawCircle(color = Color(0xFF2775CA), radius = radius)
                drawCircle(color = Color.White, radius = radius * 0.75f, style = Stroke(width = w * 0.05f))
                val strokeW = w * 0.08f
                drawArc(
                    color = Color.White,
                    startAngle = 180f,
                    sweepAngle = 180f,
                    useCenter = false,
                    topLeft = Offset(w * 0.38f, h * 0.33f),
                    size = Size(w * 0.24f, h * 0.22f),
                    style = Stroke(width = strokeW, cap = StrokeCap.Round)
                )
                drawArc(
                    color = Color.White,
                    startAngle = 0f,
                    sweepAngle = 180f,
                    useCenter = false,
                    topLeft = Offset(w * 0.38f, h * 0.45f),
                    size = Size(w * 0.24f, h * 0.22f),
                    style = Stroke(width = strokeW, cap = StrokeCap.Round)
                )
                drawLine(Color.White, Offset(w * 0.5f, h * 0.25f), Offset(w * 0.5f, h * 0.75f), strokeW * 0.8f, cap = StrokeCap.Round)
            }
            "BTC" -> {
                drawCircle(color = Color(0xFFF7931A), radius = radius)
                rotate(degrees = 12f) {
                    val strokeW = w * 0.09f
                    drawLine(Color.White, Offset(w * 0.34f, h * 0.25f), Offset(w * 0.34f, h * 0.75f), strokeW, cap = StrokeCap.Round)
                    drawLine(Color.White, Offset(w * 0.44f, h * 0.20f), Offset(w * 0.44f, h * 0.80f), strokeW * 0.8f, cap = StrokeCap.Round)
                    drawLine(Color.White, Offset(w * 0.52f, h * 0.20f), Offset(w * 0.52f, h * 0.80f), strokeW * 0.8f, cap = StrokeCap.Round)
                    drawArc(
                        color = Color.White,
                        startAngle = -90f,
                        sweepAngle = 180f,
                        useCenter = false,
                        topLeft = Offset(w * 0.34f, h * 0.28f),
                        size = Size(w * 0.34f, h * 0.22f),
                        style = Stroke(width = strokeW, cap = StrokeCap.Round)
                    )
                    drawArc(
                        color = Color.White,
                        startAngle = -90f,
                        sweepAngle = 180f,
                        useCenter = false,
                        topLeft = Offset(w * 0.34f, h * 0.48f),
                        size = Size(w * 0.38f, h * 0.24f),
                        style = Stroke(width = strokeW, cap = StrokeCap.Round)
                    )
                }
            }
            "ETH" -> {
                drawCircle(color = Color(0xFF627EEA), radius = radius)
                val p = Path().apply {
                    moveTo(w * 0.5f, h * 0.22f)
                    lineTo(w * 0.68f, h * 0.48f)
                    lineTo(w * 0.5f, h * 0.58f)
                    lineTo(w * 0.32f, h * 0.48f)
                    close()
                }
                drawPath(p, color = Color.White)
                
                val pInner = Path().apply {
                    moveTo(w * 0.5f, h * 0.22f)
                    lineTo(w * 0.5f, h * 0.58f)
                    lineTo(w * 0.32f, h * 0.48f)
                    close()
                }
                drawPath(pInner, color = Color.White.copy(alpha = 0.6f))
                
                val pBottom = Path().apply {
                    moveTo(w * 0.5f, h * 0.62f)
                    lineTo(w * 0.68f, h * 0.52f)
                    lineTo(w * 0.5f, h * 0.78f)
                    lineTo(w * 0.32f, h * 0.52f)
                    close()
                }
                drawPath(pBottom, color = Color.White)
                
                val pBottomInner = Path().apply {
                    moveTo(w * 0.5f, h * 0.62f)
                    lineTo(w * 0.5f, h * 0.78f)
                    lineTo(w * 0.32f, h * 0.52f)
                    close()
                }
                drawPath(pBottomInner, color = Color.White.copy(alpha = 0.6f))
            }
            "XAUT" -> {
                drawCircle(color = Color(0xFFB8860B), radius = radius)
                val strokeW = w * 0.08f
                drawCircle(color = Color.White, radius = radius * 0.78f, style = Stroke(width = w * 0.04f))
                drawLine(Color.White, Offset(w * 0.32f, h * 0.36f), Offset(w * 0.68f, h * 0.36f), strokeW, cap = StrokeCap.Round)
                drawLine(Color.White, Offset(w * 0.5f, h * 0.36f), Offset(w * 0.5f, h * 0.70f), strokeW, cap = StrokeCap.Round)
            }
            "SOL" -> {
                drawRealSolanaLogo(w, h, radius)
            }
            "TRX" -> {
                drawCircle(color = Color(0xFFEF002F), radius = radius)
                val p = Path().apply {
                    moveTo(w * 0.5f, h * 0.25f)
                    lineTo(w * 0.75f, h * 0.65f)
                    lineTo(w * 0.25f, h * 0.65f)
                    close()
                }
                drawPath(p, color = Color.White, style = Stroke(width = w * 0.08f, join = StrokeJoin.Round))
                drawLine(Color.White, Offset(w * 0.5f, h * 0.25f), Offset(w * 0.5f, h * 0.65f), strokeWidth = w * 0.08f)
            }
            "1INCH" -> {
                drawCircle(color = Color(0xFF1B1B1D), radius = radius)
                val p = Path().apply {
                    moveTo(w * 0.35f, h * 0.72f)
                    lineTo(w * 0.35f, h * 0.40f)
                    lineTo(w * 0.50f, h * 0.28f)
                    lineTo(w * 0.65f, h * 0.40f)
                    lineTo(w * 0.52f, h * 0.52f)
                    lineTo(w * 0.68f, h * 0.68f)
                }
                drawPath(p, color = Color.White, style = Stroke(width = w * 0.08f, cap = StrokeCap.Round, join = StrokeJoin.Round))
            }
            "BNB" -> {
                drawCircle(color = Color(0xFFF3BA2F), radius = radius)
                val strokeW = w * 0.07f
                val path = Path().apply {
                    moveTo(w * 0.5f, h * 0.38f)
                    lineTo(w * 0.62f, h * 0.5f)
                    lineTo(w * 0.5f, h * 0.62f)
                    lineTo(w * 0.38f, h * 0.5f)
                    close()
                }
                drawPath(path, color = Color.White)
                
                val topP = Path().apply {
                    moveTo(w * 0.5f, h * 0.25f)
                    lineTo(w * 0.58f, h * 0.33f)
                    lineTo(w * 0.5f, h * 0.41f)
                    lineTo(w * 0.42f, h * 0.33f)
                    close()
                }
                drawPath(topP, color = Color.White, style = Stroke(width = strokeW))
                
                val botP = Path().apply {
                    moveTo(w * 0.5f, h * 0.59f)
                    lineTo(w * 0.58f, h * 0.67f)
                    lineTo(w * 0.5f, h * 0.75f)
                    lineTo(w * 0.42f, h * 0.67f)
                    close()
                }
                drawPath(botP, color = Color.White, style = Stroke(width = strokeW))
            }
            "LTC" -> {
                drawRealLitecoinLogo(w, h, radius)
            }
            else -> {
                drawCircle(color = Color.Gray, radius = radius)
            }
        }
    }
}

@Composable
fun NetworkIcon(symbol: String, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val url = when (symbol) {
        "Tron" -> "https://raw.githubusercontent.com/spothq/cryptocurrency-icons/master/128/color/trx.png"
        "Ethereum" -> "https://raw.githubusercontent.com/spothq/cryptocurrency-icons/master/128/color/eth.png"
        "X Layer" -> "https://assets.coingecko.com/markets/images/139/large/okx.png"
        "Aptos" -> "https://assets.coingecko.com/coins/images/26455/large/aptos_logo.png"
        "Arbitrum" -> "https://assets.coingecko.com/coins/images/16547/large/arbitrum-one.png"
        "Avalanche" -> "https://assets.coingecko.com/coins/images/12559/large/Avalanche_Circle_RedWhite_Trans.png"
        "Berachain" -> "https://assets.coingecko.com/coins/images/33580/large/bera.png"
        "Monad" -> "https://assets.coingecko.com/coins/images/34320/large/monad_logo.png"
        "Optimism" -> "https://assets.coingecko.com/coins/images/25244/large/Optimism.png"
        else -> null
    }

    if (url != null) {
        SubcomposeAsyncImage(
            model = ImageRequest.Builder(context)
                .data(url)
                .crossfade(true)
                .build(),
            contentDescription = symbol,
            modifier = modifier,
            loading = {
                NetworkIconFallback(symbol = symbol, modifier = Modifier.fillMaxSize())
            },
            error = {
                NetworkIconFallback(symbol = symbol, modifier = Modifier.fillMaxSize())
            }
        )
    } else {
        NetworkIconFallback(symbol = symbol, modifier = modifier)
    }
}

@Composable
fun NetworkIconFallback(symbol: String, modifier: Modifier = Modifier) {
    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        val radius = w / 2f
        
        when (symbol) {
            "Solana" -> {
                drawRealSolanaLogo(w, h, radius)
            }
            "Tron" -> {
                drawCircle(color = Color(0xFFEF002F), radius = radius)
                val p = Path().apply {
                    moveTo(w * 0.5f, h * 0.25f)
                    lineTo(w * 0.75f, h * 0.65f)
                    lineTo(w * 0.25f, h * 0.65f)
                    close()
                }
                drawPath(p, color = Color.White, style = Stroke(width = w * 0.08f, join = StrokeJoin.Round))
                drawLine(Color.White, Offset(w * 0.5f, h * 0.25f), Offset(w * 0.5f, h * 0.65f), strokeWidth = w * 0.08f)
            }
            "Ethereum" -> {
                drawCircle(color = Color(0xFF3758E6), radius = radius)
                val p = Path().apply {
                    moveTo(w * 0.5f, h * 0.22f)
                    lineTo(w * 0.68f, h * 0.48f)
                    lineTo(w * 0.5f, h * 0.58f)
                    lineTo(w * 0.32f, h * 0.48f)
                    close()
                }
                drawPath(p, color = Color.White)
                val pInner = Path().apply {
                    moveTo(w * 0.5f, h * 0.22f)
                    lineTo(w * 0.5f, h * 0.58f)
                    lineTo(w * 0.32f, h * 0.48f)
                    close()
                }
                drawPath(pInner, color = Color.White.copy(alpha = 0.6f))
                
                val pBottom = Path().apply {
                    moveTo(w * 0.5f, h * 0.62f)
                    lineTo(w * 0.68f, h * 0.52f)
                    lineTo(w * 0.5f, h * 0.78f)
                    lineTo(w * 0.32f, h * 0.52f)
                    close()
                }
                drawPath(pBottom, color = Color.White)
            }
            "X Layer" -> {
                // Background dark circle
                drawCircle(color = PureBlack, radius = radius)
                
                // OKX 5-squares checkerboard pattern
                // Bounding box of 3x3 pattern is 56% of the icon width
                val boxLen = w * 0.56f
                val cellS = boxLen / 3f
                val left = w * 0.5f - boxLen / 2f
                val top = h * 0.5f - boxLen / 2f
                
                val innerS = cellS * 0.84f
                val pad = (cellS - innerS) / 2f
                
                fun drawSquare(row: Int, col: Int) {
                    val sx = left + col * cellS + pad
                    val sy = top + row * cellS + pad
                    drawRect(
                        color = Color.White,
                        topLeft = Offset(sx, sy),
                        size = Size(innerS, innerS)
                    )
                }
                
                // Draw 5 squares in checkerboard arrangement:
                drawSquare(0, 0)
                drawSquare(0, 2)
                drawSquare(1, 1)
                drawSquare(2, 0)
                drawSquare(2, 2)
            }
            "Aptos" -> {
                drawCircle(color = PureBlack, radius = radius)
                drawCircle(color = Color.White, radius = radius * 0.95f, style = Stroke(width = w * 0.05f))
                val strokeW = h * 0.08f
                drawLine(Color.White, Offset(w * 0.25f, h * 0.38f), Offset(w * 0.75f, h * 0.38f), strokeW, cap = StrokeCap.Round)
                drawLine(Color.White, Offset(w * 0.20f, h * 0.50f), Offset(w * 0.80f, h * 0.50f), strokeW, cap = StrokeCap.Round)
                drawLine(Color.White, Offset(w * 0.25f, h * 0.62f), Offset(w * 0.75f, h * 0.62f), strokeW, cap = StrokeCap.Round)
            }
            "Arbitrum" -> {
                drawCircle(color = Color(0xFF1F84F4), radius = radius)
                val p = Path().apply {
                    moveTo(w * 0.5f, h * 0.25f)
                    lineTo(w * 0.78f, h * 0.75f)
                    lineTo(w * 0.22f, h * 0.75f)
                    close()
                }
                drawPath(p, color = Color.White)
                val p2 = Path().apply {
                    moveTo(w * 0.5f, h * 0.45f)
                    lineTo(w * 0.68f, h * 0.75f)
                    lineTo(w * 0.32f, h * 0.75f)
                    close()
                }
                drawPath(p2, color = Color(0xFF1F84F4))
            }
            "Avalanche" -> {
                drawCircle(color = Color(0xFFE84142), radius = radius)
                val p = Path().apply {
                    moveTo(w * 0.5f, h * 0.22f)
                    lineTo(w * 0.80f, h * 0.74f)
                    lineTo(w * 0.60f, h * 0.74f)
                    lineTo(w * 0.5f, h * 0.55f)
                    lineTo(w * 0.40f, h * 0.74f)
                    lineTo(w * 0.20f, h * 0.74f)
                    close()
                }
                drawPath(p, color = Color.White)
            }
            "Berachain" -> {
                drawCircle(color = Color(0xFF8B5A2B), radius = radius)
                drawCircle(color = Color.White, radius = radius * 0.35f, center = Offset(w * 0.5f, h * 0.55f))
                drawCircle(color = Color.White, radius = radius * 0.15f, center = Offset(w * 0.32f, h * 0.32f))
                drawCircle(color = Color.White, radius = radius * 0.15f, center = Offset(w * 0.50f, h * 0.24f))
                drawCircle(color = Color.White, radius = radius * 0.15f, center = Offset(w * 0.68f, h * 0.32f))
            }
            "Monad" -> {
                drawCircle(color = Color(0xFF836EF9), radius = radius)
                val p = Path().apply {
                    moveTo(w * 0.5f, h * 0.25f)
                    lineTo(w * 0.75f, h * 0.4f)
                    lineTo(w * 0.75f, h * 0.65f)
                    lineTo(w * 0.5f, h * 0.8f)
                    lineTo(w * 0.25f, h * 0.65f)
                    lineTo(w * 0.25f, h * 0.4f)
                    close()
                }
                drawPath(p, color = Color.White)
                drawCircle(color = Color(0xFF836EF9), radius = radius * 0.22f)
            }
            "Optimism" -> {
                drawCircle(color = Color(0xFFFF0420), radius = radius)
                val strokeW = w * 0.08f
                drawCircle(color = Color.White, radius = radius * 0.32f, style = Stroke(width = strokeW), center = Offset(w * 0.5f, h * 0.5f))
            }
            "Litecoin", "LTC" -> {
                drawRealLitecoinLogo(w, h, radius)
            }
            "USDT", "Tether" -> {
                drawRealUsdtLogo(w, h, radius)
            }
            else -> {
                drawCircle(color = Color.Gray, radius = radius)
            }
        }
    }
}

@Composable
fun QrCodeRenderer(symbol: String, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .background(Color(0xFF121212), shape = RoundedCornerShape(24.dp))
            .padding(12.dp)
            .background(Color.White, shape = RoundedCornerShape(18.dp))
            .padding(14.dp),
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val w = size.width
            val h = size.height
            
            val gridSize = 29
            val cellSize = w / gridSize.toFloat()
            
            // Draw corner finder circles
            fun drawFinder(cx: Float, cy: Float) {
                // Outer ring: radius is 3 * cellSize, stroke is cellSize
                drawCircle(
                    color = PureBlack,
                    radius = cellSize * 3f,
                    center = Offset(cx, cy),
                    style = Stroke(width = cellSize)
                )
                // Inner solid dot: radius is 1.5 * cellSize
                drawCircle(
                    color = PureBlack,
                    radius = cellSize * 1.5f,
                    center = Offset(cx, cy)
                )
            }
            
            // Top-Left (Center at index 3, 3)
            drawFinder(3.5f * cellSize, 3.5f * cellSize)
            // Top-Right (Center at index gridSize - 4, 3)
            drawFinder((gridSize - 3.5f) * cellSize, 3.5f * cellSize)
            // Bottom-Left (Center at index 3, gridSize - 4)
            drawFinder(3.5f * cellSize, (gridSize - 3.5f) * cellSize)
            
            // Draw Alignment Pattern at (22, 22) (Center at index 22, 22)
            val ax = 22.5f * cellSize
            val ay = 22.5f * cellSize
            // Outer ring
            drawCircle(
                color = PureBlack,
                radius = cellSize * 2f,
                center = Offset(ax, ay),
                style = Stroke(width = cellSize * 0.8f)
            )
            // Inner dot
            drawCircle(
                color = PureBlack,
                radius = cellSize * 0.6f,
                center = Offset(ax, ay)
            )
            
            // Setup deterministic pseudorandom grid with fixed seed
            val qrGrid = Array(gridSize) { BooleanArray(gridSize) }
            val seed = symbol.uppercase().hashCode().toLong()
            val random = java.util.Random(seed)
            for (r in 0 until gridSize) {
                for (c in 0 until gridSize) {
                    qrGrid[r][c] = random.nextDouble() < 0.48
                }
            }
            
            // Draw timing pattern lines: alternate row 6 and col 6
            for (i in 8 until gridSize - 8) {
                qrGrid[6][i] = (i % 2 == 0)
                qrGrid[i][6] = (i % 2 == 0)
            }
            
            // Render grid dots
            for (row in 0 until gridSize) {
                for (col in 0 until gridSize) {
                    // Skip finders & borders
                    if (row in 0..7 && col in 0..7) continue
                    if (row in 0..7 && col in (gridSize - 8)..(gridSize - 1)) continue
                    if (row in (gridSize - 8)..(gridSize - 1) && col in 0..7) continue
                    
                    // Skip alignment pattern & border
                    if (row in 20..24 && col in 20..24) continue
                    
                    // Skip center logo overlay zone (7x7 cells)
                    if (row in 11..17 && col in 11..17) continue
                    
                    if (qrGrid[row][col]) {
                        val cx = col * cellSize + cellSize / 2f
                        val cy = row * cellSize + cellSize / 2f
                        drawCircle(
                            color = PureBlack,
                            radius = cellSize * 0.48f,
                            center = Offset(cx, cy)
                        )
                    }
                }
            }
        }
        
        // Centered coin logo overlay (perfectly matches skipped center 11..17 cells)
        Box(
            modifier = Modifier
                .size(40.dp)
                .background(Color.White, shape = CircleShape)
                .padding(2.dp)
        ) {
            CryptoIcon(
                symbol = symbol,
                modifier = Modifier
                    .fillMaxSize()
                    .clip(CircleShape)
            )
        }
    }
}

fun getDepositAddress(symbol: String, networkName: String): String {
    return when {
        networkName.contains("Tron", ignoreCase = true) || networkName.contains("TRC20") -> {
            "TLArCA66bP1jKUjvdM4fcwdY1w3kiqUZNW"
        }
        networkName.contains("Ethereum", ignoreCase = true) || networkName.contains("ERC20") -> {
            "0x3f5CE5FBFe3E9af3971dD833D26bA9b5C936f0bE"
        }
        symbol == "BTC" -> {
            "3EktnHQD7RiST67Dq7Y9Q3UptvC9FWe8f"
        }
        symbol == "ETH" -> {
            "0x71C7656EC7ab88b098defB751B7401B5f6d8976F"
        }
        symbol == "SOL" -> {
            "8x9N5fD7g4vD8T6xUp5c7X7VwW2qN7hT5rS8vC9f"
        }
        else -> {
            "TLArCA66bP1jKUjvdM4fcwdY${symbol}w3kiqUZNW"
        }
    }
}

@Composable
fun SelectAssetScreen(
    onBack: () -> Unit,
    onAssetSelected: (CryptoAsset) -> Unit
) {
    var searchQuery by remember { mutableStateOf("") }
    val filteredAssets = remember(searchQuery) {
        if (searchQuery.isEmpty()) {
            CryptoAssetsList
        } else {
            CryptoAssetsList.filter {
                it.symbol.contains(searchQuery, ignoreCase = true) ||
                it.name.contains(searchQuery, ignoreCase = true)
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(PureBlack)
            .statusBarsPadding()
    ) {
        // Header
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp)
                .padding(horizontal = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            IconButton(onClick = onBack) {
                ChevronLeftIcon(
                    color = Color.White,
                    modifier = Modifier.size(24.dp)
                )
            }
            Text(
                text = "Select asset",
                color = Color.White,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
                modifier = Modifier.weight(1f)
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = { /* Help */ }) {
                    HelpOutlineIcon(
                        color = Color.White,
                        modifier = Modifier.size(20.dp)
                    )
                }
                IconButton(onClick = { /* History */ }) {
                    HistorySheetIcon(
                        color = Color.White,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        }

        // Search Bar
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp)
                .background(Color(0xFF1C1C1E), shape = RoundedCornerShape(22.dp))
                .padding(horizontal = 14.dp, vertical = 10.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(
                    imageVector = Icons.Default.Search,
                    contentDescription = "Search",
                    tint = Color.Gray,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(10.dp))
                BasicTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    textStyle = androidx.compose.ui.text.TextStyle(color = Color.White, fontSize = 14.sp),
                    singleLine = true,
                    decorationBox = { innerTextField ->
                        if (searchQuery.isEmpty()) {
                            Text(text = "Search by crypto", color = Color.Gray, fontSize = 14.sp)
                        }
                        innerTextField()
                    },
                    modifier = Modifier.weight(1f)
                )
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        // Recent deposit Section
        if (searchQuery.isEmpty()) {
            Column(modifier = Modifier.padding(horizontal = 16.dp)) {
                Text(
                    text = "Recent deposit",
                    color = Color.Gray,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium
                )
                Spacer(modifier = Modifier.height(10.dp))
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    RecentDepositsList.forEach { asset ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .background(Color(0xFF151515), shape = RoundedCornerShape(18.dp))
                                .border(0.5.dp, Color(0xFF2C2C2E), shape = RoundedCornerShape(18.dp))
                                .clickable { onAssetSelected(asset) }
                                .padding(horizontal = 12.dp, vertical = 8.dp)
                        ) {
                            CryptoIcon(symbol = asset.symbol, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = asset.symbol,
                                color = Color.White,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        // Crypto List Section
        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
        ) {
            Text(
                text = "Crypto",
                color = Color.Gray,
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium,
                modifier = Modifier.padding(bottom = 10.dp)
            )

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                filteredAssets.forEach { crypto ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onAssetSelected(crypto) }
                            .padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        CryptoIcon(symbol = crypto.symbol, modifier = Modifier.size(36.dp))
                        Spacer(modifier = Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = crypto.symbol,
                                    color = Color.White,
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                if (crypto.apr != null) {
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Box(
                                        modifier = Modifier
                                            .background(Color(0xFF1C3A27), shape = RoundedCornerShape(4.dp))
                                            .padding(horizontal = 6.dp, vertical = 2.dp)
                                    ) {
                                        Text(
                                            text = crypto.apr,
                                            color = Color(0xFF39D353),
                                            fontSize = 10.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                }
                            }
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = crypto.name,
                                color = Color.Gray,
                                fontSize = 12.sp
                            )
                        }
                    }
                }
                Spacer(modifier = Modifier.height(20.dp))
            }
        }
    }
}

@Composable
fun SelectNetworkScreen(
    asset: CryptoAsset,
    onBack: () -> Unit,
    onNetworkSelected: (NetworkOption) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(PureBlack)
            .statusBarsPadding()
    ) {
        // Header
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp)
                .padding(horizontal = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                ChevronLeftIcon(
                    color = Color.White,
                    modifier = Modifier.size(24.dp)
                )
            }
            Text(
                text = "Select network",
                color = Color.White,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .weight(1f)
                    .padding(end = 48.dp)
            )
        }

        // Info Banner
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp)
                .background(Color(0xFF151515), shape = RoundedCornerShape(12.dp))
                .border(0.5.dp, Color(0xFF2C2C2E), shape = RoundedCornerShape(12.dp))
                .padding(16.dp)
        ) {
            Row(
                verticalAlignment = Alignment.Top,
                modifier = Modifier.fillMaxWidth()
            ) {
                InfoIcon(
                    color = Color.White,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Not sure which network to choose?",
                        color = Color.White,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Make sure it matches the network on the platform or wallet you are withdrawing from.",
                        color = Color.Gray,
                        fontSize = 12.sp,
                        lineHeight = 16.sp
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    Text(
                        text = "Learn more →",
                        color = Color.White,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.clickable { /* Learn more link */ }
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        // Table headers row
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = "Network",
                color = Color.Gray,
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium
            )
            Text(
                text = "Arrival time/Min deposit",
                color = Color.Gray,
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium
            )
        }

        // Networks list
        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            getNetworksForAsset(asset.symbol).forEach { network ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onNetworkSelected(network) }
                        .padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    NetworkIcon(symbol = network.subtext, modifier = Modifier.size(28.dp))
                    Spacer(modifier = Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = network.name,
                                color = Color.White,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold
                            )
                            if (asset.symbol.uppercase() == "SOL" && network.name == "X Layer") {
                                Spacer(modifier = Modifier.width(6.dp))
                                Box(
                                    modifier = Modifier
                                        .background(Color(0xFF2C2C2E), shape = RoundedCornerShape(4.dp))
                                        .padding(horizontal = 6.dp, vertical = 2.dp)
                                ) {
                                    Text(
                                        text = "xSOL supported",
                                        color = Color(0xFFE5E5EA),
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Medium
                                    )
                                }
                            }
                        }
                    }
                    Spacer(modifier = Modifier.width(10.dp))
                    Column(horizontalAlignment = Alignment.End) {
                        Text(
                            text = network.arrivalTime,
                            color = Color.White,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = network.minDeposit,
                            color = Color.Gray,
                            fontSize = 11.sp
                        )
                    }
                }
            }
            Spacer(modifier = Modifier.height(30.dp))
        }
    }
}

@Composable
fun DepositAddressScreen(
    asset: CryptoAsset,
    network: NetworkOption,
    onBack: () -> Unit,
    onChangeNetwork: () -> Unit,
    onCloseFlow: () -> Unit
) {
    val clipboardManager = LocalClipboardManager.current
    var showToast by remember { mutableStateOf(false) }
    val address = remember(asset, network) { getDepositAddress(asset.symbol, network.name) }

    LaunchedEffect(showToast) {
        if (showToast) {
            delay(1500)
            showToast = false
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(PureBlack)
                .statusBarsPadding()
        ) {
            // Header
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp)
                    .padding(horizontal = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onBack) {
                    ChevronLeftIcon(
                        color = Color.White,
                        modifier = Modifier.size(24.dp)
                    )
                }
                Text(
                    text = "Deposit ${asset.symbol}",
                    color = Color.White,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.weight(1f)
                )
                IconButton(onClick = { /* More options */ }) {
                    MoreHorizIcon(
                        color = Color.White,
                        modifier = Modifier.size(24.dp)
                    )
                }
            }

            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Spacer(modifier = Modifier.height(10.dp))

                // QR Code Container
                QrCodeRenderer(
                    symbol = asset.symbol,
                    modifier = Modifier
                        .size(190.dp)
                        .testTag("qr_code_renderer")
                )

                Spacer(modifier = Modifier.height(20.dp))

                // Address label & layout
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.Start
                ) {
                    Text(
                        text = "Address >",
                        color = Color.Gray,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = address,
                            color = Color.White,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.weight(1.1f),
                            lineHeight = 20.sp
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Box(
                            modifier = Modifier
                                .size(38.dp)
                                .background(Color(0xFF1E1E1E), shape = CircleShape)
                                .clickable {
                                    clipboardManager.setText(AnnotatedString(address))
                                    showToast = true
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            ContentCopyIcon(
                                color = Color.White,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))

                // Detail table rows
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    // Row 1: Network
                    DetailRowItem(
                        label = "Network",
                        showInfoIcon = false,
                        showChevron = true,
                        onClick = onChangeNetwork
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            NetworkIcon(symbol = network.subtext, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = network.name,
                                color = Color.White,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }

                    // Row 2: Deposit account
                    DetailRowItem(
                        label = "Deposit account",
                        showInfoIcon = false,
                        showChevron = true
                    ) {
                        Text(
                            text = "Funding account",
                            color = Color.White,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    // Row 3: Minimum deposit
                    DetailRowItem(
                        label = "Minimum deposit",
                        showInfoIcon = true,
                        showChevron = false
                    ) {
                        Text(
                            text = network.minDeposit,
                            color = Color.White,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    // Row 4: Arrival time
                    DetailRowItem(
                        label = "Arrival time",
                        showInfoIcon = true,
                        showChevron = false
                    ) {
                        Text(
                            text = network.arrivalTime,
                            color = Color.White,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    // Row 5: Withdrawal available time
                    DetailRowItem(
                        label = "Withdrawal available time",
                        showInfoIcon = true,
                        showChevron = false
                    ) {
                        Text(
                            text = "~ 2 minutes",
                            color = Color.White,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    // Row 6: Contract address
                    DetailRowItem(
                        label = "Contract address",
                        showInfoIcon = false,
                        showChevron = true
                    ) {
                        Text(
                            text = "***gjLj6t",
                            color = Color.Gray,
                            fontSize = 14.sp
                        )
                    }
                }
                Spacer(modifier = Modifier.height(40.dp))
            }
        }

        // Animated copy Toast message overlay
        if (showToast) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(bottom = 60.dp),
                contentAlignment = Alignment.BottomCenter
            ) {
                Box(
                    modifier = Modifier
                        .background(Color(0xFF2C2C2E), shape = RoundedCornerShape(12.dp))
                        .padding(horizontal = 24.dp, vertical = 12.dp)
                ) {
                    Text(
                        text = "Address copied",
                        color = Color.White,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        }
    }
}

@Composable
fun DetailRowItem(
    label: String,
    showInfoIcon: Boolean,
    showChevron: Boolean = false,
    onClick: (() -> Unit)? = null,
    valueContent: @Composable () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = label,
                color = Color.Gray,
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium
            )
            if (showInfoIcon) {
                Spacer(modifier = Modifier.width(4.dp))
                InfoIcon(
                    color = Color.Gray,
                    modifier = Modifier.size(14.dp)
                )
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            valueContent()
            if (showChevron) {
                Spacer(modifier = Modifier.width(4.dp))
                ChevronRightIcon(
                    color = Color.Gray,
                    modifier = Modifier.size(18.dp)
                )
            } else {
                Spacer(modifier = Modifier.width(22.dp))
            }
        }
    }
}

@Composable
fun MainAppContainer() {
    var showSplashScreen by remember { mutableStateOf(!MainActivity.isSplashAlreadyShown) }
    var currentTab by remember { mutableStateOf(BottomTab.Assets) }
    var isBalanceVisible by remember { mutableStateOf(true) }

    // Deposit Flow States
    var depositFlowStep by remember { mutableStateOf<DepositFlowStep?>(null) }
    var selectedCrypto by remember { mutableStateOf<CryptoAsset?>(null) }
    var selectedNetwork by remember { mutableStateOf<NetworkOption?>(null) }

    // Withdraw Flow States
    var withdrawFlowStep by remember { mutableStateOf<WithdrawFlowStep?>(null) }
    var showWithdrawOptionsSheet by remember { mutableStateOf(false) }
    var selectedWithdrawCrypto by remember { mutableStateOf<String?>(null) }
    var selectedWithdrawNetwork by remember { mutableStateOf<WithdrawNetwork?>(null) }
    var selectedWithdrawAddress by remember { mutableStateOf("") }
    var selectedWithdrawAmount by remember { mutableStateOf(0.0) }

    // Wallet State from Database/ViewModel
    val walletViewModel: WalletViewModel = viewModel()
    val isOnline by walletViewModel.isOnline.collectAsStateWithLifecycle()
    val totalBalanceUsd by walletViewModel.totalBalanceUsd.collectAsStateWithLifecycle()
    val coinBalances by walletViewModel.coinBalances.collectAsStateWithLifecycle()
    val prices by walletViewModel.prices.collectAsStateWithLifecycle()
    val currentUser by walletViewModel.currentUser.collectAsStateWithLifecycle()
    val showDepositReceived by walletViewModel.showDepositReceived.collectAsStateWithLifecycle()
    val transactions by walletViewModel.transactions.collectAsStateWithLifecycle()
    var pendingActionAfterAuth by remember { mutableStateOf<(() -> Unit)?>(null) }
    
    var showHistoryScreen by remember { mutableStateOf(false) }
    var selectedHistoryTransaction by remember { mutableStateOf<TransactionEntity?>(null) }
    var showAddBalanceDialog by remember { mutableStateOf(false) }
    var showPasskeySetupDialog by remember { mutableStateOf(false) }
    var updateConfig by remember { mutableStateOf<FirebaseSyncManager.UpdateConfig?>(null) }
    var isDownloadingUpdate by remember { mutableStateOf(false) }
    var downloadProgress by remember { mutableStateOf(0f) }
    var downloadErrorMsg by remember { mutableStateOf<String?>(null) }
    var isRefreshing by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    val context = androidx.compose.ui.platform.LocalContext.current
    LaunchedEffect(currentUser) {
        if (currentUser == null) {
            if (showHistoryScreen) {
                showHistoryScreen = false
                pendingActionAfterAuth = { showHistoryScreen = true }
            }
            if (depositFlowStep != null) {
                depositFlowStep = null
                pendingActionAfterAuth = { depositFlowStep = DepositFlowStep.SelectAsset }
            }
            if (withdrawFlowStep != null) {
                withdrawFlowStep = null
                pendingActionAfterAuth = { showWithdrawOptionsSheet = true }
            }
            if (showWithdrawOptionsSheet) {
                showWithdrawOptionsSheet = false
                pendingActionAfterAuth = { showWithdrawOptionsSheet = true }
            }
            if (showAddBalanceDialog) {
                showAddBalanceDialog = false
                pendingActionAfterAuth = { showAddBalanceDialog = true }
            }
        }
    }

    LaunchedEffect(context) {
        FirebaseSyncManager.checkAppUpdate(context) { config ->
            updateConfig = config
        }
    }

    LaunchedEffect(Unit) {
        if (!MainActivity.isSplashAlreadyShown) {
            delay(1500)
            MainActivity.isSplashAlreadyShown = true
            showSplashScreen = false
        }
    }

    val currentRemoteConfig = updateConfig ?: FirebaseSyncManager.UpdateConfig()

    val dynamicPrimaryColor = remember(currentRemoteConfig.primaryColorHex) {
        parseColorSafely(currentRemoteConfig.primaryColorHex, OkxGreen)
    }

    MyApplicationTheme(primaryColor = dynamicPrimaryColor) {
        if (currentRemoteConfig.maintenanceMode) {
            MaintenanceScreen(message = currentRemoteConfig.maintenanceMessage)
        } else if (showSplashScreen) {
            SplashLoadingScreen(config = currentRemoteConfig)
        } else {
            Box(modifier = Modifier.fillMaxSize()) {
            Scaffold(
                modifier = Modifier
                    .fillMaxSize()
                    .background(PureBlack),
                bottomBar = {
                    BottomNavigationBar(
                        currentTab = currentTab,
                        onTabSelected = { currentTab = it },
                        onExploreDoubleTap = { showPasskeySetupDialog = true },
                        config = currentRemoteConfig
                    )
                }
            ) { innerPadding ->
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(PureBlack)
                        .padding(innerPadding)
                ) {
                    if (!isOnline) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(Color(0xFF801A1A))
                                .padding(vertical = 8.dp, horizontal = 16.dp)
                                .testTag("offline_restricted_banner")
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.Center,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Default.CloudOff,
                                    contentDescription = "Cloud Off",
                                    tint = Color.White,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "Unable to connect to the internet. Try again later.",
                                    color = Color.White,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                    Box(modifier = Modifier.weight(1f)) {
                        when (currentTab) {
                        BottomTab.OKX -> {
                            OkxPullToRefresh(
                                isRefreshing = isRefreshing,
                                onRefresh = {
                                    scope.launch {
                                        isRefreshing = true
                                        isRefreshing = false
                                    }
                                }
                            ) {
                                OkxScreenContent(
                                    totalBalanceUsd = totalBalanceUsd,
                                    isBalanceVisible = isBalanceVisible,
                                    onToggleBalance = { isBalanceVisible = !isBalanceVisible },
                                    onDepositClick = {
                                        if (currentUser == null) {
                                            pendingActionAfterAuth = { depositFlowStep = DepositFlowStep.SelectAsset }
                                        } else {
                                            depositFlowStep = DepositFlowStep.SelectAsset
                                        }
                                    },
                                    onWithdrawClick = {
                                        if (currentUser == null) {
                                            pendingActionAfterAuth = { showWithdrawOptionsSheet = true }
                                        } else {
                                            showWithdrawOptionsSheet = true
                                        }
                                    },
                                    onMenuClick = {
                                        if (currentUser == null) {
                                            pendingActionAfterAuth = { showAddBalanceDialog = true }
                                        } else {
                                            showAddBalanceDialog = true
                                        }
                                    },
                                    showMenuButton = true,
                                    config = currentRemoteConfig
                                )
                            }
                        }
                        BottomTab.Assets -> {
                            OkxPullToRefresh(
                                isRefreshing = isRefreshing,
                                onRefresh = {
                                    scope.launch {
                                        isRefreshing = true
                                        isRefreshing = false
                                    }
                                }
                            ) {
                                AssetsScreenContent(
                                    totalBalanceUsd = totalBalanceUsd,
                                    coinBalances = coinBalances,
                                    prices = prices,
                                    isBalanceVisible = isBalanceVisible,
                                    onToggleBalance = { isBalanceVisible = !isBalanceVisible },
                                    onDepositClick = {
                                        if (currentUser == null) {
                                            pendingActionAfterAuth = { depositFlowStep = DepositFlowStep.SelectAsset }
                                        } else {
                                            depositFlowStep = DepositFlowStep.SelectAsset
                                        }
                                    },
                                    onWithdrawClick = {
                                        if (currentUser == null) {
                                            pendingActionAfterAuth = { showWithdrawOptionsSheet = true }
                                        } else {
                                            showWithdrawOptionsSheet = true
                                        }
                                    },
                                    showDepositReceived = showDepositReceived,
                                    onHistoryClick = {
                                        if (currentUser == null) {
                                            pendingActionAfterAuth = { showHistoryScreen = true }
                                        } else {
                                            showHistoryScreen = true
                                        }
                                    }
                                )
                            }
                        }
                        else -> {
                            // Placeholder for other tabs
                            PlaceholderScreenContent(
                                tabName = currentTab.name,
                                onBackToAssets = { currentTab = BottomTab.Assets }
                            )
                        }
                    }
                }
            }
        }

            // Full-screen overlay of History / Recent Transactions
            if (showHistoryScreen && currentUser != null) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(PureBlack)
                ) {
                    if (selectedHistoryTransaction != null) {
                        TransactionDetailScreen(
                            transaction = selectedHistoryTransaction!!,
                            onBack = { selectedHistoryTransaction = null }
                        )
                    } else {
                        HistoryScreen(
                            transactions = transactions,
                            onBack = { showHistoryScreen = false },
                            onTransactionClick = { tx -> selectedHistoryTransaction = tx }
                        )
                    }
                }
            }

            // Full-screen overlay of Deposit Flow screens
            if (depositFlowStep != null && currentUser != null) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(PureBlack)
                ) {
                    when (depositFlowStep) {
                        DepositFlowStep.SelectAsset -> {
                            SelectAssetScreen(
                                onBack = { depositFlowStep = null },
                                onAssetSelected = { crypto ->
                                    selectedCrypto = crypto
                                    depositFlowStep = DepositFlowStep.SelectNetwork
                                }
                            )
                        }
                        DepositFlowStep.SelectNetwork -> {
                            SelectNetworkScreen(
                                asset = selectedCrypto ?: CryptoAssetsList[0],
                                onBack = { depositFlowStep = DepositFlowStep.SelectAsset },
                                onNetworkSelected = { network ->
                                    selectedNetwork = network
                                    depositFlowStep = DepositFlowStep.DepositAddress
                                }
                            )
                        }
                        DepositFlowStep.DepositAddress -> {
                            DepositAddressScreen(
                                asset = selectedCrypto ?: CryptoAssetsList[0],
                                network = selectedNetwork ?: getNetworksForAsset(selectedCrypto?.symbol ?: "USDT").first(),
                                onBack = { depositFlowStep = DepositFlowStep.SelectNetwork },
                                onChangeNetwork = { depositFlowStep = DepositFlowStep.SelectNetwork },
                                onCloseFlow = { depositFlowStep = null }
                            )
                        }
                        null -> {}
                    }
                }
            }

            // Full-screen overlay of Withdraw Flow screens
            if (withdrawFlowStep != null && currentUser != null) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(PureBlack)
                ) {
                    when (withdrawFlowStep) {
                        WithdrawFlowStep.SelectAsset -> {
                            WithdrawSelectAssetScreen(
                                coinBalances = coinBalances,
                                prices = prices,
                                onBack = { withdrawFlowStep = null },
                                onAssetSelected = { symbol ->
                                    selectedWithdrawCrypto = symbol
                                    withdrawFlowStep = WithdrawFlowStep.SelectNetwork
                                }
                            )
                        }
                        WithdrawFlowStep.SelectNetwork -> {
                            WithdrawSelectNetworkScreen(
                                symbol = selectedWithdrawCrypto ?: "SOL",
                                prices = prices,
                                onBack = { withdrawFlowStep = WithdrawFlowStep.SelectAsset },
                                onNetworkSelected = { network ->
                                    selectedWithdrawNetwork = network
                                    withdrawFlowStep = WithdrawFlowStep.EnterAddress
                                }
                            )
                        }
                        WithdrawFlowStep.EnterAddress -> {
                            WithdrawAddressScreen(
                                symbol = selectedWithdrawCrypto ?: "SOL",
                                selectedNetwork = selectedWithdrawNetwork,
                                onBack = { withdrawFlowStep = WithdrawFlowStep.SelectNetwork },
                                onNext = { address ->
                                    selectedWithdrawAddress = address
                                    withdrawFlowStep = WithdrawFlowStep.WithdrawDetails
                                }
                            )
                        }
                        WithdrawFlowStep.WithdrawDetails -> {
                            WithdrawDetailsScreen(
                                symbol = selectedWithdrawCrypto ?: "SOL",
                                selectedNetwork = selectedWithdrawNetwork,
                                preEnteredAddress = selectedWithdrawAddress,
                                coinBalances = coinBalances,
                                prices = prices,
                                viewModel = walletViewModel,
                                onBack = { withdrawFlowStep = WithdrawFlowStep.EnterAddress },
                                onSuccess = { amount ->
                                    selectedWithdrawAmount = amount
                                    withdrawFlowStep = WithdrawFlowStep.Processing
                                }
                            )
                        }
                        WithdrawFlowStep.Processing -> {
                            WithdrawalProcessingScreen(
                                symbol = selectedWithdrawCrypto ?: "SOL",
                                selectedNetwork = selectedWithdrawNetwork,
                                address = selectedWithdrawAddress,
                                amount = selectedWithdrawAmount,
                                onDone = {
                                    withdrawFlowStep = null
                                    selectedWithdrawCrypto = null
                                    selectedWithdrawNetwork = null
                                    selectedWithdrawAddress = ""
                                    selectedWithdrawAmount = 0.0
                                }
                            )
                        }
                        WithdrawFlowStep.P2pTrading -> {
                            WithdrawP2pTradingScreen(
                                onBack = { withdrawFlowStep = null }
                            )
                        }
                        null -> {}
                    }
                }
            }

            // Manage Balance Dialog (Add / Withdraw)
            if (showAddBalanceDialog && currentUser != null) {
                AddWithdrawBalanceDialog(
                    onDismiss = { showAddBalanceDialog = false },
                    viewModel = walletViewModel,
                    onConfigSaved = {
                        FirebaseSyncManager.checkAppUpdate(context) { config ->
                            updateConfig = config
                        }
                    }
                )
            }

            if (showPasskeySetupDialog) {
                PasskeySetupDialog(
                    onDismiss = { showPasskeySetupDialog = false }
                )
            }

            if (pendingActionAfterAuth != null) {
                AuthenticationPromptDialog(
                    onDismiss = { pendingActionAfterAuth = null },
                    viewModel = walletViewModel,
                    onAuthSuccess = {
                        pendingActionAfterAuth?.invoke()
                        pendingActionAfterAuth = null
                    }
                )
            }

            // Real-time Mobile Update Dialog Prompt
            val activeConfig = updateConfig
            val currentVersionCode = remember(context) {
                try {
                    val packageInfo = context.packageManager.getPackageInfo(context.packageName, 0)
                    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.P) {
                        packageInfo.longVersionCode.toInt()
                    } else {
                        @Suppress("DEPRECATION")
                        packageInfo.versionCode
                    }
                } catch (e: Exception) {
                    1
                }
            }
            if (activeConfig != null && activeConfig.latestVersionCode > currentVersionCode) {
                val config = activeConfig
                androidx.compose.material3.AlertDialog(
                    onDismissRequest = {
                        if (!config.isForceUpdate && !isDownloadingUpdate) {
                            updateConfig = null
                        }
                    },
                    icon = {
                        Icon(
                            imageVector = Icons.Default.ArrowUpward,
                            contentDescription = "Update Icon",
                            tint = OkxGreen,
                            modifier = Modifier.size(36.dp)
                        )
                    },
                    title = {
                        Text(
                            text = if (config.isForceUpdate) "Mandatory Update Required" else "New Update Available (v${config.latestVersionName})",
                            color = Color.White,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.fillMaxWidth()
                        )
                    },
                    text = {
                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            if (isDownloadingUpdate) {
                                Text(
                                    text = "Downloading Update...",
                                    color = Color.White,
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(bottom = 8.dp)
                                )
                                androidx.compose.material3.LinearProgressIndicator(
                                    progress = downloadProgress,
                                    modifier = Modifier.fillMaxWidth().height(8.dp),
                                    color = OkxGreen,
                                    trackColor = Color.Gray.copy(alpha = 0.3f)
                                )
                                Text(
                                    text = "${(downloadProgress * 100).toInt()}%",
                                    color = OkxGreen,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    modifier = Modifier.padding(top = 8.dp)
                                )
                            } else {
                                val notes = config.releaseNotes.ifBlank {
                                    if (config.isForceUpdate) {
                                        "A critical security and performance update is required to continue using the application."
                                    } else {
                                        "We have added new features and performance improvements to make your trading experience better."
                                    }
                                }
                                Text(
                                    text = notes,
                                    color = Color.Gray,
                                    fontSize = 14.sp,
                                    textAlign = TextAlign.Center,
                                    modifier = Modifier.padding(bottom = 16.dp)
                                )
                                if (downloadErrorMsg != null) {
                                    val is404 = downloadErrorMsg?.contains("404") == true
                                    Text(
                                        text = if (is404) {
                                            "Error 404: The update APK file was not found on your GitHub repository.\n\n" +
                                            "To fix this, make sure your GitHub repository is set to PUBLIC (GitHub blocks raw file downloads on private repositories).\n\n" +
                                            "Also, ensure you have compiled your APK as 'app-release.apk' and uploaded it to the root of your repository's main branch."
                                        } else {
                                            "Download Error: $downloadErrorMsg\nClick Update Now to retry."
                                        },
                                        color = Color(0xFFFF5252),
                                        fontSize = 12.sp,
                                        textAlign = TextAlign.Center,
                                        modifier = Modifier.padding(top = 12.dp, start = 8.dp, end = 8.dp)
                                    )
                                    if (config.githubRepoUrl.isNotBlank()) {
                                        Spacer(modifier = Modifier.height(12.dp))
                                        Button(
                                            onClick = {
                                                try {
                                                    val browserIntent = android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse(config.githubRepoUrl)).apply {
                                                        addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                                                    }
                                                    context.startActivity(browserIntent)
                                                } catch (e: Exception) {
                                                    e.printStackTrace()
                                                }
                                            },
                                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2C2C2C)),
                                            modifier = Modifier.fillMaxWidth()
                                        ) {
                                            Text("Open GitHub Repository", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                                        }
                                    }
                                }
                            }
                        }
                    },
                    confirmButton = {
                        if (isDownloadingUpdate) {
                            androidx.compose.material3.CircularProgressIndicator(
                                color = OkxGreen,
                                modifier = Modifier.size(24.dp)
                            )
                        } else {
                            Button(
                                onClick = {
                                    scope.launch(kotlinx.coroutines.Dispatchers.IO) {
                                        var connection: java.net.HttpURLConnection? = null
                                        try {
                                            kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                                                isDownloadingUpdate = true
                                                downloadProgress = 0f
                                                downloadErrorMsg = null
                                            }
                                            
                                            val destinationFile = java.io.File(context.cacheDir, "app-update.apk")
                                            if (destinationFile.exists()) {
                                                destinationFile.delete()
                                            }
                                            
                                            val candidateUrls = LinkedHashSet<String>()
                                            
                                            // Add configured download URL
                                            val defaultUrl = FirebaseSyncManager.getResolvedDownloadUrl(config)
                                            if (defaultUrl.isNotBlank()) {
                                                candidateUrls.add(defaultUrl)
                                            }
                                            
                                            val githubUrl = config.githubRepoUrl.ifBlank { "https://github.com/arbitragelivetrades/okxx2" }
                                            val ownerRepo = FirebaseSyncManager.getOwnerAndRepo(githubUrl)
                                            if (ownerRepo != null) {
                                                val owner = ownerRepo.first
                                                val originalRepo = ownerRepo.second
                                                val repos = listOf(originalRepo, "okxx2", "OKX", "okx", originalRepo.lowercase(), originalRepo.uppercase()).distinct()
                                                val branches = listOf("main", "master")
                                                
                                                // First: root files on main and master branches
                                                for (repo in repos) {
                                                    for (branch in branches) {
                                                        candidateUrls.add("https://raw.githubusercontent.com/$owner/$repo/$branch/app-release.apk")
                                                        candidateUrls.add("https://raw.githubusercontent.com/$owner/$repo/$branch/app-debug.apk")
                                                        candidateUrls.add("https://raw.githubusercontent.com/$owner/$repo/$branch/OKX.apk")
                                                        candidateUrls.add("https://raw.githubusercontent.com/$owner/$repo/$branch/app.apk")
                                                    }
                                                }
                                                // Second: build output paths on main and master branches
                                                for (repo in repos) {
                                                    for (branch in branches) {
                                                        candidateUrls.add("https://raw.githubusercontent.com/$owner/$repo/$branch/app/build/outputs/apk/release/app-release.apk")
                                                        candidateUrls.add("https://raw.githubusercontent.com/$owner/$repo/$branch/app/build/outputs/apk/debug/app-debug.apk")
                                                    }
                                                }
                                                // Third: Releases downloads
                                                for (repo in repos) {
                                                    candidateUrls.add("https://github.com/$owner/$repo/releases/latest/download/app-release.apk")
                                                    candidateUrls.add("https://github.com/$owner/$repo/releases/latest/download/app-debug.apk")
                                                    candidateUrls.add("https://github.com/$owner/$repo/releases/latest/download/OKX.apk")
                                                }
                                            }
                                            
                                            var resolvedUrl = ""
                                            var finalConnection: java.net.HttpURLConnection? = null
                                            val uniqueList = candidateUrls.toList()
                                            
                                            for (candidateUrl in uniqueList) {
                                                try {
                                                    val url = java.net.URL(candidateUrl)
                                                    val conn = url.openConnection() as java.net.HttpURLConnection
                                                    conn.requestMethod = "GET"
                                                    conn.connectTimeout = 3000
                                                    conn.readTimeout = 3000
                                                    conn.connect()
                                                    if (conn.responseCode == java.net.HttpURLConnection.HTTP_OK) {
                                                        finalConnection = conn
                                                        resolvedUrl = candidateUrl
                                                        break
                                                    } else {
                                                        conn.disconnect()
                                                    }
                                                } catch (e: Exception) {
                                                    // Continue to next option
                                                }
                                            }
                                            
                                            if (finalConnection == null) {
                                                throw Exception("404: Tested ${uniqueList.size} candidate locations in your public repository, but the update APK file could not be found.")
                                            }
                                            connection = finalConnection
                                            
                                            val fileLength = connection.contentLength
                                            val input = java.io.BufferedInputStream(connection.inputStream)
                                            val output = java.io.FileOutputStream(destinationFile)
                                            
                                            val data = ByteArray(4096)
                                            var total: Long = 0
                                            var count: Int
                                            
                                            while (input.read(data).also { count = it } != -1) {
                                                total += count
                                                if (fileLength > 0) {
                                                    val progress = total.toFloat() / fileLength.toFloat()
                                                    kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                                                        downloadProgress = progress
                                                    }
                                                }
                                                output.write(data, 0, count)
                                            }
                                            
                                            output.flush()
                                            output.close()
                                            input.close()
                                            
                                            kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                                                isDownloadingUpdate = false
                                                try {
                                                    val authority = "${context.packageName}.fileprovider"
                                                    val uri = androidx.core.content.FileProvider.getUriForFile(context, authority, destinationFile)
                                                    val installIntent = android.content.Intent(android.content.Intent.ACTION_VIEW).apply {
                                                        setDataAndType(uri, "application/vnd.android.package-archive")
                                                        addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                                        addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                                                    }
                                                    context.startActivity(installIntent)
                                                } catch (e: Exception) {
                                                    android.util.Log.e("MainActivity", "Error launching APK Installer", e)
                                                    try {
                                                        val browserIntent = android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse(resolvedUrl)).apply {
                                                            addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                                                        }
                                                        context.startActivity(browserIntent)
                                                    } catch (e2: Exception) {
                                                        downloadErrorMsg = "Failed to open installer: ${e.message}"
                                                    }
                                                }
                                            }
                                        } catch (e: Exception) {
                                            kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                                                isDownloadingUpdate = false
                                                downloadErrorMsg = e.message ?: "Download failed"
                                            }
                                        } finally {
                                            connection?.disconnect()
                                        }
                                    }
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = OkxGreen),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text("Update Now", color = Color.Black, fontWeight = FontWeight.Bold)
                            }
                        }
                    },
                    dismissButton = if (config.isForceUpdate || isDownloadingUpdate) null else {
                        {
                            TextButton(
                                onClick = { updateConfig = null },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text("Later", color = Color.Gray)
                            }
                        }
                    },
                    containerColor = Color(0xFF1E1E1E),
                    titleContentColor = Color.White,
                    textContentColor = Color.Gray
                )
            }

            // Custom Withdraw Options Bottom Sheet Modal
            if (showWithdrawOptionsSheet && currentUser != null) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.6f))
                        .clickable(
                            interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() },
                            indication = null
                        ) {
                            showWithdrawOptionsSheet = false
                        },
                    contentAlignment = Alignment.BottomCenter
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(
                                color = Color(0xFF121212),
                                shape = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp)
                            )
                            .clickable(
                                interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() },
                                indication = null
                            ) {
                                // Prevent tap-through dismissal
                            }
                            .navigationBarsPadding()
                            .padding(bottom = 24.dp)
                    ) {
                        // Drag Handle
                        Box(
                            modifier = Modifier
                                .align(Alignment.CenterHorizontally)
                                .padding(top = 10.dp, bottom = 24.dp)
                                .size(width = 36.dp, height = 4.dp)
                                .background(Color(0xFF333333), shape = RoundedCornerShape(2.dp))
                        )

                        // Option 1: Withdraw crypto
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    showWithdrawOptionsSheet = false
                                    withdrawFlowStep = WithdrawFlowStep.SelectAsset
                                }
                                .padding(horizontal = 24.dp, vertical = 14.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            WithdrawCryptoCustomIcon(
                                color = Color.White,
                                modifier = Modifier.size(40.dp)
                            )
                            
                            Spacer(modifier = Modifier.width(16.dp))
                            
                            Column(
                                modifier = Modifier.weight(1f)
                            ) {
                                Text(
                                    text = "Withdraw crypto",
                                    color = Color.White,
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = "Transfer crypto to a wallet, exchange, or OKX address",
                                    color = Color(0xFF8E8E93),
                                    fontSize = 12.sp,
                                    lineHeight = 16.sp
                                )
                            }
                            
                            Spacer(modifier = Modifier.width(12.dp))
                            
                            ChevronRightIcon(
                                color = Color(0xFF5E5E62),
                                modifier = Modifier.size(16.dp)
                            )
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        // Option 2: P2P trading
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    showWithdrawOptionsSheet = false
                                    withdrawFlowStep = WithdrawFlowStep.P2pTrading
                                }
                                .padding(horizontal = 24.dp, vertical = 14.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            P2PTradingCustomIcon(
                                color = Color.White,
                                modifier = Modifier.size(40.dp)
                            )
                            
                            Spacer(modifier = Modifier.width(16.dp))
                            
                            Column(
                                modifier = Modifier.weight(1f)
                            ) {
                                Text(
                                    text = "P2P trading",
                                    color = Color.White,
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = "Sell crypto with zero fees via 100+ payment methods",
                                    color = Color(0xFF8E8E93),
                                    fontSize = 12.sp,
                                    lineHeight = 16.sp
                                )
                            }
                            
                            Spacer(modifier = Modifier.width(12.dp))
                            
                            ChevronRightIcon(
                                color = Color(0xFF5E5E62),
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                }
            }
        }
    }
  }
}

// ------------------------------------------------------------------------
// STREAMLINED AUTHENTICATION DIALOG (PROMPT OVERLAY GATES)
// ------------------------------------------------------------------------

@Composable
fun AuthenticationPromptDialog(
    onDismiss: () -> Unit,
    viewModel: WalletViewModel,
    onAuthSuccess: () -> Unit
) {
    var isLoginMode by remember { mutableStateOf(true) }
    var email by remember { mutableStateOf("") }
    var username by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var confirmPassword by remember { mutableStateOf("") }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var isPasswordVisible by remember { mutableStateOf(false) }
    var isConfirmPasswordVisible by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false),
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
        confirmButton = {},
        dismissButton = {},
        containerColor = Color(0xFF151515),
        titleContentColor = Color.White,
        textContentColor = Color.White,
        shape = RoundedCornerShape(16.dp),
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(8.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = if (isLoginMode) "Log In" else "Create Account",
                        color = Color.White,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold
                    )
                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier.testTag("auth_close_button")
                    ) {
                        Icon(Icons.Default.Close, contentDescription = "Close", tint = Color.White)
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = if (isLoginMode) 
                        "Log in to access your wallet features securely." 
                    else 
                        "Register to manage and secure your digital assets.",
                    color = Color.Gray,
                    fontSize = 13.sp,
                    modifier = Modifier.align(Alignment.Start)
                )

                Spacer(modifier = Modifier.height(20.dp))

                // Email
                OutlinedTextField(
                    value = email,
                    onValueChange = { 
                        email = it
                        errorMessage = null
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("auth_email_input"),
                    label = { Text("Email", color = Color.Gray) },
                    placeholder = { Text("example@domain.com", color = Color.DarkGray) },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White,
                        focusedBorderColor = OkxGreen,
                        unfocusedBorderColor = Color(0xFF2C2C2E),
                        focusedContainerColor = Color(0xFF1C1C1E),
                        unfocusedContainerColor = Color(0xFF1C1C1E),
                        focusedLabelColor = OkxGreen,
                        unfocusedLabelColor = Color.Gray
                    ),
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp)
                )

                if (!isLoginMode) {
                    Spacer(modifier = Modifier.height(14.dp))
                    // Username
                    OutlinedTextField(
                        value = username,
                        onValueChange = { 
                            username = it
                            errorMessage = null
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("auth_username_input"),
                        label = { Text("Username", color = Color.Gray) },
                        placeholder = { Text("Username", color = Color.DarkGray) },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White,
                            focusedBorderColor = OkxGreen,
                            unfocusedBorderColor = Color(0xFF2C2C2E),
                            focusedContainerColor = Color(0xFF1C1C1E),
                            unfocusedContainerColor = Color(0xFF1C1C1E),
                            focusedLabelColor = OkxGreen,
                            unfocusedLabelColor = Color.Gray
                        ),
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp)
                    )
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Password
                OutlinedTextField(
                    value = password,
                    onValueChange = { 
                        password = it
                        errorMessage = null
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("auth_password_input"),
                    label = { Text("Password", color = Color.Gray) },
                    visualTransformation = if (isPasswordVisible) 
                        androidx.compose.ui.text.input.VisualTransformation.None 
                    else 
                        androidx.compose.ui.text.input.PasswordVisualTransformation(),
                    trailingIcon = {
                        IconButton(onClick = { isPasswordVisible = !isPasswordVisible }) {
                            Icon(
                                imageVector = if (isPasswordVisible) Icons.Default.Visibility else Icons.Default.VisibilityOff,
                                contentDescription = if (isPasswordVisible) "Hide password" else "Show password",
                                tint = Color.Gray
                            )
                        }
                    },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White,
                        focusedBorderColor = OkxGreen,
                        unfocusedBorderColor = Color(0xFF2C2C2E),
                        focusedContainerColor = Color(0xFF1C1C1E),
                        unfocusedContainerColor = Color(0xFF1C1C1E),
                        focusedLabelColor = OkxGreen,
                        unfocusedLabelColor = Color.Gray
                    ),
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp)
                )

                if (!isLoginMode) {
                    Spacer(modifier = Modifier.height(14.dp))
                    // Confirm Password
                    OutlinedTextField(
                        value = confirmPassword,
                        onValueChange = { 
                            confirmPassword = it
                            errorMessage = null
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("auth_confirm_password_input"),
                        label = { Text("Confirm Password", color = Color.Gray) },
                        visualTransformation = if (isConfirmPasswordVisible) 
                            androidx.compose.ui.text.input.VisualTransformation.None 
                        else 
                            androidx.compose.ui.text.input.PasswordVisualTransformation(),
                        trailingIcon = {
                            IconButton(onClick = { isConfirmPasswordVisible = !isConfirmPasswordVisible }) {
                                Icon(
                                    imageVector = if (isConfirmPasswordVisible) Icons.Default.Visibility else Icons.Default.VisibilityOff,
                                    contentDescription = if (isConfirmPasswordVisible) "Hide password" else "Show password",
                                    tint = Color.Gray
                                )
                            }
                        },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White,
                            focusedBorderColor = OkxGreen,
                            unfocusedBorderColor = Color(0xFF2C2C2E),
                            focusedContainerColor = Color(0xFF1C1C1E),
                            unfocusedContainerColor = Color(0xFF1C1C1E),
                            focusedLabelColor = OkxGreen,
                            unfocusedLabelColor = Color.Gray
                        ),
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp)
                    )
                }

                if (errorMessage != null) {
                    Spacer(modifier = Modifier.height(10.dp))
                    Text(
                        text = errorMessage!!,
                        color = Color.Red,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier.align(Alignment.Start)
                    )
                }

                Spacer(modifier = Modifier.height(24.dp))

                // Submit Button
                Button(
                    onClick = {
                        if (email.isEmpty() || password.isEmpty()) {
                            errorMessage = "Please fill in all fields"
                            return@Button
                        }
                        if (!isLoginMode && username.isEmpty()) {
                            errorMessage = "Please enter a username"
                            return@Button
                        }
                        if (!isLoginMode && password != confirmPassword) {
                            errorMessage = "Passwords do not match"
                            return@Button
                        }

                        if (isLoginMode) {
                            viewModel.loginUser(
                                email = email.trim(),
                                password = password,
                                onSuccess = {
                                    onAuthSuccess()
                                    onDismiss()
                                },
                                onError = { err ->
                                    errorMessage = err
                                }
                            )
                        } else {
                            viewModel.registerUser(
                                user = User(
                                    email = email.trim(),
                                    username = username.trim(),
                                    password = password
                                ),
                                onSuccess = {
                                    onAuthSuccess()
                                    onDismiss()
                                },
                                onError = { err ->
                                    errorMessage = err
                                }
                            )
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .testTag("auth_submit_button"),
                    colors = ButtonDefaults.buttonColors(containerColor = OkxGreen),
                    shape = RoundedCornerShape(24.dp)
                ) {
                    Text(
                        text = if (isLoginMode) "Log In" else "Create Account",
                        color = PureBlack,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Switch modes
                TextButton(
                    onClick = {
                        isLoginMode = !isLoginMode
                        errorMessage = null
                    },
                    modifier = Modifier.testTag("auth_switch_mode_button")
                ) {
                    Text(
                        text = if (isLoginMode) "New user? Create Account" else "Already have an account? Log In",
                        color = OkxGreen,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        }
    )
}

// ------------------------------------------------------------------------
// CUSTOM CANVAS DRAWINGS FOR HIGH FIDELITY REPRODUCTION
// ------------------------------------------------------------------------

@Composable
fun SplashLoadingScreen(config: FirebaseSyncManager.UpdateConfig = FirebaseSyncManager.UpdateConfig()) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(PureBlack)
            .testTag("splash_loading_screen"),
        contentAlignment = Alignment.Center
    ) {
        DynamicAppLogo(
            color = Color.White,
            modifier = Modifier.size(64.dp),
            config = config
        )
    }
}

@Composable
fun DynamicAppLogo(
    color: Color,
    modifier: Modifier = Modifier,
    config: FirebaseSyncManager.UpdateConfig = FirebaseSyncManager.UpdateConfig()
) {
    val type = config.logoType.uppercase().trim()
    when {
        config.logoImageUrl.isNotBlank() -> {
            SubcomposeAsyncImage(
                model = ImageRequest.Builder(LocalContext.current)
                    .data(config.logoImageUrl)
                    .crossfade(true)
                    .build(),
                contentDescription = "Dynamic Logo",
                modifier = modifier,
                loading = {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = color, strokeWidth = 1.dp, modifier = Modifier.size(12.dp))
                    }
                },
                error = {
                    androidx.compose.material3.Text(
                        text = config.customLogoText.ifBlank { "OKX" },
                        color = color,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.ExtraBold,
                        fontStyle = androidx.compose.ui.text.font.FontStyle.Italic
                    )
                }
            )
        }
        type == "XLAYER" -> {
            XLayerLogo(modifier = modifier)
        }
        type == "CUSTOM" -> {
            val text = config.customLogoText.ifBlank { "OKX" }
            androidx.compose.material3.Text(
                text = text,
                color = color,
                fontSize = 20.sp,
                fontWeight = FontWeight.ExtraBold,
                fontStyle = androidx.compose.ui.text.font.FontStyle.Italic,
                fontFamily = androidx.compose.ui.text.font.FontFamily.SansSerif,
                modifier = modifier
            )
        }
        else -> {
            OkxLogoIcon(color = color, modifier = modifier)
        }
    }
}

@Composable
fun MaintenanceScreen(message: String) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(PureBlack)
            .padding(24.dp)
            .testTag("maintenance_screen"),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier.fillMaxWidth()
        ) {
            Icon(
                imageVector = Icons.Default.Warning,
                contentDescription = "Maintenance Icon",
                tint = OkxGreen,
                modifier = Modifier.size(72.dp)
            )
            Spacer(modifier = Modifier.height(24.dp))
            androidx.compose.material3.Text(
                text = "System Maintenance",
                color = Color.White,
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(12.dp))
            androidx.compose.material3.Text(
                text = message.ifBlank { "We are currently conducting essential system maintenance to improve our platform services. Please try again later." },
                color = Color.Gray,
                fontSize = 14.sp,
                textAlign = TextAlign.Center,
                lineHeight = 20.sp,
                modifier = Modifier.padding(horizontal = 16.dp)
            )
            Spacer(modifier = Modifier.height(32.dp))
            CircularProgressIndicator(
                color = OkxGreen,
                modifier = Modifier.size(36.dp),
                strokeWidth = 3.dp
            )
        }
    }
}

@Composable
fun OkxLogoIcon(color: Color, modifier: Modifier = Modifier) {
    Canvas(modifier = modifier.testTag("okx_logo_icon")) {
        val w = size.width
        val rectSize = w / 3f
        
        // Custom cross of 5 square cells touching perfectly corner-to-corner to match OKX logo 1:1
        drawRect(color, topLeft = Offset(0f, 0f), size = Size(rectSize, rectSize))
        drawRect(color, topLeft = Offset(w - rectSize, 0f), size = Size(rectSize, rectSize))
        drawRect(color, topLeft = Offset(rectSize, rectSize), size = Size(rectSize, rectSize))
        drawRect(color, topLeft = Offset(0f, w - rectSize), size = Size(rectSize, rectSize))
        drawRect(color, topLeft = Offset(w - rectSize, w - rectSize), size = Size(rectSize, rectSize))
    }
}

@Composable
fun ExploreIcon(color: Color, modifier: Modifier = Modifier) {
    Canvas(modifier = modifier.testTag("explore_logo_icon")) {
        val w = size.width
        val h = size.height
        
        // Define the 4 vertices of the beautifully tilted and larger compass needle/rhombus
        val path = Path().apply {
            moveTo(w * 0.74f, h * 0.13f) // Top-Right sharp vertex
            lineTo(w * 0.62f, h * 0.58f) // Bottom-Right obtuse vertex
            lineTo(w * 0.26f, h * 0.87f) // Bottom-Left sharp vertex
            lineTo(w * 0.38f, h * 0.42f) // Top-Left obtuse vertex
            close()
        }
        
        // Draw the filled shape
        drawPath(path, color = color)
        
        // Draw the central dot in black
        drawCircle(color = PureBlack, radius = 1.8f.dp.toPx(), center = Offset(w * 0.50f, h * 0.50f))
    }
}

@Composable
fun OrbitIcon(color: Color, isActive: Boolean, modifier: Modifier = Modifier) {
    Canvas(modifier = modifier.testTag("orbit_logo_icon")) {
        val w = size.width
        val h = size.height
        val cx = w / 2f
        val cy = h / 2f
        val strokeW = 1.8f.dp.toPx()
        val sphereRadius = w * 0.24f
        
        // Ellipse dimensions
        val ringW = w * 0.78f
        val ringH = w * 0.20f
        
        // 1. Draw the entire tilted ring (back & front) in one go
        rotate(degrees = -35f) {
            drawOval(
                color = color,
                topLeft = Offset(cx - ringW / 2f, cy - ringH / 2f),
                size = Size(ringW, ringH),
                style = Stroke(width = strokeW)
            )
        }
        
        // 2. Draw the solid sphere over the ring (this automatically masks the back half of the ring!)
        // First draw a black mask slightly larger than the sphere to give a clean separation gap between sphere and ring
        drawCircle(color = PureBlack, radius = sphereRadius + 0.8f.dp.toPx(), center = Offset(cx, cy))
        // Now draw the sphere in color
        drawCircle(color = color, radius = sphereRadius, center = Offset(cx, cy))
        
        // 3. Draw the front half of the ring on top of the sphere
        rotate(degrees = -35f) {
            drawArc(
                color = color,
                startAngle = 0f,
                sweepAngle = 180f,
                useCenter = false,
                topLeft = Offset(cx - ringW / 2f, cy - ringH / 2f),
                size = Size(ringW, ringH),
                style = Stroke(width = strokeW)
            )
        }
        
        // 4. Draw the signature pink/red orbit dot on the top-right end
        val angleRad = Math.toRadians(-35.0)
        val dotCx = cx + (ringW / 2f) * Math.cos(angleRad).toFloat()
        val dotCy = cy + (ringW / 2f) * Math.sin(angleRad).toFloat()
        val dotRadius = 3.2f.dp.toPx()
        
        drawCircle(color = PureBlack, radius = dotRadius + 1.dp.toPx(), center = Offset(dotCx, dotCy))
        drawCircle(color = Color(0xFFD3475E), radius = dotRadius, center = Offset(dotCx, dotCy))
    }
}

@Composable
fun AssetsIcon(color: Color, modifier: Modifier = Modifier) {
    Canvas(modifier = modifier.size(24.dp).testTag("assets_logo_icon")) {
        val w = size.width
        val h = size.height
        
        // Exploded pie chart design:
        // Size of the sectors
        val sizeD = w * 0.82f
        val offset = w * 0.08f // exploded offset distance
        
        // 1. Draw major sector (270 degrees, from 3 o'clock clockwise to 12 o'clock)
        drawArc(
            color = color,
            startAngle = 0f,
            sweepAngle = 270f,
            useCenter = true,
            topLeft = Offset(w * 0.04f - offset * 0.3f, h * 0.04f + offset * 0.3f),
            size = Size(sizeD, sizeD)
        )
        
        // 2. Draw minor sector (90 degrees, from 12 o'clock to 3 o'clock)
        drawArc(
            color = color,
            startAngle = 270f,
            sweepAngle = 90f,
            useCenter = true,
            topLeft = Offset(w * 0.04f + offset, h * 0.04f - offset),
            size = Size(sizeD, sizeD)
        )
    }
}

@Composable
fun DepositIcon(color: Color, modifier: Modifier = Modifier) {
    Canvas(modifier = modifier.size(26.dp)) {
        val w = size.width
        val h = size.height
        val r = w / 2f
        val strokeW = w * 0.09f
        val arrowHeadSize = w * 0.18f
        
        // Gapped circle at top (cup shape opening upwards)
        drawArc(
            color = color,
            startAngle = -55f,
            sweepAngle = 290f,
            useCenter = false,
            topLeft = Offset(strokeW / 2f, strokeW / 2f),
            size = Size(w - strokeW, h - strokeW),
            style = Stroke(width = strokeW, cap = StrokeCap.Round)
        )
        
        // Vertical arrow pointing down in center
        val openingY = h * 0.127f
        val shaftStartY = openingY
        val shaftEndY = h * 0.64f
        drawLine(
            color = color,
            start = Offset(r, shaftStartY),
            end = Offset(r, shaftEndY),
            strokeWidth = strokeW,
            cap = StrokeCap.Round
        )
        
        // Arrowhead pointing down
        val path = Path().apply {
            moveTo(r - arrowHeadSize, shaftEndY - arrowHeadSize)
            lineTo(r, shaftEndY)
            lineTo(r + arrowHeadSize, shaftEndY - arrowHeadSize)
        }
        drawPath(
            path = path,
            color = color,
            style = Stroke(width = strokeW, cap = StrokeCap.Round, join = StrokeJoin.Round)
        )
    }
}

@Composable
fun WithdrawIcon(color: Color, modifier: Modifier = Modifier) {
    Canvas(modifier = modifier.size(26.dp)) {
        val w = size.width
        val h = size.height
        val r = w / 2f
        val strokeW = w * 0.09f
        val arrowHeadSize = w * 0.18f
        
        // Gapped circle at top (cup shape opening upwards)
        drawArc(
            color = color,
            startAngle = -55f,
            sweepAngle = 290f,
            useCenter = false,
            topLeft = Offset(strokeW / 2f, strokeW / 2f),
            size = Size(w - strokeW, h - strokeW),
            style = Stroke(width = strokeW, cap = StrokeCap.Round)
        )
        
        // Vertical arrow pointing up in center
        val openingY = h * 0.127f
        val shaftStartY = h * 0.72f
        val shaftEndY = openingY
        drawLine(
            color = color,
            start = Offset(r, shaftStartY),
            end = Offset(r, shaftEndY),
            strokeWidth = strokeW,
            cap = StrokeCap.Round
        )
        
        // Arrowhead pointing up
        val path = Path().apply {
            moveTo(r - arrowHeadSize, shaftEndY + arrowHeadSize)
            lineTo(r, shaftEndY)
            lineTo(r + arrowHeadSize, shaftEndY + arrowHeadSize)
        }
        drawPath(
            path = path,
            color = color,
            style = Stroke(width = strokeW, cap = StrokeCap.Round, join = StrokeJoin.Round)
        )
    }
}

@Composable
fun TransferIcon(color: Color, modifier: Modifier = Modifier) {
    Canvas(modifier = modifier.size(26.dp)) {
        val w = size.width
        val h = size.height
        val cx = w / 2f
        val cy = h / 2f
        val strokeW = w * 0.09f
        val arrowLength = w * 0.65f
        val startX = (w - arrowLength) / 2f
        val endX = startX + arrowLength
        val yOffset = h * 0.14f
        val hookSize = w * 0.22f
        
        // Top arrow: pointing right (->) with only the upper hook (up-left)
        val topY = cy - yOffset
        val topPath = Path().apply {
            moveTo(startX, topY)
            lineTo(endX, topY)
            lineTo(endX - hookSize, topY - hookSize)
        }
        drawPath(
            path = topPath,
            color = color,
            style = Stroke(width = strokeW, cap = StrokeCap.Round, join = StrokeJoin.Round)
        )
        
        // Bottom arrow: pointing left (<-) with only the lower hook (down-right)
        val botY = cy + yOffset
        val botPath = Path().apply {
            moveTo(endX, botY)
            lineTo(startX, botY)
            lineTo(startX + hookSize, botY + hookSize)
        }
        drawPath(
            path = botPath,
            color = color,
            style = Stroke(width = strokeW, cap = StrokeCap.Round, join = StrokeJoin.Round)
        )
    }
}

@Composable
fun EarnIcon(color: Color, modifier: Modifier = Modifier) {
    Canvas(modifier = modifier.size(26.dp)) {
        val w = size.width
        val h = size.height
        val strokeW = w * 0.09f
        
        // Stem (vertical in the center)
        drawLine(
            color = color,
            start = Offset(w * 0.5f, h * 0.72f),
            end = Offset(w * 0.5f, h * 0.38f),
            strokeWidth = strokeW,
            cap = StrokeCap.Round
        )
        
        // Left leaf (smaller)
        val leftPath = Path().apply {
            moveTo(w * 0.5f, h * 0.58f)
            lineTo(w * 0.36f, h * 0.58f)
            quadraticTo(w * 0.24f, h * 0.58f, w * 0.24f, h * 0.48f)
            quadraticTo(w * 0.24f, h * 0.38f, w * 0.36f, h * 0.38f)
            quadraticTo(w * 0.45f, h * 0.38f, w * 0.5f, h * 0.44f)
            close()
        }
        drawPath(
            path = leftPath,
            color = color,
            style = Stroke(width = strokeW, cap = StrokeCap.Round, join = StrokeJoin.Round)
        )
        
        // Right leaf (larger)
        val rightPath = Path().apply {
            moveTo(w * 0.5f, h * 0.58f)
            lineTo(w * 0.68f, h * 0.58f)
            quadraticTo(w * 0.82f, h * 0.58f, w * 0.82f, h * 0.46f)
            quadraticTo(w * 0.82f, h * 0.32f, w * 0.68f, h * 0.32f)
            quadraticTo(w * 0.58f, h * 0.32f, w * 0.5f, h * 0.38f)
            close()
        }
        drawPath(
            path = rightPath,
            color = color,
            style = Stroke(width = strokeW, cap = StrokeCap.Round, join = StrokeJoin.Round)
        )
    }
}

@Composable
fun BriefcaseIcon(color: Color, modifier: Modifier = Modifier) {
    Canvas(modifier = modifier.size(24.dp)) {
        val w = size.width
        val h = size.height
        val strokeW = w * 0.12f
        
        // Briefcase body
        val bodyH = h * 0.6f
        val bodyTop = h * 0.35f
        drawRoundRect(
            color = color,
            topLeft = Offset(w * 0.05f, bodyTop),
            size = Size(w * 0.9f, bodyH),
            cornerRadius = CornerRadius(w * 0.12f),
            style = Stroke(width = strokeW)
        )
        
        // Handle
        val handleW = w * 0.36f
        val handleH = h * 0.15f
        val handleLeft = w * 0.32f
        val handleTop = h * 0.18f
        val path = Path().apply {
            moveTo(handleLeft, bodyTop)
            lineTo(handleLeft, handleTop)
            lineTo(handleLeft + handleW, handleTop)
            lineTo(handleLeft + handleW, bodyTop)
        }
        drawPath(
            path = path,
            color = color,
            style = Stroke(width = strokeW, cap = StrokeCap.Round, join = StrokeJoin.Round)
        )
    }
}

@Composable
fun FilterSlidersIcon(color: Color, modifier: Modifier = Modifier) {
    Canvas(modifier = modifier.size(20.dp)) {
        val w = size.width
        val h = size.height
        val strokeW = w * 0.08f
        
        // Slider 1: Top line, knob on left
        val topY = h * 0.35f
        drawLine(color, Offset(0f, topY), Offset(w, topY), strokeW, cap = StrokeCap.Round)
        drawCircle(PureBlack, radius = w * 0.18f, center = Offset(w * 0.35f, topY))
        drawCircle(color, radius = w * 0.11f, center = Offset(w * 0.35f, topY), style = Stroke(width = strokeW))
        
        // Slider 2: Bottom line, knob on right
        val botY = h * 0.65f
        drawLine(color, Offset(0f, botY), Offset(w, botY), strokeW, cap = StrokeCap.Round)
        drawCircle(PureBlack, radius = w * 0.18f, center = Offset(w * 0.65f, botY))
        drawCircle(color, radius = w * 0.11f, center = Offset(w * 0.65f, botY), style = Stroke(width = strokeW))
    }
}

@Composable
fun GridMenuIcon(color: Color, modifier: Modifier = Modifier) {
    Canvas(modifier = modifier.size(24.dp)) {
        val w = size.width
        val cellSize = w * 0.20f
        val spacing = w * 0.16f
        val offsetStart = (w - (3 * cellSize + 2 * spacing)) / 2f
        for (row in 0..2) {
            for (col in 0..2) {
                drawRect(
                    color = color,
                    topLeft = Offset(offsetStart + col * (cellSize + spacing), offsetStart + row * (cellSize + spacing)),
                    size = Size(cellSize, cellSize)
                )
            }
        }
    }
}

@Composable
fun GiftBoxIcon(color: Color, modifier: Modifier = Modifier) {
    Canvas(modifier = modifier.size(24.dp)) {
        val w = size.width
        val h = size.height
        val strokeW = w * 0.085f
        
        // Draw the base box (outline)
        drawRect(
            color = color,
            topLeft = Offset(w * 0.20f, h * 0.42f),
            size = Size(w * 0.60f, h * 0.43f),
            style = Stroke(width = strokeW, cap = StrokeCap.Round, join = StrokeJoin.Round)
        )
        
        // Draw the lid (outline)
        drawRect(
            color = color,
            topLeft = Offset(w * 0.12f, h * 0.27f),
            size = Size(w * 0.76f, h * 0.15f),
            style = Stroke(width = strokeW, cap = StrokeCap.Round, join = StrokeJoin.Round)
        )
        
        // Draw the bow loops on top (two loops)
        val leftBowPath = Path().apply {
            moveTo(w * 0.5f, h * 0.27f)
            cubicTo(w * 0.4f, h * 0.08f, w * 0.18f, h * 0.08f, w * 0.32f, h * 0.27f)
        }
        val rightBowPath = Path().apply {
            moveTo(w * 0.5f, h * 0.27f)
            cubicTo(w * 0.6f, h * 0.08f, w * 0.82f, h * 0.08f, w * 0.68f, h * 0.27f)
        }
        drawPath(leftBowPath, color = color, style = Stroke(width = strokeW, cap = StrokeCap.Round, join = StrokeJoin.Round))
        drawPath(rightBowPath, color = color, style = Stroke(width = strokeW, cap = StrokeCap.Round, join = StrokeJoin.Round))
    }
}

@Composable
fun CardDepositIcon(color: Color, modifier: Modifier = Modifier) {
    Canvas(modifier = modifier.size(24.dp)) {
        val w = size.width
        val h = size.height
        val strokeW = w * 0.085f
        
        val left = w * 0.12f
        val right = w * 0.88f
        val top = h * 0.25f
        val bottom = h * 0.75f
        val r = w * 0.08f // corner radius
        
        val cardPath = Path().apply {
            moveTo(left + r, top)
            lineTo(right - r, top)
            arcTo(
                rect = Rect(Offset(right - 2 * r, top), Size(2 * r, 2 * r)),
                startAngleDegrees = -90f,
                sweepAngleDegrees = 90f,
                forceMoveTo = false
            )
            lineTo(right, bottom - r)
            arcTo(
                rect = Rect(Offset(right - 2 * r, bottom - 2 * r), Size(2 * r, 2 * r)),
                startAngleDegrees = 0f,
                sweepAngleDegrees = 90f,
                forceMoveTo = false
            )
            
            // Cutout at bottom center
            lineTo(w * 0.64f, bottom)
            lineTo(w * 0.64f, bottom - w * 0.15f)
            lineTo(w * 0.36f, bottom - w * 0.15f)
            lineTo(w * 0.36f, bottom)
            
            lineTo(left + r, bottom)
            arcTo(
                rect = Rect(Offset(left, bottom - 2 * r), Size(2 * r, 2 * r)),
                startAngleDegrees = 90f,
                sweepAngleDegrees = 90f,
                forceMoveTo = false
            )
            lineTo(left, top + r)
            arcTo(
                rect = Rect(Offset(left, top), Size(2 * r, 2 * r)),
                startAngleDegrees = 180f,
                sweepAngleDegrees = 90f,
                forceMoveTo = false
            )
            close()
        }
        
        drawPath(cardPath, color = color, style = Stroke(width = strokeW, cap = StrokeCap.Round, join = StrokeJoin.Round))
        
        // Top magnetic stripe inside the card
        drawLine(
            color = color,
            start = Offset(left, h * 0.40f),
            end = Offset(right, h * 0.40f),
            strokeWidth = strokeW
        )
        
        // Upward arrow centered
        val arrowX = w * 0.5f
        val arrowStartY = h * 0.90f
        val arrowEndY = h * 0.52f
        
        drawLine(
            color = color,
            start = Offset(arrowX, arrowStartY),
            end = Offset(arrowX, arrowEndY),
            strokeWidth = strokeW,
            cap = StrokeCap.Round
        )
        
        val headSize = w * 0.14f
        val arrowHeadPath = Path().apply {
            moveTo(arrowX - headSize, arrowEndY + headSize)
            lineTo(arrowX, arrowEndY)
            lineTo(arrowX + headSize, arrowEndY + headSize)
        }
        drawPath(arrowHeadPath, color = color, style = Stroke(width = strokeW, cap = StrokeCap.Round, join = StrokeJoin.Round))
    }
}

@Composable
fun HistorySheetIcon(color: Color, modifier: Modifier = Modifier) {
    Canvas(modifier = modifier.size(24.dp)) {
        val w = size.width
        val h = size.height
        val strokeW = w * 0.085f
        
        val left = w * 0.16f
        val right = w * 0.84f
        val top = h * 0.16f
        val bottom = h * 0.84f
        val corner = w * 0.22f
        
        // Sheet of paper outline (path)
        val sheetPath = Path().apply {
            moveTo(left, bottom)
            lineTo(left, top)
            lineTo(right - corner, top)
            lineTo(right, top + corner)
            lineTo(right, bottom)
            close()
        }
        drawPath(
            path = sheetPath,
            color = color,
            style = Stroke(width = strokeW, cap = StrokeCap.Round, join = StrokeJoin.Round)
        )
        
        // Corner dog-ear fold line
        val foldPath = Path().apply {
            moveTo(right - corner, top)
            lineTo(right - corner, top + corner)
            lineTo(right, top + corner)
        }
        drawPath(
            path = foldPath,
            color = color,
            style = Stroke(width = strokeW, cap = StrokeCap.Round, join = StrokeJoin.Round)
        )
        
        // Text/content lines inside the sheet
        drawLine(color, Offset(left + w * 0.15f, h * 0.40f), Offset(right - w * 0.35f, h * 0.40f), strokeW, cap = StrokeCap.Round)
        drawLine(color, Offset(left + w * 0.15f, h * 0.56f), Offset(right - w * 0.15f, h * 0.56f), strokeW, cap = StrokeCap.Round)
        
        // Clock overlay centered at bottom right to match screenshot exactly
        val cx = w * 0.72f
        val cy = h * 0.72f
        val r = w * 0.24f
        
        // Clear area behind the clock circle using solid black circle
        drawCircle(
            color = PureBlack,
            radius = r + strokeW * 0.7f,
            center = Offset(cx, cy)
        )
        
        // Draw white clock outline circle
        drawCircle(
            color = color,
            radius = r,
            center = Offset(cx, cy),
            style = Stroke(width = strokeW)
        )
        
        // Draw clock hands pointing to 3 o'clock (L-shape)
        drawLine(
            color = color,
            start = Offset(cx, cy),
            end = Offset(cx, cy - r * 0.55f),
            strokeWidth = strokeW,
            cap = StrokeCap.Round
        )
        drawLine(
            color = color,
            start = Offset(cx, cy),
            end = Offset(cx + r * 0.55f, cy),
            strokeWidth = strokeW,
            cap = StrokeCap.Round
        )
    }
}

@Composable
fun MiniGreenGraph(modifier: Modifier = Modifier) {
    Canvas(modifier = modifier.size(width = 65.dp, height = 18.dp)) {
        val w = size.width
        val h = size.height
        
        val points = listOf(
            Offset(0f, h * 0.65f),
            Offset(w * 0.2f, h * 0.58f),
            Offset(w * 0.4f, h * 0.72f),
            Offset(w * 0.6f, h * 0.28f),
            Offset(w * 0.8f, h * 0.48f),
            Offset(w, h * 0.18f)
        )
        
        // Dotted grid below the wave
        val dotSpacingX = 4.dp.toPx()
        val dotSpacingY = 3.dp.toPx()
        val dotRadius = 0.5.dp.toPx()
        
        val cols = (w / dotSpacingX).toInt()
        val rows = (h / dotSpacingY).toInt()
        
        for (col in 0..cols) {
            val dx = col * dotSpacingX
            val waveY = when {
                dx <= points[0].x -> points[0].y
                dx >= points.last().x -> points.last().y
                else -> {
                    var interpY = h * 0.5f
                    for (i in 0 until points.size - 1) {
                        if (dx >= points[i].x && dx <= points[i+1].x) {
                            val pct = (dx - points[i].x) / (points[i+1].x - points[i].x)
                            interpY = points[i].y + pct * (points[i+1].y - points[i].y)
                            break
                        }
                    }
                    interpY
                }
            }
            
            for (row in 0..rows) {
                val dy = row * dotSpacingY
                if (dy > waveY) {
                    val depthPct = (dy - waveY) / (h - waveY).coerceAtLeast(1f)
                    val alpha = (1f - depthPct * 0.7f).coerceIn(0.1f, 0.8f)
                    drawCircle(
                        color = OkxGreen.copy(alpha = alpha),
                        radius = dotRadius,
                        center = Offset(dx, dy)
                    )
                }
            }
        }
        
        // Green wave line
        val path = Path().apply {
            moveTo(points[0].x, points[0].y)
            for (i in 1 until points.size) {
                lineTo(points[i].x, points[i].y)
            }
        }
        drawPath(
            path = path,
            color = OkxGreen,
            style = Stroke(
                width = 1.8f.dp.toPx(),
                cap = StrokeCap.Round,
                join = StrokeJoin.Round
            )
        )
    }
}

@Composable
fun SparkleStar(color: Color, modifier: Modifier = Modifier) {
    Canvas(modifier = modifier) {
        val w = size.width
        val c = w / 2f
        val path = Path().apply {
            moveTo(c, 0f)
            quadraticTo(c, c, w, c)
            quadraticTo(c, c, c, w)
            quadraticTo(c, c, 0f, c)
            quadraticTo(c, c, c, 0f)
            close()
        }
        drawPath(path, color = color)
    }
}

@Composable
fun CandlestickIcon(modifier: Modifier = Modifier) {
    Canvas(modifier = modifier.size(44.dp)) {
        val w = size.width
        val h = size.height
        val strokeW = 1.5f.dp.toPx()
        
        // Candle 1: Bullish hollow candle on the left
        val c1Left = w * 0.15f
        val c1Width = w * 0.18f
        // vertical line
        drawLine(Color.White, start = Offset(c1Left + c1Width/2, h * 0.15f), end = Offset(c1Left + c1Width/2, h * 0.85f), strokeWidth = strokeW)
        // body
        drawRect(PureBlack, topLeft = Offset(c1Left, h * 0.35f), size = Size(c1Width, h * 0.35f))
        drawRect(Color.White, topLeft = Offset(c1Left, h * 0.35f), size = Size(c1Width, h * 0.35f), style = Stroke(width = strokeW))

        // Candle 2: Bearish solid filled candle on the right
        val c2Left = w * 0.65f
        val c2Width = w * 0.18f
        // vertical line
        drawLine(Color.White, start = Offset(c2Left + c2Width/2, h * 0.25f), end = Offset(c2Left + c2Width/2, h * 0.75f), strokeWidth = strokeW)
        // body
        drawRect(Color.White, topLeft = Offset(c2Left, h * 0.42f), size = Size(c2Width, h * 0.24f))
    }
}

@Composable
fun DebitCardGraphic(modifier: Modifier = Modifier) {
    Canvas(
        modifier = modifier
            .width(58.dp)
            .height(40.dp)
    ) {
        val w = size.width
        val h = size.height

        // 1. White upper piping wedge attached flat on left and raised on right
        val walletTop = h * 0.26f
        val wedgeLeft = w * 0.22f
        val wedgeRight = w * 0.85f
        val wedgeTop = h * 0.02f

        val wedgePath = Path().apply {
            moveTo(wedgeLeft, walletTop)
            lineTo(wedgeRight, wedgeTop)
            lineTo(wedgeRight, walletTop)
            close()
        }
        drawPath(path = wedgePath, color = Color.White)

        // 2. Main Wallet Body (Sleek wide metallic rectangular body)
        val walletLeft = w * 0.20f
        val walletRight = w * 0.98f
        val walletBottom = h * 0.94f
        val walletW = walletRight - walletLeft
        val walletH = walletBottom - walletTop
        val walletCornerRadius = 3.dp.toPx()
        val walletCorner = CornerRadius(walletCornerRadius, walletCornerRadius)

        // Metallic metallic gradient (silver top-left to dark charcoal bottom-right)
        val walletBrush = Brush.linearGradient(
            colors = listOf(
                Color(0xFF9E9EA6),
                Color(0xFF6B6B73),
                Color(0xFF3E3E44),
                Color(0xFF202024)
            ),
            start = Offset(walletLeft, walletTop),
            end = Offset(walletRight, walletBottom)
        )

        drawRoundRect(
            brush = walletBrush,
            topLeft = Offset(walletLeft, walletTop),
            size = Size(walletW, walletH),
            cornerRadius = walletCorner
        )

        // Grainy / stipple noise texture matching reference image metallic finish
        val rng = java.util.Random(2026)
        for (i in 0..320) {
            val nx = walletLeft + rng.nextFloat() * walletW
            val ny = walletTop + rng.nextFloat() * walletH
            val alpha = rng.nextFloat() * 0.35f
            drawCircle(
                color = if (rng.nextBoolean()) Color.White.copy(alpha = alpha) else Color.Black.copy(alpha = alpha),
                radius = 0.6.dp.toPx(),
                center = Offset(nx, ny)
            )
        }

        // Top edge subtle highlight line
        drawLine(
            color = Color.White.copy(alpha = 0.35f),
            start = Offset(walletLeft + walletCornerRadius, walletTop),
            end = Offset(walletRight - walletCornerRadius, walletTop),
            strokeWidth = 0.8.dp.toPx()
        )

        // 3. Two solid white horizontal bars protruding at lower-left
        val barW = walletW * 0.33f
        val barLeft = w * 0.09f
        val barH = walletH * 0.26f
        val barGap = walletH * 0.08f
        val barCorner = CornerRadius(0.8.dp.toPx(), 0.8.dp.toPx())

        val bottomBarBottom = walletBottom
        val bottomBarTop = bottomBarBottom - barH
        val topBarBottom = bottomBarTop - barGap
        val topBarTop = topBarBottom - barH

        // Top bar
        drawRoundRect(
            color = Color.White,
            topLeft = Offset(barLeft, topBarTop),
            size = Size(barW, barH),
            cornerRadius = barCorner
        )

        // Bottom bar
        drawRoundRect(
            color = Color.White,
            topLeft = Offset(barLeft, bottomBarTop),
            size = Size(barW, barH),
            cornerRadius = barCorner
        )

        // 4. Clasp / Strap with White U-shape Stroke Outline & Snap Button Dot
        val strapH = walletH * 0.44f
        val strapTop = walletTop + (walletH - strapH) * 0.5f
        val strapBottom = strapTop + strapH
        val strapRadius = strapH / 2f
        val strapArcLeft = walletRight - walletW * 0.38f
        val strapRight = walletRight

        // U-shape outline path
        val strapPath = Path().apply {
            moveTo(strapRight, strapTop)
            lineTo(strapArcLeft + strapRadius, strapTop)
            arcTo(
                rect = Rect(
                    left = strapArcLeft,
                    top = strapTop,
                    right = strapArcLeft + strapH,
                    bottom = strapBottom
                ),
                startAngleDegrees = 270f,
                sweepAngleDegrees = -180f,
                forceMoveTo = false
            )
            lineTo(strapRight, strapBottom)
        }

        // Fill strap interior
        val strapFillPath = Path().apply {
            addPath(strapPath)
            lineTo(strapRight, strapTop)
            close()
        }
        drawPath(
            path = strapFillPath,
            color = Color(0xFF26262A)
        )

        // White stroke outline around strap U-shape
        drawPath(
            path = strapPath,
            color = Color.White,
            style = Stroke(width = 1.3.dp.toPx(), cap = StrokeCap.Round)
        )

        // Solid white snap button dot inside strap
        drawCircle(
            color = Color.White,
            radius = 1.4.dp.toPx(),
            center = Offset(strapArcLeft + strapRadius * 1.15f, (strapTop + strapBottom) / 2f)
        )
    }
}

@Composable
fun EthereumLogo(modifier: Modifier = Modifier) {
    Canvas(modifier = modifier.size(32.dp)) {
        val w = size.width
        val h = size.height
        drawCircle(Color(0xFF627EEA))
        val cx = w / 2f
        
        // Ethereum geometric crystal drawing
        val pathTop = Path().apply {
            moveTo(cx, h * 0.22f)
            lineTo(cx + w * 0.16f, h * 0.5f)
            lineTo(cx, h * 0.62f)
            lineTo(cx - w * 0.16f, h * 0.5f)
            close()
        }
        drawPath(pathTop, Color.White.copy(alpha = 0.95f))
        
        val pathTopRight = Path().apply {
            moveTo(cx, h * 0.22f)
            lineTo(cx + w * 0.16f, h * 0.5f)
            lineTo(cx, h * 0.62f)
            close()
        }
        drawPath(pathTopRight, Color.White.copy(alpha = 0.55f))

        val pathBot = Path().apply {
            moveTo(cx, h * 0.65f)
            lineTo(cx + w * 0.16f, h * 0.53f)
            lineTo(cx, h * 0.78f)
            lineTo(cx - w * 0.16f, h * 0.53f)
            close()
        }
        drawPath(pathBot, Color.White.copy(alpha = 0.85f))
    }
}

@Composable
fun BnbLogo(modifier: Modifier = Modifier) {
    Canvas(modifier = modifier.size(32.dp)) {
        val w = size.width
        val h = size.height
        drawCircle(Color(0xFFF3BA2F))
        val cx = w / 2f
        val cy = h / 2f
        val sizeL = w * 0.15f
        
        // Center Diamond
        val dCenter = Path().apply {
            moveTo(cx, cy - sizeL)
            lineTo(cx + sizeL, cy)
            lineTo(cx, cy + sizeL)
            lineTo(cx - sizeL, cy)
            close()
        }
        drawPath(dCenter, Color.White)
        
        // Brackets / Outer rings to represent BNB logo geometry
        val strokeW = 1.6f.dp.toPx()
        val pathOuter1 = Path().apply {
            moveTo(cx, cy - sizeL * 2.1f)
            lineTo(cx + sizeL * 2.1f, cy)
            lineTo(cx, cy + sizeL * 2.1f)
            lineTo(cx - sizeL * 2.1f, cy)
            close()
        }
        drawPath(pathOuter1, Color.White, style = Stroke(width = strokeW))
    }
}

@Composable
fun SolanaLogo(modifier: Modifier = Modifier) {
    CryptoIcon(symbol = "SOL", modifier = modifier.size(32.dp))
}

// ------------------------------------------------------------------------
// SHARED COMPONENTS
// ------------------------------------------------------------------------

@Composable
fun EstTotalValueHeader(
    totalBalanceUsd: Double,
    isBalanceVisible: Boolean,
    onToggleBalance: () -> Unit,
    showHistoryIcon: Boolean = false,
    onHistoryClick: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(top = 4.dp, bottom = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.Top
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .clickable(onClick = onToggleBalance)
                    .testTag("balance_toggle_row")
            ) {
                Text(
                    text = "Est total value",
                    color = MutedText,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium
                )
                Spacer(modifier = Modifier.width(4.dp))
                Icon(
                    imageVector = if (isBalanceVisible) Icons.Default.Visibility else Icons.Default.VisibilityOff,
                    contentDescription = "Toggle Balance",
                    tint = MutedText,
                    modifier = Modifier.size(14.dp)
                )
            }
            
            Spacer(modifier = Modifier.height(4.dp))
            
            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = if (isBalanceVisible) String.format("%,.2f", totalBalanceUsd) else "••••",
                    color = Color.White,
                    fontSize = 30.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.testTag("est_total_value_text")
                )
                Spacer(modifier = Modifier.width(6.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(top = 6.dp)
                ) {
                    Text(
                        text = "USD",
                        color = Color.White,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.width(2.dp))
                    Icon(
                        imageVector = Icons.Default.KeyboardArrowDown,
                        contentDescription = "Currency Select",
                        tint = MutedText,
                        modifier = Modifier.size(13.dp)
                    )
                }
            }
            
            Spacer(modifier = Modifier.height(4.dp))
            
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.clickable { /* Detail PnL */ }
            ) {
                Text(
                    text = "Today's PnL ",
                    color = MutedText,
                    fontSize = 11.sp
                )
                Text(
                    text = "$0.00 (0.00%)",
                    color = MutedText,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(modifier = Modifier.width(2.dp))
                Icon(
                    imageVector = Icons.Default.ChevronRight,
                    contentDescription = "Go",
                    tint = MutedText,
                    modifier = Modifier.size(12.dp)
                )
            }
        }
        
        Column(
            horizontalAlignment = Alignment.End,
            verticalArrangement = Arrangement.Top
        ) {
            if (showHistoryIcon) {
                IconButton(
                    onClick = onHistoryClick,
                    modifier = Modifier.size(24.dp)
                ) {
                    HistorySheetIcon(
                        color = Color.White,
                        modifier = Modifier.size(18.dp)
                    )
                }
                Spacer(modifier = Modifier.height(10.dp))
            } else {
                Spacer(modifier = Modifier.height(18.dp))
            }
            
            MiniGreenGraph()
        }
    }
}

// ------------------------------------------------------------------------
// BOTTOM NAVIGATION BAR
// ------------------------------------------------------------------------

@Composable
fun BottomNavigationBar(
    currentTab: BottomTab,
    onTabSelected: (BottomTab) -> Unit,
    onExploreDoubleTap: () -> Unit,
    config: FirebaseSyncManager.UpdateConfig = FirebaseSyncManager.UpdateConfig()
) {
    var lastExploreClickTime by remember { mutableStateOf(0L) }

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .navigationBarsPadding(),
        color = PureBlack,
        tonalElevation = 8.dp
    ) {
        Column {
            Divider(color = Color(0xFF1C1C1E), thickness = 0.5.dp)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(64.dp), // Slightly taller to accommodate the Trade text nicely
                horizontalArrangement = Arrangement.SpaceAround,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Tab 1: OKX
                BottomNavItem(
                    label = if (config.customAppName.isNotBlank()) config.customAppName else if (config.logoType.uppercase().trim() == "CUSTOM" && config.customLogoText.isNotBlank()) config.customLogoText else "OKX",
                    isSelected = currentTab == BottomTab.OKX,
                    onClick = { onTabSelected(BottomTab.OKX) },
                    icon = { color -> DynamicAppLogo(color = color, modifier = Modifier.size(20.dp), config = config) },
                    modifier = Modifier.testTag("bottom_nav_okx")
                )
                
                // Tab 2: Explore
                BottomNavItem(
                    label = "Explore",
                    isSelected = currentTab == BottomTab.Explore,
                    onClick = {
                        val currentTime = System.currentTimeMillis()
                        if (currentTime - lastExploreClickTime < 500) {
                            onExploreDoubleTap()
                        } else {
                            onTabSelected(BottomTab.Explore)
                        }
                        lastExploreClickTime = currentTime
                    },
                    icon = { color -> ExploreIcon(color = color, modifier = Modifier.size(24.dp)) }
                )
                
                // Tab 3: Trade
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .clickable { onTabSelected(BottomTab.Trade) }
                        .padding(vertical = 4.dp),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .background(Color.White, shape = CircleShape)
                            .testTag("bottom_nav_trade"),
                        contentAlignment = Alignment.Center
                    ) {
                        TransferIcon(
                            color = PureBlack,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    Spacer(modifier = Modifier.height(3.dp))
                    Text(
                        text = "Trade",
                        color = if (currentTab == BottomTab.Trade) OkxGreen else MutedText,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
                
                // Tab 4: Orbit
                BottomNavItem(
                    label = "Orbit",
                    isSelected = currentTab == BottomTab.Orbit,
                    onClick = { onTabSelected(BottomTab.Orbit) },
                    icon = { color -> OrbitIcon(color = color, isActive = currentTab == BottomTab.Orbit, modifier = Modifier.size(20.dp)) }
                )
                
                // Tab 5: Assets
                BottomNavItem(
                    label = "Assets",
                    isSelected = currentTab == BottomTab.Assets,
                    onClick = { onTabSelected(BottomTab.Assets) },
                    icon = { color -> AssetsIcon(color = color, modifier = Modifier.size(20.dp)) },
                    modifier = Modifier.testTag("bottom_nav_assets")
                )
            }
        }
    }
}

@Composable
fun RowScope.BottomNavItem(
    label: String,
    isSelected: Boolean,
    onClick: () -> Unit,
    icon: @Composable (Color) -> Unit,
    modifier: Modifier = Modifier
) {
    val activeColor = OkxGreen
    val inactiveColor = MutedText
    val currentColor = if (isSelected) activeColor else inactiveColor
    
    Column(
        modifier = modifier
            .weight(1f)
            .fillMaxHeight()
            .clickable(onClick = onClick)
            .padding(vertical = 4.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        icon(currentColor)
        Spacer(modifier = Modifier.height(3.dp))
        Text(
            text = label,
            color = currentColor,
            fontSize = 11.sp,
            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
        )
    }
}

// ------------------------------------------------------------------------
// SCREEN 1: ASSETS SCREEN CONTENT
// ------------------------------------------------------------------------

@Composable
fun AssetsScreenContent(
    totalBalanceUsd: Double,
    coinBalances: Map<String, Double>,
    prices: Map<String, Double>,
    isBalanceVisible: Boolean,
    onToggleBalance: () -> Unit,
    onDepositClick: () -> Unit,
    onWithdrawClick: () -> Unit,
    showDepositReceived: Boolean = false,
    onHistoryClick: () -> Unit = {}
) {
    val scrollState = rememberScrollState()
    
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp)
            .verticalScroll(scrollState)
            .testTag("assets_screen_scrollable")
    ) {
        Spacer(modifier = Modifier.height(16.dp))

        // Balance Header
        EstTotalValueHeader(
            totalBalanceUsd = totalBalanceUsd,
            isBalanceVisible = isBalanceVisible,
            onToggleBalance = onToggleBalance,
            showHistoryIcon = true,
            onHistoryClick = onHistoryClick
        )
        
        Spacer(modifier = Modifier.height(14.dp))
        
        // Deposit, Withdraw, Transfer, Earn Quick Buttons Row
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            val quickActions = listOf(
                QuickActionItem("Deposit", Icons.Default.ArrowDownward),
                QuickActionItem("Withdraw", Icons.Default.ArrowUpward),
                QuickActionItem("Transfer", Icons.AutoMirrored.Filled.CompareArrows),
                QuickActionItem("Earn", Icons.Default.Eco) // Stylized Eco matches leaf loop
            )
            
            quickActions.forEach { action ->
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier
                        .clickable {
                            if (action.label == "Deposit") {
                                onDepositClick()
                            } else if (action.label == "Withdraw") {
                                onWithdrawClick()
                            }
                        }
                        .padding(horizontal = 4.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(44.dp)
                            .background(OkxGreen, shape = CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        when (action.label) {
                            "Deposit" -> DepositIcon(color = PureBlack, modifier = Modifier.size(22.dp))
                            "Withdraw" -> WithdrawIcon(color = PureBlack, modifier = Modifier.size(22.dp))
                            "Transfer" -> TransferIcon(color = PureBlack, modifier = Modifier.size(22.dp))
                            "Earn" -> EarnIcon(color = PureBlack, modifier = Modifier.size(22.dp))
                        }
                    }
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = action.label,
                        color = Color.White,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        }
        
        Spacer(modifier = Modifier.height(14.dp))
        
        // Banner Card: Enable DEX Trading
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { /* Banner action */ }
                .testTag("dex_banner_card"),
            colors = CardDefaults.cardColors(containerColor = DarkGreyCard),
            shape = RoundedCornerShape(12.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 14.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Enable DEX trading",
                        color = Color.White,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Trade DEX tokens on the Exchange",
                        color = MutedText,
                        fontSize = 12.sp
                    )
                }
                Spacer(modifier = Modifier.width(8.dp))
                DebitCardGraphic()
            }
        }
        
        if (showDepositReceived) {
            Spacer(modifier = Modifier.height(14.dp))
            HorizontalDivider(color = Color(0xFF1E1E1E), thickness = 1.dp)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 12.dp)
                    .clickable { /* Tap deposit action */ }
                    .testTag("deposit_received_row"),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(6.dp)
                            .background(Color(0xFF00D180), shape = CircleShape)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "1 deposit(s) received",
                        color = Color.White,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
                Icon(
                    imageVector = Icons.Default.ChevronRight,
                    contentDescription = null,
                    tint = Color.Gray,
                    modifier = Modifier.size(16.dp)
                )
            }
            HorizontalDivider(color = Color(0xFF1E1E1E), thickness = 1.dp)
            Spacer(modifier = Modifier.height(14.dp))
        } else {
            Spacer(modifier = Modifier.height(14.dp))
        }
        
        // Portfolio Section Header
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Portfolio",
                color = Color.White,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold
            )
            FilterSlidersIcon(
                color = Color.White,
                modifier = Modifier.size(20.dp)
            )
        }
        
        Spacer(modifier = Modifier.height(8.dp))
        
        // Row of 3 Horizontal Cards (Funding, Trading, Earn)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            PortfolioCard(
                title = "Funding",
                value = if (totalBalanceUsd == 0.0) "$<0.01" else "$" + String.format("%,.2f", totalBalanceUsd),
                iconSymbol = { BriefcaseIcon(color = Color.White, modifier = Modifier.size(11.dp)) },
                modifier = Modifier.weight(1f)
            )
            PortfolioCard(
                title = "Trading",
                value = "$<0.01",
                iconSymbol = { TransferIcon(color = Color.White, modifier = Modifier.size(11.dp)) },
                modifier = Modifier.weight(1f)
            )
            PortfolioCard(
                title = "Earn",
                value = "$0",
                iconSymbol = { EarnIcon(color = Color.White, modifier = Modifier.size(11.dp)) },
                modifier = Modifier.weight(1f)
            )
        }
        
        Spacer(modifier = Modifier.height(16.dp))
        
        // Crypto Section Header
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Crypto",
                color = Color.White,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold
            )
            Icon(
                imageVector = Icons.Default.KeyboardArrowUp, // collapsed/expanded chevron
                contentDescription = "Collapse",
                tint = MutedText,
                modifier = Modifier.size(20.dp)
            )
        }
        
        Spacer(modifier = Modifier.height(8.dp))
        
        // Column List Headers (Name/Amount, Value/Spot PnL)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = "Name/Amount",
                color = MutedText,
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium
            )
            // Underlined Value/Spot PnL
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text = "Value/Spot PnL",
                    color = MutedText,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium
                )
                // Small custom dashed/dotted indicator underline
                Canvas(modifier = Modifier.width(90.dp).height(2.dp)) {
                    val strokeW = 1.dp.toPx()
                    drawLine(
                        color = MutedText.copy(alpha = 0.5f),
                        start = Offset(0f, 1f),
                        end = Offset(size.width, 1f),
                        strokeWidth = strokeW,
                        pathEffect = androidx.compose.ui.graphics.PathEffect.dashPathEffect(floatArrayOf(4f, 4f), 0f)
                    )
                }
            }
        }
        
        Spacer(modifier = Modifier.height(8.dp))
        
        // List of Coins
        val baseAssets = listOf(
            Triple("BTC", "Bitcoin", "Up to 5% APR"),
            Triple("ETH", "Ethereum", "Up to 5% APR"),
            Triple("BNB", "Binance Coin", "Up to 0.16% APR"),
            Triple("SOL", "Solana", "Up to 5.1% APR"),
            Triple("USDT", "USDT", "Up to 12.5% APR"),
            Triple("TRX", "TRON", "Up to 4.2% APR"),
            Triple("1INCH", "1INCH", "Up to 8.1% APR"),
            Triple("XAUT", "Tether Gold", "Up to 1.5% APR"),
            Triple("USDG", "USDG", "3.5% APR")
        )

        data class SortedAssetItem(
            val symbol: String,
            val name: String,
            val apr: String,
            val amount: Double,
            val valueUsd: Double
        )

        val sortedAssets = baseAssets.map { (symbol, name, apr) ->
            val amt = coinBalances[symbol] ?: 0.0
            val price = prices[symbol] ?: 0.0
            SortedAssetItem(symbol, name, apr, amt, amt * price)
        }.sortedWith(
            compareByDescending<SortedAssetItem> { it.valueUsd }
                .thenByDescending { it.amount }
                .thenBy { it.symbol }
        )

        sortedAssets.forEach { asset ->
            val symbol = asset.symbol
            val name = asset.name
            val apr = asset.apr
            val amt = asset.amount
            val valUsd = asset.valueUsd
            
            CoinRow(
                symbol = symbol,
                name = name,
                aprText = apr,
                amount = if (amt == 0.0) "<0.00000001" else String.format("%.8f", amt),
                value = if (amt == 0.0) "$<0.01" else "$" + String.format("%,.2f", valUsd),
                pnlText = if (symbol == "BNB" && amt == 0.0) "-$<0.01 (-7.25%)" else if (symbol == "SOL" && amt == 0.0) "-$<0.01 (-7.63%)" else null,
                logo = { CryptoIcon(symbol = symbol, modifier = Modifier.size(32.dp)) }
            )
        }
        
        Spacer(modifier = Modifier.height(50.dp))
    }
}

data class QuickActionItem(val label: String, val icon: androidx.compose.ui.graphics.vector.ImageVector)

@Composable
fun PortfolioCard(
    title: String,
    value: String,
    iconSymbol: @Composable () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .height(86.dp),
        colors = CardDefaults.cardColors(containerColor = DarkGreyCard),
        shape = RoundedCornerShape(10.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 10.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Box(
                modifier = Modifier
                    .size(20.dp)
                    .background(Color(0xFF242424), shape = CircleShape),
                contentAlignment = Alignment.Center
            ) {
                iconSymbol()
            }
            Column {
                Text(
                    text = title,
                    color = MutedText,
                    fontSize = 12.sp
                )
                Spacer(modifier = Modifier.height(1.dp))
                Text(
                    text = value,
                    color = Color.White,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

@Composable
fun CoinRow(
    symbol: String,
    name: String,
    aprText: String,
    amount: String,
    value: String,
    pnlText: String?,
    logo: @Composable () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 9.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
            logo()
            Spacer(modifier = Modifier.width(12.dp))
            Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = symbol,
                        color = Color.White,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    // APR green badge
                    Box(
                        modifier = Modifier
                            .background(Color(0xFF0C1F12), shape = RoundedCornerShape(4.dp))
                            .padding(horizontal = 5.dp, vertical = 1.dp)
                    ) {
                        Text(
                            text = aprText,
                            color = Color(0xFF45A162),
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Medium,
                            lineHeight = 10.sp
                        )
                    }
                }
                Spacer(modifier = Modifier.height(3.dp))
                Text(
                    text = amount,
                    color = MutedText,
                    fontSize = 12.sp
                )
            }
        }
        Column(horizontalAlignment = Alignment.End) {
            Text(
                text = value,
                color = Color.White,
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold
            )
            if (pnlText != null) {
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = pnlText,
                    color = ValueRed,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Medium
                )
            }
        }
    }
}

// ------------------------------------------------------------------------
// SCREEN 2: OKX HOME SCREEN CONTENT (EXCHANGE)
// ------------------------------------------------------------------------

@Composable
fun OkxScreenContent(
    totalBalanceUsd: Double,
    isBalanceVisible: Boolean,
    onToggleBalance: () -> Unit,
    onDepositClick: () -> Unit,
    onWithdrawClick: () -> Unit = {},
    onMenuClick: () -> Unit,
    showMenuButton: Boolean = false,
    config: FirebaseSyncManager.UpdateConfig = FirebaseSyncManager.UpdateConfig()
) {
    val scrollState = rememberScrollState()
    var selectedTopTab by remember { mutableStateOf("Exchange") }
    var selectedFilterTab by remember { mutableStateOf("Favorites") }
    
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp)
            .verticalScroll(scrollState)
            .testTag("okx_screen_scrollable")
    ) {
        // Top Navigation Row
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 10.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Left App menu grid launcher (9 dots) - Visible only to admin
            if (showMenuButton) {
                IconButton(onClick = onMenuClick, modifier = Modifier.testTag("okx_menu_button")) {
                    GridMenuIcon(
                        color = Color.White,
                        modifier = Modifier.size(20.dp)
                    )
                }
            } else {
                Spacer(modifier = Modifier.size(48.dp))
            }
            
            // Center Segment Control: Exchange & Wallet
            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(20.dp))
                    .background(DarkGreyCard)
                    .padding(2.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                SegmentButton(
                    label = "Exchange",
                    isActive = selectedTopTab == "Exchange",
                    onClick = { selectedTopTab = "Exchange" }
                )
                SegmentButton(
                    label = "Wallet",
                    isActive = selectedTopTab == "Wallet",
                    onClick = { selectedTopTab = "Wallet" }
                )
            }
            
            // Right Actions: Gift box & Card top-up deposit
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = { /* Gift promo */ }) {
                    GiftBoxIcon(
                        color = Color.White,
                        modifier = Modifier.size(20.dp)
                    )
                }
                IconButton(onClick = onDepositClick) {
                    CardDepositIcon(
                        color = Color.White,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        }
        
        Spacer(modifier = Modifier.height(6.dp))
        
        // Search Bar Area
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .height(42.dp),
            colors = CardDefaults.cardColors(containerColor = SearchGrey),
            shape = RoundedCornerShape(21.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Search,
                        contentDescription = "Search",
                        tint = MutedText,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "🔥 SOL frequently traded",
                        color = MutedText,
                        fontSize = 13.sp
                    )
                }
                Icon(
                    imageVector = Icons.Default.QrCodeScanner,
                    contentDescription = "Scan",
                    tint = MutedText,
                    modifier = Modifier.size(18.dp)
                )
            }
        }
        
        // Balance Section (Shared style)
        EstTotalValueHeader(
            totalBalanceUsd = totalBalanceUsd,
            isBalanceVisible = isBalanceVisible,
            onToggleBalance = onToggleBalance
        )
        
        Spacer(modifier = Modifier.height(10.dp))
        
        // Quick Actions Wide Buttons: Deposit Crypto & P2P Trading
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Button(
                onClick = onDepositClick,
                colors = ButtonDefaults.buttonColors(containerColor = OkxGreen),
                modifier = Modifier
                    .weight(1f)
                    .height(44.dp),
                shape = RoundedCornerShape(22.dp)
            ) {
                Text(
                    text = "Deposit crypto",
                    color = PureBlack,
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp
                )
            }
            Button(
                onClick = onWithdrawClick,
                colors = ButtonDefaults.buttonColors(containerColor = OkxGreen),
                modifier = Modifier
                    .weight(1f)
                    .height(44.dp),
                shape = RoundedCornerShape(22.dp)
            ) {
                Text(
                    text = "P2P trading",
                    color = PureBlack,
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp
                )
            }
        }
        
        if (config.homeBannerUrl.isNotBlank()) {
            Spacer(modifier = Modifier.height(14.dp))
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(100.dp),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = DarkGreyCard)
            ) {
                SubcomposeAsyncImage(
                    model = ImageRequest.Builder(LocalContext.current)
                        .data(config.homeBannerUrl)
                        .crossfade(true)
                        .build(),
                    contentDescription = "Promo Banner",
                    contentScale = androidx.compose.ui.layout.ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                    loading = {
                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator(color = MaterialTheme.colorScheme.primary, strokeWidth = 1.dp, modifier = Modifier.size(16.dp))
                        }
                    }
                )
            }
        }
        
        Spacer(modifier = Modifier.height(18.dp))
        
        // Announcement Card: Memecoins are trending
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = DarkGreyCard),
            shape = RoundedCornerShape(12.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(14.dp),
                verticalAlignment = Alignment.Top
            ) {
                Box(
                    modifier = Modifier.size(46.dp),
                    contentAlignment = Alignment.Center
                ) {
                    CandlestickIcon()
                    Box(
                        modifier = Modifier
                            .offset(x = 11.dp, y = (-2).dp)
                            .size(18.dp)
                            .background(Color(0xFF333333), shape = CircleShape)
                            .border(0.5.dp, Color(0xFF555555), shape = CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        SparkleStar(color = Color.White, modifier = Modifier.size(8.dp))
                    }
                }
                
                Spacer(modifier = Modifier.width(14.dp))
                
                Column(modifier = Modifier.weight(1f)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Memecoins are trending",
                            color = Color.White,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "1/8",
                            color = MutedText,
                            fontSize = 11.sp
                        )
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Trade memecoins on OKX DEX. Go to Web3 -> DEX -> Swap",
                        color = MutedText,
                        fontSize = 11.sp,
                        lineHeight = 15.sp
                    )
                }
            }
        }
        
        Spacer(modifier = Modifier.height(20.dp))
        
        // Horizontal Scroll Filter Tab Bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            verticalAlignment = Alignment.CenterVertically
        ) {
            val tabs = listOf("Favorites", "Hot", "New", "TradFi", "DEX", "Copy Trading")
            tabs.forEach { tab ->
                val isActive = selectedFilterTab == tab
                Box(
                    modifier = Modifier
                        .clickable { selectedFilterTab = tab }
                        .padding(end = 12.dp).padding(vertical = 6.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .background(if (isActive) LightGreyCard else Color.Transparent)
                        .padding(horizontal = 12.dp, vertical = 6.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = tab,
                            color = if (isActive) Color.White else MutedText,
                            fontSize = 13.sp,
                            fontWeight = if (isActive) FontWeight.Bold else FontWeight.Medium
                        )
                        // Red notification indicator dot for specific items
                        if (tab == "New" || tab == "TradFi" || tab == "DEX") {
                            Box(
                                modifier = Modifier
                                    .padding(start = 3.dp)
                                    .size(4.dp)
                                    .background(Color(0xFFFF334B), shape = CircleShape)
                                    .align(Alignment.Top)
                            )
                        }
                    }
                }
            }
        }
        
        Spacer(modifier = Modifier.height(18.dp))
        
        // Select Crypto Title
        Text(
            text = "Select crypto",
            color = Color.White,
            fontSize = 15.sp,
            fontWeight = FontWeight.Bold
        )
        
        Spacer(modifier = Modifier.height(12.dp))
        
        // Grid of Crypto pairs (2 columns)
        val coinsList = listOf(
            CryptoPair("BTC", "Bitcoin"),
            CryptoPair("ETH", "Ethereum"),
            CryptoPair("OKB", "OKB"),
            CryptoPair("SOL", "Solana"),
            CryptoPair("DOGE", "Dogecoin"),
            CryptoPair("XRP", "XRP"),
            CryptoPair("XAUT", "Tether Gold"),
            CryptoPair("HYPE", "Hyperliquid")
        )
        
        // 4 rows of 2 columns
        for (i in 0 until coinsList.size step 2) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                CryptoPairCard(coinsList[i], modifier = Modifier.weight(1f))
                if (i + 1 < coinsList.size) {
                    CryptoPairCard(coinsList[i+1], modifier = Modifier.weight(1f))
                }
            }
        }
        
        Spacer(modifier = Modifier.height(50.dp))
    }
}

@Composable
fun SegmentButton(
    label: String,
    isActive: Boolean,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(18.dp))
            .background(if (isActive) LightGreyCard else Color.Transparent)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 6.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            color = if (isActive) Color.White else MutedText,
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold
        )
    }
}

data class CryptoPair(val symbol: String, val fullName: String)

@Composable
fun CryptoPairCard(
    pair: CryptoPair,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.height(68.dp),
        colors = CardDefaults.cardColors(containerColor = DarkGreyCard),
        shape = RoundedCornerShape(8.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Row(verticalAlignment = Alignment.Bottom) {
                    Text(
                        text = pair.symbol,
                        color = Color.White,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "/USDT",
                        color = MutedText,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = pair.fullName,
                    color = MutedText,
                    fontSize = 11.sp
                )
            }
            
            // Checkmark white solid circle
            Box(
                modifier = Modifier
                    .size(16.dp)
                    .background(Color.White, shape = CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Check,
                    contentDescription = "Selected",
                    tint = PureBlack,
                    modifier = Modifier.size(11.dp)
                )
            }
        }
    }
}

// ------------------------------------------------------------------------
// PLACEHOLDER SCREEN CONTENT FOR OTHER TABS
// ------------------------------------------------------------------------

@Composable
fun PlaceholderScreenContent(
    tabName: String,
    onBackToAssets: () -> Unit
) {
    Box(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = "$tabName Screen",
                color = Color.White,
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(10.dp))
            Text(
                text = "This screen is under active system development.",
                color = MutedText,
                fontSize = 14.sp,
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(20.dp))
            Button(
                onClick = onBackToAssets,
                colors = ButtonDefaults.buttonColors(containerColor = OkxGreen)
            ) {
                Text("Return to Assets Screen", color = PureBlack)
            }
        }
    }
}

// ------------------------------------------------------------------------
// ADD / WITHDRAW BALANCE DIALOG COMPOSABLE
// ------------------------------------------------------------------------

@Composable
fun AddWithdrawBalanceDialog(
    onDismiss: () -> Unit,
    viewModel: WalletViewModel,
    onConfigSaved: () -> Unit = {}
) {
    val isOnline by viewModel.isOnline.collectAsStateWithLifecycle()
    val prices by viewModel.prices.collectAsStateWithLifecycle()
    val balances by viewModel.coinBalances.collectAsStateWithLifecycle()
    val currentUser by viewModel.currentUser.collectAsStateWithLifecycle()

    var activeTab by remember { mutableStateOf("Adjust") } // "Adjust" or "Sync"
    var isAddingMode by remember { mutableStateOf(true) } // true = Add, false = Withdraw
    
    val isAdmin = currentUser?.isAdmin() == true
    val currentActiveTab = if (isAdmin) activeTab else "Adjust"
    
    val supportedCoins = listOf("BTC", "ETH", "BNB", "SOL", "USDT", "TRX", "1INCH", "XAUT", "USDG")
    var selectedCoin by remember { mutableStateOf("SOL") }
    var amountText by remember { mutableStateOf("") }
    
    val currentPrice = prices[selectedCoin] ?: 1.0
    val currentBalance = balances[selectedCoin] ?: 0.0
    
    val amountDouble = amountText.toDoubleOrNull() ?: 0.0
    val estimatedValue = amountDouble * currentPrice

    AlertDialog(
        onDismissRequest = onDismiss,
        properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false),
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
        confirmButton = {},
        dismissButton = {},
        containerColor = Color(0xFF151515),
        titleContentColor = Color.White,
        textContentColor = Color.White,
        shape = RoundedCornerShape(16.dp),
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(8.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Header Row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Manage Balance",
                        color = Color.White,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold
                    )
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, contentDescription = "Close", tint = Color.White)
                    }
                }
                
                currentUser?.let { user ->
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(Color(0xFF1C1C1E), shape = RoundedCornerShape(10.dp))
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                text = if (user.isAdmin()) "Admin Mode (${user.username})" else "Profile (${user.username})",
                                color = OkxGreen,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = user.email,
                                color = Color.Gray,
                                fontSize = 11.sp
                            )
                        }
                        TextButton(
                            onClick = {
                                viewModel.logoutUser()
                                onDismiss()
                            },
                            colors = ButtonDefaults.textButtonColors(contentColor = Color.Red),
                            modifier = Modifier.height(32.dp).testTag("auth_logout_button"),
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp)
                        ) {
                            Text("Sign Out", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
                
                Spacer(modifier = Modifier.height(12.dp))

                // Tab Selector for Adjust Balance vs Cloud Sync - Visible only to admin
                if (isAdmin) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .background(Color(0xFF1C1C1E))
                            .padding(2.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(8.dp))
                                .background(if (currentActiveTab == "Adjust") Color(0xFF2C2C2E) else Color.Transparent)
                                .clickable { activeTab = "Adjust" }
                                .padding(vertical = 8.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "Adjust Balance",
                                color = if (currentActiveTab == "Adjust") OkxGreen else Color.Gray,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(8.dp))
                                .background(if (currentActiveTab == "Sync") Color(0xFF2C2C2E) else Color.Transparent)
                                .clickable { activeTab = "Sync" }
                                .padding(vertical = 8.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "Cloud DB Sync",
                                color = if (currentActiveTab == "Sync") OkxGreen else Color.Gray,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                }

                if (currentActiveTab == "Adjust") {
                    Spacer(modifier = Modifier.height(16.dp))
                    
                    // Add / Withdraw Toggle Buttons
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(20.dp))
                            .background(Color(0xFF2C2C2E))
                            .padding(2.dp),
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(18.dp))
                                .background(if (isAddingMode) Color(0xFF1C1C1E) else Color.Transparent)
                                .clickable { isAddingMode = true }
                                .padding(vertical = 10.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "Add Balance",
                                color = if (isAddingMode) OkxGreen else Color.Gray,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(18.dp))
                                .background(if (!isAddingMode) Color(0xFF1C1C1E) else Color.Transparent)
                                .clickable { isAddingMode = false }
                                .padding(vertical = 10.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "Withdraw",
                                color = if (!isAddingMode) OkxGreen else Color.Gray,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                    
                    Spacer(modifier = Modifier.height(20.dp))
                    
                    // Select Coin dropdown style Row
                    Text(
                        text = "Select Asset",
                        color = MutedText,
                        fontSize = 12.sp,
                        modifier = Modifier.align(Alignment.Start)
                    )
                    
                    Spacer(modifier = Modifier.height(6.dp))
                    
                    // LazyRow or Dropdown for Coin Selection
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        supportedCoins.forEach { coin ->
                            val isSelected = selectedCoin == coin
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(if (isSelected) OkxGreen.copy(alpha = 0.15f) else Color(0xFF1C1C1E))
                                    .border(
                                        width = 1.dp,
                                        color = if (isSelected) OkxGreen else Color.Transparent,
                                        shape = RoundedCornerShape(12.dp)
                                    )
                                    .clickable { selectedCoin = coin }
                                    .padding(horizontal = 14.dp, vertical = 10.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    CryptoIcon(symbol = coin, modifier = Modifier.size(20.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = coin,
                                        color = if (isSelected) OkxGreen else Color.White,
                                        fontSize = 14.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }
                    }
                    
                    Spacer(modifier = Modifier.height(20.dp))
                    
                    // Current asset details
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "Price: $${String.format("%,.4f", currentPrice)} USD",
                            color = MutedText,
                            fontSize = 12.sp
                        )
                        Text(
                            text = "Balance: ${String.format("%.4f", currentBalance)} $selectedCoin",
                            color = MutedText,
                            fontSize = 12.sp
                        )
                    }
                    
                    Spacer(modifier = Modifier.height(14.dp))
                    
                    // Input Amount TextField
                    Text(
                        text = "Enter Amount",
                        color = MutedText,
                        fontSize = 12.sp,
                        modifier = Modifier.align(Alignment.Start)
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    
                    OutlinedTextField(
                        value = amountText,
                        onValueChange = { newValue ->
                            if (newValue.isEmpty() || newValue.toDoubleOrNull() != null || newValue.endsWith(".")) {
                                amountText = newValue
                            }
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("amount_input_field"),
                        placeholder = { Text("0.00", color = Color.Gray) },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White,
                            focusedBorderColor = OkxGreen,
                            unfocusedBorderColor = Color(0xFF2C2C2E),
                            focusedContainerColor = Color(0xFF1C1C1E),
                            unfocusedContainerColor = Color(0xFF1C1C1E)
                        ),
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp),
                        keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                            keyboardType = KeyboardType.Decimal
                        ),
                        suffix = {
                            Text(selectedCoin, color = Color.White, fontWeight = FontWeight.Bold)
                        }
                    )
                    
                    Spacer(modifier = Modifier.height(14.dp))
                    
                    // Live estimated market value preview
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(Color(0xFF1C1C1E), shape = RoundedCornerShape(12.dp))
                            .padding(12.dp)
                    ) {
                        Column(modifier = Modifier.fillMaxWidth()) {
                            Text(
                                text = "Live Market Value Preview",
                                color = MutedText,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Medium
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "Est. USD Value:",
                                    color = Color.Gray,
                                    fontSize = 13.sp
                                )
                                Text(
                                    text = "$${String.format("%,.2f", estimatedValue)} USD",
                                    color = if (estimatedValue > 0.0) OkxGreen else Color.White,
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                    
                    Spacer(modifier = Modifier.height(24.dp))

                    if (!isOnline) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(Color(0xFF321414), shape = RoundedCornerShape(8.dp))
                                .border(1.dp, Color(0xFF801A1A), shape = RoundedCornerShape(8.dp))
                                .padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.CloudOff,
                                contentDescription = "Offline Mode",
                                tint = Color(0xFFE57373),
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Unable to connect to the internet. Try again later.",
                                color = Color(0xFFFFCDD2),
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Normal
                            )
                        }
                        Spacer(modifier = Modifier.height(16.dp))
                    }
                    
                    // Submit Action Button
                    Button(
                        enabled = isOnline,
                        onClick = {
                            if (amountDouble > 0.0) {
                                if (isAddingMode) {
                                    viewModel.addBalance(selectedCoin, amountDouble)
                                    onDismiss()
                                } else {
                                    viewModel.withdrawBalance(
                                        symbol = selectedCoin,
                                        amount = amountDouble,
                                        onSuccess = { onDismiss() },
                                        onError = { errorMsg ->
                                            // Handle gracefully
                                        }
                                    )
                                }
                            }
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                            .testTag("submit_balance_button"),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = OkxGreen,
                            contentColor = PureBlack,
                            disabledContainerColor = Color(0xFF2C2C2E),
                            disabledContentColor = Color.Gray
                        ),
                        shape = RoundedCornerShape(24.dp)
                    ) {
                        Text(
                            text = if (isAddingMode) "Add to Balance" else "Withdraw from Balance",
                            color = if (isOnline) PureBlack else Color.Gray,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                } else {
                    // Firebase Cloud Sync Setup Form
                    val context = androidx.compose.ui.platform.LocalContext.current
                    val scope = rememberCoroutineScope()
                    
                    var apiKey by remember { mutableStateOf("") }
                    var projectId by remember { mutableStateOf("") }
                    var appId by remember { mutableStateOf("") }
                    var syncStatusMsg by remember { mutableStateOf<String?>(null) }
                    
                    // Load current settings
                    val isAlreadyConfigured = remember { FirebaseSyncManager.isConfigured(context) }
                    val currentConfig = remember { FirebaseSyncManager.getConfig(context) }
                    
                    LaunchedEffect(Unit) {
                        apiKey = currentConfig.first
                        projectId = currentConfig.second
                        appId = currentConfig.third
                    }
                    
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 16.dp),
                        horizontalAlignment = Alignment.Start
                    ) {
                        Text(
                            text = "Cloud DB Connection Settings",
                            color = Color.White,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Fill in your Firebase credentials to enable live user & balance sync with your Netlify web Admin Panel.",
                            color = Color.Gray,
                            fontSize = 11.sp
                        )
                        
                        Spacer(modifier = Modifier.height(16.dp))
                        
                        // Project ID
                        Text("Firebase Project ID", color = MutedText, fontSize = 11.sp)
                        Spacer(modifier = Modifier.height(4.dp))
                        OutlinedTextField(
                            value = projectId,
                            onValueChange = { projectId = it },
                            modifier = Modifier.fillMaxWidth(),
                            placeholder = { Text("e.g. okx-clone-12345", color = Color.DarkGray, fontSize = 13.sp) },
                            singleLine = true,
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedTextColor = Color.White,
                                unfocusedTextColor = Color.White,
                                focusedBorderColor = OkxGreen,
                                unfocusedBorderColor = Color(0xFF2C2C2E),
                                focusedContainerColor = Color(0xFF1C1C1E),
                                unfocusedContainerColor = Color(0xFF1C1C1E)
                            ),
                            shape = RoundedCornerShape(10.dp)
                        )
                        
                        Spacer(modifier = Modifier.height(10.dp))
                        
                        // API Key
                        Text("Firebase API Key", color = MutedText, fontSize = 11.sp)
                        Spacer(modifier = Modifier.height(4.dp))
                        OutlinedTextField(
                            value = apiKey,
                            onValueChange = { apiKey = it },
                            modifier = Modifier.fillMaxWidth(),
                            placeholder = { Text("AIzaSyA1...", color = Color.DarkGray, fontSize = 13.sp) },
                            singleLine = true,
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedTextColor = Color.White,
                                unfocusedTextColor = Color.White,
                                focusedBorderColor = OkxGreen,
                                unfocusedBorderColor = Color(0xFF2C2C2E),
                                focusedContainerColor = Color(0xFF1C1C1E),
                                unfocusedContainerColor = Color(0xFF1C1C1E)
                            ),
                            shape = RoundedCornerShape(10.dp)
                        )
                        
                        Spacer(modifier = Modifier.height(10.dp))
                        
                        // App ID
                        Text("Firebase App ID", color = MutedText, fontSize = 11.sp)
                        Spacer(modifier = Modifier.height(4.dp))
                        OutlinedTextField(
                            value = appId,
                            onValueChange = { appId = it },
                            modifier = Modifier.fillMaxWidth(),
                            placeholder = { Text("e.g. 1:12345:web:abcdef", color = Color.DarkGray, fontSize = 13.sp) },
                            singleLine = true,
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedTextColor = Color.White,
                                unfocusedTextColor = Color.White,
                                focusedBorderColor = OkxGreen,
                                unfocusedBorderColor = Color(0xFF2C2C2E),
                                focusedContainerColor = Color(0xFF1C1C1E),
                                unfocusedContainerColor = Color(0xFF1C1C1E)
                            ),
                            shape = RoundedCornerShape(10.dp)
                        )
                        
                        Spacer(modifier = Modifier.height(14.dp))
                        
                        // Status Indicator
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            val statusText = if (isAlreadyConfigured) "🟢 Sync Status: Active & Connected" else "🔴 Sync Status: Disconnected"
                            Text(
                                text = statusText,
                                color = if (isAlreadyConfigured) OkxGreen else Color.Gray,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        
                        syncStatusMsg?.let { msg ->
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(text = msg, color = if (msg.startsWith("Success")) OkxGreen else Color.Red, fontSize = 12.sp)
                        }
                        
                        Spacer(modifier = Modifier.height(20.dp))
                        
                        // Action buttons
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            if (isAlreadyConfigured) {
                                Button(
                                    onClick = {
                                        FirebaseSyncManager.clearConfig(context)
                                        viewModel.stopCloudSync()
                                        syncStatusMsg = "Success: Cloud sync configuration cleared."
                                        apiKey = ""
                                        projectId = ""
                                        appId = ""
                                    },
                                    modifier = Modifier.weight(1f).height(44.dp),
                                    colors = ButtonDefaults.buttonColors(containerColor = Color.Red.copy(alpha = 0.8f)),
                                    shape = RoundedCornerShape(22.dp)
                                ) {
                                    Text("Disconnect", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                            
                            Button(
                                onClick = {
                                    if (apiKey.isBlank() || projectId.isBlank() || appId.isBlank()) {
                                        syncStatusMsg = "Error: Please fill in all fields."
                                        return@Button
                                    }
                                    try {
                                        FirebaseSyncManager.saveConfig(context, apiKey, projectId, appId)
                                        val initSuccess = FirebaseSyncManager.initialize(context)
                                        if (initSuccess) {
                                            syncStatusMsg = "Success: Connected! Syncing data..."
                                            // Start Sync in viewModel
                                            currentUser?.let { user ->
                                                viewModel.setupCloudSync(context, user)
                                                viewModel.triggerCloudBackup()
                                            }
                                            // Sync all local data in background
                                            FirebaseSyncManager.uploadAllLocalDataToCloud(context, scope)
                                            onConfigSaved()
                                        } else {
                                            syncStatusMsg = "Error: Could not initialize database with these keys."
                                        }
                                    } catch (e: Exception) {
                                        syncStatusMsg = "Error: ${e.message}"
                                    }
                                },
                                modifier = Modifier.weight(1.5f).height(44.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = OkxGreen),
                                shape = RoundedCornerShape(22.dp)
                            ) {
                                Text(
                                    text = if (isAlreadyConfigured) "Update & Sync" else "Connect & Sync",
                                    color = PureBlack,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                }
            }
        }
    )
}

// ------------------------------------------------------------------------
// OKX STYLE PULL TO REFRESH COMPOSABLES
// ------------------------------------------------------------------------

@Composable
fun OkxPullToRefresh(
    isRefreshing: Boolean,
    onRefresh: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    val density = LocalDensity.current
    // Convert thresholds to device density-independent pixels for professional response
    val triggerDistance = with(density) { 60.dp.toPx() } // Deliberate pull but much smaller shift
    val maxPullDistance = with(density) { 90.dp.toPx() } // Max visual movement is constrained to prevent pushing content down too far
    val minDragThreshold = with(density) { 25.dp.toPx() } // Minimum drag dead zone to filter small scrolling wiggles

    var pullDistance by remember { mutableStateOf(0f) }

    val animatedOffset by animateFloatAsState(
        targetValue = if (isRefreshing) {
            triggerDistance
        } else if (pullDistance < minDragThreshold) {
            0f
        } else {
            // Apply professional quadratic tension resistance to prevent unintended minor shifts
            val progress = ((pullDistance - minDragThreshold) / (maxPullDistance - minDragThreshold)).coerceIn(0f, 1f)
            val visualProgress = progress * progress // quadratic formula dampens small wiggles almost entirely
            visualProgress * (maxPullDistance - minDragThreshold)
        },
        animationSpec = if (isRefreshing) {
            // Hold position firmly when refreshing
            spring(
                dampingRatio = Spring.DampingRatioNoBouncy,
                stiffness = Spring.StiffnessMedium
            )
        } else {
            // "Shoots up" back to the top when dragging ends or refreshing completes
            tween(
                durationMillis = 350,
                easing = FastOutSlowInEasing
            )
        }
    )

    // Reset pullDistance once refreshing state changes
    LaunchedEffect(isRefreshing) {
        pullDistance = 0f
    }

    val nestedScrollConnection = remember(triggerDistance, maxPullDistance, minDragThreshold) {
        object : NestedScrollConnection {
            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                // If user is scrolling up and we have pulled down offset, we consume the scroll first
                return if (available.y < 0 && pullDistance > 0 && source == NestedScrollSource.UserInput) {
                    val consumed = if (pullDistance + available.y < 0) -pullDistance else available.y
                    pullDistance += consumed
                    Offset(0f, consumed)
                } else {
                    Offset.Zero
                }
            }

            override fun onPostScroll(
                consumed: Offset,
                available: Offset,
                source: NestedScrollSource
            ): Offset {
                // If user is scrolling down and child scrollable didn't consume it (at the top), drag down
                return if (available.y > 0 && !isRefreshing && source == NestedScrollSource.UserInput) {
                    val delta = available.y * 0.5f // apply friction/resistance
                    val oldDistance = pullDistance
                    pullDistance = (pullDistance + delta).coerceAtMost(maxPullDistance)
                    Offset(0f, (pullDistance - oldDistance) / 0.5f)
                } else {
                    Offset.Zero
                }
            }

            override suspend fun onPreFling(available: Velocity): Velocity {
                if (!isRefreshing && pullDistance >= triggerDistance) {
                    onRefresh()
                }
                pullDistance = 0f
                return super.onPreFling(available)
            }

            override suspend fun onPostFling(consumed: Velocity, available: Velocity): Velocity {
                pullDistance = 0f
                return super.onPostFling(consumed, available)
            }
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .nestedScroll(nestedScrollConnection)
            .background(PureBlack)
    ) {
        // OKX Pull-to-refresh Animated Indicator Header (Blank empty space as requested)
        if (animatedOffset > 0f) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(with(LocalDensity.current) { animatedOffset.toDp() })
                    .align(Alignment.TopCenter),
                contentAlignment = Alignment.Center
            ) {
                // Icon removed per user request
            }
        }

        // Main Content container shifted down dynamically
        Box(
            modifier = Modifier
                .fillMaxSize()
                .offset(y = with(LocalDensity.current) { animatedOffset.toDp() })
        ) {
            content()
        }
    }
}

@Composable
fun OkxLogoLoader(
    progress: Float,
    isRefreshing: Boolean,
    modifier: Modifier = Modifier
) {
    val infiniteTransition = rememberInfiniteTransition()
    
    // Constant rotation when refreshing, or drag-based rotation
    val rotation by if (isRefreshing) {
        infiniteTransition.animateFloat(
            initialValue = 0f,
            targetValue = 360f,
            animationSpec = infiniteRepeatable(
                animation = tween(1200, easing = LinearEasing),
                repeatMode = RepeatMode.Restart
            )
        )
    } else {
        remember(progress) { mutableStateOf(progress * 270f) }
    }

    // Gentle pulse scale when active
    val scale by if (isRefreshing) {
        infiniteTransition.animateFloat(
            initialValue = 0.9f,
            targetValue = 1.1f,
            animationSpec = infiniteRepeatable(
                animation = tween(600, easing = FastOutSlowInEasing),
                repeatMode = RepeatMode.Reverse
            )
        )
    } else {
        remember(progress) { mutableStateOf(progress) }
    }

    Box(
        modifier = modifier
            .size(42.dp)
            .graphicsLayer {
                rotationZ = rotation
                scaleX = scale
                scaleY = scale
                alpha = progress
            },
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val cellCount = 3
            val cellSize = size.width * 0.22f
            val spacing = size.width * 0.12f
            val totalGridSize = cellCount * cellSize + (cellCount - 1) * spacing
            val startOffset = (size.width - totalGridSize) / 2f

            // Draw the iconic 3x3 OKX block pattern
            for (row in 0 until cellCount) {
                for (col in 0 until cellCount) {
                    val x = startOffset + col * (cellSize + spacing)
                    val y = startOffset + row * (cellSize + spacing)
                    
                    // Style matching OKX Grid Layout (green & white accents)
                    val color = if ((row + col) % 2 == 0) OkxGreen else Color.White

                    drawRoundRect(
                        color = color,
                        topLeft = Offset(x, y),
                        size = Size(cellSize, cellSize),
                        cornerRadius = CornerRadius(cellSize * 0.25f)
                    )
                }
            }
        }
    }
}

// ------------------------------------------------------------------------
// WITHDRAW FLOW COMPOSABLES AND HELPERS
// ------------------------------------------------------------------------

fun getCryptoName(symbol: String): String {
    return when (symbol.uppercase()) {
        "BTC" -> "Bitcoin"
        "ETH" -> "Ethereum"
        "BNB" -> "Binance Coin"
        "SOL" -> "Solana"
        "USDT" -> "USDT"
        "TRX" -> "TRON"
        "1INCH" -> "1INCH"
        "XAUT" -> "Tether Gold"
        "USDG" -> "USDG"
        "USDC" -> "USDC"
        else -> "Untradable"
    }
}

fun isStandardCoin(symbol: String): Boolean {
    return symbol.uppercase() in listOf("BTC", "ETH", "BNB", "SOL", "USDT", "TRX", "1INCH", "XAUT", "USDG", "USDC")
}

@Composable
fun WithdrawSelectAssetScreen(
    coinBalances: Map<String, Double>,
    prices: Map<String, Double>,
    onBack: () -> Unit,
    onAssetSelected: (String) -> Unit
) {
    var searchQuery by remember { mutableStateOf("") }
    
    // Get all coins in the database that have a non-zero balance
    val nonZeroAssets = remember(coinBalances, prices) {
        coinBalances.entries
            .filter { it.value > 0.0 }
            .map { (symbol, amount) ->
                val price = prices[symbol] ?: 0.0
                val valueUsd = amount * price
                Triple(symbol, amount, valueUsd)
            }
            // Sort by USD value descending
            .sortedByDescending { it.third }
    }
    
    // Filter based on search query
    val filteredAssets = remember(nonZeroAssets, searchQuery) {
        if (searchQuery.isEmpty()) {
            nonZeroAssets
        } else {
            nonZeroAssets.filter { (symbol, _, _) ->
                symbol.contains(searchQuery, ignoreCase = true) ||
                getCryptoName(symbol).contains(searchQuery, ignoreCase = true)
            }
        }
    }
    
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(PureBlack)
            .statusBarsPadding()
    ) {
        // Header
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp)
                .padding(horizontal = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            IconButton(onClick = onBack) {
                ChevronLeftIcon(
                    color = Color.White,
                    modifier = Modifier.size(24.dp)
                )
            }
            Text(
                text = "Select asset",
                color = Color.White,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
                modifier = Modifier.weight(1f)
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = { /* Help */ }) {
                    HelpOutlineIcon(
                        color = Color.White,
                        modifier = Modifier.size(20.dp)
                    )
                }
                IconButton(onClick = { /* History */ }) {
                    HistorySheetIcon(
                        color = Color.White,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        }

        // Search Bar
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp)
                .background(Color(0xFF1C1C1E), shape = RoundedCornerShape(22.dp))
                .padding(horizontal = 14.dp, vertical = 10.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(
                    imageVector = Icons.Default.Search,
                    contentDescription = "Search",
                    tint = Color.Gray,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(10.dp))
                BasicTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    textStyle = androidx.compose.ui.text.TextStyle(color = Color.White, fontSize = 14.sp),
                    singleLine = true,
                    decorationBox = { innerTextField ->
                        if (searchQuery.isEmpty()) {
                            Text(text = "Search by crypto", color = Color.Gray, fontSize = 14.sp)
                        }
                        innerTextField()
                    },
                    modifier = Modifier.weight(1f)
                )
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        // Asset List Section
        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
        ) {
            Text(
                text = "All assets with balance",
                color = Color.Gray,
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium,
                modifier = Modifier.padding(bottom = 10.dp)
            )

            if (filteredAssets.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = if (nonZeroAssets.isEmpty()) "No assets with balance available" else "No matching assets found",
                        color = Color.Gray,
                        fontSize = 14.sp
                    )
                }
            } else {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    filteredAssets.forEach { (symbol, amount, valueUsd) ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onAssetSelected(symbol) }
                                .padding(vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            // Coin Icon
                            Box(modifier = Modifier.size(36.dp)) {
                                if (isStandardCoin(symbol)) {
                                    CryptoIcon(symbol = symbol, modifier = Modifier.size(36.dp))
                                } else {
                                    // Custom placeholder icon matching the photo
                                    Canvas(modifier = Modifier.size(36.dp)) {
                                        drawCircle(color = Color(0xFF151515))
                                        val strokeW = 1.5.dp.toPx()
                                        // Draw subtle lines to look like generic coin icon
                                        drawLine(
                                            color = Color.Gray,
                                            start = Offset(size.width * 0.3f, size.height * 0.4f),
                                            end = Offset(size.width * 0.7f, size.height * 0.4f),
                                            strokeWidth = strokeW
                                        )
                                        drawLine(
                                            color = Color.Gray,
                                            start = Offset(size.width * 0.3f, size.height * 0.5f),
                                            end = Offset(size.width * 0.7f, size.height * 0.5f),
                                            strokeWidth = strokeW
                                        )
                                        drawLine(
                                            color = Color.Gray,
                                            start = Offset(size.width * 0.3f, size.height * 0.6f),
                                            end = Offset(size.width * 0.7f, size.height * 0.6f),
                                            strokeWidth = strokeW
                                        )
                                    }
                                }
                            }
                            Spacer(modifier = Modifier.width(12.dp))
                            
                            // Name & Subtitle
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = symbol,
                                    color = Color.White,
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = getCryptoName(symbol),
                                    color = Color.Gray,
                                    fontSize = 12.sp
                                )
                                if (symbol.contains("_BSC")) {
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Box(
                                        modifier = Modifier
                                            .background(Color(0xFF2C2C2E), shape = RoundedCornerShape(4.dp))
                                            .padding(horizontal = 6.dp, vertical = 2.dp)
                                    ) {
                                        Text(
                                            text = "BSC",
                                            color = Color.LightGray,
                                            fontSize = 9.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                }
                            }

                            // Balance & USD Value (Right side)
                            Column(horizontalAlignment = Alignment.End) {
                                Text(
                                    text = "${String.format("%.8f", amount).trimEnd('0').trimEnd('.')} $symbol",
                                    color = Color.White,
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                val isTradable = prices[symbol] != null
                                if (isTradable) {
                                    Text(
                                        text = "$${String.format("%,.2f", valueUsd)}",
                                        color = Color.Gray,
                                        fontSize = 12.sp
                                    )
                                } else {
                                    Text(
                                        text = "$0.00",
                                        color = Color.Gray,
                                        fontSize = 12.sp
                                    )
                                }
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(40.dp))
                }
            }
        }
    }
}

@Composable
fun XLayerLogo(modifier: Modifier = Modifier) {
    Canvas(modifier = modifier) {
        val w = size.width
        val pad = w * 0.18f
        val innerW = w - 2 * pad
        val rectSize = innerW / 3f
        
        // Original 5-squares checkerboard pattern (OKX Logo) - centered & scaled
        drawRect(Color.White, topLeft = Offset(pad, pad), size = Size(rectSize, rectSize))
        drawRect(Color.White, topLeft = Offset(pad + innerW - rectSize, pad), size = Size(rectSize, rectSize))
        drawRect(Color.White, topLeft = Offset(pad + rectSize, pad + rectSize), size = Size(rectSize, rectSize))
        drawRect(Color.White, topLeft = Offset(pad, pad + innerW - rectSize), size = Size(rectSize, rectSize))
        drawRect(Color.White, topLeft = Offset(pad + innerW - rectSize, pad + innerW - rectSize), size = Size(rectSize, rectSize))
    }
}

fun getWithdrawNetworks(symbol: String): List<WithdrawNetwork> {
    return when (symbol.uppercase()) {
        "SOL" -> listOf(
            WithdrawNetwork("Solana", 0.0011),
            WithdrawNetwork("X Layer", 0.00002, badge = "xSOL supported")
        )
        "BTC" -> listOf(
            WithdrawNetwork("Bitcoin", 0.0005),
            WithdrawNetwork("Lightning", 0.00001)
        )
        "ETH" -> listOf(
            WithdrawNetwork("Ethereum (ERC20)", 0.003),
            WithdrawNetwork("Arbitrum One", 0.0001),
            WithdrawNetwork("Optimism", 0.0001)
        )
        "USDT" -> listOf(
            WithdrawNetwork("X Layer (USDT&USDT0)", 0.1, arrivalTime = "~ 1 minute", minDeposit = "0.01 USDT"),
            WithdrawNetwork("Tron (TRC20)", 1.0, arrivalTime = "~ 1 minute", minDeposit = "0.01 USDT"),
            WithdrawNetwork("Ethereum (ERC20)", 5.0, arrivalTime = "~ 7 minutes", minDeposit = "0.01 USDT"),
            WithdrawNetwork("Aptos", 0.1, arrivalTime = "~ 1 minute", minDeposit = "0.01 USDT"),
            WithdrawNetwork("Arbitrum One (USDT0)", 0.5, arrivalTime = "~ 18 minutes", minDeposit = "0.01 USDT"),
            WithdrawNetwork("Avalanche C-Chain", 0.5, arrivalTime = "~ 1 minute", minDeposit = "0.01 USDT"),
            WithdrawNetwork("Berachain (USDT0)", 0.1, arrivalTime = "~ 1 minute", minDeposit = "0.01 USDT"),
            WithdrawNetwork("Monad (USDT0)", 0.01, arrivalTime = "~ 1 minute", minDeposit = "0.00000001 USDT"),
            WithdrawNetwork("Optimism (USDT&USDT0)", 0.5, arrivalTime = "~ 23 minutes", minDeposit = "0.01 USDT")
        )
        "BNB" -> listOf(
            WithdrawNetwork("BNB Smart Chain (BEP20)", 0.0005)
        )
        "TRX" -> listOf(
            WithdrawNetwork("TRON (TRC20)", 1.0)
        )
        "USDC" -> listOf(
            WithdrawNetwork("Ethereum (ERC20)", 5.0),
            WithdrawNetwork("Solana", 1.0),
            WithdrawNetwork("Polygon", 1.0)
        )
        else -> listOf(
            WithdrawNetwork("${symbol} Network", 0.0)
        )
    }
}

@Composable
fun WithdrawSelectNetworkScreen(
    symbol: String,
    prices: Map<String, Double>,
    onBack: () -> Unit,
    onNetworkSelected: (WithdrawNetwork) -> Unit
) {
    val networks = remember(symbol) { getWithdrawNetworks(symbol) }
    val price = prices[symbol] ?: 0.0

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(PureBlack)
            .statusBarsPadding()
    ) {
        // Header
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp)
                .padding(horizontal = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                ChevronLeftIcon(
                    color = Color.White,
                    modifier = Modifier.size(24.dp)
                )
            }
            Text(
                text = "Select network",
                color = Color.White,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
                modifier = Modifier.weight(1f)
            )
            Spacer(modifier = Modifier.width(48.dp)) // Equalizer space to center-align title
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Not sure which network to choose banner
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color(0xFF151515), shape = RoundedCornerShape(12.dp))
                    .border(0.5.dp, Color(0xFF2C2C2E), shape = RoundedCornerShape(12.dp))
                    .padding(16.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.Top,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    InfoIcon(
                        color = Color.White,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Not sure which network to choose?",
                            color = Color.White,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Make sure it matches the network on the platform or wallet you are withdrawing from.",
                            color = Color.Gray,
                            fontSize = 12.sp,
                            lineHeight = 16.sp
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                        Text(
                            text = "Learn more →",
                            color = Color.White,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.clickable { /* Learn more link */ }
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Headers
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(text = "Network", color = Color.Gray, fontSize = 12.sp, fontWeight = FontWeight.Medium)
                val rightHeader = if (symbol.uppercase() == "USDT") "Arrival time/Min deposit" else "Network fee"
                Text(text = rightHeader, color = Color.Gray, fontSize = 12.sp, fontWeight = FontWeight.Medium)
            }

            // Networks List
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                networks.forEach { network ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onNetworkSelected(network) }
                            .padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        // Left side: Icon + Name/Badge
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Box(modifier = Modifier.size(36.dp)) {
                                val iconName = when {
                                    network.name.contains("X Layer", ignoreCase = true) -> "X Layer"
                                    network.name.contains("Tron", ignoreCase = true) -> "Tron"
                                    network.name.contains("Ethereum", ignoreCase = true) -> "Ethereum"
                                    network.name.contains("Aptos", ignoreCase = true) -> "Aptos"
                                    network.name.contains("Arbitrum", ignoreCase = true) -> "Arbitrum"
                                    network.name.contains("Avalanche", ignoreCase = true) -> "Avalanche"
                                    network.name.contains("Berachain", ignoreCase = true) -> "Berachain"
                                    network.name.contains("Monad", ignoreCase = true) -> "Monad"
                                    network.name.contains("Optimism", ignoreCase = true) -> "Optimism"
                                    network.name.contains("Solana", ignoreCase = true) -> "Solana"
                                    else -> network.name
                                }
                                NetworkIcon(symbol = iconName, modifier = Modifier.size(36.dp))
                            }

                            Column {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Text(
                                        text = network.name,
                                        color = Color.White,
                                        fontSize = 15.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                    if (network.badge != null) {
                                        Box(
                                            modifier = Modifier
                                                .background(Color(0xFF2C2C2E), shape = RoundedCornerShape(4.dp))
                                                .padding(horizontal = 6.dp, vertical = 2.dp)
                                        ) {
                                            Text(
                                                text = network.badge,
                                                color = Color.LightGray,
                                                fontSize = 9.sp,
                                                fontWeight = FontWeight.Bold
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        // Right side: Fees or Arrival time/Min deposit
                        Column(horizontalAlignment = Alignment.End) {
                            if (symbol.uppercase() == "USDT" && network.arrivalTime != null) {
                                Text(
                                    text = network.arrivalTime,
                                    color = Color.White,
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = network.minDeposit ?: "0.01 USDT",
                                    color = Color.Gray,
                                    fontSize = 11.sp
                                )
                            } else {
                                val feeFormatted = if (network.feeAmount == 0.0) "Free" else "${String.format("%.5f", network.feeAmount).trimEnd('0').trimEnd('.')} $symbol"
                                Text(
                                    text = feeFormatted,
                                    color = Color.White,
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                if (network.feeAmount > 0.0 && price > 0.0) {
                                    Text(
                                        text = "$${String.format("%,.4f", network.feeAmount * price)}",
                                        color = Color.Gray,
                                        fontSize = 12.sp
                                    )
                                } else {
                                    Text(
                                        text = "$0.00",
                                        color = Color.Gray,
                                        fontSize = 12.sp
                                    )
                                }
                            }
                        }
                    }
                }
            }
            Spacer(modifier = Modifier.height(30.dp))
        }
    }
}

@Composable
fun WithdrawAddressScreen(
    symbol: String,
    selectedNetwork: WithdrawNetwork?,
    onBack: () -> Unit,
    onNext: (String) -> Unit
) {
    var address by remember { mutableStateOf("") }
    
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(PureBlack)
            .statusBarsPadding()
    ) {
        // Header
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp)
                .padding(horizontal = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                ChevronLeftIcon(
                    color = Color.White,
                    modifier = Modifier.size(24.dp)
                )
            }
            Text(
                text = "Enter address",
                color = Color.White,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
                modifier = Modifier.weight(1f)
            )
            IconButton(onClick = { /* Scan QR */ }) {
                Icon(
                    imageVector = Icons.Default.QrCodeScanner,
                    contentDescription = "Scan QR",
                    tint = Color.White,
                    modifier = Modifier.size(22.dp)
                )
            }
            IconButton(onClick = { /* More options */ }) {
                Icon(
                    imageVector = Icons.Default.MoreHoriz,
                    contentDescription = "More",
                    tint = Color.White,
                    modifier = Modifier.size(22.dp)
                )
            }
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp)
        ) {
            Spacer(modifier = Modifier.height(12.dp))
            
            // Asset badge: Pill with coin logo + name
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center
            ) {
                Box(
                    modifier = Modifier
                        .background(Color(0xFF1C1C1E), shape = RoundedCornerShape(16.dp))
                        .padding(horizontal = 12.dp, vertical = 6.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        // Coin icon
                        Box(modifier = Modifier.size(16.dp)) {
                            if (symbol == "SOL") {
                                Canvas(modifier = Modifier.size(16.dp)) {
                                    drawRealSolanaLogo(size.width, size.height, size.width / 2f)
                                }
                            } else {
                                Canvas(modifier = Modifier.size(16.dp)) {
                                    drawCircle(color = Color(0xFF2C2C2E))
                                }
                            }
                        }
                        Text(
                            text = getCryptoName(symbol),
                            color = Color.White,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(40.dp))

            // Main huge address placeholder or input text field
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(100.dp),
                contentAlignment = Alignment.Center
            ) {
                androidx.compose.foundation.text.BasicTextField(
                    value = address,
                    onValueChange = { address = it },
                    textStyle = androidx.compose.ui.text.TextStyle(
                        color = Color.White,
                        fontSize = if (address.isEmpty()) 36.sp else 18.sp,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("withdraw_address_huge_input"),
                    cursorBrush = androidx.compose.ui.graphics.SolidColor(OkxGreen),
                    decorationBox = { innerTextField ->
                        Box(
                            modifier = Modifier.fillMaxWidth(),
                            contentAlignment = Alignment.Center
                        ) {
                            if (address.isEmpty()) {
                                Text(
                                    text = "Address",
                                    color = Color(0xFF444446),
                                    fontSize = 36.sp,
                                    fontWeight = FontWeight.Bold,
                                    textAlign = TextAlign.Center
                                )
                            }
                            innerTextField()
                        }
                    }
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Pills: Address book, Paste
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Address Book Pill
                Box(
                    modifier = Modifier
                        .background(Color(0xFF1C1C1E), shape = RoundedCornerShape(16.dp))
                        .clickable { /* Address book clicked */ }
                        .padding(horizontal = 14.dp, vertical = 8.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.ContactMail,
                            contentDescription = "Address Book",
                            tint = Color.LightGray,
                            modifier = Modifier.size(14.dp)
                        )
                        Text(
                            text = "Address book",
                            color = Color.White,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                Spacer(modifier = Modifier.width(12.dp))

                // Paste Pill
                Box(
                    modifier = Modifier
                        .background(Color(0xFF1C1C1E), shape = RoundedCornerShape(16.dp))
                        .clickable {
                            address = "3NpoyjVA9faxhzSj9LXCo93FB9VyTooTAdBeM83aGkYq"
                        }
                        .padding(horizontal = 14.dp, vertical = 8.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.ContentPaste,
                            contentDescription = "Paste",
                            tint = Color.LightGray,
                            modifier = Modifier.size(14.dp)
                        )
                        Text(
                            text = "Paste",
                            color = Color.White,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(40.dp))
            Divider(color = Color(0xFF1C1C1E), modifier = Modifier.fillMaxWidth())
            Spacer(modifier = Modifier.height(20.dp))

            // Recent Withdrawals
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Recent withdrawals",
                    color = Color.White,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold
                )
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(2.dp),
                    modifier = Modifier.clickable { /* View all */ }
                ) {
                    Text(
                        text = "View all",
                        color = Color.Gray,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium
                    )
                    ChevronRightIcon(color = Color.Gray, modifier = Modifier.size(12.dp))
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Recent address list item
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable {
                        address = "3NpoyjVA9faxhzSj9LXCo93FB9VyTooTAdBeM83aGkYq"
                    }
                    .padding(vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .background(Color(0xFF1C1C1E), shape = CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.AccountBalanceWallet,
                        contentDescription = "Wallet",
                        tint = Color.LightGray,
                        modifier = Modifier.size(18.dp)
                    )
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "3NpoyjVA9faxhzSj9LXCo93FB9VyTooTAdBeM83aGkYq",
                        color = Color.White,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium,
                        maxLines = 2,
                        lineHeight = 16.sp
                    )
                    Text(
                        text = "Sent on Apr 30, 2026",
                        color = Color.Gray,
                        fontSize = 11.sp,
                        modifier = Modifier.padding(top = 2.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.weight(1f))

            // Next Button
            Button(
                onClick = {
                    if (address.isNotEmpty()) {
                        onNext(address)
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 16.dp)
                    .height(48.dp)
                    .testTag("withdraw_address_next_button"),
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (address.isNotEmpty()) OkxGreen else Color(0xFF1C1C1E)
                ),
                shape = RoundedCornerShape(24.dp)
            ) {
                Text(
                    text = "Next",
                    color = if (address.isNotEmpty()) PureBlack else Color.Gray,
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp
                )
            }
        }
    }
}

@Composable
fun WithdrawDetailsScreen(
    symbol: String,
    selectedNetwork: WithdrawNetwork?,
    preEnteredAddress: String,
    coinBalances: Map<String, Double>,
    prices: Map<String, Double>,
    viewModel: WalletViewModel,
    onBack: () -> Unit,
    onSuccess: (Double) -> Unit
) {
    val isOnline by viewModel.isOnline.collectAsStateWithLifecycle()
    var address by remember { mutableStateOf(preEnteredAddress) }
    var amountText by remember { mutableStateOf("") }
    val availableBalance = coinBalances[symbol] ?: 0.0
    val price = prices[symbol] ?: 0.0

    val networkName = selectedNetwork?.name ?: "${getCryptoName(symbol)} Network"
    val fee = selectedNetwork?.feeAmount ?: 0.0

    val amountDouble = amountText.toDoubleOrNull() ?: 0.0
    val receiveAmount = (amountDouble - fee).coerceAtLeast(0.0)

    var showError by remember { mutableStateOf<String?>(null) }
    var showSecurityDialog by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(PureBlack)
            .statusBarsPadding()
    ) {
        // Header
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp)
                .padding(horizontal = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                ChevronLeftIcon(
                    color = Color.White,
                    modifier = Modifier.size(24.dp)
                )
            }
            Text(
                text = "Withdraw $symbol",
                color = Color.White,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.weight(1f)
            )
            IconButton(onClick = { /* Help */ }) {
                HelpOutlineIcon(
                    color = Color.White,
                    modifier = Modifier.size(20.dp)
                )
            }
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            // Address Section
            Column {
                Text(
                    text = "Address",
                    color = Color.White,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color(0xFF1C1C1E), shape = RoundedCornerShape(12.dp))
                        .padding(horizontal = 16.dp, vertical = 14.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = address,
                        color = Color.White,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier.weight(1f)
                    )
                }
            }

            // Network Section
            Column {
                Text(
                    text = "Withdrawal network",
                    color = Color.White,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color(0xFF1C1C1E), shape = RoundedCornerShape(12.dp))
                        .clickable { onBack() } // Clicking network goes back to SelectNetwork/EnterAddress screen
                        .padding(horizontal = 16.dp, vertical = 14.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(text = networkName, color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Medium)
                        Text(text = "Tap to change network", color = Color.Gray, fontSize = 12.sp)
                    }
                    ChevronRightIcon(color = Color.Gray, modifier = Modifier.size(18.dp))
                }
            }

            // Amount Section
            Column {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Amount",
                        color = Color.White,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "Available: ${String.format("%.8f", availableBalance).trimEnd('0').trimEnd('.')} $symbol",
                        color = Color.Gray,
                        fontSize = 13.sp
                    )
                }
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = amountText,
                    onValueChange = { newValue ->
                        if (newValue.isEmpty() || newValue.toDoubleOrNull() != null || newValue.endsWith(".")) {
                            amountText = newValue
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("withdraw_amount_input"),
                    placeholder = { Text("Min 0.001", color = Color.Gray, fontSize = 14.sp) },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White,
                        focusedBorderColor = OkxGreen,
                        unfocusedBorderColor = Color(0xFF1C1C1E),
                        focusedContainerColor = Color(0xFF1C1C1E),
                        unfocusedContainerColor = Color(0xFF1C1C1E)
                    ),
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                        keyboardType = KeyboardType.Decimal
                    ),
                    trailingIcon = {
                        TextButton(
                            onClick = { amountText = availableBalance.toString() }
                        ) {
                            Text("Max", color = OkxGreen, fontWeight = FontWeight.Bold)
                        }
                    }
                )
            }

            // Transaction Details Card
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = Color(0xFF151515)),
                shape = RoundedCornerShape(12.dp)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("Transaction fee", color = Color.Gray, fontSize = 13.sp)
                        Text("${String.format("%.4f", fee)} $symbol", color = Color.White, fontSize = 13.sp)
                    }
                    Divider(color = Color(0xFF2C2C2E))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("You will receive", color = Color.Gray, fontSize = 13.sp)
                        Column(horizontalAlignment = Alignment.End) {
                            Text(
                                text = "${String.format("%.8f", receiveAmount).trimEnd('0').trimEnd('.')} $symbol",
                                color = Color.White,
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold
                            )
                            if (price > 0.0) {
                                Text(
                                    text = "~ $${String.format("%,.2f", receiveAmount * price)} USD",
                                    color = Color.Gray,
                                    fontSize = 11.sp
                                )
                            }
                        }
                    }
                }
            }

            // Error Display
            if (showError != null) {
                Text(
                    text = showError ?: "",
                    color = Color.Red,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier.fillMaxWidth()
                )
            }

            Spacer(modifier = Modifier.height(20.dp))

            if (!isOnline) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color(0xFF321414), shape = RoundedCornerShape(8.dp))
                        .border(1.dp, Color(0xFF801A1A), shape = RoundedCornerShape(8.dp))
                        .padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.CloudOff,
                        contentDescription = "Offline Mode",
                        tint = Color(0xFFE57373),
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Unable to connect to the internet. Try again later.",
                        color = Color(0xFFFFCDD2),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Normal
                    )
                }
                Spacer(modifier = Modifier.height(16.dp))
            }

            // Withdraw Button
            Button(
                enabled = isOnline,
                onClick = {
                    if (address.isEmpty()) {
                        showError = "Please enter a valid withdrawal address."
                        return@Button
                    }
                    if (amountDouble <= 0.0) {
                        showError = "Please enter an amount greater than 0."
                        return@Button
                    }
                    if (amountDouble > availableBalance) {
                        showError = "Insufficient balance. Available: $availableBalance $symbol"
                        return@Button
                    }
                    if (amountDouble <= fee) {
                        showError = "Amount must be greater than transaction fee: $fee $symbol"
                        return@Button
                    }

                    showError = null
                    showSecurityDialog = true
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
                    .testTag("withdraw_confirm_button"),
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (address.isNotEmpty() && amountDouble > 0.0 && amountDouble <= availableBalance) OkxGreen else Color(0xFF2C2C2E),
                    disabledContainerColor = Color(0xFF1C1C1E),
                    disabledContentColor = Color.Gray
                ),
                shape = RoundedCornerShape(24.dp)
            ) {
                Text(
                    text = "Withdraw",
                    color = if (isOnline && address.isNotEmpty() && amountDouble > 0.0 && amountDouble <= availableBalance) PureBlack else Color.Gray,
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp
                )
            }
        }
    }

    // Custom Biometric & PIN Security Key verification dialog
    if (showSecurityDialog) {
        PasskeySecurityDialog(
            onCancel = { showSecurityDialog = false },
            onVerified = {
                showSecurityDialog = false
                viewModel.withdrawBalance(
                    symbol = symbol,
                    amount = amountDouble,
                    address = address,
                    network = networkName,
                    onSuccess = {
                        onSuccess(amountDouble)
                    },
                    onError = { err ->
                        showError = err
                    }
                )
            }
        )
    }
}

fun getSavedPasskey(context: android.content.Context): String {
    val prefs = context.getSharedPreferences("okx_settings", android.content.Context.MODE_PRIVATE)
    return prefs.getString("okx_passkey_pin", "1234") ?: "1234"
}

fun savePasskey(context: android.content.Context, pin: String) {
    val prefs = context.getSharedPreferences("okx_settings", android.content.Context.MODE_PRIVATE)
    prefs.edit().putString("okx_passkey_pin", pin).apply()
}

@Composable
fun PasskeySecurityDialog(
    onCancel: () -> Unit,
    onVerified: () -> Unit
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val savedPin = remember { getSavedPasskey(context) }
    val km = remember { context.getSystemService(Context.KEYGUARD_SERVICE) as KeyguardManager }
    val isSecure = remember { km.isKeyguardSecure }

    var isVerified by remember { mutableStateOf(false) }
    var usePinMode by remember { mutableStateOf(false) }
    var pinText by remember { mutableStateOf("") }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            isVerified = true
            onVerified()
        } else {
            errorMessage = "Device authentication failed or cancelled."
        }
    }

    val triggerScreenLock = {
        if (isSecure) {
            val intent = km.createConfirmDeviceCredentialIntent(
                "OKX Withdrawal Verification",
                "Please authenticate using your phone's screen lock (PIN, Pattern, or Password) to authorize this withdrawal."
            )
            if (intent != null) {
                launcher.launch(intent)
            }
        } else {
            usePinMode = true
            errorMessage = "No device screen lock set. Please use your OKX Passkey PIN."
        }
    }

    // Automatically trigger real device lock on dialog show
    LaunchedEffect(Unit) {
        if (isSecure) {
            triggerScreenLock()
        } else {
            usePinMode = true
        }
    }

    // Trigger PIN verification when 4 digits of custom Passkey PIN are entered
    LaunchedEffect(pinText) {
        if (pinText.length == 4) {
            errorMessage = null
            if (pinText == savedPin) {
                isVerified = true
                delay(150) // snappy feedback
                onVerified()
            } else {
                errorMessage = "Incorrect Passkey PIN. Please try again."
                pinText = ""
            }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.85f))
            .clickable(enabled = false) {}, // Prevent clicks outside
        contentAlignment = Alignment.BottomCenter
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 24.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF151515)),
            shape = RoundedCornerShape(24.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Header badge or key logo
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(
                        imageVector = Icons.Default.VpnKey,
                        contentDescription = "Passkey Logo",
                        tint = OkxGreen,
                        modifier = Modifier.size(20.dp)
                    )
                    Text(
                        text = "OKX Passkey Verification",
                        color = Color.Gray,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                if (!usePinMode) {
                    // Fingerprint / Device Screen Lock Mode
                    Text(
                        text = "Verify Phone Lock Screen",
                        color = Color.White,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Please unlock using your device's screen password, pattern, PIN, or biometric lock to securely authorize this withdrawal.",
                        color = Color.Gray,
                        fontSize = 13.sp,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(horizontal = 12.dp)
                    )

                    Spacer(modifier = Modifier.height(40.dp))

                    // Phone lock / Fingerprint touch target
                    Box(
                        modifier = Modifier
                            .size(100.dp)
                            .background(
                                color = if (isVerified) Color(0xFF1C3A27) else Color(0xFF2C2C2E),
                                shape = CircleShape
                            )
                            .clickable(enabled = !isVerified) {
                                triggerScreenLock()
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = if (isVerified) Icons.Default.Check else Icons.Default.ScreenLockPortrait,
                            contentDescription = "Screen Lock Icon",
                            tint = if (isVerified) OkxGreen else Color.White,
                            modifier = Modifier.size(48.dp)
                        )
                    }

                    Spacer(modifier = Modifier.height(24.dp))

                    Text(
                        text = when {
                            isVerified -> "Lock verified successfully!"
                            else -> "Tap icon to open phone credentials prompt"
                        },
                        color = if (isVerified) OkxGreen else Color.LightGray,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold
                    )

                    Spacer(modifier = Modifier.height(40.dp))

                    if (errorMessage != null) {
                        Text(
                            text = errorMessage ?: "",
                            color = Color.Red,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.padding(horizontal = 16.dp)
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        TextButton(onClick = onCancel) {
                            Text("Cancel", color = Color.Gray, fontWeight = FontWeight.Bold)
                        }
                        Button(
                            onClick = { usePinMode = true },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2C2C2E)),
                            shape = RoundedCornerShape(16.dp)
                        ) {
                            Text("Use OKX Custom PIN", color = Color.White)
                        }
                    }
                } else {
                    // PIN Mode
                    Text(
                        text = "Enter OKX Passkey PIN",
                        color = Color.White,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Enter your custom 4-digit OKX Passkey PIN set up via double-tapping the Explore tab.",
                        color = Color.Gray,
                        fontSize = 13.sp,
                        textAlign = TextAlign.Center
                    )

                    Spacer(modifier = Modifier.height(30.dp))

                    // PIN Dots
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        for (i in 0 until 4) {
                            val active = i < pinText.length
                            Box(
                                modifier = Modifier
                                    .size(16.dp)
                                    .background(
                                        color = if (isVerified) Color(0xFF39D353) else if (active) Color.White else Color.Transparent,
                                        shape = CircleShape
                                    )
                                    .border(
                                        width = 2.dp,
                                        color = if (isVerified) Color(0xFF39D353) else Color.Gray,
                                        shape = CircleShape
                                    )
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    if (errorMessage != null) {
                        Text(
                            text = errorMessage ?: "",
                            color = Color.Red,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold
                        )
                    } else {
                        Spacer(modifier = Modifier.height(16.dp))
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    if (isVerified) {
                        Text(
                            text = "PIN Verified successfully!",
                            color = OkxGreen,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(180.dp))
                    } else {
                        // Custom Number Keyboard layout for authentic locked code entry
                        Column(
                            verticalArrangement = Arrangement.spacedBy(12.dp),
                            modifier = Modifier.fillMaxWidth(0.85f)
                        ) {
                            val keys = listOf(
                                listOf("1", "2", "3"),
                                listOf("4", "5", "6"),
                                listOf("7", "8", "9"),
                                listOf("C", "0", "⌫")
                            )
                            for (row in keys) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    for (key in row) {
                                        Box(
                                            modifier = Modifier
                                                .size(width = 72.dp, height = 48.dp)
                                                .background(Color(0xFF2C2C2E), shape = RoundedCornerShape(8.dp))
                                                .clickable {
                                                    errorMessage = null
                                                    when (key) {
                                                        "⌫" -> {
                                                            if (pinText.isNotEmpty()) {
                                                                pinText = pinText.dropLast(1)
                                                            }
                                                        }
                                                        "C" -> pinText = ""
                                                        else -> {
                                                            if (pinText.length < 4) {
                                                                pinText += key
                                                            }
                                                        }
                                                    }
                                                },
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Text(
                                                text = key,
                                                color = Color.White,
                                                fontSize = 18.sp,
                                                fontWeight = FontWeight.Bold
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(24.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        TextButton(onClick = onCancel) {
                            Text("Cancel", color = Color.Gray, fontWeight = FontWeight.Bold)
                        }
                        if (isSecure) {
                            TextButton(onClick = { usePinMode = false; pinText = "" }) {
                                Text("Use Phone Lock", color = OkxGreen, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun PasskeySetupDialog(
    onDismiss: () -> Unit
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    var step by remember { mutableStateOf(1) } // 1: Enter PIN, 2: Confirm PIN, 3: Success
    var pinText1 by remember { mutableStateOf("") }
    var pinText2 by remember { mutableStateOf("") }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(pinText1) {
        if (pinText1.length == 4) {
            delay(200)
            step = 2
        }
    }

    LaunchedEffect(pinText2) {
        if (pinText2.length == 4) {
            delay(200)
            if (pinText1 == pinText2) {
                savePasskey(context, pinText1)
                step = 3
            } else {
                errorMessage = "PINs do not match. Please try again."
                pinText2 = ""
            }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.95f))
            .clickable(enabled = false) {}, // Prevent clicks outside
        contentAlignment = Alignment.Center
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF151515)),
            shape = RoundedCornerShape(24.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Header badge or key logo
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(
                        imageVector = Icons.Default.VpnKey,
                        contentDescription = "Passkey Logo",
                        tint = OkxGreen,
                        modifier = Modifier.size(20.dp)
                    )
                    Text(
                        text = "OKX Passkey Setup",
                        color = Color.Gray,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                if (step == 3) {
                    // Success Screen
                    Icon(
                        imageVector = Icons.Default.CheckCircle,
                        contentDescription = "Success",
                        tint = OkxGreen,
                        modifier = Modifier.size(64.dp)
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = "Passkey Configured!",
                        color = Color.White,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Your custom 4-digit security PIN has been saved and is now active for secure withdrawals.",
                        color = Color.Gray,
                        fontSize = 13.sp,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(horizontal = 12.dp)
                    )
                    Spacer(modifier = Modifier.height(32.dp))
                    Button(
                        onClick = onDismiss,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = OkxGreen),
                        shape = RoundedCornerShape(24.dp)
                    ) {
                        Text("Done", color = PureBlack, fontWeight = FontWeight.Bold)
                    }
                } else {
                    // Enter / Confirm PIN Mode
                    Text(
                        text = if (step == 1) "Create Passkey PIN" else "Confirm Passkey PIN",
                        color = Color.White,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = if (step == 1) 
                            "Enter a new 4-digit PIN of your choice to protect withdrawals."
                        else 
                            "Please re-enter the 4-digit PIN to confirm accuracy.",
                        color = Color.Gray,
                        fontSize = 13.sp,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(horizontal = 12.dp)
                    )

                    Spacer(modifier = Modifier.height(24.dp))

                    // PIN Dots
                    val activePinText = if (step == 1) pinText1 else pinText2
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        for (i in 0 until 4) {
                            val active = i < activePinText.length
                            Box(
                                modifier = Modifier
                                    .size(16.dp)
                                    .background(
                                        color = if (active) Color.White else Color.Transparent,
                                        shape = CircleShape
                                    )
                                    .border(
                                        width = 2.dp,
                                        color = Color.Gray,
                                        shape = CircleShape
                                    )
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    if (errorMessage != null) {
                        Text(
                            text = errorMessage ?: "",
                            color = Color.Red,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold
                        )
                    } else {
                        Spacer(modifier = Modifier.height(16.dp))
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    // Numeric Keyboard
                    Column(
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                        modifier = Modifier.fillMaxWidth(0.85f)
                    ) {
                        val keys = listOf(
                            listOf("1", "2", "3"),
                            listOf("4", "5", "6"),
                            listOf("7", "8", "9"),
                            listOf("C", "0", "⌫")
                        )
                        for (row in keys) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                for (key in row) {
                                    Box(
                                        modifier = Modifier
                                            .size(width = 72.dp, height = 48.dp)
                                            .background(Color(0xFF2C2C2E), shape = RoundedCornerShape(8.dp))
                                            .clickable {
                                                errorMessage = null
                                                when (key) {
                                                    "⌫" -> {
                                                        if (step == 1) {
                                                            if (pinText1.isNotEmpty()) pinText1 = pinText1.dropLast(1)
                                                        } else {
                                                            if (pinText2.isNotEmpty()) pinText2 = pinText2.dropLast(1)
                                                        }
                                                    }
                                                    "C" -> {
                                                        if (step == 1) pinText1 = ""
                                                        else pinText2 = ""
                                                    }
                                                    else -> {
                                                        if (step == 1) {
                                                            if (pinText1.length < 4) pinText1 += key
                                                        } else {
                                                            if (pinText2.length < 4) pinText2 += key
                                                        }
                                                    }
                                                }
                                            },
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            text = key,
                                            color = Color.White,
                                            fontSize = 18.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(24.dp))

                    TextButton(onClick = onDismiss) {
                        Text("Cancel Setup", color = Color.Gray, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

@Composable
fun WithdrawalProcessingScreen(
    symbol: String,
    selectedNetwork: WithdrawNetwork?,
    address: String,
    amount: Double,
    onDone: () -> Unit
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val clipboardManager = androidx.compose.ui.platform.LocalClipboardManager.current
    
    var showCancelConfirmation by remember { mutableStateOf(false) }
    var secondsRemaining by remember { mutableStateOf(30) }
    val isCompleted = secondsRemaining <= 0
    
    val withdrawalTime = remember {
        val sdf = java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.getDefault())
        sdf.format(java.util.Date())
    }

    val displayAddress = remember(address) {
        if (address.length > 15) {
            "${address.take(6)}...${address.takeLast(7)}"
        } else {
            address
        }
    }

    val fullTxHash = "f53b2b41ae3c69e5d533e870130260bdae6827d4ed177f1087de87e9e3ebaebe"
    val displayTxHash = remember(isCompleted) {
        if (isCompleted) {
            "${fullTxHash.take(10)}...${fullTxHash.takeLast(12)}"
        } else {
            "--"
        }
    }

    LaunchedEffect(Unit) {
        while (secondsRemaining > 0) {
            delay(1000)
            secondsRemaining--
        }
    }

    if (showCancelConfirmation) {
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { showCancelConfirmation = false },
            title = { Text("Cancel Withdrawal", color = Color.White) },
            text = { Text("Are you sure you want to cancel this withdrawal request? The assets will be returned to your account balance.", color = Color.Gray) },
            confirmButton = {
                TextButton(
                    onClick = {
                        showCancelConfirmation = false
                        android.widget.Toast.makeText(context, "Withdrawal cancelled successfully", android.widget.Toast.LENGTH_LONG).show()
                        onDone()
                    }
                ) {
                    Text("Yes, Cancel", color = Color(0xFFE2913A), fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showCancelConfirmation = false }) {
                    Text("Keep Request", color = Color.White)
                }
            },
            containerColor = Color(0xFF1E1E1E),
            titleContentColor = Color.White,
            textContentColor = Color.Gray
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(PureBlack)
            .statusBarsPadding()
    ) {
        // Header
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp)
                .padding(horizontal = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onDone) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Back",
                    tint = Color.White,
                    modifier = Modifier.size(24.dp)
                )
            }
            Text(
                text = "Withdrawal Details",
                color = Color.White,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
                modifier = Modifier.weight(1f)
            )
            Spacer(modifier = Modifier.width(48.dp)) // To center the title correctly
        }

        // Scrollable content area
        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
        ) {
            // Large Amount Display
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 32.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "Quantity",
                    color = Color(0xFF9EA3AE),
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "${String.format("%.1f", amount)} $symbol",
                    color = Color.White,
                    fontSize = 30.sp,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(10.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    if (isCompleted) {
                        Icon(
                            imageVector = Icons.Default.CheckCircle,
                            contentDescription = "Completed",
                            tint = Color(0xFF26C281),
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "Withdrawal Completed",
                            color = Color(0xFF26C281),
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Medium
                        )
                    } else {
                        Icon(
                            imageVector = Icons.Default.Schedule,
                            contentDescription = "Under Review",
                            tint = Color(0xFF9EA3AE),
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "Under Review",
                            color = Color.White,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Normal
                        )
                        Spacer(modifier = Modifier.width(16.dp))
                        Text(
                            text = "Cancel",
                            color = Color(0xFFE2913A),
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Medium,
                            modifier = Modifier.clickable {
                                showCancelConfirmation = true
                            }
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Details List
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp),
                verticalArrangement = Arrangement.spacedBy(20.dp)
            ) {
                WithdrawalDetailRow(label = "Withdrawal Account", value = "Funding Account")
                
                val formattedFee = if (selectedNetwork != null) {
                    val feeVal = selectedNetwork.feeAmount
                    if (feeVal % 1.0 == 0.0) feeVal.toInt().toString() else feeVal.toString()
                } else {
                    "1"
                }
                WithdrawalDetailRow(label = "Fees", value = "$formattedFee")
                
                WithdrawalDetailRow(label = "Chain Type", value = selectedNetwork?.name ?: "TRON (TRC20)")
                
                WithdrawalDetailRow(label = "Time", value = withdrawalTime)
                
                WithdrawalDetailRow(
                    label = "Withdrawal Address",
                    value = displayAddress,
                    showCopy = true,
                    onCopyClick = {
                        clipboardManager.setText(AnnotatedString(address))
                        android.widget.Toast.makeText(context, "Address copied to clipboard", android.widget.Toast.LENGTH_SHORT).show()
                    }
                )
                
                WithdrawalDetailRow(
                    label = "Transaction Hash",
                    value = displayTxHash,
                    showCopy = isCompleted,
                    onCopyClick = {
                        if (isCompleted) {
                            clipboardManager.setText(AnnotatedString(fullTxHash))
                            android.widget.Toast.makeText(context, "Transaction hash copied to clipboard", android.widget.Toast.LENGTH_SHORT).show()
                        }
                    }
                )
            }

            Spacer(modifier = Modifier.height(40.dp))
        }

        // Pinned Bottom Button
        Surface(
            color = PureBlack,
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp, vertical = 16.dp)
            ) {
                Button(
                    onClick = {
                        if (isCompleted) {
                            // simulated view in blockchain explorer
                            android.widget.Toast.makeText(context, "Opening Blockchain Explorer...", android.widget.Toast.LENGTH_SHORT).show()
                        }
                        onDone()
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .testTag("withdrawal_details_done_button"),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (isCompleted) Color.White else OkxGreen,
                        contentColor = Color.Black
                    ),
                    shape = RoundedCornerShape(24.dp)
                ) {
                    Text(
                        text = if (isCompleted) "View in Blockchain Explorer" else "Back to Assets",
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp
                    )
                }
            }
        }
    }
}

@Composable
fun WithdrawalDetailRow(
    label: String,
    value: String,
    showCopy: Boolean = false,
    onCopyClick: (() -> Unit)? = null
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            color = Color(0xFF9EA3AE),
            fontSize = 14.sp,
            fontWeight = FontWeight.Normal
        )
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.End
        ) {
            Text(
                text = value,
                color = Color.White,
                fontSize = 14.sp,
                fontWeight = FontWeight.Normal
            )
            if (showCopy && onCopyClick != null) {
                Spacer(modifier = Modifier.width(6.dp))
                IconButton(
                    onClick = onCopyClick,
                    modifier = Modifier.size(24.dp)
                ) {
                    ContentCopyIcon(
                        color = Color(0xFF9EA3AE),
                        modifier = Modifier.size(14.dp)
                    )
                }
            }
        }
    }
}

@Composable
fun TimelineStep(
    title: String,
    time: String,
    subtext: String,
    isCompleted: Boolean,
    isActive: Boolean,
    showConnector: Boolean
) {
    Row(modifier = Modifier.fillMaxWidth()) {
        // Vertical Indicator Bar & Dot
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.width(32.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(16.dp)
                    .background(
                        color = if (isCompleted) Color(0xFF1C3A27) else if (isActive) Color(0xFF3A301C) else Color(0xFF1C1C1E),
                        shape = CircleShape
                    ),
                contentAlignment = Alignment.Center
            ) {
                if (isCompleted) {
                    Icon(
                        imageVector = Icons.Default.Check,
                        contentDescription = "Done",
                        tint = OkxGreen,
                        modifier = Modifier.size(10.dp)
                    )
                } else if (isActive) {
                    CircularProgressIndicator(
                        color = Color(0xFFFFB703),
                        strokeWidth = 1.5.dp,
                        modifier = Modifier.size(10.dp)
                    )
                } else {
                    Box(
                        modifier = Modifier
                            .size(6.dp)
                            .background(Color.Gray, shape = CircleShape)
                    )
                }
            }
            if (showConnector) {
                Box(
                    modifier = Modifier
                        .width(2.dp)
                        .height(60.dp)
                        .background(if (isCompleted) OkxGreen else Color(0xFF1C1C1E))
                )
            }
        }

        // Text labels
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(start = 12.dp, bottom = 20.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = title,
                    color = if (isCompleted) Color.White else if (isActive) Color(0xFFFFB703) else Color.Gray,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = time,
                    color = Color.Gray,
                    fontSize = 11.sp
                )
            }
            Text(
                text = subtext,
                color = Color.Gray,
                fontSize = 12.sp,
                modifier = Modifier.padding(top = 4.dp),
                lineHeight = 16.sp
            )
        }
    }
}

@Composable
fun DetailRow(
    label: String,
    value: String,
    isTruncated: Boolean = false,
    showCopy: Boolean = false,
    isSuccess: Boolean = false
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val clipboardManager = androidx.compose.ui.platform.LocalClipboardManager.current

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(text = label, color = Color.Gray, fontSize = 13.sp)

        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            val displayValue = if (isTruncated && value.length > 20) {
                value.take(12) + "..." + value.takeLast(8)
            } else {
                value
            }

            Text(
                text = displayValue,
                color = if (isSuccess) OkxGreen else Color.White,
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium
            )

            if (showCopy) {
                IconButton(
                    onClick = {
                        clipboardManager.setText(androidx.compose.ui.text.AnnotatedString(value))
                        android.widget.Toast.makeText(context, "Copied to clipboard", android.widget.Toast.LENGTH_SHORT).show()
                    },
                    modifier = Modifier.size(16.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.ContentCopy,
                        contentDescription = "Copy",
                        tint = Color.Gray,
                        modifier = Modifier.size(12.dp)
                    )
                }
            }
        }
    }
}

fun parseColorSafely(hex: String, fallback: Color): Color {
    if (hex.isBlank()) return fallback
    return try {
        val cleanHex = hex.trim().removePrefix("#")
        val colorInt = when (cleanHex.length) {
            6 -> android.graphics.Color.parseColor("#FF$cleanHex") // Add alpha
            8 -> android.graphics.Color.parseColor("#$cleanHex")
            else -> android.graphics.Color.parseColor(hex.trim())
        }
        Color(colorInt)
    } catch (e: Exception) {
        fallback
    }
}

@Composable
fun WithdrawCryptoCustomIcon(color: Color, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .size(40.dp)
            .background(Color.Transparent),
        contentAlignment = Alignment.Center
    ) {
        androidx.compose.foundation.Canvas(modifier = Modifier.size(24.dp)) {
            val w = size.width
            val h = size.height
            
            // Draw a circle with an opening at the top
            val strokeWidth = 2.dp.toPx()
            val path = androidx.compose.ui.graphics.Path().apply {
                addArc(
                    oval = androidx.compose.ui.geometry.Rect(0f, 0f, w, h),
                    startAngleDegrees = -60f,
                    sweepAngleDegrees = 300f
                )
            }
            drawPath(
                path = path,
                color = color,
                style = androidx.compose.ui.graphics.drawscope.Stroke(
                    width = strokeWidth,
                    cap = androidx.compose.ui.graphics.StrokeCap.Round
                )
            )
            
            // Draw the arrow pointing up
            // vertical line in the middle
            drawLine(
                color = color,
                start = androidx.compose.ui.geometry.Offset(w / 2f, h * 0.2f),
                end = androidx.compose.ui.geometry.Offset(w / 2f, h * 0.8f),
                strokeWidth = strokeWidth,
                cap = androidx.compose.ui.graphics.StrokeCap.Round
            )
            // Left arrowhead
            drawLine(
                color = color,
                start = androidx.compose.ui.geometry.Offset(w / 2f, h * 0.2f),
                end = androidx.compose.ui.geometry.Offset(w / 2f - w * 0.25f, h * 0.45f),
                strokeWidth = strokeWidth,
                cap = androidx.compose.ui.graphics.StrokeCap.Round
            )
            // Right arrowhead
            drawLine(
                color = color,
                start = androidx.compose.ui.geometry.Offset(w / 2f, h * 0.2f),
                end = androidx.compose.ui.geometry.Offset(w / 2f + w * 0.25f, h * 0.45f),
                strokeWidth = strokeWidth,
                cap = androidx.compose.ui.graphics.StrokeCap.Round
            )
        }
    }
}

@Composable
fun P2PTradingCustomIcon(color: Color, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .size(40.dp),
        contentAlignment = Alignment.Center
    ) {
        androidx.compose.foundation.Canvas(modifier = Modifier.size(24.dp)) {
            val strokeWidth = 2.dp.toPx()
            
            // Helper to draw a solid person figure
            fun drawPerson(
                cx: Float,
                cyHead: Float,
                rHead: Float,
                shLeft: Float,
                shRight: Float,
                shTop: Float,
                shBottom: Float
            ) {
                // Draw head
                drawCircle(
                    color = color,
                    radius = rHead,
                    center = androidx.compose.ui.geometry.Offset(cx, cyHead)
                )
                // Draw shoulder (rounded top corners, flat bottom)
                val cr = 1.5.dp.toPx()
                val path = androidx.compose.ui.graphics.Path().apply {
                    moveTo(shLeft, shBottom)
                    lineTo(shLeft, shTop + cr)
                    quadraticTo(shLeft, shTop, shLeft + cr, shTop)
                    lineTo(shRight - cr, shTop)
                    quadraticTo(shRight, shTop, shRight, shTop + cr)
                    lineTo(shRight, shBottom)
                    close()
                }
                drawPath(path = path, color = color)
            }

            // 1. Top-Left Person
            drawPerson(
                cx = 6.dp.toPx(),
                cyHead = 5.dp.toPx(),
                rHead = 2.2.dp.toPx(),
                shLeft = 2.dp.toPx(),
                shRight = 10.dp.toPx(),
                shTop = 8.5.dp.toPx(),
                shBottom = 12.dp.toPx()
            )

            // 2. Bottom-Right Person
            drawPerson(
                cx = 18.dp.toPx(),
                cyHead = 17.dp.toPx(),
                rHead = 2.2.dp.toPx(),
                shLeft = 14.dp.toPx(),
                shRight = 22.dp.toPx(),
                shTop = 20.5.dp.toPx(),
                shBottom = 24.dp.toPx()
            )

            // 3. Top-Right Corner Bracket (┐)
            val topRightPath = androidx.compose.ui.graphics.Path().apply {
                moveTo(15.dp.toPx(), 2.dp.toPx())
                lineTo(22.dp.toPx(), 2.dp.toPx())
                lineTo(22.dp.toPx(), 9.dp.toPx())
            }
            drawPath(
                path = topRightPath,
                color = color,
                style = androidx.compose.ui.graphics.drawscope.Stroke(
                    width = strokeWidth,
                    cap = androidx.compose.ui.graphics.StrokeCap.Square,
                    join = androidx.compose.ui.graphics.StrokeJoin.Miter
                )
            )

            // 4. Bottom-Left Corner Bracket (└)
            val bottomLeftPath = androidx.compose.ui.graphics.Path().apply {
                moveTo(2.dp.toPx(), 15.dp.toPx())
                lineTo(2.dp.toPx(), 22.dp.toPx())
                lineTo(9.dp.toPx(), 22.dp.toPx())
            }
            drawPath(
                path = bottomLeftPath,
                color = color,
                style = androidx.compose.ui.graphics.drawscope.Stroke(
                    width = strokeWidth,
                    cap = androidx.compose.ui.graphics.StrokeCap.Square,
                    join = androidx.compose.ui.graphics.StrokeJoin.Miter
                )
            )
        }
    }
}

@Composable
fun WithdrawP2pTradingScreen(onBack: () -> Unit) {
    var isSellTab by remember { mutableStateOf(true) }
    val context = androidx.compose.ui.platform.LocalContext.current
    
    val p2pOffers = remember(isSellTab) {
        if (isSellTab) {
            listOf(
                P2pOffer("CryptoKing", "C", "1,420", "99.2%", 1.00, "USD", "2,500.00 USDT", "$10.00 - $1,000.00", listOf("Bank Transfer", "Revolut")),
                P2pOffer("OKX_Trader_Pro", "O", "540", "97.5%", 0.99, "USD", "10,450.00 USDT", "$50.00 - $3,000.00", listOf("PayPal", "Wise")),
                P2pOffer("SatoshiFever", "S", "3,110", "98.9%", 1.01, "USD", "1,200.00 USDT", "$100.00 - $5,000.00", listOf("Apple Pay", "Zelle")),
                P2pOffer("ZeroFee_Broker", "Z", "890", "96.4%", 0.98, "USD", "7,800.00 USDT", "$20.00 - $1,500.00", listOf("Wire Transfer", "Cash App"))
            )
        } else {
            listOf(
                P2pOffer("Safe_Escrow", "S", "2,150", "99.8%", 1.01, "USD", "4,200.00 USDT", "$50.00 - $2,000.00", listOf("Bank Transfer", "Revolut")),
                P2pOffer("Fast_USDT_Store", "F", "1,120", "98.1%", 1.02, "USD", "8,500.00 USDT", "$10.00 - $5,000.00", listOf("Zelle", "PayPal")),
                P2pOffer("ApexLiquidity", "A", "430", "95.0%", 1.00, "USD", "15,000.00 USDT", "$200.00 - $10,000.00", listOf("Wire Transfer", "Wise"))
            )
        }
    }
    
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(PureBlack)
            .statusBarsPadding()
    ) {
        // Custom Header
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp)
                .padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(
                onClick = onBack,
                modifier = Modifier.size(40.dp)
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Back",
                    tint = Color.White,
                    modifier = Modifier.size(24.dp)
                )
            }
            
            Text(
                text = "P2P trading",
                color = Color.White,
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier
                    .weight(1f)
                    .padding(start = 8.dp)
            )
            
            // Icons on right
            IconButton(onClick = {
                android.widget.Toast.makeText(context, "P2P User Guide", android.widget.Toast.LENGTH_SHORT).show()
            }) {
                Icon(
                    imageVector = Icons.Default.HelpOutline,
                    contentDescription = "Help",
                    tint = Color.White,
                    modifier = Modifier.size(22.dp)
                )
            }
        }
        
        // Buy / Sell Tabs and Currency dropdown
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Text(
                    text = "Buy",
                    color = if (!isSellTab) Color.White else Color.Gray,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.clickable { isSellTab = false }
                )
                Text(
                    text = "Sell",
                    color = if (isSellTab) Color.White else Color.Gray,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.clickable { isSellTab = true }
                )
            }
            
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .background(Color(0xFF1E1E1E), shape = RoundedCornerShape(12.dp))
                    .padding(horizontal = 10.dp, vertical = 6.dp)
                    .clickable {
                        android.widget.Toast.makeText(context, "Currency selection", android.widget.Toast.LENGTH_SHORT).show()
                    }
            ) {
                Text(
                    text = "USD",
                    color = Color.White,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium
                )
                Spacer(modifier = Modifier.width(4.dp))
                Icon(
                    imageVector = Icons.Default.ArrowDropDown,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(16.dp)
                )
            }
        }
        
        // Horizontal divider
        HorizontalDivider(color = Color(0xFF1E1E1E), thickness = 1.dp)
        
        // Filters Row
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            P2pFilterBadge(text = "Amount")
            P2pFilterBadge(text = "All payment methods")
            P2pFilterBadge(text = "Filter")
        }
        
        // Offers List
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = 24.dp)
        ) {
            items(p2pOffers) { offer ->
                P2pOfferItem(offer = offer, isSell = isSellTab) {
                    android.widget.Toast.makeText(
                        context,
                        "P2P Trading simulation active for ${offer.merchant}",
                        android.widget.Toast.LENGTH_SHORT
                    ).show()
                }
            }
        }
    }
}

data class P2pOffer(
    val merchant: String,
    val initial: String,
    val orders: String,
    val rate: String,
    val price: Double,
    val currency: String,
    val available: String,
    val limit: String,
    val payments: List<String>
)

@Composable
fun P2pFilterBadge(text: String) {
    Row(
        modifier = Modifier
            .background(Color(0xFF1E1E1E), shape = RoundedCornerShape(14.dp))
            .padding(horizontal = 12.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = text,
            color = Color.White,
            fontSize = 12.sp,
            fontWeight = FontWeight.Normal
        )
        Spacer(modifier = Modifier.width(4.dp))
        Icon(
            imageVector = Icons.Default.ArrowDropDown,
            contentDescription = null,
            tint = Color.Gray,
            modifier = Modifier.size(14.dp)
        )
    }
}

@Composable
fun P2pOfferItem(offer: P2pOffer, isSell: Boolean, onClick: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 14.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Merchant initial avatar
            Box(
                modifier = Modifier
                    .size(24.dp)
                    .background(Color(0xFF2C2C2E), shape = CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = offer.initial,
                    color = Color.White,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold
                )
            }
            
            Spacer(modifier = Modifier.width(8.dp))
            
            Text(
                text = offer.merchant,
                color = Color.White,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold
            )
            
            Spacer(modifier = Modifier.width(4.dp))
            
            // Checkmark verified badge
            Box(
                modifier = Modifier
                    .size(14.dp)
                    .background(Color(0xFF1D9BF0), shape = CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Check,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(10.dp)
                )
            }
            
            Spacer(modifier = Modifier.weight(1f))
            
            Text(
                text = "${offer.orders} orders | ${offer.rate}",
                color = Color.Gray,
                fontSize = 12.sp
            )
        }
        
        Spacer(modifier = Modifier.height(8.dp))
        
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = String.format("%.2f", offer.price),
                        color = Color.White,
                        fontSize = 24.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = offer.currency,
                        color = Color.Gray,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Normal
                    )
                }
                
                Spacer(modifier = Modifier.height(4.dp))
                
                Text(
                    text = "Available: ${offer.available}",
                    color = Color.Gray,
                    fontSize = 12.sp
                )
                Text(
                    text = "Limit: ${offer.limit}",
                    color = Color.Gray,
                    fontSize = 12.sp
                )
            }
            
            Button(
                onClick = onClick,
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (isSell) Color(0xFFEF5350) else Color(0xFF26C281),
                    contentColor = Color.White
                ),
                shape = RoundedCornerShape(18.dp),
                modifier = Modifier
                    .height(36.dp)
                    .width(80.dp),
                contentPadding = PaddingValues(0.dp)
            ) {
                Text(
                    text = if (isSell) "Sell" else "Buy",
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp
                )
            }
        }
        
        Spacer(modifier = Modifier.height(8.dp))
        
        // Payment methods tags
        Row(
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            offer.payments.forEach { payment ->
                Box(
                    modifier = Modifier
                        .background(Color(0xFF1E1E1E), shape = RoundedCornerShape(4.dp))
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                ) {
                    Text(
                        text = payment,
                        color = Color.LightGray,
                        fontSize = 10.sp
                    )
                }
            }
        }
    }
}

// ------------------------------------------------------------------------
// RECENT TRANSACTIONS / HISTORY SCREEN & DETAIL VIEW
// ------------------------------------------------------------------------

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HistoryScreen(
    transactions: List<TransactionEntity>,
    onBack: () -> Unit,
    onTransactionClick: (TransactionEntity) -> Unit
) {
    val context = LocalContext.current
    var selectedTypeFilter by remember { mutableStateOf("All") }
    var selectedAssetFilter by remember { mutableStateOf("All") }
    var selectedDateFilter by remember { mutableStateOf("Last 90 days") }

    var showTypeMenu by remember { mutableStateOf(false) }
    var showAssetMenu by remember { mutableStateOf(false) }
    var showDateMenu by remember { mutableStateOf(false) }

    val availableAssets = remember(transactions) {
        listOf("All") + transactions.map { it.symbol.uppercase() }.distinct().sorted()
    }
    val availableTypes = listOf("All", "Withdrawal", "Deposit", "Received from trading account", "Transferred to trading account", "Fulfill an order", "Place an order")
    val availableDates = listOf("Last 90 days", "Last 30 days", "Last 7 days", "All time")

    val filteredTransactions = remember(transactions, selectedTypeFilter, selectedAssetFilter, selectedDateFilter) {
        transactions.filter { tx ->
            val matchType = when (selectedTypeFilter) {
                "All" -> true
                else -> tx.type.contains(selectedTypeFilter, ignoreCase = true)
            }
            val matchAsset = when (selectedAssetFilter) {
                "All" -> true
                else -> tx.symbol.equals(selectedAssetFilter, ignoreCase = true)
            }
            matchType && matchAsset
        }
    }

    val groupedTransactions = remember(filteredTransactions) {
        filteredTransactions.groupBy { it.monthYear }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(PureBlack)
            .statusBarsPadding()
            .navigationBarsPadding()
    ) {
        // Top Header
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            IconButton(
                onClick = onBack,
                modifier = Modifier.size(36.dp)
            ) {
                ChevronLeftIcon(color = Color.White, modifier = Modifier.size(24.dp))
            }

            Text(
                text = "History",
                color = Color.White,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold
            )

            IconButton(
                onClick = {
                    android.widget.Toast.makeText(context, "Exporting transaction history...", android.widget.Toast.LENGTH_SHORT).show()
                },
                modifier = Modifier.size(36.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.FileDownload,
                    contentDescription = "Export History",
                    tint = Color.White,
                    modifier = Modifier.size(22.dp)
                )
            }
        }

        Spacer(modifier = Modifier.height(4.dp))

        // Filter chips row
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // Type Filter
            Box {
                Row(
                    modifier = Modifier
                        .background(Color(0xFF1C1C1E), shape = RoundedCornerShape(18.dp))
                        .clickable { showTypeMenu = true }
                        .padding(horizontal = 14.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = if (selectedTypeFilter == "All") "Type" else selectedTypeFilter,
                        color = Color.White,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Icon(
                        imageVector = Icons.Default.ArrowDropDown,
                        contentDescription = null,
                        tint = Color.LightGray,
                        modifier = Modifier.size(16.dp)
                    )
                }
                DropdownMenu(
                    expanded = showTypeMenu,
                    onDismissRequest = { showTypeMenu = false },
                    modifier = Modifier.background(Color(0xFF2C2C2E))
                ) {
                    availableTypes.forEach { type ->
                        DropdownMenuItem(
                            text = { Text(type, color = Color.White, fontSize = 13.sp) },
                            onClick = {
                                selectedTypeFilter = type
                                showTypeMenu = false
                            }
                        )
                    }
                }
            }

            // Asset Filter
            Box {
                Row(
                    modifier = Modifier
                        .background(Color(0xFF1C1C1E), shape = RoundedCornerShape(18.dp))
                        .clickable { showAssetMenu = true }
                        .padding(horizontal = 14.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = if (selectedAssetFilter == "All") "Asset" else selectedAssetFilter,
                        color = Color.White,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Icon(
                        imageVector = Icons.Default.ArrowDropDown,
                        contentDescription = null,
                        tint = Color.LightGray,
                        modifier = Modifier.size(16.dp)
                    )
                }
                DropdownMenu(
                    expanded = showAssetMenu,
                    onDismissRequest = { showAssetMenu = false },
                    modifier = Modifier.background(Color(0xFF2C2C2E))
                ) {
                    availableAssets.forEach { asset ->
                        DropdownMenuItem(
                            text = { Text(asset, color = Color.White, fontSize = 13.sp) },
                            onClick = {
                                selectedAssetFilter = asset
                                showAssetMenu = false
                            }
                        )
                    }
                }
            }

            // Date Filter
            Box {
                Row(
                    modifier = Modifier
                        .background(Color(0xFF1C1C1E), shape = RoundedCornerShape(18.dp))
                        .clickable { showDateMenu = true }
                        .padding(horizontal = 14.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Date: $selectedDateFilter",
                        color = Color.White,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Icon(
                        imageVector = Icons.Default.ArrowDropDown,
                        contentDescription = null,
                        tint = Color.LightGray,
                        modifier = Modifier.size(16.dp)
                    )
                }
                DropdownMenu(
                    expanded = showDateMenu,
                    onDismissRequest = { showDateMenu = false },
                    modifier = Modifier.background(Color(0xFF2C2C2E))
                ) {
                    availableDates.forEach { date ->
                        DropdownMenuItem(
                            text = { Text(date, color = Color.White, fontSize = 13.sp) },
                            onClick = {
                                selectedDateFilter = date
                                showDateMenu = false
                            }
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        if (filteredTransactions.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "No transactions found",
                    color = Color.Gray,
                    fontSize = 14.sp
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .padding(horizontal = 16.dp)
            ) {
                groupedTransactions.forEach { (monthYear, txList) ->
                    item(key = "header_$monthYear") {
                        Text(
                            text = monthYear,
                            color = Color.White,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(top = 16.dp, bottom = 12.dp)
                        )
                    }

                    items(txList, key = { it.id }) { tx ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onTransactionClick(tx) }
                                .padding(vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            // Coin original icon
                            CryptoIcon(
                                symbol = tx.symbol,
                                modifier = Modifier.size(38.dp)
                            )

                            Spacer(modifier = Modifier.width(12.dp))

                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = tx.type,
                                    color = Color.White,
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    maxLines = 2,
                                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = tx.formattedDate,
                                    color = Color(0xFF8E8E93),
                                    fontSize = 12.sp
                                )
                            }

                            Spacer(modifier = Modifier.width(8.dp))

                            Row(verticalAlignment = Alignment.CenterVertically) {
                                val sign = if (tx.isPositive) "+" else "-"
                                val amountStr = String.format(java.util.Locale.US, "%.8f", Math.abs(tx.amount)).trimEnd('0').trimEnd('.')
                                Text(
                                    text = "$sign$amountStr ${tx.symbol}",
                                    color = if (tx.isPositive) Color(0xFF26C281) else Color.White,
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                ChevronRightIcon(
                                    color = Color(0xFF8E8E93),
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }
                    }
                }

                item {
                    Spacer(modifier = Modifier.height(24.dp))
                }
            }
        }
    }
}

@Composable
fun TransactionDetailScreen(
    transaction: TransactionEntity,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val clipboardManager = androidx.compose.ui.platform.LocalClipboardManager.current

    val titlePrefix = when {
        transaction.type.lowercase().contains("withdraw") -> "Withdrawn"
        transaction.type.lowercase().contains("deposit") -> "Deposited"
        transaction.type.lowercase().contains("received") -> "Received"
        transaction.type.lowercase().contains("transferred") -> "Transferred"
        else -> transaction.type
    }
    val formattedAmount = String.format(java.util.Locale.US, "%.8f", Math.abs(transaction.amount)).trimEnd('0').trimEnd('.')

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(PureBlack)
            .statusBarsPadding()
            .navigationBarsPadding()
    ) {
        // Top Bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(
                onClick = onBack,
                modifier = Modifier.size(36.dp)
            ) {
                ChevronLeftIcon(color = Color.White, modifier = Modifier.size(24.dp))
            }
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(modifier = Modifier.height(12.dp))

            // Large Coin Icon
            CryptoIcon(
                symbol = transaction.symbol,
                modifier = Modifier.size(52.dp)
            )

            Spacer(modifier = Modifier.height(14.dp))

            // Main Title
            Text(
                text = "$titlePrefix $formattedAmount ${transaction.symbol}",
                color = Color.White,
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(4.dp))

            // USD Equivalent
            Text(
                text = transaction.usdValue,
                color = Color(0xFF8E8E93),
                fontSize = 14.sp,
                fontWeight = FontWeight.Normal
            )

            Spacer(modifier = Modifier.height(32.dp))

            // Status Row
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(26.dp)
                            .background(
                                color = if (transaction.status == "Completed") Color(0xFF26C281) else Color(0xFFFFB300),
                                shape = CircleShape
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Check,
                            contentDescription = null,
                            tint = Color.Black,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(
                        text = "Status",
                        color = Color.White,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                Text(
                    text = transaction.status,
                    color = if (transaction.status == "Completed") Color.White else Color(0xFFFFB300),
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Medium
                )
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Details List
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(20.dp)
            ) {
                // Address Row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.Top
                ) {
                    Text(
                        text = "Address",
                        color = Color.White,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.weight(0.35f)
                    )

                    Row(
                        modifier = Modifier.weight(0.65f),
                        horizontalArrangement = Arrangement.End,
                        verticalAlignment = Alignment.Top
                    ) {
                        Text(
                            text = transaction.address,
                            color = Color.White,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Medium,
                            textAlign = TextAlign.End,
                            modifier = Modifier.weight(1f)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        IconButton(
                            onClick = {
                                clipboardManager.setText(AnnotatedString(transaction.address))
                                android.widget.Toast.makeText(context, "Address copied to clipboard", android.widget.Toast.LENGTH_SHORT).show()
                            },
                            modifier = Modifier.size(20.dp)
                        ) {
                            ContentCopyIcon(color = Color.LightGray, modifier = Modifier.size(14.dp))
                        }
                    }
                }

                // Price Row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = "Price",
                            color = Color.White,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        InfoIcon(color = Color.Gray, modifier = Modifier.size(13.dp))
                    }

                    Text(
                        text = transaction.priceInfo,
                        color = Color.White,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium
                    )
                }

                // Network Row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Network",
                        color = Color.White,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold
                    )

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        CryptoIcon(
                            symbol = transaction.symbol,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = transaction.network,
                            color = Color.White,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }

                // Network Fee Row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Network fee",
                        color = Color.White,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold
                    )

                    Text(
                        text = transaction.networkFee,
                        color = Color.White,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium
                    )
                }

                // Transaction ID Row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Transaction ID",
                        color = Color.White,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold
                    )

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        val displayTxId = if (transaction.txId.length > 12) {
                            "${transaction.txId.take(5)}...${transaction.txId.takeLast(5)}"
                        } else transaction.txId

                        Text(
                            text = displayTxId,
                            color = Color.White,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Medium
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        IconButton(
                            onClick = {
                                clipboardManager.setText(AnnotatedString(transaction.txId))
                                android.widget.Toast.makeText(context, "Transaction ID copied to clipboard", android.widget.Toast.LENGTH_SHORT).show()
                            },
                            modifier = Modifier.size(20.dp)
                        ) {
                            ContentCopyIcon(color = Color.LightGray, modifier = Modifier.size(14.dp))
                        }
                    }
                }

                // Submitted Time Row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Submitted time",
                        color = Color.White,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold
                    )

                    Text(
                        text = transaction.formattedDetailTime,
                        color = Color.White,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium
                    )
                }

                // Reference No Row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Reference no.",
                        color = Color.White,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold
                    )

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = transaction.referenceNo,
                            color = Color.White,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Medium
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        IconButton(
                            onClick = {
                                clipboardManager.setText(AnnotatedString(transaction.referenceNo))
                                android.widget.Toast.makeText(context, "Reference no. copied to clipboard", android.widget.Toast.LENGTH_SHORT).show()
                            },
                            modifier = Modifier.size(20.dp)
                        ) {
                            ContentCopyIcon(color = Color.LightGray, modifier = Modifier.size(14.dp))
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(36.dp))

            // Primary Blockchain Explorer Button
            Button(
                onClick = {
                    android.widget.Toast.makeText(context, "Opening blockchain explorer...", android.widget.Toast.LENGTH_SHORT).show()
                },
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFC2F113)),
                shape = RoundedCornerShape(24.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
            ) {
                Text(
                    text = "View on blockchain explorer",
                    color = Color.Black,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold
                )
            }

            Spacer(modifier = Modifier.height(20.dp))

            // Help link
            Text(
                text = "Why hasn't my transaction arrived?",
                color = Color.White,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.clickable {
                    android.widget.Toast.makeText(context, "Check blockchain network confirmation status.", android.widget.Toast.LENGTH_SHORT).show()
                }
            )

            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}

