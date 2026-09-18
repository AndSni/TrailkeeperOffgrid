package com.asnidev.trailkeeperoffgrid.ui.share

import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.core.content.FileProvider
import com.asnidev.trailkeeperoffgrid.data.ShareBundle
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/** Builds a scoped `.tkshare` bundle and hands it to the OS share sheet —
 * the one path all five share entry points (project/task/route/trail/
 * structure) go through; each just supplies its own [ShareBundle] export
 * call. */
fun CoroutineScope.launchShare(context: Context, export: suspend () -> ShareBundle.Result) {
    launch {
        val r = export()
        val file = r.file
        if (!r.ok || file == null) {
            Toast.makeText(context, "Share failed: ${r.detail}", Toast.LENGTH_LONG).show()
            return@launch
        }
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        val send = Intent(Intent.ACTION_SEND).apply {
            type = ShareBundle.MIME
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(send, "Share via"))
    }
}
