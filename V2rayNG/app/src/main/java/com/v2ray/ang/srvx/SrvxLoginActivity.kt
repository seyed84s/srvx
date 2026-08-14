package com.v2ray.ang.srvx

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.EditText
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.v2ray.ang.R
import com.v2ray.ang.handler.AngConfigManager
import com.v2ray.ang.ui.MainActivity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class SrvxLoginActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Already logged in -> straight to main (configs were imported at first login)
        if (SrvxSession.token(this) != null) {
            goMain(); return
        }

        setContentView(R.layout.activity_srvx_login)

        val userField = findViewById<EditText>(R.id.srvx_username)
        val passField = findViewById<EditText>(R.id.srvx_password)
        val errorText = findViewById<TextView>(R.id.srvx_error)
        val loginBtn = findViewById<TextView>(R.id.srvx_login_btn)
        val progress = findViewById<ProgressBar>(R.id.srvx_progress)

        loginBtn.setOnClickListener {
            val u = userField.text.toString().trim()
            val p = passField.text.toString()
            if (u.isEmpty() || p.isEmpty()) {
                errorText.text = "نام کاربری و رمز را وارد کنید"
                return@setOnClickListener
            }
            errorText.text = ""
            loginBtn.text = ""
            loginBtn.isEnabled = false
            progress.visibility = View.VISIBLE

            lifecycleScope.launch {
                // 1) login
                val token = withContext(Dispatchers.IO) {
                    try { SrvxApi.login(u, p) } catch (e: Throwable) { null }
                }
                if (token == null) {
                    progress.visibility = View.GONE
                    loginBtn.text = "ورود"
                    loginBtn.isEnabled = true
                    errorText.text = "ورود ناموفق — اطلاعات نادرست است"
                    return@launch
                }
                SrvxSession.save(this@SrvxLoginActivity, token, u)

                // 2) import this user's configs ONCE, right after login
                val ok = withContext(Dispatchers.IO) {
                    try {
                        val data = SrvxApi.fetchConfigs(token) ?: return@withContext false
                        if (data.configs.isEmpty()) return@withContext false
                        val text = data.configs.joinToString("\n") { it.link }
                        AngConfigManager.importBatchConfig(text, "", false)
                        true
                    } catch (e: Throwable) { false }
                }

                progress.visibility = View.GONE
                if (!ok) {
                    Toast.makeText(this@SrvxLoginActivity,
                        "ورود موفق بود ولی دریافت کانفیگ ناموفق شد", Toast.LENGTH_LONG).show()
                }
                goMain()
            }
        }
    }

    private fun goMain() {
        startActivity(Intent(this, MainActivity::class.java))
        finish()
    }
}
