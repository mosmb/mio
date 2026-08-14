package io.morgan.mio

import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.xmlpull.v1.XmlPullParser

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class BackupRulesTest {
    private val context: Context
        get() = ApplicationProvider.getApplicationContext()

    @Test
    fun manifestReferencesBothBackupRuleFormatsAndDisablesBackup() {
        val applicationInfo =
            context.packageManager.getApplicationInfo(
                context.packageName,
                PackageManager.GET_META_DATA,
            )

        assertFalse(applicationInfo.flags and ApplicationInfo.FLAG_ALLOW_BACKUP != 0)
        assertNotEquals(0, context.resources.getIdentifier("backup_rules", "xml", context.packageName))
        assertNotEquals(
            0,
            context.resources.getIdentifier("data_extraction_rules", "xml", context.packageName),
        )
    }

    @Test
    fun legacyRulesExcludeEveryRequiredCloudDomainWithoutIncludes() {
        val exclusions = parseExclusions(R.xml.backup_rules)

        assertEquals(
            REQUIRED_CLOUD_DOMAINS,
            exclusions,
        )
    }

    @Test
    fun modernRulesExcludeEveryCloudAndDeviceTransferDomainWithoutIncludes() {
        val rules = parseModernRules(R.xml.data_extraction_rules)

        assertEquals(REQUIRED_CLOUD_DOMAINS, rules.cloudExclusions)
        assertEquals(REQUIRED_DEVICE_DOMAINS, rules.deviceExclusions)
        assertTrue(rules.includes.isEmpty())
    }

    private fun parseExclusions(resourceId: Int): Set<Pair<String, String>> {
        val parser = context.resources.getXml(resourceId)
        val exclusions = linkedSetOf<Pair<String, String>>()
        var event = parser.eventType
        while (event != XmlPullParser.END_DOCUMENT) {
            if (event == XmlPullParser.START_TAG) {
                assertFalse("Backup rules must not contain include entries", parser.name == "include")
                if (parser.name == "exclude") {
                    exclusions +=
                        parser.getAttributeValue(null, "domain") to
                        parser.getAttributeValue(null, "path")
                }
            }
            event = parser.next()
        }
        parser.close()
        return exclusions
    }

    private fun parseModernRules(resourceId: Int): ModernRules {
        val parser = context.resources.getXml(resourceId)
        val cloudExclusions = linkedSetOf<Pair<String, String>>()
        val deviceExclusions = linkedSetOf<Pair<String, String>>()
        val includes = mutableListOf<Pair<String, String>>()
        var section: String? = null
        var event = parser.eventType
        while (event != XmlPullParser.END_DOCUMENT) {
            when (event) {
                XmlPullParser.START_TAG -> {
                    when (parser.name) {
                        "cloud-backup", "device-transfer" -> section = parser.name
                        "include" ->
                            includes +=
                                parser.getAttributeValue(null, "domain") to
                                parser.getAttributeValue(null, "path")
                        "exclude" -> {
                            val exclusion =
                                parser.getAttributeValue(null, "domain") to
                                    parser.getAttributeValue(null, "path")
                            when (section) {
                                "cloud-backup" -> cloudExclusions += exclusion
                                "device-transfer" -> deviceExclusions += exclusion
                            }
                        }
                    }
                }
                XmlPullParser.END_TAG ->
                    if (parser.name == "cloud-backup" || parser.name == "device-transfer") {
                        section = null
                    }
            }
            event = parser.next()
        }
        parser.close()
        return ModernRules(cloudExclusions, deviceExclusions, includes)
    }

    private data class ModernRules(
        val cloudExclusions: Set<Pair<String, String>>,
        val deviceExclusions: Set<Pair<String, String>>,
        val includes: List<Pair<String, String>>,
    )

    private companion object {
        val REQUIRED_CLOUD_DOMAINS =
            setOf("root", "file", "database", "sharedpref", "external")
                .map { it to "." }
                .toSet()
        val REQUIRED_DEVICE_DOMAINS =
            setOf("device_root", "device_file", "device_database", "device_sharedpref")
                .map { it to "." }
                .toSet()
    }
}
