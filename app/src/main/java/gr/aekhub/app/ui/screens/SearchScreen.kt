package gr.aekhub.app.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import gr.aekhub.app.data.Api
import gr.aekhub.app.data.PostCard
import gr.aekhub.app.ui.components.Loading
import gr.aekhub.app.ui.components.PostRow
import gr.aekhub.app.ui.theme.C
import gr.aekhub.app.ui.theme.body
import kotlinx.coroutines.delay

@Composable
fun SearchScreen(back: () -> Unit, openPost: (Long) -> Unit) {
    var q by rememberSaveable { mutableStateOf("") }
    var res by remember { mutableStateOf<List<PostCard>?>(null) }
    var busy by remember { mutableStateOf(false) }
    val fr = remember { FocusRequester() }
    LaunchedEffect(Unit) { fr.requestFocus() }
    LaunchedEffect(q) {
        if (q.trim().length < 3) { res = null; return@LaunchedEffect }
        delay(400); busy = true
        res = runCatching { Api.search(q.trim()).items }.getOrElse { emptyList() }
        busy = false
    }
    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().padding(end = 16.dp, top = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = back) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Πίσω", tint = C.Text) }
            OutlinedTextField(
                value = q, onValueChange = { q = it }, singleLine = true,
                placeholder = { Text("Αναζήτηση άρθρων", style = body(15, color = C.Muted)) },
                textStyle = body(16), shape = RoundedCornerShape(14.dp),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search), keyboardActions = KeyboardActions(),
                colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = C.Gold, unfocusedBorderColor = C.Line2, cursorColor = C.Gold, focusedContainerColor = C.Panel, unfocusedContainerColor = C.Panel),
                modifier = Modifier.weight(1f).focusRequester(fr),
            )
        }
        when {
            busy && res == null -> Loading()
            res?.isEmpty() == true -> Text("Δεν βρέθηκαν άρθρα.", Modifier.padding(24.dp), style = body(15, color = C.Muted))
            else -> LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(res ?: emptyList(), key = { it.id }) { p -> PostRow(p) { openPost(p.id) } }
            }
        }
    }
}
