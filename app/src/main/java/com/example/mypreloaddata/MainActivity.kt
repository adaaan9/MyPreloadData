package com.example.mypreloaddata

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.*
import androidx.appcompat.app.AppCompatActivity
import android.util.Log
import android.widget.Toast
import com.example.mypreloaddata.services.DataManagerService
import com.example.mypreloaddata.services.DataManagerService.Companion.CANCEL_MESSAGE
import com.example.mypreloaddata.services.DataManagerService.Companion.FAILED_MESSAGE
import com.example.mypreloaddata.services.DataManagerService.Companion.PREPARATION_MESSAGE
import com.example.mypreloaddata.services.DataManagerService.Companion.SUCCESS_MESSAGE
import com.example.mypreloaddata.services.DataManagerService.Companion.UPDATE_MESSAGE
import kotlinx.android.synthetic.main.activity_main.*
import java.lang.ref.WeakReference

class MainActivity : AppCompatActivity(), HandlerCallback {
    private lateinit var mBoundService: Messenger
    private var mServiceBound: Boolean = false

    private val mServiceConnection = object : ServiceConnection {
        override fun onServiceDisconnected(name: ComponentName) {
            mServiceBound = false
        }

        override fun onServiceConnected(name: ComponentName, service: IBinder) {
            mBoundService = Messenger(service)
            mServiceBound = true
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        val mBoundServiceIntent = Intent(this@MainActivity, DataManagerService::class.java)
        val mActivityMessenger = Messenger(IncomingHandler(this))
        mBoundServiceIntent.putExtra(DataManagerService.ACTIVITY_HANDLER, mActivityMessenger)

        bindService(mBoundServiceIntent, mServiceConnection, Context.BIND_AUTO_CREATE)
    }

    override fun onPreparation() {
        Toast.makeText(this, "MEMULAI MEMUAT DATA", Toast.LENGTH_LONG).show()
    }

    override fun updateProgress(progress: Long) {
        Log.d("PROGRESS", "updateProgress: $progress")
        progress_bar.progress = progress.toInt()
    }

    override fun loadSuccess() {
        Toast.makeText(this, "BERHAIL", Toast.LENGTH_SHORT).show()
        startActivity(Intent(this@MainActivity, MahasiswaActivity::class.java))
    }

    override fun loadFailed() {
        Toast.makeText(this, "GAGAL", Toast.LENGTH_LONG).show()
    }

    override fun loadCancel() {
        finish()
    }



    private class IncomingHandler (callback: HandlerCallback) : Handler() {
        private var WeakCallback: WeakReference<HandlerCallback> = WeakReference(callback)

        override fun handleMessage(msg: Message) {
            when (msg.what) {
                PREPARATION_MESSAGE -> WeakCallback.get()?.onPreparation()
                UPDATE_MESSAGE -> {
                    val bundle = msg.data
                    val progress = bundle.getLong("KEY_PROGRESS")
                    WeakCallback.get()?.updateProgress(progress)
                }
                SUCCESS_MESSAGE -> WeakCallback.get()?.loadSuccess()
                FAILED_MESSAGE -> WeakCallback.get()?.loadFailed()
                CANCEL_MESSAGE -> WeakCallback.get()?.loadCancel()
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()

        unbindService(mServiceConnection)
    }
}

private interface HandlerCallback {
    fun onPreparation()

    fun updateProgress(progress: Long)

    fun loadSuccess()

    fun loadFailed()

    fun loadCancel()
}





















