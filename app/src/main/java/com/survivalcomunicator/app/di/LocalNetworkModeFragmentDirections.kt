package com.survivalcomunicator.app.di

import android.app.Application
import com.survivalcomunicator.app.data.MessageRepository
import com.survivalcomunicator.app.data.UserRepository
import com.survivalcomunicator.app.data.local.SurvivalDatabase
import com.survivalcomunicator.app.network.LocationManager
import com.survivalcomunicator.app.network.NetworkService
import com.survivalcomunicator.app.network.NetworkServiceImpl
import com.survivalcomunicator.app.network.WebSocketManager
import com.survivalcomunicator.app.network.p2p.LocalNetworkDiscovery
import com.survivalcomunicator.app.network.p2p.NATTraversal
import com.survivalcomunicator.app.network.p2p.P2PClient
import com.survivalcomunicator.app.network.p2p.P2PServer
import com.survivalcomunicator.app.network.p2p.StunClient
import com.survivalcomunicator.app.security.CryptoManager
import com.survivalcomunicator.app.security.PeerVerification
import com.survivalcomunicator.app.ui.chat.ChatViewModel
import com.survivalcomunicator.app.ui.chats.ChatsViewModel
import com.survivalcomunicator.app.ui.localnetwork.LocalNetworkViewModel
import com.survivalcomunicator.app.ui.verification.VerificationViewModel
import org.koin.android.ext.koin.androidContext
import org.koin.androidx.viewmodel.dsl.viewModel
import org.koin.core.context.startKoin
import org.koin.dsl.module

class SurvivalCommunicatorApp : Application() {

    override fun onCreate() {
        super.onCreate()
        
        startKoin {
            androidContext(this@SurvivalCommunicatorApp)
            modules(appModule)
        }
    }
    
    private val appModule = module {
        // Servicios singleton
        single { CryptoManager.getInstance() }
        single<NetworkService> { NetworkServiceImpl() }
        single { WebSocketManager(get()) }
        
        // Base de datos
        single { SurvivalDatabase.getDatabase(androidContext()) }
        
        // DAOs
        single { get<SurvivalDatabase>().userDao() }
        single { get<SurvivalDatabase>().messageDao() }
        single { get<SurvivalDatabase>().pendingMessageDao() }
        
        // Repositorios
        single { UserRepository(get(), get()) }
        single { MessageRepository(get(), get(), get()) }
        
        // Componentes P2P
        single { P2PServer(androidContext()) }
        single { StunClient() }
        single { NATTraversal(get()) }
        single { P2PClient(androidContext(), get(), get(), get(), get()) }
        single { LocalNetworkDiscovery(androidContext(), get()) }
        single { PeerVerification(get()) }
        
        // Gestores
        single { LocationManager(androidContext(), get()) }
        
        // ViewModels
        viewModel { (userId: String) -> ChatViewModel(get(), get(), get(), get(), userId) }
        viewModel { ChatsViewModel(get(), get(), get()) }
        viewModel { LocalNetworkViewModel(get(), get(), get()) }
        viewModel { (userId: String) -> VerificationViewModel(get(), get(), userId) }
    }
}