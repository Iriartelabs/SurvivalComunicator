package com.survivalcomunicator.app.ui.chat

import android.content.Context
import android.text.format.DateFormat
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.survivalcomunicator.app.R
import com.survivalcomunicator.app.model.ChatMessage
import com.survivalcomunicator.app.model.MessageStatus
import java.util.*

class MessageAdapter(
    private val context: Context,
    private val currentUserId: String,
    private val messageStatuses: Map<String, MessageStatus>,
    private val onRetryClick: (String) -> Unit,
    private val onMessageLongClick: (ChatMessage) -> Unit
) : ListAdapter<ChatMessage, RecyclerView.ViewHolder>(MessageDiffCallback()) {

    companion object {
        private const val VIEW_TYPE_SENT = 1
        private const val VIEW_TYPE_RECEIVED = 2
    }

    override fun getItemViewType(position: Int): Int {
        val message = getItem(position)
        return if (message.senderId == currentUserId) {
            VIEW_TYPE_SENT
        } else {
            VIEW_TYPE_RECEIVED
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        return when (viewType) {
            VIEW_TYPE_SENT -> {
                val view = LayoutInflater.from(parent.context)
                    .inflate(R.layout.item_message_sent, parent, false)
                SentMessageViewHolder(view)
            }
            else -> {
                val view = LayoutInflater.from(parent.context)
                    .inflate(R.layout.item_message_received, parent, false)
                ReceivedMessageViewHolder(view)
            }
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        val message = getItem(position)
        
        when (holder) {
            is SentMessageViewHolder -> {
                holder.bind(message, messageStatuses[message.id] ?: MessageStatus.SENT)
                holder.btnRetry.setOnClickListener {
                    onRetryClick(message.id)
                }
                holder.itemView.setOnLongClickListener {
                    onMessageLongClick(message)
                    true
                }
            }
            is ReceivedMessageViewHolder -> {
                holder.bind(message)
                holder.itemView.setOnLongClickListener {
                    onMessageLongClick(message)
                    true
                }
            }
        }
    }

    fun updateMessageStatus(newStatuses: Map<String, MessageStatus>) {
        notifyDataSetChanged() // En una implementación real, usaríamos DiffUtil para actualizar solo los elementos necesarios
    }

    inner class SentMessageViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val txtMessage: TextView = itemView.findViewById(R.id.txtMessage)
        private val txtTime: TextView = itemView.findViewById(R.id.txtTime)
        private val iconStatus: ImageView = itemView.findViewById(R.id.iconStatus)
        val btnRetry: ImageButton = itemView.findViewById(R.id.btnRetry)

        fun bind(message: ChatMessage, status: MessageStatus) {
            txtMessage.text = message.decryptedContent ?: "[Mensaje cifrado]"
            txtTime.text = formatTime(message.timestamp)
            
            // Configurar icono de estado según el estado del mensaje
            when (status) {
                MessageStatus.SENDING -> {
                    iconStatus.setImageResource(R.drawable.ic_message_sending)
                    btnRetry.visibility = View.GONE
                }
                MessageStatus.SENT -> {
                    iconStatus.setImageResource(R.drawable.ic_message_sent)
                    btnRetry.visibility = View.GONE
                }
                MessageStatus.DELIVERED -> {
                    iconStatus.setImageResource(R.drawable.ic_message_delivered)
                    btnRetry.visibility = View.GONE
                }
                MessageStatus.READ -> {
                    iconStatus.setImageResource(R.drawable.ic_message_read)
                    btnRetry.visibility = View.GONE
                }
                MessageStatus.FAILED -> {
                    iconStatus.setImageResource(R.drawable.ic_message_failed)
                    btnRetry.visibility = View.VISIBLE
                }
            }
        }
    }

    inner class ReceivedMessageViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val txtMessage: TextView = itemView.findViewById(R.id.txtMessage)
        private val txtTime: TextView = itemView.findViewById(R.id.txtTime)
        private val iconP2PIndicator: ImageView = itemView.findViewById(R.id.iconP2PIndicator)

        fun bind(message: ChatMessage) {
            txtMessage.text = message.decryptedContent ?: "[Mensaje cifrado]"
            txtTime.text = formatTime(message.timestamp)
            
            // Mostrar indicador P2P si el mensaje se recibió directamente
            iconP2PIndicator.visibility = if (message.receivedViaP2P) View.VISIBLE else View.GONE
        }
    }

    private fun formatTime(timestamp: Long): String {
        return DateFormat.format("HH:mm", Date(timestamp)).toString()
    }

    class MessageDiffCallback : DiffUtil.ItemCallback<ChatMessage>() {
        override fun areItemsTheSame(oldItem: ChatMessage, newItem: ChatMessage): Boolean {
            return oldItem.id == newItem.id
        }

        override fun areContentsTheSame(oldItem: ChatMessage, newItem: ChatMessage): Boolean {
            return oldItem == newItem
        }
    }
}