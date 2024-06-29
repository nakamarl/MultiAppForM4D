package com.example.nakamari.multiappform4d

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.widget.Button
import android.widget.Toast
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat

class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        val btnDualTimer : Button = findViewById(R.id.btnDualTimer)
        val btnLapTimer : Button = findViewById(R.id.btnLapTimer)
        val btnCalcRpm : Button = findViewById(R.id.btnCalcMtrRpm)
        val button4 : Button = findViewById(R.id.button4)
        val button5 : Button = findViewById(R.id.button5)

        btnDualTimer.setOnClickListener{
            startActivity(Intent(this, DualTimer::class.java))
        }

        btnLapTimer.setOnClickListener {
            startActivity(Intent(this, LapTimer::class.java))
        }

        btnCalcRpm.setOnClickListener{
            startActivity(Intent(this, CalcRpm::class.java))
        }

        button4.setOnClickListener {
            notImplButton(1)
        }

        button5.setOnClickListener {
            notImplButton(1)
        }
    }

    // 機能のないボタンの制御
    private fun notImplButton(flag: Int) {
        when {
            // 実装未定の機能(作成だけした空のボタン)
            flag == 1 -> {
                Toast.makeText(this, "実装未定です", Toast.LENGTH_LONG).show()
            }
            // 作成中
            flag == 2 -> {
                Toast.makeText(this, "作成中です", Toast.LENGTH_LONG).show()
            }
            // 作成予定(機能決定済、実装未着手)
            flag == 3 -> {
                Toast.makeText(this, "作成予定はあります", Toast.LENGTH_LONG).show()
            }
            // 何もしない
            else -> {}
        }
    }
}