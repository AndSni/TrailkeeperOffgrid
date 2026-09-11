package com.asnidev.trailkeeperoffgrid.ui.common

import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import com.asnidev.trailkeeperoffgrid.data.Coordinates

/**
 * A GPS point shown in the three forms the field actually uses — decimal
 * (for another app), DMS (read out loud), UTM (a paper map's grid) — each
 * with a one-tap copy. Used in the task / structure / report detail sheets.
 */
@Composable
fun CoordinatesRow(lat: Double, lon: Double, modifier: Modifier = Modifier) {
    val clipboard = LocalClipboardManager.current
    val context = LocalContext.current
    val formats = remember(lat, lon) { Coordinates.allFormats(lat, lon) }

    Column(modifier, verticalArrangement = Arrangement.spacedBy(2.dp)) {
        formats.forEach { (label, value) ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "$label: ",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(value, style = MaterialTheme.typography.labelMedium)
                IconButton(
                    onClick = {
                        clipboard.setText(AnnotatedString(value))
                        Toast.makeText(context, "Copied", Toast.LENGTH_SHORT).show()
                    },
                    modifier = Modifier.size(28.dp),
                ) {
                    Icon(
                        Icons.Filled.ContentCopy,
                        contentDescription = "Copy $label coordinates",
                        modifier = Modifier.size(14.dp),
                    )
                }
            }
        }
    }
}
