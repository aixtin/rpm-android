package com.rpm.player.tv.ui

import android.graphics.Bitmap
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter
import com.rpm.player.core.R
import com.rpm.player.core.auth.AuthClient
import com.rpm.player.core.auth.LoginResponse
import com.rpm.player.core.auth.Provider
import com.rpm.player.core.auth.SocialReturn
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

/**
 * Sign-in on a TV: one QR code per provider, each a `loginUrl` from `auth/social-login`. The person
 * scans one and signs in on the phone; this side polls `auth/poll-session` until one finishes.
 *
 * The codes are not replaced while the screen waits. The accounts service keeps a TV's pending sign-in for
 * 24 hours. Each QR code has a user code under it, which the phone's sign-in page shows too: a person who was
 * sent someone else's QR code sees that it is not their TV.
 */
class SignInFlow(private val auth: AuthClient, private val clock: () -> Long) {

    /** One provider's sign-in. [userCode] is the code to compare with the phone's sign-in page. */
    data class Code(val provider: Provider, val url: String, val sessionId: String, val userCode: String?)

    sealed interface View {
        data object Starting : View
        data class Codes(val codes: List<Code>) : View
        /** The accounts service could not be reached to start; retried every [RETRY_MS]. */
        data object Unreachable : View
    }

    /** Runs until a sign-in finishes, and answers it without adopting it: the caller adopts it, after whatever has
     * to be in place before the app sees a session ([SignInScreen]). Or until cancelled. Network work runs on IO;
     * [show] is called from that context and must only publish state. */
    suspend fun run(show: (View) -> Unit): LoginResponse = withContext(Dispatchers.IO) {
        show(View.Starting)
        val codes = start(show)
        show(View.Codes(codes))
        val shownAt = clock()
        while (true) {
            delay(pollInterval(clock() - shownAt))
            for (code in codes) {
                val said = try {
                    auth.poll(code.sessionId)
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    continue
                }
                if (said.isComplete) return@withContext said.asLogin()
            }
        }
        @Suppress("UNREACHABLE_CODE")
        error("unreachable")
    }

    private suspend fun start(show: (View) -> Unit): List<Code> {
        while (true) {
            try {
                return Provider.entries.map { provider ->
                    val started = auth.startSocial(provider, SocialReturn.DEVICE)
                    Code(provider, started.loginUrl, started.sessionId, started.userCode)
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                show(View.Unreachable)
                delay(RETRY_MS)
            }
        }
    }

    companion object {
        const val RETRY_MS = 10_000L

        /** Every 2 seconds for the first 5 minutes the codes are shown, then every 10, so a screen
         * left open costs the accounts service less. */
        fun pollInterval(sinceShownMs: Long): Long = if (sinceShownMs < 5 * 60_000L) 2_000L else 10_000L
    }
}

/**
 * The first screen: the opening sound as it comes up, the wordmark on the desktop's default
 * backdrop (RPaMp's), "(re)discover your music collection" and the ways to sign in, one card each under each other:
 * Google, Apple and an rpm account (its email and password, on the same hosted page). OK on a card opens a pop-up in
 * the settings panel's glass with that way's QR code; Back closes it. All the codes are asked for and polled from the
 * start, whichever pop-up is open, so a sign-in finished on the phone is seen however the remote was left.
 *
 * A finished sign-in marks the set-up as owed ([PlayerSettings.beginSetup]) before the session is adopted, so the
 * app's first look at the session already finds the set-up waiting rather than home.
 */
@Composable
fun SignInScreen(auth: AuthClient, settings: PlayerSettings, openingSound: OpeningSound) {
    var view by remember { mutableStateOf<SignInFlow.View>(SignInFlow.View.Starting) }
    var shown by remember { mutableStateOf<Provider?>(null) }
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    LaunchedEffect(Unit) { openingSound.play() }

    // Polls only while the activity is started: a TV that goes to sleep on this screen stops asking.
    LaunchedEffect(auth) {
        lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            val login = SignInFlow(auth, System::currentTimeMillis).run { view = it }
            settings.beginSetup()
            auth.adopt(login)
        }
    }

    val layer = rememberGraphicsLayer()
    val backdrop = remember(layer) { Backdrop(layer) }
    val cards = remember { Provider.entries.associateWith { FocusRequester() } }
    var last by remember { mutableStateOf(Provider.entries.first()) }

    Box(Modifier.fillMaxSize()) {
        Box(Modifier.fillMaxSize().backdropSource(backdrop), contentAlignment = Alignment.Center) {
            SetupGround()
            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.width(760.s)) {
                Image(painterResource(R.drawable.wordmark), stringResource(R.string.App_Name), Modifier.width(240.s))
                Spacer(Modifier.height(28.s))
                Text(stringResource(R.string.Tv_SignIn_Intro), style = ui(24.ts), color = Ink.Words, textAlign = TextAlign.Center)
                Spacer(Modifier.height(36.s))
                if (view == SignInFlow.View.Unreachable) {
                    Text(stringResource(R.string.Rpm_Net_Failed), style = ui(20.ts), color = Ink.Artist, textAlign = TextAlign.Center, modifier = Modifier.padding(bottom = 20.s))
                }
                Column(verticalArrangement = Arrangement.spacedBy(14.s)) {
                    for (provider in Provider.entries) {
                        SignInCard(
                            label = stringResource(provider.label),
                            onClick = {
                                last = provider
                                shown = provider
                            },
                            modifier = Modifier.focusRequester(cards.getValue(provider)),
                        )
                    }
                }
            }
        }
        shown?.let { provider ->
            val code = (view as? SignInFlow.View.Codes)?.codes?.firstOrNull { it.provider == provider }
            CodePopup(provider, code, backdrop, onClose = { shown = null })
        }
    }
    LaunchedEffect(shown) { if (shown == null) runCatching { cards.getValue(last).requestFocus() } }
}

