package com.survivalcomunicator.app.ui.localnetwork

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import com.survivalcomunicator.app.data.models.User
import com.survivalcomunicator.app.databinding.FragmentLocalNetworkModeBinding
import com.survivalcomunicator.app.ui.adapters.ContactsAdapter
import org.koin.androidx.viewmodel.ext.android.viewModel

class LocalNetworkModeFragment : Fragment() {
    
    private val viewModel: LocalNetworkViewModel by viewModel()
    private var _binding: FragmentLocalNetworkModeBinding? = null
    private val binding get() = _binding!!
    
    private val contactsAdapter = ContactsAdapter { user ->
        navigateToChat(user)
    }
    
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
        
        setupRecyclerView()
        setupObservers()
        setupListeners()
        
        viewModel.startDiscovery()
    }
    
    private fun setupRecyclerView() {
        binding.recyclerViewContacts.apply {
            layoutManager = LinearLayoutManager(requireContext())
            adapter = contactsAdapter
        }
    }
    
    private fun setupObservers() {
        viewModel.discoveredUsers.observe(viewLifecycleOwner) { users ->
            contactsAdapter.submitList(users)
            binding.textNoDevices.isVisible = users.isEmpty()
        }
        
        viewModel.discoveringDevices.observe(viewLifecycleOwner) { discovering ->
            binding.progressDiscovering.isVisible = discovering
            binding.textDiscovering.isVisible = discovering
        }
        
        viewModel.errorMessage.observe(viewLifecycleOwner) { errorMsg ->
            if (errorMsg != null) {
                binding.textError.text = errorMsg
                binding.textError.isVisible = true
            } else {
                binding.textError.isVisible = false
            }
        }
    }
    
    private fun setupListeners() {
        binding.buttonScan.setOnClickListener {
            viewModel.startDiscovery()
        }
    }
    
    private fun navigateToChat(user: User) {
        val directions = LocalNetworkModeFragmentDirections.actionToChat(user.id)
        findNavController().navigate(directions)
    }
    
    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}