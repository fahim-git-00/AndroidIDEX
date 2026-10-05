package com.aidex.project

import android.content.Context
import java.io.File

/**
 * Helpers for creating / opening / deleting projects in app-private storage.
 */
object ProjectActions {

    fun projectsRoot(context: Context): File {
        val dir = File(context.getExternalFilesDir(null), "projects")
        if (!dir.exists()) dir.mkdirs()
        return dir
    }

    data class CreateResult(val root: File, val packageName: String)

    /**
     * Creates a fresh project under projectsRoot/&lt;name&gt; with a minimal
     * buildable Kotlin Android app.
     */
    fun createProject(context: Context, rawName: String): CreateResult {
        val name = sanitizeName(rawName)
        val pkg = "com.aidex.user.${name.lowercase().replace("-", "_")}"
        val root = File(projectsRoot(context), name)
        require(!root.exists()) { "Project '$name' already exists." }

        // Directory layout
        val srcDir = File(root, "src/$pkg".replace(".", "/"))
        val resLayoutDir = File(root, "res/layout")
        val resValuesDir = File(root, "res/values")
        val resMipmapDir = File(root, "res/mipmap-anydpi-v26")
        listOf(srcDir, resLayoutDir, resValuesDir, resMipmapDir).forEach { it.mkdirs() }

        // AndroidManifest.xml
        File(root, "AndroidManifest.xml").writeText(
            """
            <?xml version="1.0" encoding="utf-8"?>
            <manifest xmlns:android="http://schemas.android.com/apk/res/android"
                package="$pkg">

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

        // MainActivity.java
        File(srcDir, "MainActivity.java").writeText(
            """
            package $pkg;

            import android.app.Activity;
            import android.os.Bundle;
            import android.widget.TextView;

            public class MainActivity extends Activity {
                @Override
                protected void onCreate(Bundle savedInstanceState) {
                    super.onCreate(savedInstanceState);
                    TextView tv = new TextView(this);
                    tv.setText("Hello from $name!");
                    tv.setTextSize(22f);
                    setContentView(tv);
                }
            }
            """.trimIndent()
        )

        // strings.xml
        File(resValuesDir, "strings.xml").writeText(
            """
            <?xml version="1.0" encoding="utf-8"?>
            <resources>
                <string name="app_name">$name</string>
            </resources>
            """.trimIndent()
        )

        // ic_launcher.xml (adaptive placeholder — vector foreground)
        File(resMipmapDir, "ic_launcher.xml").writeText(
            """
            <?xml version="1.0" encoding="utf-8"?>
            <adaptive-icon xmlns:android="http://schemas.android.com/apk/res/android">
                <background android:drawable="@android:color/white"/>
                <foreground android:drawable="@android:color/holo_blue_light"/>
            </adaptive-icon>
            """.trimIndent()
        )

        // README
        File(root, "README.md").writeText(
            """
            # $name

            Package: `$pkg`

            Tap **Build** in AIDEX to compile this project into an installable APK.
            """.trimIndent()
        )

        return CreateResult(root, pkg)
    }

    fun deleteProject(context: Context, root: File): Boolean {
        if (!root.exists()) return false
        val projectsRoot = projectsRoot(context).absolutePath
        if (!root.absolutePath.startsWith(projectsRoot)) return false
        return root.deleteRecursively()
    }

    fun listProjects(context: Context): List<File> =
        projectsRoot(context)
            .listFiles { f -> f.isDirectory }
            ?.sortedBy { it.name.lowercase() }
            ?: emptyList()

    private fun sanitizeName(raw: String): String {
        val cleaned = raw.trim()
            .replace(Regex("[^A-Za-z0-9_\\-]"), "_")
            .ifBlank { "UntitledProject" }
        return cleaned.replaceFirstChar { it.uppercase() }
    }
}
