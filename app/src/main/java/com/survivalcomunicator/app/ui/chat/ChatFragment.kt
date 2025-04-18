package com.survivalcomunicator.app.ui.chat

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.ImageButton
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.button.MaterialButton
import com.survivalcomunicator.app.R

class ChatFragment : Fragment() {
    
    private lateinit var viewModel: ChatViewModel
    private lateinit var adapter: MessageAdapter
    
    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_chat, container, false)
    }
    
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        
        // Obtener userId del argumento - usando Bundle
        val userId = arguments?.getString("userId") ?: ""
        
        // Inicializa ViewModel
        val factory = ChatViewModelFactory(requireActivity().application, userId)
        viewModel = ViewModelProvider(this, factory)[ChatViewModel::class.java]
        
        // Configura RecyclerView
        val recyclerView = view.findViewById<RecyclerView>(R.id.messages_recycler_view)
        adapter = MessageAdapter()
        
        recyclerView.apply {
            layoutManager = LinearLayoutManager(requireContext()).apply {
                stackFromEnd = true  // Para que el scroll comience desde abajo
            }
            adapter = this@ChatFragment.adapter
        }
        
        // Configura botones
        val messageInput = view.findViewById<EditText>(R.id.message_input)
        val sendButton = view.findViewById<ImageButton>(R.id.send_button)
        val walkieTalkieButton = view.findViewById<MaterialButton>(R.id.walkie_talkie_button)
        
        sendButton.setOnClickListener {
            val messageText = messageInput.text.toString().trim()
            if (messageText.isNotEmpty()) {
                viewModel.sendTextMessage(messageText)
                messageInput.text.clear()
            }
        }
        
        walkieTalkieButton.setOnTouchListener { _, event ->
            when (event.action) {
                android.view.MotionEvent.ACTION_DOWN -> {
                    viewModel.startWalkieTalkieRecording()
                    walkieTalkieButton.setText("Grabando...")
                    true
                }
                android.view.MotionEvent.ACTION_UP -> {
                    viewModel.stopWalkieTalkieRecording()
                    walkieTalkieButton.setText("Mantén para hablar")
                    true
                }
                else -> false
            }
        }
        
        // Observa mensajes
        viewModel.messages.observe(viewLifecycleOwner) { messages ->
            adapter.submitList(messages) {
                // Scroll al último mensaje cuando la lista se actualiza
                if (messages.isNotEmpty()) {
                    recyclerView.smoothScrollToPosition(messages.size - 1)
                }
            }
        }
        
        // Observa el título
        viewModel.chatTitle.observe(viewLifecycleOwner) { title ->
            requireActivity().title = title
        }
    }
}