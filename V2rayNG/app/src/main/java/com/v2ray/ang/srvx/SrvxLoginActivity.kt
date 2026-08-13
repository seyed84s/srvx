package com.v2ray.ang.srvx

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.ProgressBar
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.v2ray.ang.R
import com.v2ray.ang.ui.MainActivity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class SrvxLoginActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Already logged in -> go straight to main
        if (SrvxSession.token(this) != null) {
            goMain(); return
        }

        setContentView(R.layout.activity_srvx_login)

        val userField = findViewById<EditText>(R.id.srvx_username)
        val passField = findViewById<EditText>(R.id.srvx_password)
        val errorText = findViewById<TextView>(R.id.srvx_error)
        val loginBtn = findViewById<Button>(R.id.srvx_login_btn)
        val progress = findViewById<ProgressBar>(R.id.srvx_progress)

        loginBtn.setOnClickListener {
            val u = userField.text.toString().trim()
            val p = passField.text.toString()
            if (u.isEmpty() || p.isEmpty()) {
                errorText.text = "نام کاربری و رمز را وارد کنید"
                return@setOnClickListener
            }
            errorText.text = ""
            loginBtn.isEnabled = false
            progress.visibility = View.VISIBLE

            lifecycleScope.launch {
                val token = withContext(Dispatchers.IO) { SrvxApi.login(u, p) }
                progress.visibility = View.GONE
                loginBtn.isEnabled = true
                if (token != null) {
                    SrvxSession.save(this@SrvxLoginActivity, token, u)
                    goMain()
                } else {
                    errorText.text = "ورود ناموفق — اطلاعات نادرست است"
                }
            }
        }
    }

    private fun goMain() {
        startActivity(Intent(this, MainActivity::class.java))
        finish()
    }
}
