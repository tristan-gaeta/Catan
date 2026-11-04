package com.tgaetaprojects.settledown.online

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialCancellationException
import androidx.credentials.exceptions.GetCredentialException
import androidx.credentials.exceptions.NoCredentialException
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.android.libraries.identity.googleid.GoogleIdTokenParsingException
import java.io.File
import java.lang.ref.WeakReference

@Composable
actual fun rememberPlatformAuth(): PlatformAuth {
    val context = LocalContext.current
    return remember(context) { AndroidAuth(context) }
}

/**
 * Signs in through Credential Manager, which needs the activity. It's held weakly, because this object can outlive it
 * in a long-running request after a rotation. The session token sits in the no-backup directory: app-private, and left
 * out of cloud backups and device transfers.
 */
private class AndroidAuth(context: Context) : PlatformAuth {
    private val activity = WeakReference(context)
    private val tokenFile = File(context.noBackupFilesDir, "session-token")

    override suspend fun googleIdToken(config: OnlineConfig): String {
        val clientId = config.googleClientId ?: throw SignInFailedException("This build isn't set up for Google sign-in.")
        val context = activity.get() ?: throw SignInCancelledException()
        val option = GetGoogleIdOption.Builder()
            .setServerClientId(clientId)
            .setFilterByAuthorizedAccounts(false)
            .build()
        val request = GetCredentialRequest.Builder().addCredentialOption(option).build()
        val credential = try {
            CredentialManager.create(context).getCredential(context, request).credential
        } catch (_: GetCredentialCancellationException) {
            throw SignInCancelledException()
        } catch (_: NoCredentialException) {
            throw SignInFailedException("Add a Google account to this device to sign in.")
        } catch (_: GetCredentialException) {
            throw SignInFailedException("Google sign-in didn't work. Please try again.")
        }
        if (credential !is CustomCredential || credential.type != GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL) {
            throw SignInFailedException("Google sign-in didn't work. Please try again.")
        }
        return try {
            GoogleIdTokenCredential.createFrom(credential.data).idToken
        } catch (_: GoogleIdTokenParsingException) {
            throw SignInFailedException("Google sign-in didn't work. Please try again.")
        }
    }

    override fun loadSessionToken(): String? =
        tokenFile.takeIf { it.exists() }?.readText()?.takeIf { it.isNotEmpty() }

    override fun saveSessionToken(token: String?) {
        if (token == null) tokenFile.delete() else tokenFile.writeText(token)
    }
}
