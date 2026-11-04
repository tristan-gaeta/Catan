package com.tgaetaprojects.settledown

/**
 * Settings read from the environment.
 *
 * @param googleClientIds the OAuth client IDs an ID token may be issued to.
 * @param devAuth accept unverified `dev:<name>` tokens, so anyone can sign in as anyone. Only for local testing.
 * @param trustProxy take the client's address from the last `X-Forwarded-For` entry. Turn it on behind exactly one
 *   reverse proxy that appends to that header, and only if clients can't reach the server directly.
 */
data class ServerConfig(
    val host: String = "0.0.0.0",
    val port: Int = 8080,
    val databasePath: String = "settledown.db",
    val googleClientIds: Set<String> = emptySet(),
    val devAuth: Boolean = false,
    val trustProxy: Boolean = false,
) {
    companion object {
        fun from(env: Map<String, String>): ServerConfig {
            val config = ServerConfig(
                host = env["SETTLEDOWN_HOST"] ?: "0.0.0.0",
                port = env["SETTLEDOWN_PORT"]?.toInt() ?: 8080,
                databasePath = env["SETTLEDOWN_DB"] ?: "settledown.db",
                googleClientIds = env["SETTLEDOWN_GOOGLE_CLIENT_IDS"].orEmpty()
                    .split(',').map { it.trim() }.filter { it.isNotEmpty() }.toSet(),
                devAuth = env["SETTLEDOWN_DEV_AUTH"] == "true",
                trustProxy = env["SETTLEDOWN_TRUST_PROXY"] == "true",
            )
            require(config.devAuth || config.googleClientIds.isNotEmpty()) {
                "Set SETTLEDOWN_GOOGLE_CLIENT_IDS, or SETTLEDOWN_DEV_AUTH=true for local testing"
            }
            return config
        }
    }
}
