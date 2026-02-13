package com.stellar.demo


class DemoUserService : IDemoUserService.Stub() {

    companion object {
        private const val TAG = "DemoUserService"
    }

    override fun executeCommand(command: String): String {
        return try {
            val process = Runtime.getRuntime().exec(arrayOf("sh", "-c", command))
            val reader = java.io.BufferedReader(
                java.io.InputStreamReader(process.inputStream)
            )
            val output = StringBuilder()
            var line: String?
            while (reader.readLine().also { line = it } != null) {
                output.append(line).append("\n")
            }
            process.waitFor()
            output.toString().trim()
        } catch (e: Exception) {
            "Error: ${e.message}"
        }
    }

    override fun getSystemProperty(name: String): String = try {
        val process = Runtime.getRuntime().exec(arrayOf("getprop", name))
        java.io.BufferedReader(java.io.InputStreamReader(process.inputStream)).readLine() ?: ""
    } catch (e: Exception) {
        ""
    }
}