private val Provider.label: Int
    get() = when (this) {
        Provider.GOOGLE -> R.string.Tv_SignIn_WithGoogle
        Provider.APPLE -> R.string.Tv_SignIn_WithApple
        Provider.ACCOUNT -> R.string.Tv_SignIn_WithAccount
    }

/** One way to sign in: a `560·s` by `68·s` card of light smoke with a rim, its name (22·s SemiBold) and a chevron;
 * lit while the remote is on it. */
@Composable
private fun SignInCard(label: String, onClick: () -> Unit, modifier: Modifier) {
    var lit by remember { mutableStateOf(false) }
    val shape = RoundedCornerShape(7.s)
    DeskButton(
        onClick = onClick,
        corner = 7.s,
        contentAlignment = Alignment.CenterStart,
        modifier = modifier
            .onFocusChanged { lit = it.isFocused }
            .width(560.s)
            .height(68.s)
            .background(if (lit) Color(0x40FFFFFF) else Color(0x1FFFFFFF), shape)
            .border(1.px, Color(0x59FFFFFF), shape)
            .padding(horizontal = 22.s),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(label, style = ui(22.ts, FontWeight.SemiBold), color = Ink.Words, modifier = Modifier.weight(1f))
            Glyph(Fa.CHEVRON_RIGHT, 16.ts, Ink.Words)
        }
    }
}

/**
 * The pop-up for one way to sign in, drawn as the settings panel is: the screen dimmed to `#66000000`, a `560·s` card
 * of the live glass with corner 7·s, the way's name in 26·s SemiBold, the code on white, and how to use it. Until the
 * code is in, the space it takes stays empty. Back closes it.
 */
@Composable
private fun CodePopup(provider: Provider, code: SignInFlow.Code?, backdrop: Backdrop, onClose: () -> Unit) {
    BackHandler(onBack = onClose)
    LaunchedEffect(Unit) { backdrop.capture() }
    val hold = remember { FocusRequester() }
    Box(Modifier.fillMaxSize().background(Color(0x66000000)), contentAlignment = Alignment.Center) {
        LiveGlassCard(backdrop = backdrop, corner = 7.s, modifier = Modifier.width(560.s)) {
            Box(Modifier.matchParentSize().background(GLASS_DIM))
            Column(
                Modifier.fillMaxWidth().padding(horizontal = 30.s, vertical = 26.s),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(stringResource(provider.label), style = ui(26.ts, FontWeight.SemiBold), color = Ink.Words, textAlign = TextAlign.Center)
                Spacer(Modifier.height(22.s))
                Box(
                    Modifier.size(QR_SIZE + 28.s).background(Color.White, RoundedCornerShape(12.s)).padding(14.s),
                    contentAlignment = Alignment.Center,
                ) {
                    if (code != null) {
                        val bitmap = remember(code.url) { qrBitmap(code.url) }
                        Image(bitmap, contentDescription = null, modifier = Modifier.size(QR_SIZE), filterQuality = FilterQuality.None)
                    }
                }
                Spacer(Modifier.height(20.s))
                Text(stringResource(R.string.Tv_SignIn_ScanThis), style = ui(18.ts), color = Ink.Artist, textAlign = TextAlign.Center)
                code?.userCode?.let { userCode ->
                    Spacer(Modifier.height(10.s))
                    Text(stringResource(R.string.Tv_SignIn_Code, userCode), style = ui(18.ts, FontWeight.SemiBold), color = Ink.Words, textAlign = TextAlign.Center)
                }
                // Holds the remote inside the pop-up: nothing in it takes a key but Back.
                DeskButton(onClick = {}, corner = 7.s, modifier = Modifier.focusRequester(hold).size(1.px)) {}
            }
        }
    }
    LaunchedEffect(Unit) { runCatching { hold.requestFocus() } }
}

/** The code's side on the pop-up. */
private val QR_SIZE = 300.s

/** The URL as a QR code, one pixel per module and no margin; the white box around it is the quiet zone. */
internal fun qrBitmap(text: String): ImageBitmap {
    val matrix = QRCodeWriter().encode(text, BarcodeFormat.QR_CODE, 0, 0, mapOf(EncodeHintType.MARGIN to 0))
    val pixels = IntArray(matrix.width * matrix.height) { i ->
        if (matrix.get(i % matrix.width, i / matrix.width)) android.graphics.Color.BLACK else android.graphics.Color.WHITE
    }
    return Bitmap.createBitmap(pixels, matrix.width, matrix.height, Bitmap.Config.ARGB_8888).asImageBitmap()
}
