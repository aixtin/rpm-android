package com.rpm.player.phone.signin

import android.os.SystemClock
import androidx.annotation.StringRes
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.rpm.player.core.R
import com.rpm.player.core.auth.AuthClient
import com.rpm.player.core.auth.LoginAnswer
import com.rpm.player.core.auth.Provider
import com.rpm.player.core.auth.SocialPoll
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import java.io.IOException

/** A message for the screen: a string resource, and the values that fill its placeholders. */
data class Said(@StringRes val id: Int, val args: List<Any> = emptyList())

/**
 * Signing in on the phone. There are two ways, as on the iPhone (`AppModel.signIn`): an email address and a
 * password, or Apple or Google in the browser.
 *
 * **Owned by the application, not by the screen.** The browser covers the app while the person signs in. A
 * poll held by a screen would stop when the system recreates that activity behind the browser. So this object
 * is made once, next to the services, and uses their scope.
 *
 * The browser sign-in has no callback. The accounts service returns a sign-in address and a session id. The
 * website stores the tokens on that session, and the app polls until they arrive. A return to the app is
 * handled separately (see [backFromBrowser]).
 *
 * @param bringBack puts the app back in front of the browser once the browser's sign-in has finished.
 */
class SignIn(
    private val auth: AuthClient,
    private val scope: CoroutineScope,
    private val bringBack: () -> Unit,
) {
    /** A sign-in is under way; the form is disabled. */
    var busy by mutableStateOf(false)
        private set

    /** Waiting on the browser: the line under the fields says so. */
    var waitingForBrowser by mutableStateOf(false)
        private set

    /** What went wrong, in the place the form's errors go. */
    var trouble by mutableStateOf<Said?>(null)
        private set

    /**
     * The address to open in the browser, until an activity has opened it ([opened]). It is held as state,
     * so whichever activity is on screen when the address arrives can open it.
     */
    var toOpen by mutableStateOf<String?>(null)
        private set

    /**
     * Set when the person returns to the app after the browser was opened. There is one per attempt. It is null between
     * attempts.
     */
    private var back: MutableStateFlow<Boolean>? = null

    /**
     * Whether this attempt's address has been handed to the browser. A return to the app before that, such as
     * a dialog over the activity while the address is fetched, says nothing about the browser.
     */
    private var browserShown = false

    /** Shows a message from the screen itself. The iPhone app has the same `model.report`. */
    fun report(said: Said) {
        trouble = said
    }

    fun withPassword(email: String, password: String) {
        if (busy) return
        if (email.isBlank() || password.isEmpty()) {
            trouble = Said(R.string.Login_NeedFields)
            return
        }
        busy = true
        trouble = null
        scope.launch {
            trouble = try {
                when (val answer = auth.login(email, password)) {
                    is LoginAnswer.In -> null
                    LoginAnswer.WrongPassword -> Said(R.string.Login_WrongPassword)
                    LoginAnswer.NotConfirmed -> Said(R.string.Login_NotConfirmed)
                    // A 2xx answer without a token means the sign-in did not finish. A 5xx answer means the service
                    // failed.
                    is LoginAnswer.Refused -> when (answer.code) {
                        in 200..299 -> Said(R.string.Login_Unable)
                        in 500..599 -> Said(R.string.Rpm_Error_Status, listOf(answer.code))
                        else -> Said(R.string.Rpm_SignIn_Failed)
                    }
                }
            } catch (e: IOException) {
                Said(R.string.Rpm_Net_Failed)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                // An answer that could not be read. This is caught here, because the scope belongs to the
                // application. An exception that escapes would end the app.
                Said(R.string.Rpm_SignIn_Failed)
            }
            busy = false
        }
    }

    fun withProvider(provider: Provider) {
        if (busy) return
        busy = true
        waitingForBrowser = true
        trouble = null
        browserShown = false
        val back = MutableStateFlow(false).also { this.back = it }
        scope.launch {
            try {
                val start = auth.startSocial(provider)
                toOpen = start.loginUrl
                val finished = watch(start.sessionId, back)
                if (finished != null) {
                    auth.adopt(finished.asLogin())
                    bringBack()
                }
            } catch (e: IOException) {
                trouble = Said(R.string.Rpm_Net_Failed)
            } catch (e: Incomplete) {
                trouble = Said(R.string.Login_Unable)
            } catch (e: TimedOut) {
                trouble = Said(R.string.Login_BrowserTimedOut)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                trouble = Said(R.string.Login_Unable)
            } finally {
                toOpen = null
                waitingForBrowser = false
                busy = false
                browserShown = false
                if (this@SignIn.back === back) this@SignIn.back = null
            }
        }
    }

    /** The activity has opened [toOpen] in the browser. */
    fun opened() {
        toOpen = null
        browserShown = true
    }

    /**
     * The activity came back to the front. While the browser sign-in waits, and its address is already open,
     * this means the person closed the browser, or the browser finished. The watch then checks once more and
     * stops. Closing the browser is not a failure, so no message is shown. The iPhone app does the same.
     */
    fun backFromBrowser() {
        if (browserShown) back?.value = true
    }

    /**
     * Polls every [EVERY_MS] for up to [DEADLINE_MS]. These are the same values as on iPhone
     * (`AuthClient.pollSocial`). A return to the app ends the wait early, for one last check, because the
     * browser may have finished as it closed. Returns null when the person came back without finishing.
     */
    private suspend fun watch(sessionId: String, back: MutableStateFlow<Boolean>): SocialPoll? {
        val deadline = SystemClock.elapsedRealtime() + DEADLINE_MS
        while (SystemClock.elapsedRealtime() < deadline) {
            val returned = withTimeoutOrNull(EVERY_MS) { back.first { it } } != null
            val said = auth.poll(sessionId)
            if (said.isComplete) return said
            if (said.status == "complete") throw Incomplete()
            if (returned) return null
        }
        throw TimedOut()
    }

    private class Incomplete : Exception()
    private class TimedOut : Exception()

    private companion object {
        const val EVERY_MS = 2_000L
        const val DEADLINE_MS = 180_000L
    }
}
