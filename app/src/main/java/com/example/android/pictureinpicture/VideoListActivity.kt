package com.example.android.pictureinpicture

import android.content.Context
import android.content.Intent
import android.media.MediaMetadataRetriever
import android.os.Bundle
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.android.pictureinpicture.databinding.VideoListActivityBinding
import com.example.android.pictureinpicture.databinding.VideoListItemBinding
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class VideoListActivity: AppCompatActivity() {

    private lateinit var binding: VideoListActivityBinding

    private val dataList = listOf(
        Pair(R.raw.vid_bigbuckbunny, "Big Buck Bunny"),
        Pair(R.raw.vid_coffee, "Coffee"),
        Pair(R.raw.vid_fire, "Fire"),
        Pair(R.raw.vid_flower, "Flower"),
        Pair(R.raw.vid_fruit, "Fruit"),
        Pair(R.raw.vid_leaves, "Leaves"),
        Pair(R.raw.vid_tea, "Tea"),
        Pair(R.raw.vid_vegetable, "Vegetable"),
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = VideoListActivityBinding.inflate(layoutInflater)
        setContentView(binding.root)
        // 设置Adapter
        binding.recyclerView.adapter = VideoListAdapter(this@VideoListActivity, dataList)
        binding.recyclerView.layoutManager = LinearLayoutManager(this@VideoListActivity)
    }

    class VideoListAdapter(private val context: Context, private val dataList: List<Pair<Int, String>>) : RecyclerView.Adapter<VideoListAdapter.ViewHolder>() {

        inner class ViewHolder(val binding: VideoListItemBinding) : RecyclerView.ViewHolder(binding.root)

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val binding = VideoListItemBinding.inflate(LayoutInflater.from(parent.context), parent, false)
            return ViewHolder(binding)
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val data = dataList[position]
            holder.binding.apply {
                 tvTitle.text = data.second
                CoroutineScope(Dispatchers.IO).launch {
                    val retriever = MediaMetadataRetriever()
                    val fd = context.resources.openRawResourceFd(data.first)
                    retriever.setDataSource(fd.fileDescriptor, fd.startOffset, fd.length)
                    withContext(Dispatchers.Main) {
                        ivCover.setImageBitmap(retriever.frameAtTime)
                    }
                    fd.close()
                    retriever.release()
                }
                root.setOnClickListener {
                    context.startActivity(Intent(context, VideoActivity::class.java).apply {
                        putExtra(VideoActivity.EXTRA_VIDEO_SOURCE, data.first)
                        putExtra(VideoActivity.EXTRA_VIDEO_NAME, data.second)
                    })
                }
            }
        }

        override fun getItemCount() = dataList.size
    }
}