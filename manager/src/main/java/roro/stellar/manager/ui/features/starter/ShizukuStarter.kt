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

/**
 * Shizuku 命令执行器
 * 
 * 通过 Binder IPC 直接与 Shizuku 服务通信执行 shell 命令
 * 使用底层 transact 方式，避免已废弃的 API
 */
object ShizukuStarter {

    /**
     * 检查 Shizuku 服务是否运行
     */
    fun isShizukuAvailable(): Boolean {
        return try {
            Shizuku.pingBinder()
        } catch (e: Exception) {
            false
        }
    }

    /**
     * 检查是否已授权
     */
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

    /**
     * 请求 Shizuku 权限
     */
    fun requestPermission() {
        if (isShizukuAvailable() && Shizuku.checkSelfPermission() != PackageManager.PERMISSION_GRANTED) {
            if (Shizuku.shouldShowRequestPermissionRationale()) {
                // 用户之前拒绝过，可再次请求
            }
            Shizuku.requestPermission(1001)
        }
    }

    /**
     * 执行 shell 命令
     * 
     * @param command 要执行的命令
     * @param onOutput 输出回调（逐行）
     * @return 命令退出码（0表示成功，-1表示失败）
     */
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

    /**
     * 通过 Binder 执行命令
     */
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
    
    /**
     * 创建远程进程
     * 
     * 调用 IShizukuService.newProcess (transaction code 8)
     * 
     * @return IRemoteProcess 的 IBinder
     */
    private fun createProcessViaTransact(
        shizukuBinder: IBinder,
        command: String
    ): IBinder? {
        val data = Parcel.obtain()
        val reply = Parcel.obtain()
        
        return try {
            data.writeInterfaceToken("moe.shizuku.server.IShizukuService")
            data.writeStringArray(arrayOf("sh", "-c", command))
            data.writeStringArray(null) // 环境变量
            data.writeString(null) // 工作目录
            
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
    
    /**
     * 读取进程输出并等待完成
     * 
     * 自动探测 IRemoteProcess 的 transaction codes:
     * - getInputStream: 尝试 code 2-6
     * - getErrorStream: 尝试 code 3-7
     * - destroy: code 1
     * 
     * @return 从命令输出中提取的退出码
     */
    private fun readProcessViaTransact(processBinder: IBinder, onOutput: (String) -> Unit): Int {
        return try {
            // 获取 stdout
            var inputStream: ParcelFileDescriptor? = null
            for (code in 2..6) {
                inputStream = getStreamViaTransact(processBinder, code)
                if (inputStream != null) {
                    break
                }
            }
            
            // 获取 stderr
            var errorStream: ParcelFileDescriptor? = null
            for (code in 3..7) {
                if (code == 2 || code == 3 || code == 4 || code == 5 || code == 6) continue
                errorStream = getStreamViaTransact(processBinder, code)
                if (errorStream != null) {
                    break
                }
            }
            
            var lastOutputLine = ""
            
            // 读取标准输出
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
            
            // 读取错误输出
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
            
            // 等待输出完成
            outputThread.join(3000)
            errorThread.join(3000)
            
            // 从输出中提取退出码（格式: "退出码 0"）
            var actualExitCode = 0
            if (lastOutputLine.contains("退出码")) {
                try {
                    val match = Regex("退出码\\s*(\\d+)").find(lastOutputLine)
                    if (match != null) {
                        actualExitCode = match.groupValues[1].toInt()
                    }
                } catch (e: Exception) {
                    // 使用默认值 0
                }
            }
            
            // 销毁进程
            try {
                destroyViaTransact(processBinder)
            } catch (e: Exception) {
                // 忽略
            }
            
            actualExitCode
        } catch (e: Exception) {
            -1
        }
    }
    
    /**
     * 获取进程流（stdout/stderr）
     * 
     * @return ParcelFileDescriptor 用于读取流数据
     */
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
    
    /**
     * 销毁远程进程
     * 
     * 调用 IRemoteProcess.destroy (transaction code 1)
     */
    private fun destroyViaTransact(processBinder: IBinder) {
        val data = Parcel.obtain()
        val reply = Parcel.obtain()
        
        try {
            data.writeInterfaceToken("moe.shizuku.server.IRemoteProcess")
            processBinder.transact(1, data, reply, 0)
            reply.readException()
        } catch (e: Exception) {
            // 忽略
        } finally {
            data.recycle()
            reply.recycle()
        }
    }
    
}

