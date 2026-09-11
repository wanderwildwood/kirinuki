package com.wanderwildwood.kirinuki.ui.compose.sync

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import com.wanderwildwood.kirinuki.R
import com.wanderwildwood.kirinuki.ui.compose.components.ConfirmDialog

@Composable
fun LeaveSyncChainDialog(
    onDismiss: () -> Unit,
    onOk: () -> Unit,
) = ConfirmDialog(
    onDismiss = onDismiss,
    onOk = onOk,
    title = R.string.leave_sync_chain,
    body = R.string.are_you_sure_leave_sync_chain,
)

@Preview
@Composable
private fun PreviewLeaveSyncChainDialog() {
    LeaveSyncChainDialog(
        onDismiss = {},
        onOk = {},
    )
}
