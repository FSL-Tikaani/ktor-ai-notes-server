package com.tikaani

import kotlinx.serialization.Serializable

// Все DTO собраны в одном файле чтобы не плодить пачку мелких файлов

// Статус загрузки файла на сервер - возвращается из uploadFileToServer
data class UploadFileStatus(
    var isSuccessfully: Boolean = false,
    var fileName: String = "",
    var error: String = "",
)

// Результат распознавания текста с фото через Яндекс OCR
data class OCRStatus(
    var isSuccessfully: Boolean = false,
    var extractedText: String = "",
    var error: String = "",
)

// Использовался когда рисовали bounding-boxes поверх фото для отладки OCR
data class GenerateBoxesStatus(
    var isSuccessfully: Boolean = false,
    var error: String = "",
)

// Логин/пароль для регистрации и входа
@Serializable
data class UserCredentials(
    val login: String,
    val password: String,
)

// Профиль пользователя - имя, курс и счетчики. Заполняется после регистрации
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

// Структуры под ответ Яндекс Vision OCR - вершины, рамки и блоки текста
@Serializable data class Vertex(val x: String, val y: String)
@Serializable data class BoundingBox(val vertices: List<Vertex>)
@Serializable data class BlockData(val boundingBox: BoundingBox, val lines: List<LineData>)
@Serializable data class LineData(val boundingBox: BoundingBox)
@Serializable data class TextAnnotationResponse(val result: Result)
@Serializable data class Result(val textAnnotation: TextAnnotation)
@Serializable data class FullTextAnnotation(val text: String = "")
@Serializable data class TextAnnotation(
    val blocks: List<BlockData> = emptyList(),
    val fullText: String = "",
    // fullTextAnnotation - запасной вариант, иногда Яндекс кладет весь текст сюда
    val fullTextAnnotation: FullTextAnnotation? = null,
)

// Что приходит с клиента при создании новой дисциплины
@Serializable
data class DisciplineRequest(
    val name: String,
    val color: String,   // строкой ("purple", "blue" и тд) - на клиенте мапится в цвет
    val emoji: String,   // символ для иконки дисциплины
)

// Дисциплина которую отдаем на клиент - сразу с счетчиком заметок и списком тем
@Serializable
data class DisciplineResponse(
    val id: Int,
    val name: String,
    val color: String,
    val emoji: String,
    val notesCount: Int,
    val topics: List<String>,
)

// Запрос на форматирование или сжатие текста через ИИ
@Serializable
data class AiTextRequest(val rawText: String)

// Запрос "задай вопрос к конспекту" - кидаем сам конспект и вопрос
@Serializable
data class AiAskRequest(val content: String, val question: String)

// Универсальный ответ от ИИ-эндпоинтов
@Serializable
data class AiResponse(val result: String)

// Тело запроса на создание заметки.
// content - финальный текст (после OCR + ИИ или просто ввод руками)
// fileType - "photo" / "pdf" / "text" - чтобы клиент знал как показывать
@Serializable
data class CreateNoteRequest(
    val disciplineId: Int,
    val topic: String,
    val title: String,
    val content: String,
    val fileType: String,
    val filePath: String? = null,
    val isPublic: Boolean = false,
)

// Ответ на /upload-with-transcript - имя сохраненного файла + распознанный текст
@Serializable
data class UploadWithTranscriptResponse(
    val fileName: String,
    val ocrText: String,
)

// Полная заметка - то что видит юзер на детальном экране
@Serializable
data class NoteResponse(
    val id: Int,
    val disciplineId: Int,
    val disciplineName: String,
    val disciplineColor: String = "amber",
    val topic: String,
    val title: String,
    val content: String,
    val fileType: String,
    val filePath: String? = null,
    val createdAt: String,
    val isPublic: Boolean = false,
)

// Тоже заметка, но для раздела "Сообщество" - тут еще автор и флаг "в избранном"
@Serializable
data class CommunityNoteResponse(
    val id: Int,
    val disciplineId: Int,
    val disciplineName: String,
    val disciplineEmoji: String,
    val disciplineColor: String,
    val topic: String,
    val title: String,
    val content: String,
    val fileType: String,
    val filePath: String? = null,
    val authorName: String,
    val createdAt: String,
    val isFavorite: Boolean,
)
