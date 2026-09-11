package com.naury.trubbi.feature.editor

import android.content.Context
import android.media.MediaMetadataRetriever
import android.net.Uri
import androidx.annotation.StringRes
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.C
import androidx.media3.common.Effect
import androidx.media3.common.MediaItem
import androidx.media3.common.audio.AudioProcessor
import androidx.media3.common.audio.SonicAudioProcessor
import androidx.media3.common.util.UnstableApi
import androidx.media3.effect.Crop
import androidx.media3.effect.Presentation
import androidx.media3.effect.ScaleAndRotateTransformation
import androidx.media3.effect.SpeedChangeEffect
import androidx.media3.transformer.Composition
import androidx.media3.transformer.EditedMediaItem
import androidx.media3.transformer.Effects
import androidx.media3.transformer.ExportException
import androidx.media3.transformer.ExportResult
import androidx.media3.transformer.ProgressHolder
import androidx.media3.transformer.Transformer
import com.naury.trubbi.R
import com.naury.trubbi.core.file.MediaStorage
import com.naury.trubbi.core.ui.StringProvider
import com.naury.trubbi.data.repo.RecordRepository
import com.naury.trubbi.domain.model.Labeled
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.util.UUID
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** 영상 편집기의 도구 탭. */
enum class VideoTool(@StringRes override val labelRes: Int) : Labeled {
    TRIM(R.string.video_tool_trim),
    CROP(R.string.video_tool_crop),
    ROTATE(R.string.video_tool_rotate),
    SPEED(R.string.video_tool_speed),
    AUDIO(R.string.video_tool_audio),
    QUALITY(R.string.video_tool_quality),
}

/** 내보내기 해상도. */
enum class VideoQuality(@StringRes override val labelRes: Int) : Labeled {
    ORIGINAL(R.string.option_original),
    HD(R.string.video_quality_hd),
    SD(R.string.video_quality_sd),
}

/**
 * 자르기 비율 프리셋.
 *
 * @property ratio null 이면 원본 그대로거나 사용자가 직접 끌어 정한 영역이다
 */
enum class CropRatio(@StringRes override val labelRes: Int, val ratio: Float?) : Labeled {
    ORIGINAL(R.string.option_original, null),
    FREE(R.string.crop_free, null),
    SQUARE(R.string.crop_square, 1f),
    WIDE(R.string.crop_16_9, 16f / 9f),
    TALL(R.string.crop_9_16, 9f / 16f),
}
enum class SpeedOption(val label: String, val factor: Float) {
    HALF("0.5x", 0.5f),
    NORMAL("1x", 1f),
    ONE_HALF("1.5x", 1.5f),
    DOUBLE("2x", 2f),
}

data class VideoEditorState(
    val loading: Boolean = true,
    val durationMs: Long = 0L,
    val srcWidth: Int = 0,
    val srcHeight: Int = 0,
    val trimStartMs: Long = 0L,
    val trimEndMs: Long = 0L,
    val rotation: Int = 0, // clockwise degrees: 0/90/180/270
    val flipH: Boolean = false,
    val cropRect: NormRect = NormRect.FULL,
    val cropPreset: CropRatio? = CropRatio.ORIGINAL,
    val speed: SpeedOption = SpeedOption.NORMAL,
    val muted: Boolean = false,
    val quality: VideoQuality = VideoQuality.ORIGINAL,
    val tool: VideoTool = VideoTool.TRIM,
    val saving: Boolean = false,
    val progress: Float = 0f,
    val error: String? = null,
) {
    /** 아무것도 바뀌지 않았는지. 그대로 저장하면 화질만 떨어지므로 재인코딩을 건너뛴다. */
    val isPristine: Boolean
        get() = trimStartMs <= 0L &&
            (trimEndMs <= 0L || trimEndMs >= durationMs) &&
            rotation == 0 &&
            !flipH &&
            cropRect.isFull &&
            speed == SpeedOption.NORMAL &&
            !muted &&
            quality == VideoQuality.ORIGINAL
}

