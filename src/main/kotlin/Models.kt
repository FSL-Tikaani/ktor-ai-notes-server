package com.tikaani

import kotlinx.serialization.Serializable


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

data class GenerateBoxesStatus(
    var isSuccessfully: Boolean = false,
    var error: String = "",
)

@Serializable
data class UserCredentials(
    val username: String,
    val password: String,
)

@Serializable
data class Vertex(val x: String, val y: String) // меняем на String
@Serializable
data class BoundingBox(val vertices: List<Vertex>)
@Serializable
data class BlockData(val boundingBox: BoundingBox, val lines: List<LineData>)
@Serializable
data class LineData(val boundingBox: BoundingBox)
@Serializable
data class TextAnnotationResponse(val result: Result)
@Serializable
data class Result(val textAnnotation: TextAnnotation)
@Serializable
data class TextAnnotation(val blocks: List<BlockData>)
