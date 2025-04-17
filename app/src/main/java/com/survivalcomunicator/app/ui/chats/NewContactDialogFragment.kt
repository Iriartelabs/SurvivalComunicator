package com.survivalcomunicator.app.ui.chats

import android.app.Dialog
import android.os.Bundle
import android.widget.EditText
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.DialogFragment
import androidx.lifecycle.ViewModelProvider
import com.survivalcomunicator.app.R

class NewContactDialogFragment : DialogFragment() {
    
    private lateinit var viewModel: ChatsViewModel
    
    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        viewModel = ViewModelProvider(requireParentFragment())[ChatsViewModel::class.java]
        
        val builder = AlertDialog.Builder(requireContext())
        val inflater = requireActivity().layoutInflater
        val view = inflater.inflate(R.layout.dialog_new_contact, null)
        
        val usernameInput = view.findViewById<EditText>(R.id.username_input)
        
        builder.setView(view)
            .setTitle("Añadir nuevo contacto")
            .setPositiveButton("Añadir") { _, _ ->
                val username = usernameInput.text.toString().trim()
                if (username.isNotEmpty()) {
                    viewModel.addNewContact(username)
                }
            }
            .setNegativeButton("Cancelar") { dialog, _ ->
                dialog.cancel()
            }
        
        return builder.create()
    }
}