package com.example.data

import android.content.Context
import com.example.R
import com.example.firebase.OperationType
import com.example.firebase.handleFirestoreError
import com.example.model.RecapScript
import com.example.model.SceneBeat
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.auth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.snapshots
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await

class RecapRepository(
    private val db: FirebaseFirestore,
    private val auth: FirebaseAuth = Firebase.auth
) {
    constructor(context: Context) : this(
        FirebaseFirestore.getInstance(
            context.applicationContext.getString(R.string.firestore_database_id)
        )
    )

    private fun requireUserId(): String {
        return auth.currentUser?.uid
            ?: throw IllegalStateException("User must be signed in with Google before accessing Firestore.")
    }

    fun observeScripts(userId: String): Flow<List<RecapScript>> = flow {
        val uid = requireUserId()
        check(uid == userId) { "Signed-in user changed; refusing cross-user observation." }
        val path = "users/$uid/recapScripts"
        emitAll(
            db.collection("users")
                .document(uid)
                .collection("recapScripts")
                .orderBy("createdAt", Query.Direction.DESCENDING)
                .snapshots()
                .map { snapshot ->
                    snapshot.toObjects(
                        RecapScript::class.java,
                        DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                    )
                }
                .catch { error ->
                    val firestoreError = generateSequence(error) { it.cause }
                        .filterIsInstance<FirebaseFirestoreException>()
                        .firstOrNull()
                    if (firestoreError == null && error is CancellationException) throw error
                    val exceptionToLog = firestoreError ?: (error as? Exception)
                    if (exceptionToLog != null && auth.currentUser?.uid == uid) {
                        handleFirestoreError(exceptionToLog, OperationType.LIST, path)
                    }
                    throw error
                }
        )
    }

    suspend fun saveScript(script: RecapScript): Result<String> {
        var docPath = "users/${auth.currentUser?.uid ?: "unauthenticated"}/recapScripts"
        return try {
            val uid = requireUserId()
            val docRef = db.collection("users")
                .document(uid)
                .collection("recapScripts")
                .document()
            docPath = docRef.path
            val payload = script.toCreateMap(uid)
            docRef.set(payload).await()
            Result.success(docRef.id)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, docPath)
            Result.failure(e)
        }
    }

    suspend fun toggleFavorite(userId: String, scriptId: String, favorite: Boolean): Result<Unit> {
        var docPath = "users/$userId/recapScripts/$scriptId"
        return try {
            val uid = requireUserId()
            check(uid == userId) { "Signed-in user changed; refusing cross-user write." }
            require(scriptId.isNotBlank()) { "Script ID must not be blank." }
            val docRef = db.collection("users")
                .document(uid)
                .collection("recapScripts")
                .document(scriptId)
            docPath = docRef.path
            docRef.update(
                mapOf(
                    "favorite" to favorite,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, docPath)
            Result.failure(e)
        }
    }

    suspend fun updateScriptDetails(
        userId: String,
        scriptId: String,
        sourceTitle: String,
        openingHook: String,
        outro: String,
        directorNotes: String?,
        viralTitles: List<String>,
        scenes: List<SceneBeat>
    ): Result<Unit> {
        var docPath = "users/$userId/recapScripts/$scriptId"
        return try {
            val uid = requireUserId()
            check(uid == userId) { "Signed-in user changed; refusing cross-user write." }
            require(scriptId.isNotBlank()) { "Script ID must not be blank." }
            val cleanTitle = sourceTitle.trim().take(160)
            require(cleanTitle.isNotEmpty()) { "Title cannot be empty." }
            val cleanHook = openingHook.trim().take(2500)
            require(cleanHook.isNotEmpty()) { "Opening hook cannot be empty." }
            val cleanOutro = outro.trim().take(2000)
            require(cleanOutro.isNotEmpty()) { "Outro cannot be empty." }
            val cleanTitles = viralTitles.map { it.trim().take(240) }.filter { it.isNotEmpty() }.take(5)
            require(cleanTitles.isNotEmpty()) { "At least one viral title is required." }
            val cleanScenes = scenes.filter { it.voiceover.isNotBlank() }.take(60).map {
                mapOf(
                    "timestamp" to it.timestamp.trim().ifBlank { "00:15" }.take(20),
                    "visualCue" to it.visualCue.trim().take(300),
                    "voiceover" to it.voiceover.trim().take(1500)
                )
            }
            require(cleanScenes.isNotEmpty()) { "At least one scene beat is required." }

            val updates = mutableMapOf<String, Any>(
                "sourceTitle" to cleanTitle,
                "openingHook" to cleanHook,
                "outro" to cleanOutro,
                "viralTitles" to cleanTitles,
                "scenes" to cleanScenes,
                "updatedAt" to FieldValue.serverTimestamp()
            )
            val cleanNotes = directorNotes?.trim()?.takeIf { it.isNotEmpty() }?.take(1000)
            if (cleanNotes != null) {
                updates["directorNotes"] = cleanNotes
            }

            val docRef = db.collection("users")
                .document(uid)
                .collection("recapScripts")
                .document(scriptId)
            docPath = docRef.path
            docRef.update(updates).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, docPath)
            Result.failure(e)
        }
    }

    suspend fun deleteScript(userId: String, scriptId: String): Result<Unit> {
        var docPath = "users/$userId/recapScripts/$scriptId"
        return try {
            val uid = requireUserId()
            check(uid == userId) { "Signed-in user changed; refusing cross-user delete." }
            require(scriptId.isNotBlank()) { "Script ID must not be blank." }
            val docRef = db.collection("users")
                .document(uid)
                .collection("recapScripts")
                .document(scriptId)
            docPath = docRef.path
            docRef.delete().await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, docPath)
            Result.failure(e)
        }
    }
}
