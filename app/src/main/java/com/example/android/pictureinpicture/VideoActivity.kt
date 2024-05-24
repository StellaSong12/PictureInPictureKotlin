package com.example.android.pictureinpicture

import android.app.PictureInPictureParams
import android.content.res.Configuration
import android.graphics.Rect
import android.os.Build
import android.os.Bundle
import android.support.v4.media.MediaMetadataCompat
import android.support.v4.media.session.MediaControllerCompat
import android.support.v4.media.session.MediaSessionCompat
import android.support.v4.media.session.PlaybackStateCompat
import android.util.Rational
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.core.view.doOnLayout
import com.example.android.pictureinpicture.databinding.VideoActivityBinding
import com.example.android.pictureinpicture.widget.MovieView

class VideoActivity: AppCompatActivity() {
    companion object {

        private const val TAG = "MediaSessionPlaybackActivity"

        const val EXTRA_VIDEO_SOURCE = "video_source"
        const val EXTRA_VIDEO_NAME = "video_name"

        private const val MEDIA_ACTIONS_PLAY_PAUSE =
            PlaybackStateCompat.ACTION_PLAY or
                    PlaybackStateCompat.ACTION_PAUSE or
                    PlaybackStateCompat.ACTION_PLAY_PAUSE

        private const val MEDIA_ACTIONS_ALL =
            MEDIA_ACTIONS_PLAY_PAUSE or
                    PlaybackStateCompat.ACTION_SKIP_TO_NEXT or
                    PlaybackStateCompat.ACTION_SKIP_TO_PREVIOUS

        private const val PLAYLIST_SIZE = 2

        private var pipActivity: AppCompatActivity? = null
    }

    private lateinit var binding: VideoActivityBinding

    private lateinit var session: MediaSessionCompat

    /**
     * Callbacks from the [MovieView] showing the video playback.
     */
    private val movieListener = object : MovieView.MovieListener() {

        override fun onMovieStarted() {
            // We are playing the video now. Update the media session state and the PiP window will
            // update the actions.
            updatePlaybackState(
                PlaybackStateCompat.STATE_PLAYING,
                binding.movie.getCurrentPosition(),
                binding.movie.getVideoResourceId()
            )
        }

        override fun onMovieStopped() {
            // The video stopped or reached its end. Update the media session state and the PiP
            // window will update the actions.
            updatePlaybackState(
                PlaybackStateCompat.STATE_PAUSED,
                binding.movie.getCurrentPosition(),
                binding.movie.getVideoResourceId()
            )
        }

        override fun onMovieMinimized() {
            // The MovieView wants us to minimize it. We enter Picture-in-Picture mode now.
            minimize()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        pipActivity?.finish()
        pipActivity = null

        super.onCreate(savedInstanceState)
        binding = VideoActivityBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // 获取传入的视频文件路径
        val videoPath = intent?.getIntExtra(EXTRA_VIDEO_SOURCE , R.raw.vid_bigbuckbunny)
        val videoName = intent?.getStringExtra(EXTRA_VIDEO_NAME)
        // 设置视频源
        binding.movie.setVideoResourceId(videoPath ?: R.raw.vid_bigbuckbunny)
        binding.movie.title = videoName ?: ""

        // Configure parameters for the picture-in-picture mode. We do this at the first layout of
        // the MovieView because we use its layout position and size.
        binding.movie.doOnLayout { updatePictureInPictureParams() }

        // Set up the video; it automatically starts.
        binding.movie.setMovieListener(movieListener)
    }

    override fun onStart() {
        super.onStart()
        initializeMediaSession()
    }

    private fun initializeMediaSession() {
        session = MediaSessionCompat(this, TAG)
        session.isActive = true
        MediaControllerCompat.setMediaController(this, session.controller)

        val metadata = MediaMetadataCompat.Builder()
            .putString(MediaMetadataCompat.METADATA_KEY_DISPLAY_TITLE, binding.movie.title)
            .build()
        session.setMetadata(metadata)

        session.setCallback(MediaSessionCallback(binding.movie))

        val state = if (binding.movie.isPlaying) {
            PlaybackStateCompat.STATE_PLAYING
        } else {
            PlaybackStateCompat.STATE_PAUSED
        }
        updatePlaybackState(
            state,
            MEDIA_ACTIONS_ALL,
            binding.movie.getCurrentPosition(),
            binding.movie.getVideoResourceId()
        )
    }

    override fun onStop() {
        super.onStop()
        // On entering Picture-in-Picture mode, onPause is called, but not onStop.
        // For this reason, this is the place where we should pause the video playback.
        binding.movie.pause()
        session.release()
    }

    override fun onRestart() {
        super.onRestart()
        if (!isInPictureInPictureMode) {
            // Show the video controls so the video can be easily resumed.
            binding.movie.showControls()
        }
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        adjustFullScreen(newConfig)
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) {
            adjustFullScreen(resources.configuration)
        }
    }

