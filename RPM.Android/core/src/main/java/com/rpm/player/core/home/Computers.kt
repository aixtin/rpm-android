package com.rpm.player.core.home

import android.content.Context
import android.os.Build
import android.provider.Settings
import com.rpm.player.core.auth.AuthClient
import com.rpm.player.core.net.WireJson
import com.rpm.player.core.net.await
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.decodeFromStream
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.File
import java.io.IOException
import java.security.SecureRandom
import java.util.concurrent.ConcurrentHashMap

/** A computer that this TV has paired with. The pairing is stored on the TV. */
@Serializable
data class PairedComputer(val deviceId: String, val publicKey: String, val name: String)

/** A computer as the computers screen shows it: paired or not, and on the network or not. */
data class ComputerView(val key: String, val name: String, val paired: Boolean, val here: FoundComputer?)

/** The state of a pairing. The first ending wins: once a pairing has ended, later endings are ignored. */
sealed interface Pairing {
    val name: String

    data class Asking(override val name: String) : Pairing
    /** The computer shows the code. [wrong] is true after the computer refused a code. */
    data class ShowingCode(override val name: String, val triesLeft: Int, val wrong: Boolean) : Pairing
    data class Ended(override val name: String, val how: Ending) : Pairing
}

enum class Ending { Accepted, Declined, Expired, TooManyTries, Full, Lost, Failed }

/** The computer answered 401 (the token is gone because of a restart, expiry, or the computer forgot the TV) or 403. */
class PeerRefused(val status: Int) : IOException("computer answered $status")

/**
 * The computers that this TV pairs with and reads from. This is the requesting side of the protocol in
 * `RPM.Core/Sharing`. Pairing: the TV offers its key, the computer shows a six-digit code, and the person
 * types the code on the TV. Sessions: the TV signs a challenge with its key and gets a token for 30 minutes.
 */
