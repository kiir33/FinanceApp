package com.example.financeapp.auth

import com.example.financeapp.database.AppDatabase
import com.example.financeapp.database.dao.UserDao
import com.example.financeapp.database.entities.User
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.mockito.Mockito.*

class AuthServiceTest {

    private lateinit var mockDb: AppDatabase
    private lateinit var mockUserDao: UserDao
    private lateinit var authService: AuthService

    @Before
    fun setup() {
        mockDb = mock(AppDatabase::class.java)
        mockUserDao = mock(UserDao::class.java)
        `when`(mockDb.userDao()).thenReturn(mockUserDao)
        authService = AuthService.getInstanceForTest(mockDb)
    }

    @Test
    fun `login succeeds with correct credentials`() {
        val user = User(
            id = 1,
            username = "testuser",
            passwordHash = PasswordHasher.hashPassword("TestPass123", PasswordHasher.generateSalt()),
            salt = PasswordHasher.generateSalt(),
            role = "ADMIN",
            clientId = 1L,
            isActive = true,
            failedAttempts = 0,
            lockedUntil = null,
            mustChangePassword = false,
            createdAt = System.currentTimeMillis(),
            lastLoginAt = null
        )

        `when`(mockUserDao.findUserByLoginIdentifier("testuser")).thenReturn(user)

        val result = authService.login("testuser", "TestPass123")

        assertTrue(result.isSuccess)
        assertEquals(1L, result.user?.id)
        assertEquals(0, result.user?.failedAttempts)
        assertNull(result.user?.lockedUntil)
    }

    @Test
    fun `login fails with incorrect password`() {
        val user = User(
            id = 1,
            username = "testuser",
            passwordHash = PasswordHasher.hashPassword("TestPass123", PasswordHasher.generateSalt()),
            salt = PasswordHasher.generateSalt(),
            role = "ADMIN",
            clientId = 1L,
            isActive = true,
            failedAttempts = 0,
            lockedUntil = null,
            mustChangePassword = false,
            createdAt = System.currentTimeMillis(),
            lastLoginAt = null
        )

        `when`(mockUserDao.findUserByLoginIdentifier("testuser")).thenReturn(user)

        val result = authService.login("testuser", "WrongPass123")

        assertFalse(result.isSuccess)
        assertEquals("Invalid credentials", result.errorMessage)
    }

    @Test
    fun `login increments failed attempts on wrong password`() {
        val salt = PasswordHasher.generateSalt()
        val user = User(
            id = 1,
            username = "testuser",
            passwordHash = PasswordHasher.hashPassword("TestPass123", salt),
            salt = salt,
            role = "ADMIN",
            clientId = 1L,
            isActive = true,
            failedAttempts = 0,
            lockedUntil = null,
            mustChangePassword = false,
            createdAt = System.currentTimeMillis(),
            lastLoginAt = null
        )

        `when`(mockUserDao.findUserByLoginIdentifier("testuser")).thenReturn(user)
        `when`(mockUserDao.updateUser(any())).thenReturn(1)

        authService.login("testuser", "WrongPass123")
        authService.login("testuser", "WrongPass123")
        authService.login("testuser", "WrongPass123")

        verify(mockUserDao, times(3)).updateUser(argThat { it.failedAttempts >= 1 })
    }

    @Test
    fun `login locks account after 5 failed attempts`() {
        val salt = PasswordHasher.generateSalt()
        val user = User(
            id = 1,
            username = "testuser",
            passwordHash = PasswordHasher.hashPassword("TestPass123", salt),
            salt = salt,
            role = "ADMIN",
            clientId = 1L,
            isActive = true,
            failedAttempts = 4, // Already 4 failed attempts
            lockedUntil = null,
            mustChangePassword = false,
            createdAt = System.currentTimeMillis(),
            lastLoginAt = null
        )

        `when`(mockUserDao.findUserByLoginIdentifier("testuser")).thenReturn(user)
        `when`(mockUserDao.updateUser(any())).thenReturn(1)

        val result = authService.login("testuser", "WrongPass123")

        assertFalse(result.isSuccess)
        assertTrue(result.errorMessage?.contains("locked") == true)
        verify(mockUserDao).updateUser(argThat { it.failedAttempts == 5 && it.lockedUntil != null })
    }

    @Test
    fun `lockout expires after 15 minutes`() {
        val salt = PasswordHasher.generateSalt()
        val lockedUntil = System.currentTimeMillis() - 1000 // Already expired (1 second ago)
        val user = User(
            id = 1,
            username = "testuser",
            passwordHash = PasswordHasher.hashPassword("TestPass123", salt),
            salt = salt,
            role = "ADMIN",
            clientId = 1L,
            isActive = true,
            failedAttempts = 5,
            lockedUntil = lockedUntil,
            mustChangePassword = false,
            createdAt = System.currentTimeMillis(),
            lastLoginAt = null
        )

        `when`(mockUserDao.findUserByLoginIdentifier("testuser")).thenReturn(user)
        `when`(mockUserDao.updateUser(any())).thenReturn(1)

        val result = authService.login("testuser", "TestPass123")

        assertTrue("Lock should have expired, login should succeed", result.isSuccess)
        assertEquals(0, result.user?.failedAttempts)
        assertNull(result.user?.lockedUntil)
    }

