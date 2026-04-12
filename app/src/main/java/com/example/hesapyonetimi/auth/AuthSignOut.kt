package com.example.hesapyonetimi.auth

import android.content.Context
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.tasks.Tasks
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

object AuthSignOut {

    suspend fun signOutGoogleIfNeeded(context: Context, webClientId: String) {
        if (AuthPrefs.getAuthMethod(context) != AuthPrefs.AUTH_METHOD_GMAIL) return
        withContext(Dispatchers.IO) {
            runCatching {
                val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
                    .requestEmail()
                    .requestIdToken(webClientId)
                    .build()
                Tasks.await(GoogleSignIn.getClient(context, gso).signOut())
            }
        }
    }
}
