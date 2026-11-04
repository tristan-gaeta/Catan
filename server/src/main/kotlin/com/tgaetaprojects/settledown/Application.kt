package com.tgaetaprojects.settledown

import com.tgaetaprojects.settledown.api.AccountInfo
import com.tgaetaprojects.settledown.api.ApiError
import com.tgaetaprojects.settledown.api.CreateRoomRequest
import com.tgaetaprojects.settledown.api.GoogleSignInRequest
import com.tgaetaprojects.settledown.api.SignInResponse
import com.tgaetaprojects.settledown.api.UpdateAccountRequest
import com.tgaetaprojects.settledown.auth.DevTokenVerifier
import com.tgaetaprojects.settledown.auth.GoogleTokenVerifier
import com.tgaetaprojects.settledown.auth.TokenVerifier
import com.tgaetaprojects.settledown.auth.VerifierUnavailableException
import com.tgaetaprojects.settledown.game.codec.WireCodec
import com.tgaetaprojects.settledown.rooms.GameStarter
import com.tgaetaprojects.settledown.rooms.RoomService
import com.tgaetaprojects.settledown.store.Account
import com.tgaetaprojects.settledown.store.AccountStore
import com.tgaetaprojects.settledown.store.Database
import com.tgaetaprojects.settledown.store.GameStore
import com.tgaetaprojects.settledown.store.SessionStore
import io.ktor.http.HttpStatusCode
import io.ktor.http.auth.HttpAuthHeader
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.Application
import io.ktor.server.application.ApplicationCall
import io.ktor.server.application.ApplicationStopped
import io.ktor.server.application.createApplicationPlugin
import io.ktor.server.application.install
import io.ktor.server.application.log
import io.ktor.server.auth.Authentication
import io.ktor.server.auth.authenticate
import io.ktor.server.auth.bearer
import io.ktor.server.auth.parseAuthorizationHeader
import io.ktor.server.auth.principal
import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.plugins.forwardedheaders.XForwardedHeaders
import io.ktor.server.plugins.origin
import io.ktor.server.plugins.ratelimit.RateLimit
import io.ktor.server.plugins.ratelimit.RateLimitName
import io.ktor.server.plugins.ratelimit.rateLimit
import io.ktor.server.plugins.statuspages.StatusPages
import io.ktor.server.request.receive
import io.ktor.server.request.path
import io.ktor.server.response.respond
import io.ktor.server.response.respondText
import io.ktor.server.routing.delete
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.put
import io.ktor.server.routing.route
import io.ktor.server.routing.routing
import kotlin.time.Duration.Companion.minutes

fun main() {
    val config = ServerConfig.from(System.getenv())
    embeddedServer(Netty, port = config.port, host = config.host) {
        if (config.devAuth) log.warn("SETTLEDOWN_DEV_AUTH is on: anyone can sign in as anyone. Never use this in production.")
        val verifier = if (config.devAuth) DevTokenVerifier else GoogleTokenVerifier.withGoogleKeys(config.googleClientIds)
        module(Services(Database(config.databasePath), verifier), trustProxy = config.trustProxy)
    }.start(wait = true)
}

/** Everything the routes need, built once. */
class Services(
    val database: Database,
    val verifier: TokenVerifier,
    now: () -> Long = System::currentTimeMillis,
) {
    val accounts = AccountStore(database, now)
    val sessions = SessionStore(database, now)
    val games = GameStore(database, now)
    private val starter = GameStarter(games)
    val rooms = RoomService(accounts, starter::start, now)
}

/** A request that can't be carried out, sent to the client as an [ApiError]. */
class ApiFailure(val status: HttpStatusCode, val code: String, override val message: String) : RuntimeException(message)

/** Refuses a client built for other formats or rules before it tries to decode anything. */
private val ProtocolCheck = createApplicationPlugin("ProtocolCheck") {
    onCall { call ->
        if (call.request.path() != "/health" && call.request.headers[WireCodec.VERSION_HEADER] != WireCodec.PROTOCOL_VERSION) {
            call.respond(
                HttpStatusCode.UpgradeRequired,
                ApiError("update_required", "Please update the app to play online")
            )
        }
    }
}

