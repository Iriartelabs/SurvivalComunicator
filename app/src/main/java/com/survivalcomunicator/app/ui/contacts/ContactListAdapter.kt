package com.survivalcomunicator.app.ui.contacts

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.survivalcomunicator.app.R
import com.survivalcomunicator.app.models.User
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class ContactListAdapter(private val onContactClicked: (String) -> Unit) : 
    ListAdapter<User, ContactListAdapter.ViewHolder>(ContactDiffCallback()) {

    class ViewHolder(view: View, private val onContactClicked: (String) -> Unit) : RecyclerView.ViewHolder(view) {
        private val avatar: ImageView = view.findViewById(R.id.contact_avatar)
        private val name: TextView = view.findViewById(R.id.contact_name)
        private val status: TextView = view.findViewById(R.id.contact_status)
        private var currentUserId: String? = null
        
        init {
            view.setOnClickListener {
                currentUserId?.let { userId ->
                    onContactClicked(userId)
                }
            }
        }
        
        fun bind(user: User) {
            currentUserId = user.id
            name.text = user.username
            
            // Formatear la última vez visto
            val dateFormat = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault())
            status.text = "Última vez activo: ${dateFormat.format(Date(user.lastSeen))}"
            
            // En una app real, cargarías la imagen del avatar aquí
            // Por ahora, usamos un placeholder
            avatar.setImageResource(R.drawable.ic_user_placeholder)
        }
    }
    
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_contact, parent, false)
        return ViewHolder(view, onContactClicked)
    }
    
    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(getItem(position))
    }
}

class ContactDiffCallback : DiffUtil.ItemCallback<User>() {
    override fun areItemsTheSame(oldItem: User, newItem: User): Boolean {
        return oldItem.id == newItem.id
    }
    
    override fun areContentsTheSame(oldItem: User, newItem: User): Boolean {
        return oldItem == newItem
    }
}