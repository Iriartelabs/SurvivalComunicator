package com.survivalcomunicator.app.ui.settings

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import com.survivalcomunicator.app.R
import com.survivalcomunicator.app.databinding.FragmentLocalNetworkModeBinding
import com.survivalcomunicator.app.model.User
import com.survivalcomunicator.app.network.p2p.LocalNetworkDiscovery
import com.survivalcomunicator.app.ui.contact.ContactsAdapter
import com.survivalcomunicator.app.utils.NetworkUtils
import com.survivalcomunicator.app.utils.SessionManager
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

/**
 * Fragmento que muestra la interfaz para el Modo de Red Local
 */
class LocalNetworkModeFragment : Fragment() {

    private var _binding: FragmentLocalNetworkModeBinding? = null
    private val binding get() = _binding!!
    
    private lateinit var viewModel: LocalNetworkViewModel
    private lateinit var contactsAdapter: ContactsAdapter
    
    private var localNetworkDiscovery: LocalNetworkDiscovery? = null
    private lateinit var sessionManager: SessionManager
    
    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentLocalNetworkModeBinding.inflate(inflater, container, false)
        return binding.root
    }
    
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        
        viewModel = ViewModelProvider(this).get(LocalNetworkViewModel::class.java)
        sessionManager = SessionManager(requireContext())
        
        setupUI()
        setupObservers()
        initializeNetworkDiscovery()
    }
    
    private fun setupUI() {
        // Configurar RecyclerView para dispositivos descubiertos
        contactsAdapter = ContactsAdapter { user ->
            // Clic en un dispositivo descubierto
            navigateToChatWithUser(user)
        }
        
        binding.recyclerDevices.apply {
            layoutManager = LinearLayoutManager(requireContext())
            adapter = contactsAdapter
        }
        
        // Botón para activar/desactivar modo local
        binding.switchLocalMode.setOnCheckedChangeListener { _, isChecked ->
            viewModel.setLocalModeEnabled(isChecked)
            updateUIForLocalMode(isChecked)
        }
        
        // Botón para buscar dispositivos manualmente
        binding.btnScanDevices.setOnClickListener {
            startDeviceDiscovery()
        }
        
        // Botón para volver
        binding.btnBack.setOnClickListener {
            findNavController().navigateUp()
        }
    }
    
    private fun setupObservers() {
        // Observar estado de descubrimiento
        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.discoveryState.collectLatest { state ->
                updateDiscoveryStateUI(state)
            }
        }
        
        // Observar dispositivos descubiertos
        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.discoveredUsers.collectLatest { users ->
                updateDiscoveredDevices(users)
            }
        }
        
        // Observar estado del modo local
        viewModel.isLocalModeEnabled.observe(viewLifecycleOwner) { isEnabled ->
            binding.switchLocalMode.isChecked = isEnabled
            updateUIForLocalMode(isEnabled)
        }
    }
    
    private fun initializeNetworkDiscovery() {
        val userId = sessionManager.getUserId()
        val username = sessionManager.getUsername()
        val publicKey = sessionManager.getUserPublicKey()
        
        if (userId != null && username != null && publicKey != null) {
            localNetworkDiscovery = LocalNetworkDiscovery(
                requireContext(),
                userId,
                username,
                publicKey
            )
            
            localNetworkDiscovery?.initialize()
            
            // Restaurar estado anterior si el modo local estaba activo
            if (viewModel.isLocalModeEnabled.value == true) {
                startLocalNetworkMode()
            }
            
            // Observar cambios en el estado de descubrimiento
            viewLifecycleOwner.lifecycleScope.launch {
                localNetworkDiscovery?.discoveryState?.collectLatest { state ->
                    viewModel.updateDiscoveryState(state)
                }
            }
            
            // Observar dispositivos descubiertos
            viewLifecycleOwner.lifecycleScope.launch {
                localNetworkDiscovery?.discoveredUsers?.collectLatest { users ->
                    viewModel.updateDiscoveredUsers(users)
                }
            }
        } else {
            // No hay sesión válida
            Toast.makeText(
                requireContext(),
                "No se pudo inicializar el modo de red local: datos de usuario incompletos",
                Toast.LENGTH_LONG
            ).show()
            
            binding.switchLocalMode.isEnabled = false
        }
    }
    
    private fun startLocalNetworkMode() {
        if (!NetworkUtils.isWifiConnected(requireContext())) {
            Toast.makeText(
                requireContext(),
                "Se requiere conexión WiFi para el modo de red local",
                Toast.LENGTH_SHORT
            ).show()
            binding.switchLocalMode.isChecked = false
            viewModel.setLocalModeEnabled(false)
            return
        }
        
        // Iniciar servicio local
        localNetworkDiscovery?.startLocalService()
        
        // Iniciar descubrimiento de dispositivos
        startDeviceDiscovery()
        
        // Actualizar preferencias
        sessionManager.setLocalModeEnabled(true)
    }
    
    private fun stopLocalNetworkMode() {
        // Detener descubrimiento
        localNetworkDiscovery?.stopDiscovery()
        
        // Detener servicio local
        localNetworkDiscovery?.stopLocalService()
        
        // Actualizar preferencias
        sessionManager.setLocalModeEnabled(false)
    }
    
    private fun startDeviceDiscovery() {
        if (!NetworkUtils.isWifiConnected(requireContext())) {
            Toast.makeText(
                requireContext(),
                "Se requiere conexión WiFi para descubrir dispositivos",
                Toast.LENGTH_SHORT
            ).show()
            return
        }
        
        binding.progressDiscovery.visibility = View.VISIBLE
        binding.txtDiscoveryStatus.text = getString(R.string.discovering_devices)
        
        localNetworkDiscovery?.startDiscovery()
    }
    
    private fun updateUIForLocalMode(isEnabled: Boolean) {
        if (isEnabled) {
            binding.cardLocalModeInfo.visibility = View.VISIBLE
            binding.localModeContainer.visibility = View.VISIBLE
            binding.btnScanDevices.isEnabled = true
            
            // Si está habilitado pero no iniciado, iniciar
            if (localNetworkDiscovery?.discoveryState?.value == LocalNetworkDiscovery.DiscoveryState.INACTIVE) {
                startLocalNetworkMode()
            }
        } else {
            binding.cardLocalModeInfo.visibility = View.GONE
            binding.localModeContainer.visibility = View.GONE
            binding.btnScanDevices.isEnabled = false
            
            // Si está deshabilitado pero activo, detener
            if (localNetworkDiscovery?.discoveryState?.value != LocalNetworkDiscovery.DiscoveryState.INACTIVE) {
                stopLocalNetworkMode()
            }
        }
    }
    
    private fun updateDiscoveryStateUI(state: LocalNetworkDiscovery.DiscoveryState) {
        when (state) {
            LocalNetworkDiscovery.DiscoveryState.INACTIVE -> {
                binding.progressDiscovery.visibility = View.GONE
                binding.txtDiscoveryStatus.text = getString(R.string.discovery_inactive)
            }
            LocalNetworkDiscovery.DiscoveryState.STARTING -> {
                binding.progressDiscovery.visibility = View.VISIBLE
                binding.txtDiscoveryStatus.text = getString(R.string.discovery_starting)
            }
            LocalNetworkDiscovery.DiscoveryState.ACTIVE -> {
                binding.progressDiscovery.visibility = View.VISIBLE
                binding.txtDiscoveryStatus.text = getString(R.string.discovery_active)
            }
            LocalNetworkDiscovery.DiscoveryState.FAILED -> {
                binding.progressDiscovery.visibility = View.GONE
                binding.txtDiscoveryStatus.text = getString(R.string.discovery_failed)
                
                Toast.makeText(
                    requireContext(),
                    "Error en descubrimiento de dispositivos",
                    Toast.LENGTH_SHORT
                ).show()
            }
        }
    }
    
    private fun updateDiscoveredDevices(users: List<User>) {
        contactsAdapter.submitList(users)
        
        // Actualizar mensaje de estado
        if (users.isEmpty()) {
            binding.txtNoDevices.visibility = View.VISIBLE
        } else {
            binding.txtNoDevices.visibility = View.GONE
        }
        
        binding.txtDeviceCount.text = getString(R.string.devices_found, users.size)
    }
    
    private fun navigateToChatWithUser(user: User) {
        // Guardar usuario en la base de datos si no existe
        viewModel.saveUserIfNotExists(user)
        
        // Navegar al chat con modo local activado
        val action = LocalNetworkModeFragmentDirections.actionToChat(
            contactId = user.id,
            contactName = user.username,
            useLocalMode = true
        )
        findNavController().navigate(action)
    }
    
    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
    
    override fun onDestroy() {
        super.onDestroy()
        // No detener el descubrimiento si el modo local está activado
        // para que siga funcionando en segundo plano
        if (viewModel.isLocalModeEnabled.value != true) {
            localNetworkDiscovery?.cleanup()
        }
    }
}