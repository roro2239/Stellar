package roro.stellar.manager.authorization

import android.content.pm.ApplicationInfo
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import roro.stellar.Stellar
import roro.stellar.StellarApiConstants
import roro.stellar.StellarApiConstants.REQUEST_PERMISSION_REPLY_ALLOWED
import roro.stellar.StellarApiConstants.REQUEST_PERMISSION_REPLY_IS_ONETIME
import roro.stellar.StellarApiConstants.REQUEST_PERMISSION_REPLY_PERMISSION
import roro.stellar.manager.R
import roro.stellar.manager.ui.theme.AppShape
import roro.stellar.manager.ui.theme.StellarTheme
import roro.stellar.manager.util.Logger.Companion.LOGGER
import roro.stellar.server.ktx.workerHandler
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException

class RequestPermissionActivity : ComponentActivity() {

    private fun setResult(
        requestUid: Int,
        requestPid: Int,
        requestCode: Int,
        allowed: Boolean,
        onetime: Boolean,
        permission: String
    ) {
        val data = Bundle()
        data.putBoolean(REQUEST_PERMISSION_REPLY_ALLOWED, allowed)
        data.putBoolean(REQUEST_PERMISSION_REPLY_IS_ONETIME, onetime)
        data.putString(REQUEST_PERMISSION_REPLY_PERMISSION, permission)
        try {
            Stellar.dispatchPermissionConfirmationResult(requestUid, requestPid, requestCode, data)
        } catch (_: Throwable) {
            LOGGER.e("dispatchPermissionConfirmationResult")
        }
    }

    private fun waitForBinder(): Boolean {
        val countDownLatch = CountDownLatch(1)

        val listener = object : Stellar.OnBinderReceivedListener {
            override fun onBinderReceived() {
                countDownLatch.countDown()
                Stellar.removeBinderReceivedListener(this)
            }
        }

        Stellar.addBinderReceivedListenerSticky(listener, workerHandler)

        return try {
            countDownLatch.await(5, TimeUnit.SECONDS)
            true
        } catch (e: TimeoutException) {
            LOGGER.e(e, "在 5 秒内未收到 Binder")
            false
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (!waitForBinder()) {
            finish()
            return
        }

        val uid = intent.getIntExtra("uid", -1)
        val pid = intent.getIntExtra("pid", -1)
        val denyOnce = intent.getBooleanExtra("denyOnce", true)
        val requestCode = intent.getIntExtra("requestCode", -1)
        val permission = intent.getStringExtra("permission") ?: "stellar"

        @Suppress("DEPRECATION")
        val ai = intent.getParcelableExtra<ApplicationInfo>("applicationInfo")
        if (uid == -1 || pid == -1 || ai == null) {
            finish()
            return
        }

        val label = try {
            ai.loadLabel(packageManager).toString()
        } catch (_: Exception) {
            ai.packageName
        }

        setContent {
            StellarTheme {
                PermissionRequestDialog(
                    appName = label,
                    denyOnce = denyOnce,
                    onResult = { allowed, onetime ->
                        setResult(uid, pid, requestCode, allowed = allowed, onetime = onetime, permission)
                        finish()
                    },
                    permission = permission
                )
            }
        }
    }
}

@Composable
fun PermissionRequestDialog(
    appName: String,
    denyOnce: Boolean,
    onResult: (allowed: Boolean, onetime: Boolean) -> Unit,
    permission: String = "stellar"
) {
    Dialog(
        onDismissRequest = {
            onResult(false, true)
        },
        properties = DialogProperties(
            usePlatformDefaultWidth = false
        )
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.85f)
                .wrapContentHeight(),
            shape = AppShape.shapes.cardMedium24,
            color = MaterialTheme.colorScheme.surfaceContainer
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(20.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(72.dp)
                        .clip(AppShape.shapes.iconMedium18)
                        .background(MaterialTheme.colorScheme.primaryContainer),
                    contentAlignment = Alignment.Center
                ) {
                    Image(
                        painter = painterResource(id = R.drawable.stellar_icon),
                        contentDescription = null,
                        modifier = Modifier.size(48.dp)
                    )
                }

                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = stringResource(R.string.permission_request),
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center
                    )

                    val permissionSuffix = when (permission) {
                        "stellar" -> stringResource(R.string.permission_use_stellar)
                        "follow_stellar_startup" -> stringResource(R.string.permission_follow_startup)
                        else -> stringResource(R.string.permission_use_generic, permission)
                    }
                    val text = buildAnnotatedString {
                        append(stringResource(R.string.permission_allow_prefix))
                        withStyle(
                            style = SpanStyle(
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                        ) {
                            append(appName)
                        }
                        append(permissionSuffix)
                    }
                    Text(
                        text = text,
                        style = MaterialTheme.typography.bodyLarge,
                        textAlign = TextAlign.Center,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Card(
                        onClick = {
                            onResult(true, false)
                        },
                        modifier = Modifier.fillMaxWidth(),
                        shape = AppShape.shapes.buttonSmall14,
                        colors = CardDefaults.elevatedCardColors(
                            containerColor = MaterialTheme.colorScheme.primaryContainer
                        )
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            horizontalArrangement = Arrangement.Center,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = stringResource(R.string.always_allow),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onPrimaryContainer,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }

                    if (StellarApiConstants.isRuntimePermission(permission)) {
                        Card(
                            onClick = {
                                onResult(true, true)
                            },
                            modifier = Modifier.fillMaxWidth(),
                            shape = AppShape.shapes.buttonSmall14,
                            colors = CardDefaults.elevatedCardColors(
                                containerColor = MaterialTheme.colorScheme.primaryContainer
                            )
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(16.dp),
                                horizontalArrangement = Arrangement.Center,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = stringResource(R.string.allow_once),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }

                    Card(
                        onClick = {
                            onResult(false, denyOnce)
                        },
                        modifier = Modifier.fillMaxWidth(),
                        shape = AppShape.shapes.buttonSmall14,
                        colors = CardDefaults.elevatedCardColors(
                            containerColor = MaterialTheme.colorScheme.errorContainer
                        )
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            horizontalArrangement = Arrangement.Center,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = if (denyOnce) stringResource(R.string.deny) else stringResource(R.string.deny_and_dont_ask),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onErrorContainer,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }
        }
    }
}
