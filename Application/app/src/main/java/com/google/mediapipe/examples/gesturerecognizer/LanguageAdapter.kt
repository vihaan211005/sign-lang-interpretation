import android.graphics.Color
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.google.mediapipe.examples.gesturerecognizer.Language
import com.google.mediapipe.examples.gesturerecognizer.R

class LanguageAdapter(
    private val languages: MutableList<Language>,
    private val onDownload: (Language) -> Unit,
    private val onSelect: (Language) -> Unit
) : RecyclerView.Adapter<LanguageAdapter.LanguageViewHolder>() {

    inner class LanguageViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val languageName: TextView = itemView.findViewById(R.id.language_name)
        val actionButton: Button = itemView.findViewById(R.id.action_button)
        val container: LinearLayout = itemView.findViewById(R.id.language_item_container)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): LanguageViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_language, parent, false)
        return LanguageViewHolder(view)
    }

    override fun onBindViewHolder(holder: LanguageViewHolder, position: Int) {
        val language = languages[position]
        holder.languageName.text = language.name
        holder.actionButton.text = if (language.isDownloaded) "Select" else "Download"
        if(language.isSelected)
            holder.container.setBackgroundColor(Color.parseColor("#D3D3D3"))
        else
            holder.container.setBackgroundColor(Color.TRANSPARENT)

        holder.actionButton.setOnClickListener {
            if (language.isDownloaded) {
                onSelect(language)
            }else{
                onDownload(language)
            }
        }
    }

    override fun getItemCount(): Int = languages.size
}
