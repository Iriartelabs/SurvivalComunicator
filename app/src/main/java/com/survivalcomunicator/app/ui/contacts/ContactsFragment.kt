package com.survivalcomunicator.app.ui.contacts

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.core.os.bundleOf
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.survivalcomunicator.app.R
import com.survivalcomunicator.app.ui.common.NewContactDialogFragment

class ContactsFragment : Fragment(), NewContactDialogFragment.ContactAddedListener {
    
    private lateinit var viewModel: ContactsViewModel
    private lateinit var contactAdapter: ContactListAdapter
    
    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_contacts, container, false)
    }
    
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        
        viewModel = ViewModelProvider(this)[ContactsViewModel::class.java]

        // Configurar RecyclerView
        val recyclerView = view.findViewById<RecyclerView>(R.id.contacts_recycler_view)
        contactAdapter = ContactListAdapter { userId ->
            // Navegar al chat con el usuario seleccionado
            val bundle = bundleOf("userId" to userId)
            findNavController().navigate(R.id.action_contacts_to_chat, bundle)
        }
        
        recyclerView.apply {
            layoutManager = LinearLayoutManager(requireContext())
            adapter = contactAdapter
        }
        
        // Configurar botón para añadir contacto
        val addContactButton = view.findViewById<Button>(R.id.add_contact_button)
        addContactButton.setOnClickListener {
            // Mostrar diálogo para añadir nuevo contacto
            val dialogFragment = NewContactDialogFragment()
            dialogFragment.setContactAddedListener(this)
            dialogFragment.show(childFragmentManager, "NewContactDialog")
        }
        
        // Configurar mensaje de lista vacía
        val emptyView = view.findViewById<TextView>(R.id.empty_view)
        
        // Observar la lista de contactos
        viewModel.contacts.observe(viewLifecycleOwner) { contacts ->
            contactAdapter.submitList(contacts)
            
            // Mostrar u ocultar el mensaje de lista vacía
            if (contacts.isEmpty()) {
                emptyView.visibility = View.VISIBLE
                recyclerView.visibility = View.GONE
            } else {
                emptyView.visibility = View.GONE
                recyclerView.visibility = View.VISIBLE
            }
        }
        
        // Observar mensajes de error
        viewModel.errorMessage.observe(viewLifecycleOwner) { errorMessage ->
            if (!errorMessage.isNullOrEmpty()) {
                Toast.makeText(requireContext(), errorMessage, Toast.LENGTH_LONG).show()
            }
        }
        
        // Asegurarnos de refrescar los contactos cada vez que se muestra la pantalla
        viewModel.refreshContacts()
    }
    
    override fun onResume() {
        super.onResume()
        // Refrescar contactos al volver a la pantalla
        viewModel.refreshContacts()
    }
    
    // Implementación de la interfaz ContactAddedListener
    override fun onContactAdded(username: String) {
        viewModel.addNewContact(username)
    }
}