class Computers(
    context: Context,
    /** Built off the main thread as the app starts (Services.peerHttp), and first used when a request is made. */
    private val http: Lazy<OkHttpClient>,
    val discovery: Discovery,
    private val auth: AuthClient,
    private val scope: CoroutineScope,
    private val clock: () -> Long,
) {
    private val file = File(context.noBackupFilesDir, "computers.json")
    private val tvName: String = Settings.Global.getString(context.contentResolver, Settings.Global.DEVICE_NAME)
        ?.takeIf { it.isNotBlank() } ?: Build.MODEL

    private val _paired = MutableStateFlow(readPaired())
    val paired: StateFlow<List<PairedComputer>> = _paired.asStateFlow()

    /** Every computer that is on the network or paired, with paired computers first, then sorted by name. */
    val list: StateFlow<List<ComputerView>> = combine(_paired, discovery.found) { paired, found ->
        val byKey = paired.associateBy { it.deviceId.uppercase() }
        (byKey.keys + found.keys).map { key ->
            ComputerView(key, found[key]?.name ?: byKey[key]?.name.orEmpty(), key in byKey, found[key])
        }.sortedWith(compareBy<ComputerView>({ !it.paired }, { it.name.lowercase() }))
    }.stateIn(scope, SharingStarted.Eagerly, emptyList())

    private val _pairing = MutableStateFlow<Pairing?>(null)
    val pairing: StateFlow<Pairing?> = _pairing.asStateFlow()

    private var identity: PeerIdentity? = null
    private val identityLock = Mutex()

    private suspend fun identity(): PeerIdentity = identityLock.withLock { identity ?: PeerIdentity.load().also { identity = it } }

    // Pairing ----------------------------------------------------------------------------------------

    private var pairingJob: Job? = null
    private var pairingId = ""
    private var pairingWith: FoundComputer? = null
    /** Counts pairings, so a reply for a replaced pairing is ignored. */
    private var attempt = 0
    private var lastTries = 0

    /** Starts pairing with [computer], and replaces any pairing in progress. */
    fun pair(computer: FoundComputer) {
        pairingJob?.cancel()
        val mine = ++attempt
        pairingWith = computer
        pairingId = ""
        _pairing.value = Pairing.Asking(computer.name)
        pairingJob = scope.launch(Dispatchers.IO) {
            try {
                val me = identity()
                val offer = post(
                    computer.base + "/rpm/pair",
                    WireJson.encodeToString(PairRequest.serializer(), PairRequest(
                        version = PeerWire.VERSION,
                        publicKey = PeerWire.toBase64Url(me.publicKey),
                        name = tvName,
                        face = "",
                        nonce = PeerWire.toBase64Url(ByteArray(16).also(SecureRandom()::nextBytes)),
                    )),
                    PairOffer.serializer(),
                    token = null,
                ) ?: throw IOException("no offer")
                if (!PeerWire.fromBase64Url(offer.publicKey).contentEquals(computer.publicKey)) return@launch end(mine, Ending.Failed)
                if (offer.id.isEmpty()) return@launch onStatus(mine, offer.status)
                pairingId = offer.id
                lastTries = offer.status.triesLeft
                var status = offer.status
                onStatus(mine, status)
                // Ask again at once after each answer, because the computer ends a pairing that nobody follows for
                // 10 s. Failures are retried every second and treated as lost after 15 s.
                var failingSince = 0L
                while (_pairing.value !is Pairing.Ended && attempt == mine) {
                    status = try {
                        get(computer.base + "/rpm/pair/status?id=${offer.id}&rev=${status.rev}", PairStatus.serializer(), token = null)
                            .also { failingSince = 0L } ?: status
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        if (failingSince == 0L) failingSince = clock()
                        if (clock() - failingSince > 15_000) return@launch end(mine, Ending.Lost)
                        delay(1_000)
                        continue
                    }
                    onStatus(mine, status)
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                end(mine, Ending.Failed)
            }
        }
    }

    /** Sends the code the person typed. */
    fun submit(code: String) {
        val computer = pairingWith ?: return
        val id = pairingId.takeIf { it.isNotEmpty() } ?: return
        val mine = attempt
        scope.launch(Dispatchers.IO) {
            try {
                post(computer.base + "/rpm/pair/code", WireJson.encodeToString(PairCode.serializer(), PairCode(id, code)), PairStatus.serializer(), token = null)
                    ?.let { onStatus(mine, it) }
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                // The status loop still decides. A lost answer to the code does not end the pairing.
            }
        }
    }

    /** Withdraws a pairing in progress (Back on the code screen), or clears one that has ended. */
    fun cancel() {
        val computer = pairingWith
        val id = pairingId
        attempt++
        pairingJob?.cancel()
        _pairing.value = null
        if (computer != null && id.isNotEmpty()) {
            scope.launch(Dispatchers.IO) {
                try {
                    post(computer.base + "/rpm/pair/withdraw", WireJson.encodeToString(PairId.serializer(), PairId(id)), null, token = null)
                } catch (e: CancellationException) {
                    throw e
                } catch (_: Exception) {
                }
            }
        }
    }

    private fun onStatus(mine: Int, status: PairStatus) {
        if (mine != attempt || _pairing.value is Pairing.Ended) return
        val name = pairingWith?.name.orEmpty()
        when (status.state) {
            "ShowingCode" -> {
                val wrong = status.triesLeft < lastTries
                lastTries = status.triesLeft
                _pairing.value = Pairing.ShowingCode(name, status.triesLeft, wrong)
            }
            "Accepted" -> {
                pairingWith?.let(::record)
                end(mine, Ending.Accepted)
            }
            "Declined" -> end(mine, Ending.Declined)
            "Expired" -> end(mine, Ending.Expired)
            "TooManyTries" -> end(mine, Ending.TooManyTries)
            "Full" -> end(mine, Ending.Full)
            else -> end(mine, Ending.Lost)
        }
    }

    private fun end(mine: Int, how: Ending) {
        if (mine != attempt || _pairing.value is Pairing.Ended) return
        _pairing.value = Pairing.Ended(pairingWith?.name.orEmpty(), how)
    }

    /** Records [computer] as paired. Recording the same computer again replaces the old entry. */
    @Synchronized
    private fun record(computer: FoundComputer) {
        val next = _paired.value.filterNot { it.deviceId.equals(computer.deviceId, ignoreCase = true) } +
            PairedComputer(computer.deviceId, PeerWire.toBase64Url(computer.publicKey), computer.name)
        _paired.value = next
        try {
            val temp = File(file.parentFile, file.name + ".tmp")
            temp.writeText(WireJson.encodeToString(ListSerializer(PairedComputer.serializer()), next))
            if (!temp.renameTo(file)) {
                file.delete()
                temp.renameTo(file)
            }
        } catch (_: Exception) {
        }
    }

    private fun readPaired(): List<PairedComputer> = try {
        if (file.exists()) WireJson.decodeFromString(ListSerializer(PairedComputer.serializer()), file.readText()) else emptyList()
    } catch (_: Exception) {
        emptyList()
    }

    fun isPaired(key: String): Boolean = _paired.value.any { it.deviceId.equals(key, ignoreCase = true) }

    // Sessions ---------------------------------------------------------------------------------------

    private class Token(val value: String, val expiresAtMs: Long)

    private val tokens = ConcurrentHashMap<String, Token>()
    private val sessionLocks = ConcurrentHashMap<String, Mutex>()

    /** A token for [computer]. It is reused until 2 minutes before it expires. Only one request at a time runs
     * per computer, so two callers do not ask for two tokens. Throws [PeerRefused] with 403 if the computer no
     * longer trusts the TV. */
    suspend fun token(computer: FoundComputer): String = sessionLocks.getOrPut(computer.key) { Mutex() }.withLock {
        tokens[computer.key]?.takeIf { it.expiresAtMs - clock() > 120_000 }?.let { return@withLock it.value }
        val me = identity()
        val challenge = get(computer.base + "/rpm/session", SessionChallenge.serializer(), token = null)
            ?: throw IOException("no challenge")
        // The account goes inside the signature. It tells the computer whether this TV is one of its owner's own
        // devices, and nothing on the network says so before the key is proved.
        val account = auth.session.value?.userId.orEmpty()
        val signature = me.sign(PeerWire.sessionText(computer.deviceId, challenge.challenge, account))
        val grant = post(
            computer.base + "/rpm/session",
            WireJson.encodeToString(SessionRedeem.serializer(), SessionRedeem(PeerWire.toBase64Url(me.publicKey), challenge.challenge, PeerWire.toBase64Url(signature), account)),
            SessionGrant.serializer(),
            token = null,
        ) ?: throw IOException("no grant")
        tokens[computer.key] = Token(grant.token, clock() + grant.expiresIn * 1000L)
        grant.token
    }

    /** Drops [computer]'s token after a 401, so the next call gets a new one. */
    fun forgetToken(computer: FoundComputer) {
        tokens.remove(computer.key)
    }

    // Catalogues and reachability ----------------------------------------------------------------------

    /** The catalogue of [computer], or null if it is still version [have] (204). It is read from the stream and
     * not from a string, because a library of 22,000 tracks is about 15 MB of JSON. A 401 drops the token and
     * the request is repeated once. */
    @OptIn(kotlinx.serialization.ExperimentalSerializationApi::class)
    suspend fun catalog(computer: FoundComputer, have: String?): PeerCatalog? {
        val url = computer.base + "/rpm/catalog" + (have?.let { "?have=" + java.net.URLEncoder.encode(it, "UTF-8") } ?: "")
        repeat(2) { round ->
            val token = token(computer)
            val request = Request.Builder().url(url).get().auth(token).build()
            http.value.newCall(request).await().use { response ->
                when (response.code) {
                    200 -> return response.body.byteStream().use { WireJson.decodeFromStream(PeerCatalog.serializer(), it) }
                    204 -> return null
                    401 -> if (round == 0) forgetToken(computer) else throw PeerRefused(401)
                    403 -> throw PeerRefused(403)
                    else -> throw IOException("computer answered ${response.code}")
                }
            }
        }
        throw PeerRefused(401)
    }

    /** When each computer last failed (a refused connection or a dropped track), and at which address. */
    private class Failure(val atMs: Long, val base: String)

    private val failures = ConcurrentHashMap<String, Failure>()

    /** Marks [key] as away after a failure. It is tried again after [AWAY_MS], or as soon as it is found on
     * the network at another address or port (which means its server restarted). */
    fun markAway(key: String) {
        val base = discovery.found.value[key]?.base ?: return
        failures[key] = Failure(clock(), base)
    }

    /** The computer to read from now: it is paired, found on the network, and has not failed recently at the
     * address where it is found. */
    fun usable(key: String): FoundComputer? {
        if (!isPaired(key)) return null
        val found = discovery.found.value[key] ?: return null
        val failed = failures[key] ?: return found
        if (failed.base != found.base || clock() - failed.atMs > AWAY_MS) {
            failures.remove(key)
            return found
        }
        return null
    }

    // Requests ---------------------------------------------------------------------------------------

    /** A GET request. Returns null for 204. A 401 or 403 throws [PeerRefused]. */
    suspend fun <T> get(url: String, reader: KSerializer<T>, token: String?): T? =
        call(Request.Builder().url(url).get().auth(token).build(), reader)

    private suspend fun <T> post(url: String, json: String, reader: KSerializer<T>?, token: String?): T? =
        call(Request.Builder().url(url).post(json.toRequestBody(JSON)).auth(token).build(), reader)

    private fun Request.Builder.auth(token: String?) = apply { if (token != null) header("Authorization", "Bearer $token") }

    private suspend fun <T> call(request: Request, reader: KSerializer<T>?): T? =
        http.value.newCall(request).await().use { response ->
            when (response.code) {
                200 -> reader?.let { WireJson.decodeFromString(it, response.body.string()) }
                204 -> null
                401, 403 -> throw PeerRefused(response.code)
                else -> throw IOException("computer answered ${response.code}")
            }
        }

    private companion object {
        val JSON = "application/json".toMediaType()
        const val AWAY_MS = 120_000L
    }
}
