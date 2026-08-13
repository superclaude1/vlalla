package com.storybrain.app.settings

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AndroidDataProtectionContractTest {
    private val manifest = File("src/main/AndroidManifest.xml").readText()

    @Test
    fun appDataIsNotIncludedInAndroidBackupOrTransfer() {
        assertTrue(manifest.contains("android:allowBackup=\"false\""))
        assertTrue(manifest.contains("android:fullBackupContent=\"@xml/backup_rules\""))
        assertTrue(manifest.contains("android:dataExtractionRules=\"@xml/data_extraction_rules\""))
        assertFalse(manifest.contains("android:allowBackup=\"true\""))
    }

    @Test
    fun cleartextTrafficRemainsDisabled() {
        assertTrue(manifest.contains("android:usesCleartextTraffic=\"false\""))
    }
}
