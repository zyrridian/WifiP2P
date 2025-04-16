package github.leavesczy.wifip2p.receiver

import android.app.Application
import android.content.Context
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import github.leavesczy.wifip2p.common.Constants
import github.leavesczy.wifip2p.common.FileTransfer
import github.leavesczy.wifip2p.common.FileTransferViewState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.launch
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.io.ObjectInputStream
import java.net.InetSocketAddress
import java.net.ServerSocket

/**
 * @Author: CZY
 * @Date: 2022/9/26 14:18
 * @Desc: Handles receiving files via a socket connection
 */
class FileReceiverViewModel(context: Application) : AndroidViewModel(context) {

    private val _fileTransferViewState = MutableSharedFlow<FileTransferViewState>()

    val fileTransferViewState: SharedFlow<FileTransferViewState>
        get() = _fileTransferViewState

    private val _log = MutableSharedFlow<String>()

    val log: SharedFlow<String>
        get() = _log

    private var fileReceiverJob: Job? = null

    /**
     * Starts the listener for incoming file transfers
     */
    fun startListener() {
        val job = fileReceiverJob
        if (job != null && job.isActive) {
            return
        }
        fileReceiverJob = viewModelScope.launch(context = Dispatchers.IO) {
            _fileTransferViewState.emit(value = FileTransferViewState.Idle)
            var serverSocket: ServerSocket? = null
            var clientInputStream: InputStream? = null
            var objectInputStream: ObjectInputStream? = null
            var fileOutputStream: FileOutputStream? = null
            try {
                _fileTransferViewState.emit(value = FileTransferViewState.Connecting)
                log {
                    "Opening Socket"
                }
                serverSocket = ServerSocket()
                serverSocket.bind(InetSocketAddress(Constants.PORT))
                serverSocket.reuseAddress = true
                serverSocket.soTimeout = 15000
                log {
                    "Socket accepted, disconnecting if not connected in 15 seconds"
                }
                val client = serverSocket.accept()
                _fileTransferViewState.emit(value = FileTransferViewState.Receiving)
                clientInputStream = client.getInputStream()
                objectInputStream = ObjectInputStream(clientInputStream)
                val fileTransfer = objectInputStream.readObject() as FileTransfer
                val file = File(getCacheDir(context = getApplication()), fileTransfer.fileName)
                log {
                    buildString {
                        append("Connection successful, file to be received: $fileTransfer")
                        append("\n")
                        append("The file will be saved to: $file")
                        append("\n")
                        append("Starting file transfer")
                    }
                }
                fileOutputStream = FileOutputStream(file)
                val buffer = ByteArray(1024 * 1024)
                while (true) {
                    val length = clientInputStream.read(buffer)
                    if (length > 0) {
                        fileOutputStream.write(buffer, 0, length)
                    } else {
                        break
                    }
                    log {
                        "Transferring file, length: $length"
                    }
                }
                _fileTransferViewState.emit(value = FileTransferViewState.Success(file = file))
                log {
                    "File received successfully"
                }
            } catch (throwable: Throwable) {
                log {
                    "Exception thrown: " + throwable.message
                }
                _fileTransferViewState.emit(value = FileTransferViewState.Failed(throwable = throwable))
            } finally {
                serverSocket?.close()
                clientInputStream?.close()
                objectInputStream?.close()
                fileOutputStream?.close()
            }
        }
    }

    /**
     * Returns the directory where the files will be saved
     */
    private fun getCacheDir(context: Context): File {
        val cacheDir = File(context.cacheDir, "FileTransfer")
        cacheDir.mkdirs()
        return cacheDir
    }

    /**
     * Logs messages to the flow
     */
    private suspend fun log(log: () -> Any) {
        _log.emit(value = log().toString())
    }
}
