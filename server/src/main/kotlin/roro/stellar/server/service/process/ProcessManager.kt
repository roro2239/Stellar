package roro.stellar.server.service.process

import com.stellar.server.IRemoteProcess
import roro.stellar.server.ClientManager
import roro.stellar.server.api.RemoteProcessHolder
import roro.stellar.server.util.Logger
import java.io.File
import java.io.IOException

class ProcessManager(
    private val clientManager: ClientManager
) {
    companion object {
        private val LOGGER = Logger("ProcessManager")
    }

    fun newProcess(
        uid: Int,
        pid: Int,
        cmd: Array<String?>,
        env: Array<String?>?,
        dir: String?
    ): IRemoteProcess {
        LOGGER.d(
            "newProcess: uid=$uid, cmd=${cmd.contentToString()}, env=${env.contentToString()}, dir=$dir"
        )

        val process: Process = try {
            Runtime.getRuntime().exec(cmd, env, if (dir != null) File(dir) else null)
        } catch (e: IOException) {
            throw IllegalStateException(e.message)
        }

        val clientRecord = clientManager.findClient(uid, pid)
        val token = clientRecord?.client?.asBinder()

        return RemoteProcessHolder(process, token)
    }
}
