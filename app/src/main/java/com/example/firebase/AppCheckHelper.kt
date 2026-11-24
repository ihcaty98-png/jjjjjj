package com.example.firebase

import android.app.Application
import android.content.Context
import android.content.Intent
import android.util.Log
import com.example.BuildConfig
import com.example.data.ApiKeyStore
import com.google.android.gms.tasks.Task
import com.google.android.gms.tasks.Tasks
import com.google.android.recaptcha.Recaptcha
import com.google.android.recaptcha.RecaptchaAction
import com.google.android.recaptcha.RecaptchaTasksClient
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseException
import com.google.firebase.appcheck.AppCheckProvider
import com.google.firebase.appcheck.AppCheckProviderFactory
import com.google.firebase.appcheck.AppCheckToken
import com.google.firebase.appcheck.FirebaseAppCheck
import com.google.firebase.appcheck.debug.DebugAppCheckProviderFactory
import com.google.firebase.appcheck.internal.DefaultAppCheckToken
import com.google.firebase.appcheck.internal.DefaultFirebaseAppCheck
import com.google.firebase.appcheck.internal.NetworkClient
import com.google.firebase.appcheck.internal.RetryManager
import org.json.JSONObject
import java.nio.charset.StandardCharsets
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

private const val TAG = "AppCheckInit"

private const val DEBUG_TOKEN_EXTRA = "FIREBASE_APPCHECK_DEBUG_TOKEN"
private const val DEBUG_STORE_PREFIX = "com.google.firebase.appcheck.debug.store."
private const val DEBUG_SECRET_KEY = "com.google.firebase.appcheck.debug.DEBUG_SECRET"
private const val RECAPTCHA_SITE_KEY_RES = "recaptcha_site_key"

@Volatile
private var appCheckConfigured = false

@Volatile
private var lastConfiguredContext: Context? = null

@Volatile
private var usingHarnessDebugToken = false

fun hasHarnessDebugToken(): Boolean = BuildConfig.DEBUG && usingHarnessDebugToken

fun ensureAppCheckConfigured(context: Context) {
    lastConfiguredContext = context.applicationContext
    ApiKeyStore.init(context.applicationContext)
    if (!appCheckConfigured) {
        configureAppCheck(context.applicationContext, null)
    }
}

fun configureAppCheck(context: Context, intent: Intent?) {
    val appContext = context.applicationContext
    lastConfiguredContext = appContext
    ApiKeyStore.init(appContext)
    try {
        if (FirebaseApp.getApps(appContext).isEmpty()) {
            FirebaseApp.initializeApp(appContext)
        }

        val debugToken = intent?.getStringExtra(DEBUG_TOKEN_EXTRA)?.takeIf { it.isNotBlank() }

        if (BuildConfig.DEBUG && debugToken != null) {
            val persistenceKey = FirebaseApp.getInstance().persistenceKey
            val prefs = appContext.getSharedPreferences(
                "$DEBUG_STORE_PREFIX$persistenceKey",
                Context.MODE_PRIVATE
            )
            // Synchronous commit() is required before DebugAppCheckProviderFactory reads it
            prefs.edit()
                .putString(DEBUG_SECRET_KEY, debugToken)
                .commit()

            FirebaseAppCheck.getInstance().installAppCheckProviderFactory(
                DebugAppCheckProviderFactory.getInstance()
            )
            usingHarnessDebugToken = true
            appCheckConfigured = true
            Log.d(TAG, "DebugAppCheckProviderFactory installed with harness debug token.")
            return
        }

        if (appCheckConfigured) {
            return
        }

        val resId = appContext.resources.getIdentifier(
            RECAPTCHA_SITE_KEY_RES,
            "string",
            appContext.packageName
        )
        if (resId != 0) {
            val siteKey = appContext.getString(resId)
            if (siteKey.isNotBlank()) {
                val factory = ResilientRecaptchaAppCheckProviderFactory(siteKey)
                FirebaseAppCheck.getInstance().installAppCheckProviderFactory(factory)
                usingHarnessDebugToken = false
                appCheckConfigured = true
                factory.warmUpClientAsync(appContext)
                Log.d(TAG, "ResilientRecaptchaAppCheckProviderFactory installed.")
                return
            } else {
                Log.e(TAG, "recaptcha_site_key resource string is blank.")
            }
        } else {
            Log.e(TAG, "recaptcha_site_key string resource not found in package.")
        }

        if (BuildConfig.DEBUG) {
            FirebaseAppCheck.getInstance().installAppCheckProviderFactory(
                DebugAppCheckProviderFactory.getInstance()
            )
            appCheckConfigured = true
            Log.w(TAG, "Fallback DebugAppCheckProviderFactory installed.")
        }
    } catch (e: Exception) {
        Log.e(TAG, "Failed to initialize Firebase App Check", e)
    }
}

/**
 * Clears any stale/invalid token from FirebaseAppCheck's in-memory + SharedPreferences cache
 * and reinstalls the active AppCheckProviderFactory so subsequent token fetches start clean.
 */