/**
 * 영상 편집 상태와 내보내기.
 *
 * 실제 변환은 Media3 Transformer 가 한다. 자르기·회전·속도·해상도를 각각 효과로 쌓아
 * 한 번에 내보내므로, 여러 번 재인코딩하며 화질이 깎이는 일이 없다.
 *
 * 아무것도 바뀌지 않았으면 내보내지 않는다. 그대로 다시 인코딩하면 시간만 쓰고 화질만 떨어진다.
 */
@HiltViewModel
class VideoEditorViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val repo: RecordRepository,
    private val mediaStorage: MediaStorage,
    savedStateHandle: SavedStateHandle,
    private val strings: StringProvider,
) : ViewModel() {

    private val recordId: String = savedStateHandle.get<String>("recordId").orEmpty()
    val mediaUri: String = savedStateHandle.get<String>("mediaUri").orEmpty()

    val state = MutableStateFlow(VideoEditorState())

    init {
        viewModelScope.launch {
            val meta = withContext(Dispatchers.IO) {
                runCatching {
                    val r = MediaMetadataRetriever()
                    r.setDataSource(context, Uri.parse(mediaUri))
                    val d = r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L
                    val w = r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)?.toIntOrNull() ?: 0
                    val h = r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)?.toIntOrNull() ?: 0
                    r.release()
                    Triple(d, w, h)
                }.getOrDefault(Triple(0L, 0, 0))
            }
            state.update { it.copy(durationMs = meta.first, srcWidth = meta.second, srcHeight = meta.third, trimEndMs = meta.first, loading = false) }
        }
    }

    fun setTool(t: VideoTool) = state.update { it.copy(tool = t) }
    fun setTrim(startMs: Long, endMs: Long) = state.update {
        val lo = startMs.coerceIn(0L, it.durationMs)
        val hi = endMs.coerceIn(lo, it.durationMs)
        it.copy(trimStartMs = lo, trimEndMs = hi)
    }
    fun rotateLeft() = state.update { it.copy(rotation = (it.rotation + 270) % 360) }
    fun rotateRight() = state.update { it.copy(rotation = (it.rotation + 90) % 360) }
    fun toggleFlip() = state.update { it.copy(flipH = !it.flipH) }
    fun setSpeed(s: SpeedOption) = state.update { it.copy(speed = s) }
    fun toggleMute() = state.update { it.copy(muted = !it.muted) }
    fun setQuality(q: VideoQuality) = state.update { it.copy(quality = q) }

    /**
     * 비율 프리셋을 고르면 그 비율의 중앙 영역으로 맞춘다.
     *
     * @param preset `ORIGINAL` 은 전체, `FREE` 는 직접 끄는 상태, 나머지는 고정 비율
     */
    fun setCropPreset(p: CropRatio) = state.update { s ->
        val ratio = p.ratio
        val rect = when {
            p == CropRatio.ORIGINAL -> NormRect.FULL
            ratio == null -> if (s.cropRect.isFull) NormRect(0.15f, 0.15f, 0.85f, 0.85f) else s.cropRect // FREE
            s.srcWidth <= 0 || s.srcHeight <= 0 -> NormRect.FULL
            else -> {
                val w = s.srcWidth.toFloat()
                val h = s.srcHeight.toFloat()
                val rw: Float
                val rh: Float
                if (w / h > ratio) {
                    rh = h
                    rw = h * ratio
                } else {
                    rw = w
                    rh = w / ratio
                }
                val nw = rw / w
                val nh = rh / h
                val l = (1f - nw) / 2f
                val t = (1f - nh) / 2f
                NormRect(l, t, l + nw, t + nh)
            }
        }
        s.copy(cropRect = rect, cropPreset = p)
    }

    /** 손잡이로 크기를 바꾼다. 비율이 깨지므로 프리셋 표시는 해제된다. */
    fun setCropRect(r: NormRect) = state.update { it.copy(cropRect = r.sanitized(), cropPreset = CropRatio.FREE) }

    /** 크기는 그대로 두고 위치만 옮긴다. 비율이 유지되므로 프리셋 표시도 남는다. */
    fun moveCropRect(r: NormRect) = state.update { it.copy(cropRect = r.sanitized()) }

    fun reset() = state.update {
        it.copy(
            trimStartMs = 0L, trimEndMs = it.durationMs, rotation = 0, flipH = false,
            cropRect = NormRect.FULL, cropPreset = CropRatio.ORIGINAL, speed = SpeedOption.NORMAL,
            muted = false, quality = VideoQuality.ORIGINAL,
        )
    }

    @androidx.annotation.OptIn(UnstableApi::class)
    fun save(onSaved: () -> Unit) {
        val s = state.value
        if (s.saving) return
        state.update { it.copy(saving = true, progress = 0f, error = null) }

        val output = File(context.cacheDir, "video_edit_${UUID.randomUUID().toString().take(8)}.mp4")

        val clipping = MediaItem.ClippingConfiguration.Builder()
            .setStartPositionMs(s.trimStartMs)
            .setEndPositionMs(if (s.trimEndMs in 1 until s.durationMs) s.trimEndMs else C.TIME_END_OF_SOURCE)
            .build()
        val mediaItem = MediaItem.Builder()
            .setUri(Uri.parse(mediaUri))
            .setClippingConfiguration(clipping)
            .build()

        val videoEffects: List<Effect> = buildList {
            // 1) 자르기 — 0~1 비율을 Media3 가 쓰는 -1~1 좌표로 옮긴다
            if (!s.cropRect.isFull) {
                val r = s.cropRect.sanitized()
                add(Crop(r.left * 2f - 1f, r.right * 2f - 1f, 1f - r.bottom * 2f, 1f - r.top * 2f))
            }
            // 2) 반전·회전 — Media3 는 반시계 방향인데 화면 상태는 시계 방향이라 부호를 뒤집는다
            if (s.rotation != 0 || s.flipH) {
                add(
                    ScaleAndRotateTransformation.Builder()
                        .setScale(if (s.flipH) -1f else 1f, 1f)
                        .setRotationDegrees((-s.rotation).toFloat())
                        .build(),
                )
            }
            // 3) 재생 속도 — 영상 타임스탬프. 소리는 아래에서 같은 배속으로 맞춘다
            if (s.speed.factor != 1f) add(SpeedChangeEffect(s.speed.factor))
            // 4) 해상도·압축
            when (s.quality) {
                VideoQuality.HD -> add(Presentation.createForHeight(720))
                VideoQuality.SD -> add(Presentation.createForHeight(480))
                VideoQuality.ORIGINAL -> {}
            }
        }

        // 소리에도 같은 배속을 걸어 준다. 영상만 바꾸면 뒤로 갈수록 입 모양이 어긋난다.
        val audioProcessors: List<AudioProcessor> = buildList {
            if (!s.muted && s.speed.factor != 1f) {
                add(SonicAudioProcessor().apply { setSpeed(s.speed.factor) })
            }
        }

        val edited = EditedMediaItem.Builder(mediaItem)
            .setRemoveAudio(s.muted)
            .setEffects(Effects(audioProcessors, videoEffects))
            .build()

        val transformer = Transformer.Builder(context)
            .addListener(object : Transformer.Listener {
                override fun onCompleted(composition: Composition, exportResult: ExportResult) {
                    viewModelScope.launch {
                        withContext(Dispatchers.IO) {
                            runCatching {
                                val saved = mediaStorage.saveVideo(output, "edit")
                                repo.addEditedMedia(recordId, saved, mediaUri)
                            }
                            runCatching { output.delete() }
                        }
                        state.update { it.copy(saving = false, progress = 1f) }
                        onSaved()
                    }
                }

                override fun onError(composition: Composition, exportResult: ExportResult, exportException: ExportException) {
                    runCatching { output.delete() }
                    state.update { it.copy(saving = false, error = exportException.message ?: strings[R.string.video_export_failed]) }
                }
            })
            .build()

        transformer.start(edited, output.absolutePath)

        // 진행률은 Transformer 를 만든 스레드에서만 물어볼 수 있다.
        viewModelScope.launch {
            val holder = ProgressHolder()
            while (state.value.saving) {
                val st = transformer.getProgress(holder)
                if (st != Transformer.PROGRESS_STATE_NOT_STARTED) {
                    state.update { it.copy(progress = (holder.progress / 100f).coerceIn(0f, 1f)) }
                }
                delay(150)
            }
        }
    }
}
