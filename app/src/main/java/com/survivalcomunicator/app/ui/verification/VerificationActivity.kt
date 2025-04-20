package com.survivalcomunicator.app.ui.verification

import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Color
import android.os.Bundle
import android.view.MenuItem
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.ViewModelProvider
import com.google.zxing.BarcodeFormat
import com.google.zxing.MultiFormatWriter
import com.google.zxing.common.BitMatrix
import com.survivalcomunicator.app.R
import com.survivalcomunicator.app.databinding.ActivityVerificationBinding
import com.survivalcomunicator.app.model.User
import com.survivalcomunicator.app.security.PeerVerification
import com.survivalcomunicator.app.utils.SessionManager

/**
 * Actividad para verificar la identidad de un contacto mediante diversos métodos
 */
class VerificationActivity : AppCompatActivity() {

    private lateinit var binding: ActivityVerificationBinding
    private lateinit var viewModel: VerificationViewModel
    private lateinit var sessionManager: SessionManager
    
    private var contactId: String? = null
    private var contactName: String? = null
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityVerificationBinding.inflate(layoutInflater)
        setContentView(binding.root)
        
        // Inicializar ViewModel
        viewModel = ViewModelProvider(this).get(VerificationViewModel::class.java)
        sessionManager = SessionManager(this)
        
        // Configurar Toolbar
        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = getString(R.string.verify_identity)
        
        // Obtener datos del contacto
        contactId = intent.getStringExtra(EXTRA_CONTACT_ID)
        contactName = intent.getStringExtra(EXTRA_CONTACT_NAME)
        
        if (contactId == null) {
            Toast.makeText(this, R.string.contact_info_missing, Toast.LENGTH_SHORT).show()
            finish()
            return
        }
        
        // Configurar nombre del contacto en la UI
        binding.txtContactName.text = contactName
        
        // Cargar información del contacto
        viewModel.loadContactInfo(contactId!!)
        
