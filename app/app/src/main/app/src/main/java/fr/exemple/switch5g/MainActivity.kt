package fr.exemple.switch5g

import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.provider.Settings
import android.widget.Toast
import rikka.shizuku.Shizuku

class MainActivity : Activity() {

    companion object {
        private const val REQ = 42
        // Masques binaires des types de réseau autorisés
        private const val MASK_5G = "11111111111111111111" // tout autorisé, 5G (NR) incluse
        private const val MASK_4G = "01111111111111111111" // tout sauf la 5G
    }

    private val listener = Shizuku.OnRequestPermissionResultListener { code, result ->
        if (code == REQ) {
            if (result == PackageManager.PERMISSION_GRANTED) toggle()
            else { toast("Permission Shizuku refusée"); finish() }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (!Shizuku.pingBinder()) {
            toast("Shizuku n'est pas démarré : ouverture des réglages")
            openNetworkSettings()
            finish()
            return
        }
        if (Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED) {
            toggle()
        } else {
            Shizuku.addRequestPermissionResultListener(listener)
            Shizuku.requestPermission(REQ)
        }
    }

    override fun onDestroy() {
        Shizuku.removeRequestPermissionResultListener(listener)
        super.onDestroy()
    }

    private fun toggle() {
        val prefs = getSharedPreferences("etat", MODE_PRIVATE)
        val target5g = !prefs.getBoolean("is5g", true)
        val mask = if (target5g) MASK_5G else MASK_4G

        Thread {
            val (code, out) = runShell(
                "cmd phone set-allowed-network-types-for-users -s 0 $mask"
            )
            runOnUiThread {
                if (code == 0 && !out.contains("error", ignoreCase = true)) {
                    prefs.edit().putBoolean("is5g", target5g).apply()
                    toast(if (target5g) "✅ 5G activée" else "✅ 4G uniquement")
                } else {
                    toast("Échec : $out")
                }
                finish()
            }
        }.start()
    }

    private fun runShell(cmd: String): Pair<Int, String> = try {
        val m = Shizuku::class.java.getDeclaredMethod(
            "newProcess",
            Array<String>::class.java, Array<String>::class.java, String::class.java
        )
        m.isAccessible = true
        val p = m.invoke(null, arrayOf("sh", "-c", cmd), null, null) as Process
        val out = p.inputStream.bufferedReader().readText() +
                  p.errorStream.bufferedReader().readText()
        Pair(p.waitFor(), out.trim())
    } catch (e: Exception) {
        Pair(-1, e.toString())
    }

    private fun openNetworkSettings() {
        try {
            startActivity(Intent(Settings.ACTION_DATA_ROAMING_SETTINGS)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        } catch (_: Exception) {
            startActivity(Intent(Settings.ACTION_SETTINGS)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        }
    }

    private fun toast(msg: String) =
        Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
}
