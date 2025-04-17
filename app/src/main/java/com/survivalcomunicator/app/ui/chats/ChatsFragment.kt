package com.survivalcomunicator.app.ui.chats

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.survivalcomunicator.app.R

class ChatsFragment : Fragment() {
    
    private lateinit var viewModel: ChatsViewModel
    private lateinit var chatAdapter: ChatListAdapter
    
    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_chats, container, false)
    }
    
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        
        viewModel = ViewModelProvider(this)[ChatsViewModel::class.java]
        
        // Configurar RecyclerView
        val recyclerView = view.findViewById<RecyclerView>(R.id.chats_recycler_view)
        chatAdapter = ChatListAdapter { userId ->
            // Navegar al chat con el usuario seleccionado
            val action = ChatsFragmentDirections.actionChatsToChatFragment(userId)
            findNavController().navigate(action)
        }
        
        recyclerView.apply {
            layoutManager = LinearLayoutManager(requireContext())
            adapter = chatAdapter
        }
        
        // Configurar FAB para añadir nuevo chat
        val fab = view.findViewById<FloatingActionButton>(R.id.fab_new_chat)
        fab.setOnClickListener {
            // Mostrar diálogo para añadir nuevo contacto
            NewContactDialogFragment().show(childFragmentManager, "NewContactDialog")
        }
        
        // Observar la lista de chats
        viewModel.chats.observe(viewLifecycleOwner) { chats ->
            chatAdapter.submitList(chats)
        }
    }
}