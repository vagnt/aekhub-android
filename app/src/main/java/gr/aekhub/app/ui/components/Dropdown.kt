package gr.aekhub.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import gr.aekhub.app.ui.theme.C
import gr.aekhub.app.ui.theme.body

/** Λίστα επιλογής (dropdown) στο στυλ του app. */
@Composable
fun AekDropdown(label: String, value: String, options: List<Pair<String, String>>, modifier: Modifier, pick: (String) -> Unit) {
    var open by remember { mutableStateOf(false) }
    Box(modifier) {
        Column(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(C.Panel).border(1.dp, if (open) C.Gold else C.Line2, RoundedCornerShape(12.dp))
                .clickable(enabled = options.size > 1) { open = true }.padding(horizontal = 12.dp, vertical = 8.dp),
        ) {
            Text(label, style = body(9, FontWeight.ExtraBold, C.Muted).copy(letterSpacing = 1.sp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(value, Modifier.weight(1f), style = body(14, FontWeight.ExtraBold, C.Text), maxLines = 1, overflow = TextOverflow.Ellipsis)
                if (options.size > 1) androidx.compose.material3.Icon(androidx.compose.material.icons.Icons.Filled.ArrowDropDown, null, tint = C.Gold)
            }
        }
        androidx.compose.material3.DropdownMenu(expanded = open, onDismissRequest = { open = false }, modifier = Modifier.background(C.Panel2)) {
            options.forEach { (k, v) ->
                androidx.compose.material3.DropdownMenuItem(
                    text = { Text(v, style = body(14, if (v == value) FontWeight.ExtraBold else FontWeight.Bold, if (v == value) C.Gold else C.Text)) },
                    onClick = { open = false; pick(k) },
                )
            }
        }
    }
}

