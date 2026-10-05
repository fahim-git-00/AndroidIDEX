package com.aidex.fs

import android.content.Context
import java.io.File

object ProjectManager {

    fun projectsRoot(context: Context): File {
        val dir = File(context.getExternalFilesDir(null), "projects")
        if (!dir.exists()) dir.mkdirs()
        return dir
    }

    /**
     * Creates (once) a small buildable sample project. If the folder exists
     * but is missing AndroidManifest.xml, wipes and recreates it — this
     * handles migration from older AIDEX versions.
     */
    fun ensureSampleProject(context: Context): File {
        val root = File(projectsRoot(context), "SampleProject")
        val manifest = File(root, "AndroidManifest.xml")

        if (root.exists() && !manifest.exists()) {
            root.deleteRecursively()
        }
        if (root.exists()) return root

        val srcDir = File(root, "src/com/example")
        val resLayoutDir = File(root, "res/layout")
        val resValuesDir = File(root, "res/values")
        val resMipmapDir = File(root, "res/mipmap-anydpi-v26")
        listOf(srcDir, resLayoutDir, resValuesDir, resMipmapDir).forEach { it.mkdirs() }

        File(root, "AndroidManifest.xml").writeText(
            """
            <?xml version="1.0" encoding="utf-8"?>
            <manifest xmlns:android="http://schemas.android.com/apk/res/android"
                package="com.example.sample">

                <application
                    android:allowBackup="true"
                    android:label="@string/app_name"
                    android:icon="@mipmap/ic_launcher"
                    android:theme="@android:style/Theme.Material.Light">

                    <activity android:name=".MainActivity"
                        android:exported="true">
                        <intent-filter>
                            <action android:name="android.intent.action.MAIN" />
                            <category android:name="android.intent.category.LAUNCHER" />
                        </intent-filter>
                    </activity>
                </application>
            </manifest>
            """.trimIndent()
        )

        File(srcDir, "MainActivity.kt").writeText(
            """
            package com.example.sample

            import android.app.Activity
            import android.os.Bundle
            import android.widget.TextView

            class MainActivity : Activity() {
                override fun onCreate(savedInstanceState: Bundle?) {
                    super.onCreate(savedInstanceState)
                    val tv = TextView(this).apply {
                        text = "Hello from AIDEX!"
                        textSize = 22f
                    }
                    setContentView(tv)
                }
            }
            """.trimIndent()
        )

        File(resValuesDir, "strings.xml").writeText(
            """
            <?xml version="1.0" encoding="utf-8"?>
            <resources>
                <string name="app_name">SampleProject</string>
            </resources>
            """.trimIndent()
        )

        File(resMipmapDir, "ic_launcher.xml").writeText(
            """
            <?xml version="1.0" encoding="utf-8"?>
            <adaptive-icon xmlns:android="http://schemas.android.com/apk/res/android">
                <background android:drawable="@android:color/white"/>
                <foreground android:drawable="@android:color/holo_blue_light"/>
            </adaptive-icon>
            """.trimIndent()
        )

        File(root, "README.md").writeText(
            "# SampleProject\n\nTap **Build** in the toolbar to compile to APK."
        )

        return root
    }
}