    @Test
    fun `successful login resets failed attempts and lock`() {
        val salt = PasswordHasher.generateSalt()
        val user = User(
            id = 1,
            username = "testuser",
            passwordHash = PasswordHasher.hashPassword("TestPass123", salt),
            salt = salt,
            role = "ADMIN",
            clientId = 1L,
            isActive = true,
            failedAttempts = 3,
            lockedUntil = System.currentTimeMillis() + 100000, // Locked for future
            mustChangePassword = false,
            createdAt = System.currentTimeMillis(),
            lastLoginAt = null
        )

        `when`(mockUserDao.findUserByLoginIdentifier("testuser")).thenReturn(user)
        `when`(mockUserDao.updateUser(any())).thenReturn(1)

        val result = authService.login("testuser", "TestPass123")

        assertTrue(result.isSuccess)
        assertEquals(0, result.user?.failedAttempts)
        assertNull(result.user?.lockedUntil)
    }

    @Test
    fun `login fails for inactive user`() {
        val user = User(
            id = 1,
            username = "testuser",
            passwordHash = PasswordHasher.hashPassword("TestPass123", PasswordHasher.generateSalt()),
            salt = PasswordHasher.generateSalt(),
            role = "ADMIN",
            clientId = 1L,
            isActive = false, // Inactive
            failedAttempts = 0,
            lockedUntil = null,
            mustChangePassword = false,
            createdAt = System.currentTimeMillis(),
            lastLoginAt = null
        )

        `when`(mockUserDao.findUserByLoginIdentifier("testuser")).thenReturn(user)

        val result = authService.login("testuser", "TestPass123")

        assertFalse(result.isSuccess)
        assertEquals("Account is inactive", result.errorMessage)
    }

    @Test
    fun `login fails for non-existent user`() {
        `when`(mockUserDao.findUserByLoginIdentifier("nonexistent")).thenReturn(null)

        val result = authService.login("nonexistent", "TestPass123")

        assertFalse(result.isSuccess)
        assertEquals("Invalid credentials", result.errorMessage)
    }

    @Test
    fun `first login with mustChangePassword redirects to change password`() {
        val user = User(
            id = 1,
            username = "testuser",
            passwordHash = PasswordHasher.hashPassword("TestPass123", PasswordHasher.generateSalt()),
            salt = PasswordHasher.generateSalt(),
            role = "MEMBER",
            clientId = 1L,
            isActive = true,
            failedAttempts = 0,
            lockedUntil = null,
            mustChangePassword = true, // Must change password
            createdAt = System.currentTimeMillis(),
            lastLoginAt = null
        )

        `when`(mockUserDao.findUserByLoginIdentifier("testuser")).thenReturn(user)
        `when`(mockUserDao.updateUser(any())).thenReturn(1)

        val result = authService.login("testuser", "TestPass123")

        assertTrue(result.isSuccess)
        assertTrue(result.mustChangePassword)
    }

    @Test
    fun `changePassword updates hash and resets mustChangePassword`() {
        val oldSalt = PasswordHasher.generateSalt()
        val user = User(
            id = 1,
            username = "testuser",
            passwordHash = PasswordHasher.hashPassword("OldPass123", oldSalt),
            salt = oldSalt,
            role = "MEMBER",
            clientId = 1L,
            isActive = true,
            failedAttempts = 0,
            lockedUntil = null,
            mustChangePassword = true,
            createdAt = System.currentTimeMillis(),
            lastLoginAt = null
        )

        `when`(mockUserDao.findUserByLoginIdentifier("testuser")).thenReturn(user)
        `when`(mockUserDao.updateUser(any())).thenReturn(1)

        val result = authService.changePassword("testuser", "OldPass123", "NewPass123")

        assertTrue(result.isSuccess)
        verify(mockUserDao).updateUser(argThat {
            it.mustChangePassword == false &&
            PasswordHasher.verifyPassword("NewPass123", it.salt, it.passwordHash)
        })
    }

    @Test
    fun `changePassword fails with wrong old password`() {
        val user = User(
            id = 1,
            username = "testuser",
            passwordHash = PasswordHasher.hashPassword("OldPass123", PasswordHasher.generateSalt()),
            salt = PasswordHasher.generateSalt(),
            role = "MEMBER",
            clientId = 1L,
            isActive = true,
            failedAttempts = 0,
            lockedUntil = null,
            mustChangePassword = true,
            createdAt = System.currentTimeMillis(),
            lastLoginAt = null
        )

        `when`(mockUserDao.findUserByLoginIdentifier("testuser")).thenReturn(user)

        val result = authService.changePassword("testuser", "WrongOldPass", "NewPass123")

        assertFalse(result.isSuccess)
        assertEquals("Current password is incorrect", result.errorMessage)
    }

    @Test
    fun `isFirstRun returns true when no users exist`() {
        `when`(mockUserDao.getUserCount()).thenReturn(0)

        val result = authService.isFirstRun()

        assertTrue(result.getOrElse { false })
    }

    @Test
    fun `isFirstRun returns false when users exist`() {
        `when`(mockUserDao.getUserCount()).thenReturn(1)

        val result = authService.isFirstRun()

        assertFalse(result.getOrElse { true })
    }
}