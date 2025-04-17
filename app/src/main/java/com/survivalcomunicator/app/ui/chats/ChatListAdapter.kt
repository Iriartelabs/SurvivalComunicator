package com.survivalcomunicator.app.ui.chats

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.survivalcomunicator.app.R
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class ChatListAdapter(private val onChatClicked: (String) -> Unit) : 
    ListAdapter<ChatPreview, ChatListAdapter.ViewHolder>(ChatDiffCallback()) {

    class ViewHolder(view: View, private val onChatClicked: (String) -> Unit) : RecyclerView.ViewHolder(view) {
        private val avatar: ImageView = view.findViewById(R.id.user_avatar)
        private val name: TextView = view.findViewById(R.id.user_name)
        private val lastMessage: TextView = view.findViewById(R.id.last_message)
        private val time: TextView = view.findViewById(R.id.message_time)
        private val unreadCount: TextView = view.findViewById(R.id.unread_count)
        private var currentUserId: String? = null
        
        init {
            view.setOnClickListener {
                currentUserId?.let { userId ->
                    onChatClicked(userId)
                }
            }
        }
        
        fun bind(chat: ChatPreview) {
            currentUserId = chat.userId
            name.text = chat.username
            lastMessage.text = chat.lastMessage
            
            // Format timestamp
            val formatter = SimpleDateFormat("HH:mm", Locale.getDefault())
            time.text = formatter.format(Date(chat.timestamp))
            
            // Show unread count if any
            if (chat.unreadCount > 0) {
                unreadCount.visibility = View.VISIBLE
                unreadCount.text = chat.unreadCount.toString()
            } else {
                unreadCount.visibility = View.GONE
            }
            
            // In a real app, you would load the avatar image here
            // For now, we just use a placeholder
            avatar.setImageResource(R.drawable.ic_user_placeholder)
        }
    }
    
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_chat, parent, false)
        return ViewHolder(view, onChatClicked)
    }
    
    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(getItem(position))
    }
}

class ChatDiffCallback : DiffUtil.ItemCallback<ChatPreview>() {
    override fun areItemsTheSame(oldItem: ChatPreview, newItem: ChatPreview): Boolean {
        return oldItem.userId == newItem.userId
    }
    
    override fun areContentsTheSame(oldItem: ChatPreview, newItem: ChatPreview): Boolean {
        return oldItem == newItem
    }
}

data class ChatPreview(
    val userId: String,
    val username: String,
    val lastMessage: String,
    val timestamp: Long,
    val unreadCount: Int
)