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

        val userId = arguments?.getString("userId") ?: throw IllegalStateException("Falta userId en argumentos")

        // Usamos ChatViewModelFactory para pasar el userId al ViewModel
        val factory = ChatViewModelFactory(requireActivity().application, userId)
        viewModel = ViewModelProvider(this, factory)[ChatViewModel::class.java]

        // Configurar RecyclerView
        val recyclerView = view.findViewById<RecyclerView>(R.id.messages_recycler_view)
        adapter = MessageAdapter()

        recyclerView.apply {
            layoutManager = LinearLayoutManager(requireContext()).apply {
                stackFromEnd = true
            }
            adapter = this@ChatFragment.adapter
        }

        // Configurar botones
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
                    walkieTalkieButton.text = "Grabando..."
                    true
                }
                android.view.MotionEvent.ACTION_UP -> {
                    viewModel.stopWalkieTalkieRecording()
                    walkieTalkieButton.text = "Mantén para hablar"
                    true
                }
                else -> false
            }
        }

        // Observar mensajes
        viewModel.messages.observe(viewLifecycleOwner) { messages ->
            adapter.submitList(messages) {
                if (messages.isNotEmpty()) {
                    recyclerView.smoothScrollToPosition(messages.size - 1)
                }
            }
        }

        // Observar título del chat
        viewModel.chatTitle.observe(viewLifecycleOwner) { title ->
            requireActivity().title = title
        }
    }
}