        setupTabLayout()
        setupObservers()
        setupListeners()
    }
    
    private fun setupTabLayout() {
        // Configurar TabLayout para cambiar entre métodos de verificación
        binding.tabLayout.addOnTabSelectedListener(object : com.google.android.material.tabs.TabLayout.OnTabSelectedListener {
            override fun onTabSelected(tab: com.google.android.material.tabs.TabLayout.Tab?) {
                when (tab?.position) {
                    0 -> showQRVerification()
                    1 -> showWordsVerification()
                    2 -> showNumberVerification()
                }
            }
            
            override fun onTabUnselected(tab: com.google.android.material.tabs.TabLayout.Tab?) {}
            override fun onTabReselected(tab: com.google.android.material.tabs.TabLayout.Tab?) {}
        })
    }
    
    private fun setupObservers() {
        // Observar el contacto cargado
        viewModel.contact.observe(this) { contact ->
            contact?.let {
                updateContactInfo(it)
                generateVerificationData(it)
            }
        }
        
        // Observar estado de verificación
        viewModel.verificationStatus.observe(this) { status ->
            updateVerificationStatus(status)
        }
    }
    
    private fun setupListeners() {
        // Botón para marcar como verificado
        binding.btnMarkVerified.setOnClickListener {
            viewModel.markContactAsVerified(contactId!!)
            Toast.makeText(this, getString(R.string.contact_verified), Toast.LENGTH_SHORT).show()
        }
        
        // Botón para iniciar escáner QR
        binding.btnScanQr.setOnClickListener {
            // Iniciar actividad de escáner QR
            val intent = Intent(this, QRScannerActivity::class.java)
            startActivityForResult(intent, REQUEST_QR_SCAN)
        }
        
        // Botón para iniciar verificación mediante desafío
        binding.btnVerifyChallenge.setOnClickListener {
            viewModel.startChallengeVerification(contactId!!)
        }
    }
    
    /**
     * Actualiza la información del contacto en la UI
     */
    private fun updateContactInfo(contact: User) {
        binding.txtFingerprint.text = viewModel.getContactFingerprint(contact)
    }
    
    /**
     * Genera los datos de verificación (QR, palabras, números)
     */
    private fun generateVerificationData(contact: User) {
        // Generar código QR
        val qrData = viewModel.generateQRData(contact)
        val qrBitmap = generateQRCode(qrData, 500)
        binding.imgQrCode.setImageBitmap(qrBitmap)
        
        // Generar palabras de verificación
        val myPublicKey = sessionManager.getUserPublicKey() ?: ""
        val words = viewModel.generateVerificationWords(myPublicKey, contact.publicKey)
        binding.txtVerificationWords.text = words
        
        // Generar número de seguridad
        val safetyNumber = viewModel.generateSafetyNumber(myPublicKey, contact.publicKey)
        binding.txtSafetyNumber.text = safetyNumber
    }
    
    /**
     * Actualiza la UI según el estado de verificación
     */
    private fun updateVerificationStatus(status: PeerVerification.VerificationStatus) {
        when (status) {
            PeerVerification.VerificationStatus.UNVERIFIED -> {
                binding.imgVerificationStatus.setImageResource(R.drawable.ic_not_verified)
                binding.txtVerificationStatus.setText(R.string.not_verified)
                binding.txtVerificationStatus.setTextColor(getColor(R.color.warning_text))
                binding.btnMarkVerified.visibility = View.VISIBLE
            }
            PeerVerification.VerificationStatus.PENDING -> {
                binding.imgVerificationStatus.setImageResource(R.drawable.ic_pending)
                binding.txtVerificationStatus.setText(R.string.verification_in_progress)
                binding.txtVerificationStatus.setTextColor(getColor(R.color.pending_text))
            }
            PeerVerification.VerificationStatus.VERIFIED -> {
                binding.imgVerificationStatus.setImageResource(R.drawable.ic_verified)
                binding.txtVerificationStatus.setText(R.string.verified)
                binding.txtVerificationStatus.setTextColor(getColor(R.color.success_text))
                binding.btnMarkVerified.visibility = View.GONE
            }
            PeerVerification.VerificationStatus.FAILED -> {
                binding.imgVerificationStatus.setImageResource(R.drawable.ic_verification_failed)
                binding.txtVerificationStatus.setText(R.string.verification_failed)
                binding.txtVerificationStatus.setTextColor(getColor(R.color.error_text))
                binding.btnMarkVerified.visibility = View.VISIBLE
            }
        }
    }
    
    /**
     * Muestra la pestaña de verificación QR
     */
    private fun showQRVerification() {
        binding.layoutQrVerification.visibility = View.VISIBLE
        binding.layoutWordsVerification.visibility = View.GONE
        binding.layoutNumberVerification.visibility = View.GONE
    }
    
    /**
     * Muestra la pestaña de verificación por palabras
     */
    private fun showWordsVerification() {
        binding.layoutQrVerification.visibility = View.GONE
        binding.layoutWordsVerification.visibility = View.VISIBLE
        binding.layoutNumberVerification.visibility = View.GONE
    }
    
    /**
     * Muestra la pestaña de verificación por número
     */
    private fun showNumberVerification() {
        binding.layoutQrVerification.visibility = View.GONE
        binding.layoutWordsVerification.visibility = View.GONE
        binding.layoutNumberVerification.visibility = View.VISIBLE
    }
    
    /**
     * Genera un código QR a partir de datos
     */
    private fun generateQRCode(data: String, size: Int): Bitmap {
        try {
            val bitMatrix: BitMatrix = MultiFormatWriter().encode(
                data, BarcodeFormat.QR_CODE, size, size
            )
            
            val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
            for (x in 0 until size) {
                for (y in 0 until size) {
                    bitmap.setPixel(x, y, if (bitMatrix[x, y]) Color.BLACK else Color.WHITE)
                }
            }
            
            return bitmap
        } catch (e: Exception) {
            e.printStackTrace()
            
            // Retornar un bitmap vacío en caso de error
            return Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        }
    }
    
    /**
     * Maneja el resultado del escáner QR
     */
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        
        if (requestCode == REQUEST_QR_SCAN && resultCode == RESULT_OK) {
            val qrResult = data?.getStringExtra(QRScannerActivity.EXTRA_QR_RESULT)
            
            qrResult?.let {
                val verified = viewModel.verifyQRData(it, contactId!!)
                
                if (verified) {
                    Toast.makeText(this, R.string.qr_verification_success, Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(this, R.string.qr_verification_failed, Toast.LENGTH_SHORT).show()
                }
            }
        }
    }
    
    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (item.itemId == android.R.id.home) {
            finish()
            return true
        }
        return super.onOptionsItemSelected(item)
    }
    
    companion object {
        const val EXTRA_CONTACT_ID = "extra_contact_id"
        const val EXTRA_CONTACT_NAME = "extra_contact_name"
        private const val REQUEST_QR_SCAN = 1001
    }
}