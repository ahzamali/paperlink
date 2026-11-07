package com.paperink.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.CallMerge
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DividerDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.paperink.data.model.DocumentEntity
import com.paperink.data.model.MergeResult

data class MergeableItem(
    val uriString: String,
    val title: String,
    val pageCount: Int = 1,
    val fileSizeBytes: Long = 0L,
    val isExternalUri: Boolean = false
)

@Composable
fun MergePdfDialog(
    availableDocuments: List<DocumentEntity>,
    isMerging: Boolean,
    mergeResult: MergeResult?,
    onDismiss: () -> Unit,
    onAddFromStorage: () -> Unit,
    onMergeConfirmed: (items: List<MergeableItem>, outputTitle: String) -> Unit,
    onShareResult: (MergeResult) -> Unit,
    onSaveToDownloads: (MergeResult) -> Unit
) {
    val selectedItems = remember { mutableStateListOf<MergeableItem>() }
    var outputTitle by remember { mutableStateOf("Merged_Document") }
    var showSelectExistingDialog by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = { if (!isMerging) onDismiss() },
        modifier = Modifier
            .fillMaxWidth()
            .testTag("merge_pdf_dialog"),
        shape = RoundedCornerShape(20.dp),
        icon = {
            Surface(
                shape = CircleShape,
                color = MaterialTheme.colorScheme.primaryContainer,
                modifier = Modifier.size(52.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.Default.CallMerge,
                        contentDescription = "Merge PDFs",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(28.dp)
                    )
                }
            }
        },
        title = {
            Text(
                text = if (mergeResult != null) "PDFs Merged Successfully" else "Merge PDF Documents",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center
            )
        },
        text = {
            if (mergeResult != null) {
                // Success / Summary state
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.5f),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text(
                                text = mergeResult.outputFile.name,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = "Total Pages: ${mergeResult.totalPageCount}",
                                style = MaterialTheme.typography.bodyMedium
                            )
                            Text(
                                text = "File Size: ${formatFileSize(mergeResult.outputSizeBytes)}",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedButton(
                            onClick = { onShareResult(mergeResult) },
                            modifier = Modifier
                                .weight(1f)
                                .testTag("share_merged_pdf_button")
                        ) {
                            Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Share")
                        }
                        Button(
                            onClick = { onSaveToDownloads(mergeResult) },
                            modifier = Modifier
                                .weight(1f)
                                .testTag("save_merged_pdf_button")
                        ) {
                            Icon(Icons.Default.FileDownload, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Downloads")
                        }
                    }
                }
            } else if (isMerging) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    CircularProgressIndicator(modifier = Modifier.size(48.dp))
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = "Merging documents and assembling pages...",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                // Setup / Selection state
                Column(
                    modifier = Modifier.fillMaxWidth()
                ) {
                    OutlinedTextField(
                        value = outputTitle,
                        onValueChange = { outputTitle = it },
                        label = { Text("Output Document Title") },
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("merge_output_title_field")
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Documents to Merge (${selectedItems.size})",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            TextButton(
                                onClick = { showSelectExistingDialog = true },
                                modifier = Modifier.testTag("add_from_library_button")
                            ) {
                                Text("+ Library")
                            }
                            TextButton(
                                onClick = onAddFromStorage,
                                modifier = Modifier.testTag("add_from_storage_button")
                            ) {
                                Text("+ Storage")
                            }
                        }
                    }

                    if (selectedItems.isEmpty()) {
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 8.dp)
                        ) {
                            Text(
                                text = "Select at least 2 PDF documents to merge together.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.padding(16.dp)
                            )
                        }
                    } else {
                        LazyColumn(
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(max = 240.dp)
                                .testTag("merge_items_list"),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            itemsIndexed(selectedItems) { index, item ->
                                Surface(
                                    shape = RoundedCornerShape(8.dp),
                                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(horizontal = 10.dp, vertical = 6.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = "${index + 1}.",
                                            style = MaterialTheme.typography.labelMedium,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.width(22.dp)
                                        )

                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                text = item.title,
                                                style = MaterialTheme.typography.bodyMedium,
                                                fontWeight = FontWeight.SemiBold,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                            Text(
                                                text = "${item.pageCount} pages • ${formatFileSize(item.fileSizeBytes)}",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }

                                        // Reorder up
                                        IconButton(
                                            onClick = {
                                                if (index > 0) {
                                                    val temp = selectedItems[index]
                                                    selectedItems[index] = selectedItems[index - 1]
                                                    selectedItems[index - 1] = temp
                                                }
                                            },
                                            enabled = index > 0,
                                            modifier = Modifier.size(32.dp)
                                        ) {
                                            Icon(Icons.Default.ArrowUpward, contentDescription = "Move up", modifier = Modifier.size(16.dp))
                                        }

                                        // Reorder down
                                        IconButton(
                                            onClick = {
                                                if (index < selectedItems.size - 1) {
                                                    val temp = selectedItems[index]
                                                    selectedItems[index] = selectedItems[index + 1]
                                                    selectedItems[index + 1] = temp
                                                }
                                            },
                                            enabled = index < selectedItems.size - 1,
                                            modifier = Modifier.size(32.dp)
                                        ) {
                                            Icon(Icons.Default.ArrowDownward, contentDescription = "Move down", modifier = Modifier.size(16.dp))
                                        }

                                        // Remove
                                        IconButton(
                                            onClick = { selectedItems.removeAt(index) },
                                            modifier = Modifier.size(32.dp)
                                        ) {
                                            Icon(
                                                Icons.Default.Delete,
                                                contentDescription = "Remove",
                                                tint = MaterialTheme.colorScheme.error,
                                                modifier = Modifier.size(16.dp)
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            if (mergeResult == null && !isMerging) {
                Button(
                    onClick = {
                        val cleanTitle = outputTitle.trim().ifBlank { "Merged_Document" }
                        onMergeConfirmed(selectedItems.toList(), cleanTitle)
                    },
                    enabled = selectedItems.size >= 2,
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                    modifier = Modifier.testTag("confirm_merge_button")
                ) {
                    Text("Merge ${selectedItems.size} PDFs")
                }
            } else if (mergeResult != null) {
                Button(
                    onClick = onDismiss,
                    modifier = Modifier.testTag("done_merge_button")
                ) {
                    Text("Done")
                }
            }
        },
        dismissButton = {
            if (mergeResult == null && !isMerging) {
                TextButton(
                    onClick = onDismiss,
                    modifier = Modifier.testTag("cancel_merge_button")
                ) {
                    Text("Cancel")
                }
            }
        }
    )

    // Sub-dialog to pick from existing library documents
    if (showSelectExistingDialog) {
        AlertDialog(
            onDismissRequest = { showSelectExistingDialog = false },
            title = { Text("Select from Library") },
            text = {
                if (availableDocuments.isEmpty()) {
                    Text("No documents in library yet. Use '+ Storage' to pick files from device.")
                } else {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 300.dp)
                    ) {
                        items(availableDocuments.size) { idx ->
                            val doc = availableDocuments[idx]
                            val isAlreadyAdded = selectedItems.any { it.uriString == doc.filePath }
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = if (isAlreadyAdded) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f) else MaterialTheme.colorScheme.surface,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        if (!isAlreadyAdded) {
                                            selectedItems.add(
                                                MergeableItem(
                                                    uriString = doc.filePath,
                                                    title = doc.title,
                                                    pageCount = doc.pageCount,
                                                    fileSizeBytes = doc.fileSizeBytes
                                                )
                                            )
                                        }
                                        showSelectExistingDialog = false
                                    }
                                    .padding(vertical = 4.dp)
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(10.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(Icons.Default.Description, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                                    Spacer(modifier = Modifier.width(10.dp))
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(doc.title, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                        Text("${doc.pageCount} pages • ${formatFileSize(doc.fileSizeBytes)}", style = MaterialTheme.typography.labelSmall)
                                    }
                                    if (isAlreadyAdded) {
                                        Text("Added", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                                    }
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showSelectExistingDialog = false }) {
                    Text("Close")
                }
            }
        )
    }
}
