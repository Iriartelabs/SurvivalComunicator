package com.survivalcomunicator.app.ui.common

import android.app.Dialog
import android.content.Context
import android.os.Bundle
import android.widget.EditText
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.DialogFragment
import com.survivalcomunicator.app.R

/**
 * Diálogo unificado para añadir nuevos contactos.
 * Puede ser utilizado desde cualquier fragmento que implemente la interfaz ContactAddedListener.
 */
class NewContactDialogFragment : DialogFragment() {
    
    // Interfaz para notificar cuando se añade un contacto
    interface ContactAddedListener {
        fun onContactAdded(username: String)
    }
    
    private var listener: ContactAddedListener? = null
    
    fun setContactAddedListener(listener: ContactAddedListener) {
        this.listener = listener
    }
    
    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val builder = AlertDialog.Builder(requireContext())
        val inflater = requireActivity().layoutInflater
        val view = inflater.inflate(R.layout.dialog_new_contact, null)
        
        val usernameInput = view.findViewById<EditText>(R.id.username_input)
        
        builder.setView(view)
            .setTitle("Añadir nuevo contacto")
            .setPositiveButton("Añadir") { _, _ ->
                val username = usernameInput.text.toString().trim()
                if (username.isNotEmpty()) {
                    listener?.onContactAdded(username)
                }
            }
            .setNegativeButton("Cancelar") { dialog, _ ->
                dialog.cancel()
            }
        
        return builder.create()
    }
    
    override fun onAttach(context: Context) {
        super.onAttach(context)
        // Intentar detectar automáticamente el listener
        if (parentFragment is ContactAddedListener) {
            listener = parentFragment as ContactAddedListener
        }
    }
}