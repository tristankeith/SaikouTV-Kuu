package ani.saikou.manga.mangareader

import android.animation.ObjectAnimator
import android.graphics.drawable.Drawable
import android.net.Uri
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.view.updateLayoutParams
import androidx.recyclerview.widget.RecyclerView
import ani.saikou.R
import ani.saikou.databinding.ItemImageBinding
import ani.saikou.manga.MangaChapter
import ani.saikou.px
import ani.saikou.settings.CurrentReaderSettings
import ani.saikou.settings.CurrentReaderSettings.Directions.*
import ani.saikou.settings.CurrentReaderSettings.Layouts.PAGED
import ani.saikou.settings.UserInterfaceSettings
import com.bumptech.glide.Glide
import com.bumptech.glide.load.model.GlideUrl
import com.bumptech.glide.request.target.CustomViewTarget
import com.bumptech.glide.request.target.Target
import com.bumptech.glide.request.transition.Transition
import com.davemorrissey.labs.subscaleview.ImageSource
import com.davemorrissey.labs.subscaleview.SubsamplingScaleImageView
import java.io.File

class ImageAdapter(
    chapter: MangaChapter,
    private val settings: CurrentReaderSettings,
    private val uiSettings: UserInterfaceSettings
) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {
    val images = chapter.images!!

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ImageViewHolder {
        val binding = ItemImageBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ImageViewHolder(binding)
    }

    override fun getItemCount(): Int = images.size

    inner class ImageViewHolder(val binding: ItemImageBinding) : RecyclerView.ViewHolder(binding.root)

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        if (holder is ImageViewHolder) {
            val binding = holder.binding

            val imageView: SubsamplingScaleImageView = if (settings.layout != PAGED) {
                if (settings.padding) {
                    when (settings.direction) {
                        TOP_TO_BOTTOM -> binding.root.setPadding(0,0,0,16f.px)
                        LEFT_TO_RIGHT -> binding.root.setPadding(0,0,16f.px,0)
                        BOTTOM_TO_TOP -> binding.root.setPadding(0,16f.px,0,0)
                        RIGHT_TO_LEFT -> binding.root.setPadding(16f.px,0,0,0)
                    }
                }
                binding.imgProgImageNoGestures
            } else binding.imgProgImageGestures

            loadImage(imageView, position, binding.root)
        }
    }

    fun loadImage(imageView: SubsamplingScaleImageView, position: Int, parent: View) {

        val progress = parent.findViewById<View>(R.id.imgProgProgress)
        imageView.recycle()
        imageView.visibility = View.GONE
        val link = images[position].url
        val trans = images[position].transformation

        if (settings.layout != PAGED) {
            parent.updateLayoutParams {
                if (settings.direction != LEFT_TO_RIGHT && settings.direction != RIGHT_TO_LEFT) {
                    width = ViewGroup.LayoutParams.MATCH_PARENT
                    height = 480f.px
                }
                else {
                    width = 480f.px
                    height = ViewGroup.LayoutParams.MATCH_PARENT
                }
            }
        }

        if (link.url.isEmpty()) return
        Glide.with(imageView).download(GlideUrl(link.url) { link.headers })
            .override(Target.SIZE_ORIGINAL)
            .apply {
                val target = object : CustomViewTarget<SubsamplingScaleImageView, File>(imageView) {
                    override fun onLoadFailed(errorDrawable: Drawable?) {
                        progress.visibility = View.GONE
                    }

                    override fun onResourceCleared(placeholder: Drawable?) {}

                    override fun onResourceReady(resource: File, transition: Transition<in File>?) {
                        imageView.visibility = View.VISIBLE
                        if (settings.layout != PAGED)
                            parent.updateLayoutParams {
                                if (settings.direction != LEFT_TO_RIGHT && settings.direction != RIGHT_TO_LEFT)
                                    height = ViewGroup.LayoutParams.WRAP_CONTENT
                                else
                                    width = ViewGroup.LayoutParams.WRAP_CONTENT

                            }
                        view.setImage(ImageSource.uri(Uri.fromFile(resource)))
                        ObjectAnimator.ofFloat(parent, "alpha", 0f, 1f).setDuration((400 * uiSettings.animationSpeed).toLong())
                            .start()
                        progress.visibility = View.GONE
                    }
                }
                if (trans != null)
                    transform(File("").javaClass, trans).into(target)
                else
                    into(target)
            }
    }
}
