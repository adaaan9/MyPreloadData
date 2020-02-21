package com.example.mypreloaddata.services

import android.app.Service
import android.content.Intent
import android.os.*
import android.util.Log
import com.example.mypreloaddata.R
import com.example.mypreloaddata.database.MahasiswaHelper
import com.example.mypreloaddata.model.MahasiswaModel
import com.example.mypreloaddata.pref.AppPreference
import kotlinx.coroutines.*
import java.io.BufferedReader
import java.io.InputStream
import java.io.InputStreamReader
import java.lang.Exception
import kotlin.coroutines.CoroutineContext

class DataManagerService : Service(), CoroutineScope {

    private lateinit var job: Job
    override val coroutineContext: CoroutineContext
        get() = Dispatchers.Main + job
    
    private val TAG = DataManagerService::class.java.simpleName
    private var mActivityMessenger: Messenger? = null

    companion object {
        const val PREPARATION_MESSAGE = 0
        const val UPDATE_MESSAGE = 1
        const val SUCCESS_MESSAGE = 2
        const val FAILED_MESSAGE = 3
        const val CANCEL_MESSAGE = 4
        const val ACTIVITY_HANDLER = "activity_handler"
        private const val MAX_PROGRESS = 100.0
    }

    

    override fun onCreate() {
        super.onCreate()
        job = Job()  //create the job
        Log.d(TAG, "onCreate: ")
    }

    // ketika semua ikatan sudah dilepas maka onDestroy akan secara otomatis dipanggil
    override fun onDestroy() {
        super.onDestroy()
        job.cancel() // cancel the job
        Log.d(TAG, "onDestroy: ")
    }

    private fun getData(): Boolean {
        val mahasiswaHelper = MahasiswaHelper.getInstance(applicationContext)
        val appPreference = AppPreference(applicationContext)

        val firstRun = appPreference.firstRun as Boolean

        if (firstRun) {
            val mahasiswaModels = preloadRaw()

            mahasiswaHelper.open()

            var progress = 30.0
            publishProgress(progress.toInt())
            val progressMaxInsert = 80.0
            val progressDiff = (progressMaxInsert - progress) / mahasiswaModels.size

            var isInsertSuccess: Boolean

            // Gunakan ini untuk insert query dengan menggunakan standar query

            try {
                mahasiswaHelper.beginTransaction()

                loop@ for (model in mahasiswaModels) {
                    // jika service atau activity dalam keadaan onDestroy maka akan menghentikan perulangan
                    when {
                        job.isCancelled -> break@loop
                        else -> {
                            mahasiswaHelper.insertTransaction(model)
                            progress += progressDiff
                            publishProgress(progress.toInt())
                        }
                    }
                }
                when {
                    job.isCancelled -> {
                        isInsertSuccess = false
                        appPreference.firstRun = true
                        sendMessage(CANCEL_MESSAGE)
                    }
                    else -> {
                        mahasiswaHelper.setTransactionSuccess()
                        isInsertSuccess = true
                        appPreference.firstRun = false
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "doInBackground: Exception")
                isInsertSuccess = false
            } finally {
                mahasiswaHelper.endTransaction()
            }

            // akhir dari standar query
            mahasiswaHelper.close()
            publishProgress(MAX_PROGRESS.toInt())

            return isInsertSuccess
        } else {
            try {
                synchronized(this) {
                    publishProgress(50)
                    publishProgress(MAX_PROGRESS.toInt())
                    return true
                }
            } catch (e: Exception) {
                return false
            }
        }
    }

    private fun publishProgress(progress: Int) {
        try {
            val message = Message.obtain(null, UPDATE_MESSAGE)
            val bundle = Bundle()
            bundle.putLong("KEY_PROGRES", progress.toLong())
            message.data = bundle
            mActivityMessenger?.send(message)
        } catch (e: RemoteException) {
            e.printStackTrace()
        }
    }

    private fun preloadRaw(): ArrayList<MahasiswaModel> {
        val mahasiswaModels = ArrayList<MahasiswaModel>()
        var line: String?
        val reader: BufferedReader
        try {
            val rawText = resources.openRawResource(R.raw.data_mahasiswa)

            reader = BufferedReader(InputStreamReader(rawText))
            do {
                line = reader.readLine()
                val splitstr = line.split("\t".toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()

                val mahasiswaModel: MahasiswaModel

                mahasiswaModel = MahasiswaModel()
                mahasiswaModel.name = splitstr[0]
                mahasiswaModel.nim = splitstr[1]
                mahasiswaModels.add(mahasiswaModel)
            } while (line != null)
        } catch (e: Exception) {
            e.printStackTrace()
        }

        return mahasiswaModels
    }

    // method yang akan dipanggil ketika service diikatkan ke activity
    override fun onBind(intent: Intent): IBinder? {
        mActivityMessenger = intent.getParcelableExtra(ACTIVITY_HANDLER)

        //proses ambil data
        loadDataAsync()

        return mActivityMessenger.let { it?.binder }
    }

    // method yang akan dipanggil ketika service dilepas dari activity
    override fun onUnbind(intent: Intent): Boolean {
        Log.d(TAG, "onUnbind: ")
        job.cancel()
        return super.onUnbind(intent)
    }

    //method yang akan dipanggil ketika service diikatkan kembali
    override fun onRebind(intent: Intent) {
        super.onRebind(intent)
        Log.d(TAG, "onRebind: ")
    }

    private fun loadDataAsync() {
        sendMessage(PREPARATION_MESSAGE)
        job = launch {
            val isInsertSuccess = async(Dispatchers.IO) {
                getData()
            }
            if (isInsertSuccess.await()){
                sendMessage(SUCCESS_MESSAGE)
            } else {
                sendMessage(FAILED_MESSAGE)
            }
        }
        job.start()
    }

    private fun sendMessage(messageStatus: Int) {
        val message = Message.obtain(null, messageStatus)
        try {
            mActivityMessenger?.send(message)
        } catch (e: RemoteException) {
            e.printStackTrace()
        }
    }
}



























