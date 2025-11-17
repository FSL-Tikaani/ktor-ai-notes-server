package com.tikaani

data class UploadFileStatus(
    var isSuccessfully: Boolean = false,
    var fileName: String = "",
    var error: String = "",
)

data class OCRStatus(
    var isSuccessfully: Boolean = false,
    var extractedText: String = "",
    var error: String = "",
)