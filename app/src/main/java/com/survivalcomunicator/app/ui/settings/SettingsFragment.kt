package com.survivalcomunicator.app.ui.settings

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import com.survivalcomunicator.app.R

class SettingsFragment : Fragment() {
    
    private lateinit var viewModel: SettingsViewModel
    
    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_settings, container, false)
    }
    
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        
        viewModel = ViewModelProvider(this)[SettingsViewModel::class.java]
        
        // Referencias a vistas
        val usernameInput = view.findViewById<EditText>(R.id.username_input)
        val serverUrlInput = view.findViewById<EditText>(R.id.server_url_input)
        val publicKeyText = view.findViewById<TextView>(R.id.public_key_text)
        val saveButton = view.findViewById<Button>(R.id.save_settings_button)
        val exportButton = view.findViewById<Button>(R.id.export_key_button)
        val importButton = view.findViewById<Button>(R.id.import_key_button)
        
        // Cargar configuración actual
        viewModel.username.observe(viewLifecycleOwner) { username ->
            usernameInput.setText(username)
        }
        
        viewModel.serverUrl.observe(viewLifecycleOwner) { serverUrl ->
            serverUrlInput.setText(serverUrl)
        }
        
        viewModel.publicKey.observe(viewLifecycleOwner) { publicKey ->
            val shortened = if (publicKey.length > 40) {
                "${publicKey.substring(0, 20)}...${publicKey.substring(publicKey.length - 20)}"
            } else {
                publicKey
            }
            publicKeyText.text = shortened
        }
        
        // Botón guardar
        saveButton.setOnClickListener {
            val username = usernameInput.text.toString().trim()
            val serverUrl = serverUrlInput.text.toString().trim()
            
            if (username.isEmpty()) {
                Toast.makeText(requireContext(), "El nombre de usuario no puede estar vacío", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            
            if (serverUrl.isEmpty()) {
                Toast.makeText(requireContext(), "La URL del servidor no puede estar vacía", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            
            viewModel.saveSettings(username, serverUrl)
            Toast.makeText(requireContext(), "Configuración guardada", Toast.LENGTH_SHORT).show()
        }
        
        // Botón exportar clave
        exportButton.setOnClickListener {
            viewModel.exportPublicKey()?.let { key ->
                // En una app real, aquí implementaríamos la exportación real
                // Por ejemplo, copiar al portapapeles o guardar en un archivo
                Toast.makeText(requireContext(), "Clave exportada", Toast.LENGTH_SHORT).show()
            }
        }
        
        // Botón importar clave
        importButton.setOnClickListener {
            // En una app real, aquí mostraríamos un diálogo para importar
            Toast.makeText(requireContext(), "Función no implementada", Toast.LENGTH_SHORT).show()
        }
        
        // Observar mensajes de error
        viewModel.errorMessage.observe(viewLifecycleOwner) { errorMessage ->
            if (errorMessage.isNotEmpty()) {
                Toast.makeText(requireContext(), errorMessage, Toast.LENGTH_LONG).show()
            }
        }
    }
}