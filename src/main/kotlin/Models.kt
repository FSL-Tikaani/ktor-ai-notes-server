package com.tikaani

import kotlinx.serialization.Serializable

// ─── Upload / OCR helpers ────────────────────────────────────────────────────

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

// ─── Auth ────────────────────────────────────────────────────────────────────

@Serializable
data class UserCredentials(
    val login: String,
    val password: String,
)

@Serializable
data class UserDataCredentials(
    val id: Int,
    val name: String,
    val surname: String,
    val isEmailConfirmed: Boolean,
    val avatar: String,
    val studyYear: Int,
    val numberDiscipline: Int,
    val numberNotes: Int,
)

// ─── OCR / Yandex Vision types ───────────────────────────────────────────────

@Serializable data class Vertex(val x: String, val y: String)
@Serializable data class BoundingBox(val vertices: List<Vertex>)
@Serializable data class BlockData(val boundingBox: BoundingBox, val lines: List<LineData>)
@Serializable data class LineData(val boundingBox: BoundingBox)
@Serializable data class TextAnnotationResponse(val result: Result)
@Serializable data class Result(val textAnnotation: TextAnnotation)
@Serializable data class TextAnnotation(val blocks: List<BlockData>)

// ─── Disciplines ──────────────────────────────────────────────────────────────

/** Тело запроса на создание дисциплины */
@Serializable
data class DisciplineRequest(
    val name: String,
    val color: String,   // e.g. "purple", "amber", "blue", …
    val emoji: String,   // e.g. "∑", "φ", "⊛", …
)

/** Ответ при получении / создании дисциплины */
@Serializable
data class DisciplineResponse(
    val id: Int,
    val name: String,
    val color: String,
    val emoji: String,
    val notesCount: Int,
    val topics: List<String>,
)

// ─── Notes ───────────────────────────────────────────────────────────────────

/**
 * Тело POST /notes.
 * content — итоговый текст заметки (после OCR+LLM или прямой ввод).
 * fileType — "photo" | "pdf" | "text"
 */
@Serializable
data class CreateNoteRequest(
    val disciplineId: Int,
    val topic: String,
    val title: String,
    val content: String,
    val fileType: String,
)

/** Полное представление заметки, возвращаемое сервером */
@Serializable
data class NoteResponse(
    val id: Int,
    val disciplineId: Int,
    val disciplineName: String,
    val topic: String,
    val title: String,
    val content: String,
    val fileType: String,
    val createdAt: String,
)