internal fun resetAndReinstallAppCheck(): Boolean {
    val ctx = lastConfiguredContext
    return try {
        if (usingHarnessDebugToken) {
            val appCheck = FirebaseAppCheck.getInstance()
            if (appCheck is DefaultFirebaseAppCheck) {
                appCheck.resetAppCheckState()
            }
            appCheck.installAppCheckProviderFactory(DebugAppCheckProviderFactory.getInstance())
            true
        } else if (ctx != null) {
            val resId = ctx.resources.getIdentifier(RECAPTCHA_SITE_KEY_RES, "string", ctx.packageName)
            val siteKey = if (resId != 0) ctx.getString(resId) else ""
            if (siteKey.isNotBlank()) {
                val appCheck = FirebaseAppCheck.getInstance()
                if (appCheck is DefaultFirebaseAppCheck) {
                    appCheck.resetAppCheckState()
                }
                val factory = ResilientRecaptchaAppCheckProviderFactory(siteKey)
                appCheck.installAppCheckProviderFactory(factory)
                appCheckConfigured = true
                Log.d(TAG, "App Check state reset & ResilientRecaptchaAppCheckProviderFactory reinstalled.")
                true
            } else {
                false
            }
        } else {
            false
        }
    } catch (e: Exception) {
        Log.w(TAG, "Failed to reset/reinstall App Check: ${e.message}", e)
        false
    }
}

/**
 * Self-healing reCAPTCHA Enterprise App Check provider factory.
 * Uses Firebase's official NetworkClient + Recaptcha.fetchTaskClient for 100% protocol parity
 * (including X-Firebase-Client heartbeat, X-Android-Package, and X-Android-Cert), while avoiding
 * the two SDK bugs that lock out users indefinitely:
 * 1) Never permanently caches a failed RecaptchaTasksClient task across the process lifetime.
 * 2) Passes a fresh RetryManager per call so transient/VPN errors never trigger a 24h process lockout.
 */
private class ResilientRecaptchaAppCheckProviderFactory(
    private val siteKey: String
) : AppCheckProviderFactory {

    @Volatile
    private var provider: ResilientRecaptchaAppCheckProvider? = null

    override fun create(firebaseApp: FirebaseApp): AppCheckProvider {
        return provider ?: synchronized(this) {
            provider ?: ResilientRecaptchaAppCheckProvider(firebaseApp, siteKey).also {
                provider = it
            }
        }
    }

    fun warmUpClientAsync(context: Context) {
        val app = context.applicationContext as? Application ?: return
        executor.execute {
            runCatching {
                val client = Tasks.await(Recaptcha.fetchTaskClient(app, siteKey), 18, TimeUnit.SECONDS)
                provider?.setWarmClient(client)
            }.onFailure { err ->
                Log.w(TAG, "Non-fatal reCAPTCHA warmup skip (will retry on demand): ${err.message}")
            }
        }
    }

    companion object {
        private val executor = Executors.newCachedThreadPool()
    }
}

private class ResilientRecaptchaAppCheckProvider(
    private val firebaseApp: FirebaseApp,
    private val siteKey: String
) : AppCheckProvider {

    @Volatile
    private var cachedClient: RecaptchaTasksClient? = null

    private val clientLock = Any()
    private val executor = Executors.newCachedThreadPool()
    private val networkClient = NetworkClient(firebaseApp)

    fun setWarmClient(client: RecaptchaTasksClient) {
        synchronized(clientLock) {
            if (cachedClient == null) {
                cachedClient = client
            }
        }
    }

    override fun getToken(): Task<AppCheckToken> = fetchTokenTask(limitedUse = false)

    override fun getLimitedUseToken(): Task<AppCheckToken> = fetchTokenTask(limitedUse = true)

    private fun fetchTokenTask(limitedUse: Boolean): Task<AppCheckToken> {
        return Tasks.call(executor) {
            val application = firebaseApp.applicationContext as? Application
                ?: throw FirebaseException("Application context unavailable for App Check.")

            val recaptchaToken = executeRecaptchaWithSelfHeal(application)
            val payload = JSONObject().apply {
                put("recaptchaEnterpriseToken", recaptchaToken)
                if (limitedUse) {
                    put("limited_use", true)
                }
            }.toString().toByteArray(StandardCharsets.UTF_8)

            // Fresh RetryManager prevents Firebase's 24-hour exponential backoff lockout when VPN/network switches
            val response = networkClient.exchangeAttestationForAppCheckToken(
                payload,
                NetworkClient.RECAPTCHA,
                RetryManager()
            )
            DefaultAppCheckToken.constructFromAppCheckTokenResponse(response)
        }
    }

    private fun executeRecaptchaWithSelfHeal(application: Application): String {
        var lastErr: Exception? = null
        for (attempt in 0..1) {
            try {
                val client = getOrCreateRecaptchaClient(application, forceNew = attempt > 0)
                val token = Tasks.await(
                    client.executeTask(RecaptchaAction.custom("fire_app_check")),
                    18,
                    TimeUnit.SECONDS
                )
                if (!token.isNullOrBlank()) {
                    return token
                }
                throw FirebaseException("reCAPTCHA Enterprise returned an empty token.")
            } catch (e: Exception) {
                lastErr = e
                Log.w(TAG, "reCAPTCHA execute attempt ${attempt + 1} failed: ${e.message}", e)
                synchronized(clientLock) {
                    cachedClient = null
                }
            }
        }
        val errMsg = "reCAPTCHA Enterprise verification failed: ${lastErr?.message ?: "unknown error"}"
        throw if (lastErr != null) {
            FirebaseException(errMsg, lastErr)
        } else {
            FirebaseException(errMsg)
        }
    }

    private fun getOrCreateRecaptchaClient(
        application: Application,
        forceNew: Boolean
    ): RecaptchaTasksClient {
        if (!forceNew) {
            cachedClient?.let { return it }
        }
        val freshClient = Tasks.await(
            Recaptcha.fetchTaskClient(application, siteKey),
            18,
            TimeUnit.SECONDS
        )
        synchronized(clientLock) {
            cachedClient = freshClient
        }
        return freshClient
    }
}
