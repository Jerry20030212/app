package com.ex.mountaintimer

import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.auth.FirebaseAuth
import java.util.*

/**
 * Firebase 管理類別
 * 負責處理問題回報、使用者登入等雲端功能
 */
object FirebaseManager {

    private val database = FirebaseDatabase.getInstance()
    private val auth = FirebaseAuth.getInstance()

    /**
     * 回報問題到 Firebase Realtime Database
     */
    fun reportIssue(description: String, onComplete: (Boolean) -> Unit) {
        val user = auth.currentUser
        val userId = user?.uid ?: "anonymous"
        val userEmail = user?.email ?: "no_email"
        
        val issueRef = database.getReference("issues").push()
        val issueData = mapOf(
            "userId" to userId,
            "userEmail" to userEmail,
            "description" to description,
            "timestamp" to System.currentTimeMillis(),
            "status" to "open",
            "deviceInfo" to "${android.os.Build.MANUFACTURER} ${android.os.Build.MODEL} (API ${android.os.Build.VERSION.SDK_INT})",
            "appVersion" to "1.0"
        )

        issueRef.setValue(issueData)
            .addOnSuccessListener { onComplete(true) }
            .addOnFailureListener { onComplete(false) }
    }

    /**
     * 檢查是否已登入
     */
    fun isUserLoggedIn(): Boolean {
        return auth.currentUser != null
    }

    /**
     * 獲取目前使用者 Email
     */
    fun getUserEmail(): String? {
        return auth.currentUser?.email
    }
}
