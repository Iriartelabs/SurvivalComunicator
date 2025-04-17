package com.survivalcomunicator.app.ui.walkietalkie

import android.os.Bundle
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import com.survivalcomunicator.app.R

class WalkieTalkieFragment : Fragment() {
    
    private lateinit var viewModel: WalkieTalkieViewModel
    private lateinit var transmitButton: Button
    private lateinit var statusText: TextView
    
    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_walkie_talkie, container, false)
    }
    
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        
        viewModel = ViewModelProvider(this)[WalkieTalkieViewModel::class.java]
        
        transmitButton = view.findViewById(R.id.transmit_button)
        statusText = view.findViewById(R.id.status_text)
        
        transmitButton.setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    viewModel.startTransmitting()
                    true
                }
                MotionEvent.ACTION_UP -> {
                    viewModel.stopTransmitting()
                    true
                }
                else -> false
            }
        }
        
        viewModel.isTransmitting.observe(viewLifecycleOwner) { isTransmitting ->
            if (isTransmitting) {
                statusText.text = "Transmitiendo..."
                transmitButton.setBackgroundResource(R.drawable.bg_transmit_button_active)
            } else {
                statusText.text = "Suelte para transmitir"
                transmitButton.setBackgroundResource(R.drawable.bg_transmit_button)
            }
        }
        
        viewModel.connectedUsers.observe(viewLifecycleOwner) { users ->
            val usersText = view.findViewById<TextView>(R.id.connected_users_text)
            if (users.isEmpty()) {
                usersText.text = "No hay usuarios conectados"
            } else {
                usersText.text = "Usuarios conectados: ${users.joinToString(", ") { it.username }}"
            }
        }
        
        viewModel.incomingTransmission.observe(viewLifecycleOwner) { transmission ->
            if (transmission != null) {
                val incomingText = view.findViewById<TextView>(R.id.incoming_transmission_text)
                incomingText.text = "Recibiendo de: ${transmission.senderName}"
                incomingText.visibility = View.VISIBLE
            } else {
                val incomingText = view.findViewById<TextView>(R.id.incoming_transmission_text)
                incomingText.visibility = View.GONE
            }
        }
    }
    
    override fun onResume() {
        super.onResume()
        viewModel.connectToWalkieTalkieNetwork()
    }
    
    override fun onPause() {
        super.onPause()
        viewModel.disconnectFromWalkieTalkieNetwork()
    }
}