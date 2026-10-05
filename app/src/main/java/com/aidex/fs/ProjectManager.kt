package com.aidex.fs

import android.content.Context
import java.io.File

object ProjectManager {

    fun projectsRoot(context: Context): File {
        val dir = File(context.getExternalFilesDir(null), "projects")
        if (!dir.exists()) dir.mkdirs()
        return dir
    }

    fun ensureSampleProject(context: Context): File {
        val root = File(projectsRoot(context), "SampleProject")
        val manifest = File(root, "AndroidManifest.xml")

        if (root.exists() && !manifest.exists()) {
            root.deleteRecursively()
        }
        if (root.exists()) return root

        val srcDir = File(root, "src/com/example/sample")
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

        File(srcDir, "MainActivity.java").writeText(
            """
            package com.example.sample;

            import android.app.Activity;
            import android.os.Bundle;
            import android.widget.TextView;

            public class MainActivity extends Activity {
                @Override
                protected void onCreate(Bundle savedInstanceState) {
                    super.onCreate(savedInstanceState);
                    TextView tv = new TextView(this);
                    tv.setText("Hello from AIDEX!");
                    tv.setTextSize(22f);
                    setContentView(tv);
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
            "# SampleProject\n\nA minimal Java + XML Android app.\n\nTap **Build** to compile to APK."
        )

        return root
    }
}
