package com.survivalcomunicator.app.ui.chat

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.survivalcomunicator.app.R
import com.survivalcomunicator.app.models.Message
import com.survivalcomunicator.app.models.MessageType
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MessageAdapter : ListAdapter<MessageViewModel, RecyclerView.ViewHolder>(MessageDiffCallback()) {
    
    companion object {
        private const val VIEW_TYPE_SENT = 1
        private const val VIEW_TYPE_RECEIVED = 2
    }
    
    override fun getItemViewType(position: Int): Int {
        val message = getItem(position)
        return if (message.isMine) VIEW_TYPE_SENT else VIEW_TYPE_RECEIVED
    }
    
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        return when (viewType) {
            VIEW_TYPE_SENT -> {
                val view = LayoutInflater.from(parent.context)
                    .inflate(R.layout.item_message_sent, parent, false)
                SentMessageViewHolder(view)
            }
            VIEW_TYPE_RECEIVED -> {
                val view = LayoutInflater.from(parent.context)
                    .inflate(R.layout.item_message_received, parent, false)
                ReceivedMessageViewHolder(view)
            }
            else -> throw IllegalArgumentException("Invalid view type")
        }
    }
    
    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        val message = getItem(position)
        when (holder) {
            is SentMessageViewHolder -> holder.bind(message)
            is ReceivedMessageViewHolder -> holder.bind(message)
        }
    }
    
    class SentMessageViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        private val messageText: TextView = view.findViewById(R.id.message_text)
        private val messageTime: TextView = view.findViewById(R.id.message_time)
        private val messageStatus: ImageView = view.findViewById(R.id.message_status)
        private val audioIcon: ImageView? = view.findViewById(R.id.audio_icon)
        
        fun bind(message: MessageViewModel) {
            when (message.type) {
                MessageType.TEXT -> {
                    messageText.text = message.content
                    audioIcon?.visibility = View.GONE
                }
                MessageType.WALKIE_TALKIE -> {
                    messageText.text = "Mensaje de audio"
                    audioIcon?.visibility = View.VISIBLE
                }
                else -> {
                    messageText.text = message.content
                    audioIcon?.visibility = View.GONE
                }
            }
            
            val formatter = SimpleDateFormat("HH:mm", Locale.getDefault())
            messageTime.text = formatter.format(Date(message.timestamp))
            
            // Mostrar estado del mensaje
            when {
                !message.isDelivered -> messageStatus.setImageResource(R.drawable.ic_sending)
                !message.isRead -> messageStatus.setImageResource(R.drawable.ic_delivered)
                else -> messageStatus.setImageResource(R.drawable.ic_read)
            }
        }
    }
    
    class ReceivedMessageViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        private val messageText: TextView = view.findViewById(R.id.message_text)
        private val messageTime: TextView = view.findViewById(R.id.message_time)
        private val audioIcon: ImageView? = view.findViewById(R.id.audio_icon)
        
        fun bind(message: MessageViewModel) {
            when (message.type) {
                MessageType.TEXT -> {
                    messageText.text = message.content
                    audioIcon?.visibility = View.GONE
                }
                MessageType.WALKIE_TALKIE -> {
                    messageText.text = "Mensaje de audio"
                    audioIcon?.visibility = View.VISIBLE
                }
                else -> {
                    messageText.text = message.content
                    audioIcon?.visibility = View.GONE
                }
            }
            
            val formatter = SimpleDateFormat("HH:mm", Locale.getDefault())
            messageTime.text = formatter.format(Date(message.timestamp))
        }
    }
}

class MessageDiffCallback : DiffUtil.ItemCallback<MessageViewModel>() {
    override fun areItemsTheSame(oldItem: MessageViewModel, newItem: MessageViewModel): Boolean {
        return oldItem.id == newItem.id
    }
    
    override fun areContentsTheSame(oldItem: MessageViewModel, newItem: MessageViewModel): Boolean {
        return oldItem == newItem
    }
}

data class MessageViewModel(
    val id: String,
    val content: String,
    val timestamp: Long,
    val isMine: Boolean,
    val isRead: Boolean,
    val isDelivered: Boolean,
    val type: MessageType
)