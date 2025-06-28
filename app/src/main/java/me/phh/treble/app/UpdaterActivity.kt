package me.phh.treble.app

import android.app.AlertDialog
import android.os.Bundle
import android.os.SystemProperties
import android.preference.PreferenceActivity
import android.util.Log
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.Button
import android.widget.TextView
import java.io.File
import java.io.InputStream
import kotlin.concurrent.thread

class UpdaterActivity : PreferenceActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_updater)
        actionBar?.setDisplayHomeAsUpEnabled(true)

        val updateTitle = findViewById<TextView>(R.id.txt_update_title)
        updateTitle.text = "PHH Updater"

        val btnUpdate = findViewById<Button>(R.id.btn_update)
        btnUpdate.setOnClickListener {
            AlertDialog.Builder(this)
                .setTitle("Info")
                .setMessage("This is a demo. Use menu to flash system.img.")
                .setPositiveButton("OK", null)
                .show()
        }
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.menu_main, menu)
        // Add custom option for direct flashing
        menu.add(0, R.id.menu_flash_active_slot, 0, "Flash system.img to Active Slot")
        return super.onCreateOptionsMenu(menu)
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            R.id.menu_flash_active_slot -> {
                flashSystemImageToActiveSlot()
                return true
            }
            R.id.menu_delete_ota -> {
                AlertDialog.Builder(this)
                    .setTitle("Warning")
                    .setMessage("Delete OTA request?")
                    .setPositiveButton("Yes") { _, _ ->
                        SystemProperties.set("sys.phh.uninstall-ota", "true")
                    }
                    .setNegativeButton("No", null)
                    .show()
                return true
            }
            android.R.id.home -> {
                finish()
                return true
            }
        }
        return super.onOptionsItemSelected(item)
    }

    private fun flashSystemImageToActiveSlot() {
        val imageFile = File("/sdcard/system.img")
        if (!imageFile.exists()) {
            AlertDialog.Builder(this)
                .setTitle("Missing Image")
                .setMessage("system.img not found at /sdcard/")
                .setPositiveButton("OK", null)
                .show()
            return
        }

        thread {
            try {
                val slotCmd = Runtime.getRuntime().exec(arrayOf("su", "-c", "bootctl get-current-slot"))
                val slot = slotCmd.inputStream.bufferedReader().readText().trim()
                val suffix = when (slot) {
                    "0" -> "_a"
                    "1" -> "_b"
                    else -> {
                        runOnUiThread {
                            AlertDialog.Builder(this)
                                .setTitle("Error")
                                .setMessage("Could not detect current slot.")
                                .setPositiveButton("OK", null)
                                .show()
                        }
                        return@thread
                    }
                }

                val targetPath = "/dev/block/mapper/system$suffix"

                runOnUiThread {
                    AlertDialog.Builder(this)
                        .setTitle("Flash system.img?")
                        .setMessage("This will overwrite the active slot ($targetPath). Proceed?")
                        .setPositiveButton("Yes") { _, _ ->
                            thread {
                                try {
                                    val flashCmd = arrayOf("su", "-c", "dd if=${imageFile.absolutePath} of=$targetPath bs=4M")
                                    val proc = Runtime.getRuntime().exec(flashCmd)
                                    proc.waitFor()

                                    runOnUiThread {
                                        AlertDialog.Builder(this)
                                            .setTitle("Flash Complete")
                                            .setMessage("Image flashed to $targetPath. Reboot now?")
                                            .setPositiveButton("Reboot") { _, _ ->
                                                Runtime.getRuntime().exec(arrayOf("su", "-c", "reboot"))
                                            }
                                            .setNegativeButton("Later", null)
                                            .show()
                                    }
                                } catch (e: Exception) {
                                    Log.e("PHH", "Flashing failed", e)
                                    runOnUiThread {
                                        AlertDialog.Builder(this)
                                            .setTitle("Error")
                                            .setMessage("Failed to flash: ${e.message}")
                                            .setPositiveButton("OK", null)
                                            .show()
                                    }
                                }
                            }
                        }
                        .setNegativeButton("Cancel", null)
                        .show()
                }
            } catch (e: Exception) {
                Log.e("PHH", "Slot detection failed", e)
                runOnUiThread {
                    AlertDialog.Builder(this)
                        .setTitle("Error")
                        .setMessage("Could not detect slot: ${e.message}")
                        .setPositiveButton("OK", null)
                        .show()
                }
            }
        }
    }
}
