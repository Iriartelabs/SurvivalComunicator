package com.survivalcomunicator.app.ui.chat

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuInflater
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.survivalcomunicator.app.R
import com.survivalcomunicator.app.data.Message
import com.survivalcomunicator.app.databinding.FragmentChatBinding
import com.survivalcomunicator.app.network.LocationBackgroundService
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import org.koin.androidx.viewmodel.ext.android.viewModel
import org.koin.core.parameter.parametersOf

/**
 * Fragmento para la pantalla de chat, actualizado para soportar P2P.
 */
class ChatFragment : Fragment() {

    private var _binding: FragmentChatBinding? = null
    private val binding get() = _binding!!

    private val chatViewModel: ChatViewModel by viewModel {
        parametersOf(arguments?.getString("userId"), arguments?.getString("username"))
    }

    private lateinit var messageAdapter: MessageAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setHasOptionsMenu(true)
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentChatBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        
        // Configurar la interfaz
        setupUI()
        
        // Configurar observadores
        observeViewModel()
        
        // Iniciar servicio de ubicación en segundo plano
        startLocationService()
        
        // Obtener parámetros de la conversación
        val userId = arguments?.getString("userId")
        val username = arguments?.getString("username")
        
        // Si tenemos usuario, iniciar chat
        if (userId != null && username != null) {
            chatViewModel.setChatUser(userId, username)
            binding.tvUsername.text = username
        }
    }

    /**
     * Configura los elementos de la interfaz.
     */
    private fun setupUI() {
        // Configurar RecyclerView
        messageAdapter = MessageAdapter { messageId -> 
            chatViewModel.retryMessage(messageId)
        }
        
        binding.messagesRecyclerView.apply {
            layoutManager = LinearLayoutManager(context).apply {
                stackFromEnd = true
            }
            adapter = messageAdapter
        }
        
        // Configurar botón de enviar
        binding.sendButton.setOnClickListener {
            val content = binding.messageEditText.text.toString().trim()
            if (content.isNotEmpty()) {
                chatViewModel.sendMessage(content)
                binding.messageEditText.text?.clear()
            }
        }
        
        // Configurar botón para cambiar modo de conexión
        binding.btnSwitchConnectionMode.setOnClickListener {
            chatViewModel.switchConnectionMode()
        }
    }

    /**
     * Configura los observadores para los cambios en el ViewModel.
     */
    private fun observeViewModel() {
        // Observar mensajes
        chatViewModel.messages.observe(viewLifecycleOwner) { messages ->
            messageAdapter.submitList(messages)
            if (messages.isNotEmpty()) {
                binding.messagesRecyclerView.scrollToPosition(messages.size - 1)
            }
        }
        
        // Observar usuario actual
        chatViewModel.currentChatUser.observe(viewLifecycleOwner) { user ->
            binding.tvUsername.text = user.username
        }
        
        // Observar errores
        chatViewModel.error.observe(viewLifecycleOwner) { error ->
            binding.tvError.apply {
                isVisible = !error.isNullOrEmpty()
                text = error
            }
        }
        
        // Observar estado de conexión
        viewLifecycleOwner.lifecycleScope.launch {
            chatViewModel.connectionState.collectLatest { state ->
                updateConnectionStateUI(state)
            }
        }
        
        // Observar tipo de conexión
        viewLifecycleOwner.lifecycleScope.launch {
            chatViewModel.connectionType.collectLatest { type ->
                updateConnectionTypeUI(type)
            }
        }
    }

    /**
     * Actualiza la UI según el estado de conexión.
     */
    private fun updateConnectionStateUI(state: ConnectionState) {
        // Mostrar tarjeta de estado
        binding.connectionStatusCard.isVisible = true
        
        // Configurar UI según estado
        when (state) {
            ConnectionState.DISCONNECTED -> {
                binding.pbConnecting.isVisible = false
                binding.tvConnectionStatus.text = "Desconectado"
                binding.tvConnectionStatus.setTextColor(ContextCompat.getColor(requireContext(), R.color.connection_status_disconnected))
                binding.ivConnectionStatus.setImageResource(R.drawable.ic_connection_status_disconnected)
                binding.btnSwitchConnectionMode.isEnabled = true
            }
            ConnectionState.CONNECTING -> {
                binding.pbConnecting.isVisible = true
                binding.ivConnectionIcon.isVisible = false
                binding.tvConnectionStatus.text = "Conectando..."
                binding.tvConnectionStatus.setTextColor(ContextCompat.getColor(requireContext(), R.color.connection_status_connecting))
                binding.ivConnectionStatus.setImageResource(R.drawable.ic_connection_status_connecting)
                binding.btnSwitchConnectionMode.isEnabled = false
            }
            ConnectionState.CONNECTED -> {
                binding.pbConnecting.isVisible = false
                binding.ivConnectionIcon.isVisible = true
                binding.tvConnectionStatus.text = "Conectado"
                binding.tvConnectionStatus.setTextColor(ContextCompat.getColor(requireContext(), R.color.connection_status_connected))
                binding.ivConnectionStatus.setImageResource(R.drawable.ic_connection_status_connected)
                binding.btnSwitchConnectionMode.isEnabled = true
            }
        }
    }

    /**
     * Actualiza la UI según el tipo de conexión.
     */
    private fun updateConnectionTypeUI(type: ConnectionType) {
        when (type) {
            ConnectionType.NONE -> {
                binding.tvConnectionType.text = ""
                binding.ivConnectionIcon.setImageResource(R.drawable.ic_connection_none)
            }
            ConnectionType.P2P -> {
                binding.tvConnectionType.text = "P2P"
                binding.tvConnectionStatus.text = "Conectado directamente (P2P)"
                binding.ivConnectionIcon.setImageResource(R.drawable.ic_connection_p2p)
            }
            ConnectionType.WEBSOCKET -> {
                binding.tvConnectionType.text = "WS"
                binding.tvConnectionStatus.text = "Conectado vía servidor"
                binding.ivConnectionIcon.setImageResource(R.drawable.ic_connection_websocket)
            }
        }
    }

    /**
     * Inicia el servicio de ubicación en segundo plano.
     */
    private fun startLocationService() {
        val intent = LocationBackgroundService.getStartIntent(requireContext())
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            requireContext().startForegroundService(intent)
        } else {
            requireContext().startService(intent)
        }
    }

    override fun onCreateOptionsMenu(menu: Menu, inflater: MenuInflater) {
        inflater.inflate(R.menu.chat_menu, menu)
        super.onCreateOptionsMenu(menu, inflater)
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.action_change_connection -> {
                chatViewModel.switchConnectionMode()
                true
            }
            R.id.action_clear_errors -> {
                chatViewModel.clearError()
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}

/**
 * Adaptador para la lista de mensajes.
 */
class MessageAdapter(
    private val onRetryClick: (messageId: String) -> Unit
) : androidx.recyclerview.widget.ListAdapter<Message, androidx.recyclerview.widget.RecyclerView.ViewHolder>(
    object : androidx.recyclerview.widget.DiffUtil.ItemCallback<Message>() {
        override fun areItemsTheSame(oldItem: Message, newItem: Message): Boolean {
            return oldItem.id == newItem.id
        }

        override fun areContentsTheSame(oldItem: Message, newItem: Message): Boolean {
            return oldItem == newItem
        }
    }
) {
    companion object {
        private const val VIEW_TYPE_SENT = 1
        private const val VIEW_TYPE_RECEIVED = 2
    }

    override fun getItemViewType(position: Int): Int {
        return if (getItem(position).isSentByMe()) {
            VIEW_TYPE_SENT
        } else {
            VIEW_TYPE_RECEIVED
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): androidx.recyclerview.widget.RecyclerView.ViewHolder {
        return when (viewType) {
            VIEW_TYPE_SENT -> {
                val view = LayoutInflater.from(parent.context)
                    .inflate(R.layout.item_message_sent, parent, false)
                SentMessageViewHolder(view, onRetryClick)
            }
            else -> {
                val view = LayoutInflater.from(parent.context)
                    .inflate(R.layout.item_message_received, parent, false)
                ReceivedMessageViewHolder(view)
            }
        }
    }

    override fun onBindViewHolder(holder: androidx.recyclerview.widget.RecyclerView.ViewHolder, position: Int) {
        val message = getItem(position)
        when (holder) {
            is SentMessageViewHolder -> holder.bind(message)
            is ReceivedMessageViewHolder -> holder.bind(message)
        }
    }

    /**
     * ViewHolder para mensajes enviados.
     */
    class SentMessageViewHolder(
        itemView: View,
        private val onRetryClick: (messageId: String) -> Unit
    ) : androidx.recyclerview.widget.RecyclerView.ViewHolder(itemView) {
        fun bind(message: Message) {
            // Implementación de binding para mensajes enviados
            // (Omitida por brevedad, pero incluiría mostrar contenido, estado, timestamp, etc.)
        }
    }

    /**
     * ViewHolder para mensajes recibidos.
     */
    class ReceivedMessageViewHolder(itemView: View) : androidx.recyclerview.widget.RecyclerView.ViewHolder(itemView) {
        fun bind(message: Message) {
            // Implementación de binding para mensajes recibidos
            // (Omitida por brevedad, pero incluiría mostrar contenido, timestamp, etc.)
        }
    }
}