package com.example.marshallsmetronome

import android.content.Context
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import org.junit.Assert.assertEquals
import org.junit.Test
import org.mockito.ArgumentMatchers.anyInt
import org.mockito.ArgumentMatchers.anyString
import org.mockito.Mockito.mock
import org.mockito.Mockito.`when`

@Suppress("FunctionMaxLength")
class MainActivityKtUnitTest {
    // Tests for getAppVersion
    @Test
    fun `getAppVersion returns correct version`() {
        val context = mock(Context::class.java)
        val packageManager = mock(PackageManager::class.java)
        val packageInfo = PackageInfo().apply { versionName = "1.0" }
        val expectedPackageName = "com.example.app"

        `when`(context.packageName).thenReturn(expectedPackageName)
        `when`(context.packageManager).thenReturn(packageManager)
        `when`(packageManager.getPackageInfo(expectedPackageName, 0)).thenReturn(packageInfo)

        assertEquals("1.0", getAppVersion(context))
    }

    @Test
    fun `getAppVersion returns Unknown when PackageManager is null`() {
        val context = mock(Context::class.java)

        `when`(context.packageManager).thenReturn(null)

        assertEquals("Unknown", getAppVersion(context))
    }

    @Test
    fun `getAppVersion returns Unknown when PackageInfo is null`() {
        val context = mock(Context::class.java)
        val packageManager = mock(PackageManager::class.java)

        `when`(context.packageManager).thenReturn(packageManager)
        `when`(packageManager.getPackageInfo(anyString(), anyInt())).thenReturn(null)

        assertEquals("Unknown", getAppVersion(context))
    }

    @Test
    fun `getAppVersion returns Unknown when versionName is null`() {
        val context = mock(Context::class.java)
        val packageManager = mock(PackageManager::class.java)
        val packageInfo = PackageInfo().apply { versionName = null }

        `when`(context.packageManager).thenReturn(packageManager)
        `when`(packageManager.getPackageInfo(anyString(), anyInt())).thenReturn(packageInfo)

        assertEquals("Unknown", getAppVersion(context))
    }

    @Test
    fun `getAppVersion returns Unknown when NameNotFoundException is thrown`() {
        val context = mock(Context::class.java)
        val packageManager = mock(PackageManager::class.java)

        `when`(context.packageName).thenReturn("com.example.app")
        `when`(context.packageManager).thenReturn(packageManager)
        `when`(packageManager.getPackageInfo(anyString(), anyInt())).thenThrow(PackageManager.NameNotFoundException())

        val result = getAppVersion(context)

        assertEquals("Unknown", result)
    }
}
