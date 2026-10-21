package de.sonora.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.selection.selectable
import androidx.compose.material3.Button
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import de.sonora.app.core.InterviewAnswer
import de.sonora.app.core.InterviewEvaluator
import de.sonora.app.core.InterviewOption
import de.sonora.app.core.InterviewQuestion
import de.sonora.app.core.EqCurve

@Immutable
internal data class InterviewScreenState(
    val question: InterviewQuestion?,
    val index: Int,
    val questionCount: Int,
    val preview: EqCurve?,
    val answers: List<InterviewAnswer>
)

@Composable
fun InterviewScreen(vm: de.sonora.app.SonoraViewModel, modifier: Modifier = Modifier) {
    val state = vm.uiState
    InterviewScreenContent(
        state = InterviewScreenState(
            question = state.interviewPreview?.let { null }
                ?: de.sonora.app.core.InterviewCatalog.questions.getOrNull(state.interviewIndex),
            index = state.interviewIndex,
            questionCount = de.sonora.app.core.InterviewCatalog.questions.size,
            preview = state.interviewPreview,
            answers = state.interviewAnswers
        ),
        onAnswer = vm::answerInterview,
        onSkip = vm::skipInterviewQuestion,
        onPreview = vm::previewInterview,
        onSave = vm::finishInterview,
        onCancel = vm::cancelInterview,
        modifier = modifier
    )
}

@Composable
internal fun InterviewScreenContent(
    state: InterviewScreenState,
    onAnswer: (String, String) -> Unit,
    onSkip: (String) -> Unit,
    onPreview: () -> Unit,
    onSave: () -> Unit,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier
) {
    val question = state.question
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            TextButton(onClick = onCancel, modifier = Modifier.heightIn(min = 48.dp)) { Text("Abbrechen") }
            Text("Persönliches Klangprofil", style = MaterialTheme.typography.headlineMedium)
            Spacer(Modifier.height(8.dp))
            if (state.preview == null) {
                LinearProgressIndicator(
                    progress = { ((state.index + 1).toFloat() / state.questionCount).coerceIn(0f, 1f) },
                    modifier = Modifier.fillMaxWidth().height(4.dp)
                )
                Text("Frage ${state.index + 1} von ${state.questionCount}", style = MaterialTheme.typography.bodySmall, color = Muted)
            }
        }
        if (state.preview == null && question != null) {
            item { Text(question.prompt, style = MaterialTheme.typography.titleLarge) }
            items(question.options, key = { it.id }, contentType = { "interview-option" }) { option ->
                InterviewOptionRow(
                    option = option,
                    onClick = { onAnswer(question.id, option.id) }
                )
            }
            item {
                OutlinedButton(
                    onClick = { onSkip(question.id) },
                    modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)
                ) { Text("Kann ich nicht sagen · überspringen") }
            }
        } else {
            val curve = state.preview ?: de.sonora.app.core.InterviewEvaluator.evaluate(state.answers)
            item {
                Text("Dein Vorschlag", style = MaterialTheme.typography.titleLarge)
                Spacer(Modifier.height(6.dp))
                Text(InterviewEvaluator.summary(curve), style = MaterialTheme.typography.bodyLarge)
                Spacer(Modifier.height(8.dp))
                Text("Die Auswertung bleibt auf deinem Handy. Du kannst den Klang erst anhören und dann speichern.", style = MaterialTheme.typography.bodySmall, color = Muted)
            }
            item {
                OutlinedButton(onClick = onPreview, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
                    Text("Profil anhören")
                }
            }
            item {
                Button(onClick = onSave, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
                    Text("Profil speichern und aktivieren")
                }
            }
        }
    }
}

@Composable
private fun InterviewOptionRow(option: InterviewOption, onClick: () -> Unit) {
    Surface(
        color = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .selectable(selected = false, onClick = onClick, role = Role.RadioButton)
            .semantics(mergeDescendants = true) { contentDescription = option.label }
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.Center) {
            Text(option.label, style = MaterialTheme.typography.titleMedium)
        }
    }
}
