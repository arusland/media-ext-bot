# media-ext

Telegram bot (Kotlin, JVM 1.8, Maven) that downloads media from URLs (YouTube and other sites via yt-dlp),
converts it with ffmpeg and sends it back to the chat.

## Build & run
- Build: `./build.sh` (`mvn clean package -DskipTests`, copies the fat jar to `dist/media-ext-bot.jar`)
- Tests: `mvn test`; a single test: `mvn test -Dtest=StreamProcessExtractorTest`.
  `YoutubeHelperTest` hits the network and needs docker + ffmpeg.
- Run: `java -jar dist/media-ext-bot.jar`. It reads `application.properties` (`bot.name`, `bot.token`,
  `ffmpeg.path`, `ffprobe.path`, `allowed.userids`, the first id is admin) and `~/.media-ext/media-ext.properties`.
- Deploy: `./deploy.sh` (needs `secvars.sh` with `DEPLOY_HOST`), or the ansible playbook in `ansible/`.

## Layout (`src/main/java/io/arusland`)
- `telegram/MediaExtTelegramBot.kt`: the bot. Handles updates, commands and URLs. Long-running work goes through
  `runCommandAsync`.
- `telegram/StatusMessageUpdater.kt`: edits the "Please, wait" message with the current stage and a progress bar
  (throttled).
- `youtube/YoutubeHelper.kt`: runs yt-dlp through `docker run ... jauderho/yt-dlp` (mounts `/tmp`) and reports
  `DownloadStatus` updates.
- `youtube/util/ProcessExtractor.kt`: parses yt-dlp stdout into progress and stage callbacks.
- `util/FfMpegUtils.kt`: ffmpeg conversion using `net.bramp.ffmpeg`.

## Rules
- After each edit, print a short commit message (one line, imperative mood, like the existing git history,
  e.g. "Show download progress in status message"). Don't commit unless asked.
