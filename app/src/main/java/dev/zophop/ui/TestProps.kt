package dev.zophop.ui

import androidx.compose.material3.*
import androidx.compose.runtime.Composable

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TestSheet() {
    val sheetState = rememberModalBottomSheetState(
        confirmValueChange = { it != SheetValue.Hidden }
    )
    ModalBottomSheet(
        onDismissRequest = {},
        sheetState = sheetState
    ) {
    }
}
