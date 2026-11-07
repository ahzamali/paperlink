package com.paperink.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.paperink.data.model.NormalizedPoint
import com.paperink.data.model.StrokeAnnotation
import com.paperink.data.model.TextAnnotation
import com.paperink.data.model.ToolType
import kotlin.math.hypot

@Composable
fun AnnotationCanvas(
    toolType: ToolType,
    selectedColor: Color,
    strokeWidthDp: Float,
    strokes: List<StrokeAnnotation>,
    textNotes: List<TextAnnotation>,
    onAddStroke: (StrokeAnnotation) -> Unit,
    onDeleteStroke: (Long) -> Unit,
    onAddTextNote: (TextAnnotation) -> Unit,
    onDeleteTextNote: (Long) -> Unit,
    onUpdateTextNote: (TextAnnotation) -> Unit,
    modifier: Modifier = Modifier
) {
    var activePoints = remember { mutableStateListOf<NormalizedPoint>() }
    var activeTextDialogState by remember { mutableStateOf<TextDialogState?>(null) }
    var viewingNoteState by remember { mutableStateOf<TextAnnotation?>(null) }

    BoxWithConstraints(modifier = modifier) {
        val canvasWidth = constraints.maxWidth.toFloat().coerceAtLeast(1f)
        val canvasHeight = constraints.maxHeight.toFloat().coerceAtLeast(1f)

        // The drawing canvas layer - only attach touch interceptor when an active drawing/editing tool is selected
        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .testTag("annotation_drawing_canvas")
                .then(
                    if (toolType != ToolType.READ) {
                        Modifier.pointerInput(toolType, selectedColor, strokeWidthDp, strokes, textNotes) {
                            when (toolType) {
                                ToolType.READ -> {
                                    // Touch events handled by parent vertical scroll
                                }
                                ToolType.PEN, ToolType.HIGHLIGHTER -> {
                            detectDragGestures(
                                onDragStart = { offset ->
                                    activePoints.clear()
                                    val nx = (offset.x / canvasWidth).coerceIn(0f, 1f)
                                    val ny = (offset.y / canvasHeight).coerceIn(0f, 1f)
                                    activePoints.add(NormalizedPoint(nx, ny))
                                },
                                onDrag = { change, _ ->
                                    change.consume()
                                    val nx = (change.position.x / canvasWidth).coerceIn(0f, 1f)
                                    val ny = (change.position.y / canvasHeight).coerceIn(0f, 1f)
                                    activePoints.add(NormalizedPoint(nx, ny))
                                },
                                onDragEnd = {
                                    if (activePoints.size >= 2) {
                                        val newStroke = StrokeAnnotation(
                                            isHighlighter = (toolType == ToolType.HIGHLIGHTER),
                                            colorArgb = selectedColor.toArgb(),
                                            strokeWidthDp = strokeWidthDp,
                                            points = activePoints.toList()
                                        )
                                        onAddStroke(newStroke)
                                    }
                                    activePoints.clear()
                                },
                                onDragCancel = {
                                    activePoints.clear()
                                }
                            )
                        }
                        ToolType.TEXT -> {
                            detectTapGestures { offset ->
                                val nx = (offset.x / canvasWidth).coerceIn(0f, 1f)
                                val ny = (offset.y / canvasHeight).coerceIn(0f, 1f)
                                activeTextDialogState = TextDialogState(
                                    normalizedX = nx,
                                    normalizedY = ny,
                                    initialText = "",
                                    existingId = null
                                )
                            }
                        }
                        ToolType.ERASER -> {
                            detectDragGestures(
                                onDragStart = { offset ->
                                    eraseAt(offset, canvasWidth, canvasHeight, strokes, textNotes, onDeleteStroke, onDeleteTextNote)
                                },
                                onDrag = { change, _ ->
                                    change.consume()
                                    eraseAt(change.position, canvasWidth, canvasHeight, strokes, textNotes, onDeleteStroke, onDeleteTextNote)
                                }
                            )
                        }
                    }
                }
            } else Modifier
        )
    ) {
            // Draw existing strokes
            for (stroke in strokes) {
                if (stroke.points.size < 2) continue
                val strokeColor = Color(stroke.colorArgb)
                val isHighlighter = stroke.isHighlighter
                val effectiveAlpha = if (isHighlighter) 0.45f else 1.0f
                val effectiveWidth = if (isHighlighter) stroke.strokeWidthDp * 2.6f else stroke.strokeWidthDp

                val path = Path()
                val firstPt = stroke.points[0]
                path.moveTo(firstPt.x * size.width, firstPt.y * size.height)

                for (i in 1 until stroke.points.size) {
                    val pt = stroke.points[i]
                    path.lineTo(pt.x * size.width, pt.y * size.height)
                }

                drawPath(
                    path = path,
                    color = strokeColor.copy(alpha = effectiveAlpha),
                    style = Stroke(
                        width = effectiveWidth.dp.toPx(),
                        cap = StrokeCap.Round,
                        join = StrokeJoin.Round
                    ),
                    blendMode = if (isHighlighter) BlendMode.Multiply else BlendMode.SrcOver
                )
            }

            // Draw active in-progress stroke
            if (activePoints.size >= 2) {
                val isHighlighter = (toolType == ToolType.HIGHLIGHTER)
                val effectiveAlpha = if (isHighlighter) 0.45f else 1.0f
                val effectiveWidth = if (isHighlighter) strokeWidthDp * 2.6f else strokeWidthDp

                val livePath = Path()
                livePath.moveTo(activePoints[0].x * size.width, activePoints[0].y * size.height)
                for (i in 1 until activePoints.size) {
                    livePath.lineTo(activePoints[i].x * size.width, activePoints[i].y * size.height)
                }

                drawPath(
                    path = livePath,
                    color = selectedColor.copy(alpha = effectiveAlpha),
                    style = Stroke(
                        width = effectiveWidth.dp.toPx(),
                        cap = StrokeCap.Round,
                        join = StrokeJoin.Round
                    ),
                    blendMode = if (isHighlighter) BlendMode.Multiply else BlendMode.SrcOver
                )
            }
        }

        // Render text notes as interactive floating badges
        for (note in textNotes) {
            val posX = (note.x * canvasWidth).toInt()
            val posY = (note.y * canvasHeight).toInt()

            Box(
                modifier = Modifier
                    .offset { IntOffset(posX, posY) }
                    .background(
                        color = Color.White.copy(alpha = 0.95f),
                        shape = RoundedCornerShape(8.dp)
                    )
                    .border(
                        width = 1.5.dp,
                        color = Color(note.colorArgb).copy(alpha = 0.8f),
                        shape = RoundedCornerShape(8.dp)
                    )
                    .clickable {
                        viewingNoteState = note
                    }
                    .padding(horizontal = 8.dp, vertical = 4.dp)
                    .testTag("text_note_badge_${note.id}")
            ) {
                Text(
                    text = if (note.text.length > 25) note.text.take(22) + "..." else note.text,
                    fontSize = note.fontSizeSp.sp,
                    color = Color(note.colorArgb),
                    style = MaterialTheme.typography.labelSmall
                )
            }
        }

        // Text input dialog for creating new note
        activeTextDialogState?.let { state ->
            var textInput by remember { mutableStateOf(state.initialText) }

            AlertDialog(
                onDismissRequest = { activeTextDialogState = null },
                title = { Text("Add Text Annotation") },
                text = {
                    OutlinedTextField(
                        value = textInput,
                        onValueChange = { textInput = it },
                        label = { Text("Note content") },
                        placeholder = { Text("Type note or comment...") },
                        modifier = Modifier.testTag("text_note_input")
                    )
                },
                confirmButton = {
                    Button(
                        onClick = {
                            if (textInput.isNotBlank()) {
                                onAddTextNote(
                                    TextAnnotation(
                                        text = textInput.trim(),
                                        x = state.normalizedX,
                                        y = state.normalizedY,
                                        colorArgb = selectedColor.toArgb(),
                                        fontSizeSp = 13f
                                    )
                                )
                            }
                            activeTextDialogState = null
                        },
                        modifier = Modifier.testTag("save_text_note_button")
                    ) {
                        Text("Add")
                    }
                },
                dismissButton = {
                    TextButton(onClick = { activeTextDialogState = null }) {
                        Text("Cancel")
                    }
                }
            )
        }

        // Viewing / Editing existing text note modal
        viewingNoteState?.let { note ->
            var isEditing by remember { mutableStateOf(false) }
            var editText by remember { mutableStateOf(note.text) }

            AlertDialog(
                onDismissRequest = { viewingNoteState = null },
                title = {
                    Text(if (isEditing) "Edit Note" else "Text Annotation")
                },
                text = {
                    if (isEditing) {
                        OutlinedTextField(
                            value = editText,
                            onValueChange = { editText = it },
                            modifier = Modifier.testTag("edit_note_input")
                        )
                    } else {
                        Text(
                            text = note.text,
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                },
                confirmButton = {
                    if (isEditing) {
                        Button(
                            onClick = {
                                if (editText.isNotBlank()) {
                                    onUpdateTextNote(note.copy(text = editText.trim()))
                                }
                                viewingNoteState = null
                            }
                        ) {
                            Text("Save")
                        }
                    } else {
                        TextButton(
                            onClick = { isEditing = true }
                        ) {
                            Icon(Icons.Default.Edit, contentDescription = "Edit")
                            Text(" Edit", modifier = Modifier.padding(start = 4.dp))
                        }
                    }
                },
                dismissButton = {
                    Row {
                        IconButton(
                            onClick = {
                                onDeleteTextNote(note.id)
                                viewingNoteState = null
                            }
                        ) {
                            Icon(Icons.Default.Delete, contentDescription = "Delete Note", tint = MaterialTheme.colorScheme.error)
                        }
                        TextButton(onClick = { viewingNoteState = null }) {
                            Text("Close")
                        }
                    }
                }
            )
        }
    }
}

private fun eraseAt(
    pos: Offset,
    width: Float,
    height: Float,
    strokes: List<StrokeAnnotation>,
    textNotes: List<TextAnnotation>,
    onDeleteStroke: (Long) -> Unit,
    onDeleteTextNote: (Long) -> Unit
) {
    val normX = pos.x / width
    val normY = pos.y / height
    val threshold = 28f / width // approx 28px hit radius

    val thresholdSq = threshold * threshold

    // Check strokes with bounding-box pre-filtering
    for (stroke in strokes) {
        val pts = stroke.points
        if (pts.isEmpty()) continue

        var minX = Float.MAX_VALUE
        var maxX = -Float.MAX_VALUE
        var minY = Float.MAX_VALUE
        var maxY = -Float.MAX_VALUE
        for (pt in pts) {
            if (pt.x < minX) minX = pt.x
            if (pt.x > maxX) maxX = pt.x
            if (pt.y < minY) minY = pt.y
            if (pt.y > maxY) maxY = pt.y
        }

        // Quick rejection if touch point is outside stroke bounding box expanded by hit radius
        if (normX < minX - threshold || normX > maxX + threshold ||
            normY < minY - threshold || normY > maxY + threshold) {
            continue
        }

        for (pt in pts) {
            val dx = pt.x - normX
            val dy = pt.y - normY
            if (dx * dx + dy * dy < thresholdSq) {
                onDeleteStroke(stroke.id)
                return
            }
        }
    }

    // Check notes
    val noteThreshold = threshold * 2
    val noteThresholdSq = noteThreshold * noteThreshold
    for (note in textNotes) {
        val dx = note.x - normX
        val dy = note.y - normY
        if (dx * dx + dy * dy < noteThresholdSq) {
            onDeleteTextNote(note.id)
            return
        }
    }
}

data class TextDialogState(
    val normalizedX: Float,
    val normalizedY: Float,
    val initialText: String,
    val existingId: Long?
)
