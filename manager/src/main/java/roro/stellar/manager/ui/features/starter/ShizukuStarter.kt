package roro.stellar.manager.ui.features.starter

import android.content.pm.PackageManager
import android.os.IBinder
import android.os.Parcel
import android.os.ParcelFileDescriptor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import rikka.shizuku.Shizuku
import java.io.BufferedReader
import java.io.FileInputStream
import java.io.InputStreamReader

object ShizukuStarter {

    fun isShizukuAvailable(): Boolean {
        return try {
            Shizuku.pingBinder()
        } catch (e: Exception) {
            false
        }
    }

    fun checkPermission(): Boolean {
        return try {
            if (!isShizukuAvailable()) {
                return false
            }
            Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED
        } catch (e: Exception) {
            false
        }
    }

    fun requestPermission() {
        if (isShizukuAvailable() && Shizuku.checkSelfPermission() != PackageManager.PERMISSION_GRANTED) {
            if (Shizuku.shouldShowRequestPermissionRationale()) {
            }
            Shizuku.requestPermission(1001)
        }
    }

    suspend fun executeCommand(
        command: String,
        onOutput: (String) -> Unit
    ): Int = withContext(Dispatchers.IO) {
        try {
            if (!checkPermission()) {
                onOutput("错误：没有Shizuku权限")
                return@withContext -1
            }

            onOutput("$ $command")
            
            executeCommandDirectly(command, onOutput)
        } catch (e: Exception) {
            onOutput("错误：${e.message}")
            e.printStackTrace()
            -1
        }
    }

    private fun executeCommandDirectly(command: String, onOutput: (String) -> Unit): Int {
        return try {
            val binder = Shizuku.getBinder()
            if (binder == null || !binder.pingBinder()) {
                return -1
            }
            
            val processBinder = createProcessViaTransact(binder, command)
            if (processBinder == null) {
                return -1
            }
            
            readProcessViaTransact(processBinder, onOutput)
        } catch (e: Exception) {
            -1
        }
    }
    
    private fun createProcessViaTransact(
        shizukuBinder: IBinder,
        command: String
    ): IBinder? {
        val data = Parcel.obtain()
        val reply = Parcel.obtain()
        
        return try {
            data.writeInterfaceToken("moe.shizuku.server.IShizukuService")
            data.writeStringArray(arrayOf("sh", "-c", command))
            data.writeStringArray(null)
            data.writeString(null)
            
            val success = shizukuBinder.transact(8, data, reply, 0)
            if (!success) {
                return null
            }
            
            reply.readException()
            reply.readStrongBinder()
        } catch (e: Exception) {
            null
        } finally {
            data.recycle()
            reply.recycle()
        }
    }
    
    private fun readProcessViaTransact(processBinder: IBinder, onOutput: (String) -> Unit): Int {
        return try {
            var inputStream: ParcelFileDescriptor? = null
            for (code in 2..6) {
                inputStream = getStreamViaTransact(processBinder, code)
                if (inputStream != null) {
                    break
                }
            }
            
            var errorStream: ParcelFileDescriptor? = null
            for (code in 3..7) {
                if (code == 2 || code == 3 || code == 4 || code == 5 || code == 6) continue
                errorStream = getStreamViaTransact(processBinder, code)
                if (errorStream != null) {
                    break
                }
            }
            
            var lastOutputLine = ""
            
            val outputThread = Thread {
                if (inputStream != null) {
                    try {
                        val stream = FileInputStream(inputStream.fileDescriptor)
                        val reader = BufferedReader(InputStreamReader(stream))
                        var line: String?
                        while (reader.readLine().also { line = it } != null) {
                            lastOutputLine = line!!
                            onOutput(line)
                        }
                        reader.close()
                        inputStream.close()
                    } catch (e: Exception) {
                        // 忽略
                    }
                }
            }
            
            val errorThread = Thread {
                if (errorStream != null) {
                    try {
                        val stream = FileInputStream(errorStream.fileDescriptor)
                        val reader = BufferedReader(InputStreamReader(stream))
                        var line: String?
                        while (reader.readLine().also { line = it } != null) {
                            onOutput(line!!)
                        }
                        reader.close()
                        errorStream.close()
                    } catch (e: Exception) {
                        // 忽略
                    }
                }
            }
            
            outputThread.start()
            errorThread.start()
            
            outputThread.join(3000)
            errorThread.join(3000)
            
            var actualExitCode = 0
            if (lastOutputLine.contains("退出码")) {
                try {
                    val match = Regex("退出码\\s*(\\d+)").find(lastOutputLine)
                    if (match != null) {
                        actualExitCode = match.groupValues[1].toInt()
                    }
                } catch (e: Exception) {
                }
            }
            
            try {
                destroyViaTransact(processBinder)
            } catch (e: Exception) {
            }
            
            actualExitCode
        } catch (e: Exception) {
            -1
        }
    }
    
    private fun getStreamViaTransact(processBinder: IBinder, transactionCode: Int): ParcelFileDescriptor? {
        val data = Parcel.obtain()
        val reply = Parcel.obtain()
        
        return try {
            data.writeInterfaceToken("moe.shizuku.server.IRemoteProcess")
            
            processBinder.transact(transactionCode, data, reply, 0)
            reply.readException()
            
            if (reply.readInt() != 0) {
                ParcelFileDescriptor.CREATOR.createFromParcel(reply)
            } else {
                null
            }
        } catch (e: Exception) {
            null
        } finally {
            data.recycle()
            reply.recycle()
        }
    }
    
    private fun destroyViaTransact(processBinder: IBinder) {
        val data = Parcel.obtain()
        val reply = Parcel.obtain()
        
        try {
            data.writeInterfaceToken("moe.shizuku.server.IRemoteProcess")
            processBinder.transact(1, data, reply, 0)
            reply.readException()
        } catch (e: Exception) {
        } finally {
            data.recycle()
            reply.recycle()
        }
    }
    
}