    override fun onPictureInPictureModeChanged(
        isInPictureInPictureMode: Boolean, newConfig: Configuration
    ) {
        super.onPictureInPictureModeChanged(isInPictureInPictureMode, newConfig)
        if (isInPictureInPictureMode) {
            // Hide the controls in picture-in-picture mode.
            binding.movie.hideControls()
            pipActivity = this
        } else {
            // Show the video controls if the video is not playing
            if (!binding.movie.isPlaying) {
                binding.movie.showControls()
            }
            pipActivity = null
        }
    }

    private fun updatePictureInPictureParams(): PictureInPictureParams {
        // Calculate the aspect ratio of the PiP screen. Between 0.5 to 2
        val width = (binding.movie.getPlayerWidth()?.coerceAtLeast(1) ?: 1).toFloat()
        val height = (binding.movie.getPlayerHeight()?.coerceAtLeast(1) ?: 1).toFloat()
        val aspectRatio = if (width / height > 2f) {
            Rational(2, 1)
        } else if (width / height < 0.5f) {
            Rational(1, 2)
        } else {
            Rational(width.toInt(), height.toInt())
        }
        // The movie view turns into the picture-in-picture mode.
        val visibleRect = Rect()
        binding.movie.getGlobalVisibleRect(visibleRect)
        val params = PictureInPictureParams.Builder()
            .setAspectRatio(aspectRatio)
            // Specify the portion of the screen that turns into the picture-in-picture mode.
            // This makes the transition animation smoother.
            .setSourceRectHint(visibleRect)
            .apply {
                // 当前运行的API版本大于或等于31（Android 12）
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    // The screen automatically turns into the picture-in-picture mode when it is hidden
                    // by the "Home" button.
                    setAutoEnterEnabled(true)
                }
            }
            .build()
        setPictureInPictureParams(params)
        return params
    }

    /**
     * Enters Picture-in-Picture mode.
     */
    private fun minimize() {
        enterPictureInPictureMode(updatePictureInPictureParams())
    }

    /**
     * Adjusts immersive full-screen flags depending on the screen orientation.

     * @param config The current [Configuration].
     */
    private fun adjustFullScreen(config: Configuration) {
        val insetsController = ViewCompat.getWindowInsetsController(window.decorView)
        insetsController?.systemBarsBehavior =
            WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        insetsController?.hide(WindowInsetsCompat.Type.systemBars())
        binding.movie.setAdjustViewBounds(false)
    }

    /**
     * Overloaded method that persists previously set media actions.

     * @param state The state of the video, e.g. playing, paused, etc.
     * @param position The position of playback in the video.
     * @param mediaId The media id related to the video in the media session.
     */
    private fun updatePlaybackState(
        @PlaybackStateCompat.State state: Int,
        position: Int,
        mediaId: Int
    ) {
        val actions = session.controller.playbackState.actions
        updatePlaybackState(state, actions, position, mediaId)
    }

    private fun updatePlaybackState(
        @PlaybackStateCompat.State state: Int,
        playbackActions: Long,
        position: Int,
        mediaId: Int
    ) {
        val builder = PlaybackStateCompat.Builder()
            .setActions(playbackActions)
            .setActiveQueueItemId(mediaId.toLong())
            .setState(state, position.toLong(), 1.0f)
        session.setPlaybackState(builder.build())
    }

    /**
     * Updates the [MovieView] based on the callback actions. <br></br>
     * Simulates a playlist that will disable actions when you cannot skip through the playlist in a
     * certain direction.
     */
    private inner class MediaSessionCallback(
        private val movieView: MovieView
    ) : MediaSessionCompat.Callback() {

        private var indexInPlaylist: Int = 1

        override fun onPlay() {
            movieView.play()
        }

        override fun onPause() {
            movieView.pause()
        }

        override fun onSkipToNext() {
            movieView.startVideo()
            if (indexInPlaylist < PLAYLIST_SIZE) {
                indexInPlaylist++
                if (indexInPlaylist >= PLAYLIST_SIZE) {
                    updatePlaybackState(
                        PlaybackStateCompat.STATE_PLAYING,
                        MEDIA_ACTIONS_PLAY_PAUSE or PlaybackStateCompat.ACTION_SKIP_TO_PREVIOUS,
                        movieView.getCurrentPosition(),
                        movieView.getVideoResourceId()
                    )
                } else {
                    updatePlaybackState(
                        PlaybackStateCompat.STATE_PLAYING,
                        MEDIA_ACTIONS_ALL,
                        movieView.getCurrentPosition(),
                        movieView.getVideoResourceId()
                    )
                }
            }
        }

        override fun onSkipToPrevious() {
            movieView.startVideo()
            if (indexInPlaylist > 0) {
                indexInPlaylist--
                if (indexInPlaylist <= 0) {
                    updatePlaybackState(
                        PlaybackStateCompat.STATE_PLAYING,
                        MEDIA_ACTIONS_PLAY_PAUSE or PlaybackStateCompat.ACTION_SKIP_TO_NEXT,
                        movieView.getCurrentPosition(),
                        movieView.getVideoResourceId()
                    )
                } else {
                    updatePlaybackState(
                        PlaybackStateCompat.STATE_PLAYING,
                        MEDIA_ACTIONS_ALL,
                        movieView.getCurrentPosition(),
                        movieView.getVideoResourceId()
                    )
                }
            }
        }
    }
}