private val SignInLimit = RateLimitName("sign-in")
private val JoinLimit = RateLimitName("join")

private fun ApplicationCall.account(): Account = principal<Account>()!!

fun Application.module(services: Services, trustProxy: Boolean = false) {
    monitor.subscribe(ApplicationStopped) { services.database.close() }

    if (trustProxy) {
        // the proxy appends the address it saw, so entries before the last one are whatever the client claimed
        install(XForwardedHeaders) { useLastProxy() }
    }
    install(ContentNegotiation) { json() }
    install(ProtocolCheck)
    install(StatusPages) {
        exception<ApiFailure> { call, failure ->
            call.respond(failure.status, ApiError(failure.code, failure.message))
        }
        exception<VerifierUnavailableException> { call, _ ->
            call.respond(HttpStatusCode.ServiceUnavailable, ApiError("sign_in_unavailable", "Try signing in again shortly"))
        }
    }
    install(Authentication) {
        bearer {
            authenticate { credential -> services.sessions.accountFor(credential.token) }
        }
    }
    install(RateLimit) {
        register(SignInLimit) {
            rateLimiter(limit = 60, refillPeriod = 1.minutes)
            requestKey { it.request.origin.remoteHost }
        }
        // room codes are short enough to guess, given enough tries
        register(JoinLimit) {
            rateLimiter(limit = 20, refillPeriod = 1.minutes)
            requestKey { it.account().id }
        }
    }

    routing {
        get("/health") { call.respondText("ok") }

        rateLimit(SignInLimit) {
            post("/auth/google") {
                val request = call.receive<GoogleSignInRequest>()
                val subject = services.verifier.verify(request.idToken)
                    ?: throw ApiFailure(HttpStatusCode.Unauthorized, "invalid_token", "That sign-in wasn't valid")
                val (account, isNew) = services.accounts.findOrCreate(subject)
                call.respond(
                    SignInResponse(services.sessions.create(account.id), account.toInfo(), isNew)
                )
            }
        }

        authenticate {
            delete("/auth/session") {
                val header = call.request.parseAuthorizationHeader() as HttpAuthHeader.Single
                services.sessions.revoke(header.blob)
                call.respond(HttpStatusCode.NoContent)
            }

            route("/account") {
                get { call.respond(call.account().toInfo()) }
                put {
                    val name = AccountStore.normalizeDisplayName(call.receive<UpdateAccountRequest>().displayName)
                        ?: throw ApiFailure(HttpStatusCode.BadRequest, "invalid_name", "Names are 1 to 24 characters")
                    services.accounts.rename(call.account().id, name)
                    call.respond(AccountInfo(call.account().id, name))
                }
                delete {
                    // in this order, so the session stops working before the seat is given up
                    services.accounts.delete(call.account().id)
                    services.rooms.removeAccount(call.account().id)
                    call.respond(HttpStatusCode.NoContent)
                }
            }

            get("/me/games") { call.respond(services.games.gamesFor(call.account().id)) }
            get("/me/room") {
                val room = services.rooms.current(call.account().id)
                if (room == null) call.respond(HttpStatusCode.NoContent) else call.respond(room)
            }

            route("/rooms") {
                post {
                    val request = call.receive<CreateRoomRequest>()
                    call.respond(HttpStatusCode.Created, services.rooms.create(call.account().id, request.seats))
                }
                route("/{code}") {
                    get { call.respond(services.rooms.get(call.account().id, call.parameters["code"]!!)) }
                    rateLimit(JoinLimit) {
                        post("/join") {
                            call.respond(services.rooms.join(call.account().id, call.parameters["code"]!!))
                        }
                    }
                    post("/leave") {
                        services.rooms.leave(call.account().id, call.parameters["code"]!!)
                        call.respond(HttpStatusCode.NoContent)
                    }
                }
            }
        }
    }
}

private fun Account.toInfo() = AccountInfo(id, displayName)
