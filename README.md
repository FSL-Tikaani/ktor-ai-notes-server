# 📝 Ktor AI Notes Server

Бэкенд для мобильного приложения умных студенческих конспектов. Написан на Kotlin + Ktor. Поддерживает загрузку фото и PDF, распознавание рукописного текста через Яндекс OCR, форматирование и суммаризацию через YandexGPT, а также ленту публичных конспектов с избранным.

---

## Стек

| Слой | Технология |
|---|---|
| Фреймворк | [Ktor](https://ktor.io/) + Netty |
| Язык | Kotlin 2.x, JDK 21 |
| База данных | H2 (embedded) + [Exposed ORM](https://github.com/JetBrains/Exposed) + HikariCP |
| Авторизация | JWT (HMAC256) |
| AI — распознавание | Яндекс Vision OCR (рукописный + печатный текст) |
| AI — текст | YandexGPT Lite |
| Контейнеризация | Docker + GitHub Container Registry |
| CI/CD | GitHub Actions → деплой на VPS по SSH |

---

## Функциональность

### Авторизация
- `POST /auth/register` — регистрация, возвращает JWT-токен
- `POST /auth/login` — вход, возвращает JWT-токен
- `POST /auth/registerUserData` — заполнение профиля (имя, курс)
- `GET /auth/check-token` — проверка валидности токена

### Конспекты
- `GET /notes` — список всех конспектов пользователя
- `GET /notes/{id}` — конкретный конспект (своя или публичная чужая)
- `POST /notes` — создать конспект (текст, фото, PDF)

### Дисциплины
- `GET /disciplines` — список дисциплин пользователя
- `POST /disciplines` — создать дисциплину (название, цвет, эмодзи)

### Загрузка файлов
- `POST /upload` — загрузить фото или PDF
- `POST /upload-with-transcript` — загрузить файл + сразу получить распознанный текст (OCR)
- `GET /files/{fileName}` — отдача загруженных файлов (без авторизации, для Image-loader)

### AI-функции
- `POST /ai/format` — отформатировать сырой OCR-текст в структурированный конспект
- `POST /ai/summarize` — краткая выжимка конспекта (3–5 буллетов)
- `POST /ai/ask` — задать вопрос по содержимому конспекта

### Сообщество
- `GET /community/notes?q=` — лента публичных конспектов с поиском
- `POST /community/favorites/{noteId}` — добавить в избранное
- `DELETE /community/favorites/{noteId}` — убрать из избранного

---

## Запуск локально

### 1. Клонировать репозиторий

```bash
git clone https://github.com/<your-username>/ktor-ai-notes-server.git
cd ktor-ai-notes-server
```

### 2. Создать `.env` файл

```env
# JWT
JWT_SECRET=your_super_secret_key
JWT_ISSUER=ktor-server
JWT_AUDIENCE=android-app
JWT_TTL_SECONDS=2592000

# Яндекс Cloud
YANDEX_API_KEY=your_yandex_api_key
FOLDER_ID=your_yandex_folder_id
YANDEX_OCR_LOG_DATA=false
```

### 3. Запустить

```bash
./gradlew run
```

Сервер поднимется на `http://0.0.0.0:8080`.

---

## Сборка и запуск через Docker

```bash
# Собрать образ
./gradlew buildImage

# Или через docker напрямую
docker build -t ktor-ai-notes-server .

# Запустить (нужен .env файл в текущей папке)
docker compose up -d
```

---

## Структура проекта

```
src/main/kotlin/
├── Application.kt          # Точка входа, конфигурация JWT
├── Routing.kt              # Сборка всех маршрутов
├── Models.kt               # DTO (запросы и ответы)
├── Env.kt                  # Чтение переменных окружения
├── routes/
│   ├── AuthRoutes.kt       # Регистрация и логин
│   ├── NotesRoutes.kt      # CRUD конспектов
│   ├── DisciplinesRoutes.kt
│   ├── UploadRoutes.kt     # Загрузка файлов + OCR
│   ├── AiRoutes.kt         # AI-эндпоинты
│   └── CommunityRoutes.kt  # Лента и избранное
├── database/
│   ├── DatabaseFactory.kt  # Инициализация H2 + HikariCP
│   ├── NotesApi.kt         # CRUD заметок + лента сообщества
│   ├── DisciplinesApi.kt   # CRUD дисциплин, счётчики заметок и темы
│   ├── FavoritesApi.kt     # Добавление и удаление из избранного
│   └── ...
└── services/
    ├── GroqService.kt      # Интеграция с YandexGPT
    ├── OcrService.kt       # Интеграция с Яндекс OCR
    └── FileService.kt      # Сохранение файлов на диск
```

---

## Заметки

- База данных H2 хранится в томе Docker — данные переживают перезапуск контейнера.
- Загруженные файлы лежат в папке `UploadsData/` и отдаются напрямую через `/files/{fileName}` без авторизации — это сделано намеренно, чтобы изображения можно было загружать стандартными Image-loader'ами на мобильном клиенте.
- `GroqService.kt` назван так по историческим причинам: изначально использовался Groq API, позже заменён на YandexGPT.
- Срок жизни JWT по умолчанию — 30 дней (можно изменить через `JWT_TTL_SECONDS`).
