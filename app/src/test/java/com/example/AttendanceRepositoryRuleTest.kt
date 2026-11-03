package com.example

import com.example.base.FirestoreEmulatorTestBase
import com.example.data.model.AttendanceRecord
import com.example.data.model.StudentProfile
import com.google.firebase.firestore.FirebaseFirestoreException
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class AttendanceRepositoryRuleTest : FirestoreEmulatorTestBase() {

    @Test
    fun userProfile_authenticatedUser_canWriteAndReadOwnProfile() = runBlocking {
        val uid = signInTestUser("bhawishya@test.com")
        val userDoc = firestore.collection("users").document(uid)

        val profile = StudentProfile(
            userId = uid,
            name = "Bhawishya Kumar",
            dateOfBirth = "2005-10-26",
            batch = "BATCH_A",
            courseSelection = "MAJOR_HONOURS",
            examTarget = 75,
            stipendTarget = 80,
            safetyBuffer = 4,
            futureMarkingEnabled = false,
            isDarkMode = true,
            starfieldAnimationEnabled = true,
            progressiveBlurEnabled = true
        )

        withTimeout(5000L) {
            userDoc.set(profile).await()
            val snapshot = userDoc.get().await()
            assertTrue(snapshot.exists())
            assertEquals("Bhawishya Kumar", snapshot.getString("name"))
            assertEquals("BATCH_A", snapshot.getString("batch"))
        }
    }

    @Test
    fun userProfile_crossUserAccess_isDenied() = runBlocking {
        val aliceUid = signInTestUser("alice@test.com")
        val aliceDoc = firestore.collection("users").document(aliceUid)
        withTimeout(5000L) {
            aliceDoc.set(
                StudentProfile(
                    userId = aliceUid,
                    name = "Alice",
                    batch = "BATCH_A",
                    courseSelection = "MAJOR_HONOURS",
                    examTarget = 75,
                    stipendTarget = 80,
                    safetyBuffer = 4,
                    futureMarkingEnabled = false,
                    isDarkMode = true,
                    starfieldAnimationEnabled = true,
                    progressiveBlurEnabled = true
                )
            ).await()
        }

        // Bob tries to read Alice's profile
        signInTestUser("bob@test.com")
        try {
            withTimeout(5000L) {
                aliceDoc.get().await()
            }
            fail("Expected PERMISSION_DENIED on cross-user read")
        } catch (e: Exception) {
            val firestoreException = e.unwrapFirestoreException()
            assertNotNull(firestoreException)
            assertEquals(FirebaseFirestoreException.Code.PERMISSION_DENIED, firestoreException?.code)
        }
    }

    @Test
    fun attendanceRecord_unauthenticatedAccess_isDenied() = runBlocking {
        auth.signOut()
        val recordDoc = firestore.collection("users").document("someone").collection("records").document("2026-10-28")
        try {
            withTimeout(5000L) {
                recordDoc.get().await()
            }
            fail("Expected PERMISSION_DENIED for unauthenticated read")
        } catch (e: Exception) {
            val firestoreException = e.unwrapFirestoreException()
            assertNotNull(firestoreException)
            assertEquals(FirebaseFirestoreException.Code.PERMISSION_DENIED, firestoreException?.code)
        }
    }
}
