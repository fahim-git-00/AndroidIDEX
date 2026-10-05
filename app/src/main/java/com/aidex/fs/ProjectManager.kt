package com.aidex.fs

import android.content.Context
import java.io.File

object ProjectManager {

    fun projectsRoot(context: Context): File {
        val dir = File(context.getExternalFilesDir(null), "projects")
        if (!dir.exists()) dir.mkdirs()
        return dir
    }

    /** Creates (once) a small sample project so the app is useful immediately. */
    fun ensureSampleProject(context: Context): File {
        val root = File(projectsRoot(context), "SampleProject")
        if (root.exists()) return root

        File(root, "src/main/java/com/example").mkdirs()
        File(root, "src/main/res/layout").mkdirs()
        File(root, "src/main/res/values").mkdirs()

        File(root, "README.md").writeText(
            """
            # SampleProject

            - Tap a folder in the left drawer to expand it.
            - Tap a file to open it in a new tab.
            - Edit -> tap Save to persist.
            - Copy Code copies the editor buffer to the clipboard.
            - Any runtime crash pops up a copy-ready report.
            """.trimIndent()
        )

        File(root, "src/main/java/com/example/Main.kt").writeText(
            """
            package com.example

            data class Greeting(val name: String) {
                fun render(): String = "Hello, ${'$'}name!"
            }

            fun main() {
                val g = Greeting("AIDEX")
                println(g.render())
            }
            """.trimIndent()
        )

        File(root, "src/main/java/com/example/Hello.java").writeText(
            """
            package com.example;

            public class Hello {
                public static void main(String[] args) {
                    System.out.println("Hello from Java 17");
                }
            }
            """.trimIndent()
        )

        File(root, "src/main/res/layout/activity_sample.xml").writeText(
            """
            <?xml version="1.0" encoding="utf-8"?>
            <LinearLayout xmlns:android="http://schemas.android.com/apk/res/android"
                android:layout_width="match_parent"
                android:layout_height="match_parent"
                android:orientation="vertical">

                <TextView
                    android:id="@+id/title"
                    android:layout_width="wrap_content"
                    android:layout_height="wrap_content"
                    android:text="Sample Layout" />
            </LinearLayout>
            """.trimIndent()
        )

        return root
    }
}
