// app/src/main/java/com/survivalcomunicator/app/ui/verification/VerificationActivity.kt
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
import com.survivalcomunicator.app.utils.SessionManager

/**
 * Actividad para verificar la identidad de un contacto mediante varios métodos:
 * - Escaneo/lectura de código QR
 * - Comparación de palabras de verificación
 * - Comparación de número de seguridad
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

        // Inicializar ViewModel y SessionManager
        viewModel = ViewModelProvider(this).get(VerificationViewModel::class.java)
        sessionManager = SessionManager(this)

        // Configurar Toolbar
        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = getString(R.string.verify_identity)

        // Leer extras
        contactId = intent.getStringExtra(EXTRA_CONTACT_ID)
        contactName = intent.getStringExtra(EXTRA_CONTACT_NAME)

        if (contactId.isNullOrEmpty()) {
            Toast.makeText(this, R.string.contact_info_missing, Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        binding.txtContactName.text = contactName

        // Carga información del contacto desde ViewModel
        viewModel.loadContactInfo(contactId!!)

        setupTabLayout()
        setupObservers()
        setupListeners()
    }

    private fun setupTabLayout() {
        binding.tabLayout.addOnTabSelectedListener(object :
            com.google.android.material.tabs.TabLayout.OnTabSelectedListener {
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

        // Inicialmente mostrar QR
        showQRVerification()
    }

    private fun setupObservers() {
        // Observar LiveData de contacto
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
        // Marcar como verificado manualmente
        binding.btnMarkVerified.setOnClickListener {
            viewModel.markContactAsVerified(contactId!!)
            Toast.makeText(this, R.string.contact_verified, Toast.LENGTH_SHORT).show()
        }

        // Escáner QR
        binding.btnScanQr.setOnClickListener {
            val intent = Intent(this, QRScannerActivity::class.java)
            startActivityForResult(intent, REQUEST_QR_SCAN)
        }

        // Iniciar verificación por desafío
        binding.btnVerifyChallenge.setOnClickListener {
            viewModel.startChallengeVerification(contactId!!)
        }
    }

    private fun updateContactInfo(contact: User) {
        binding.txtFingerprint.text = viewModel.getContactFingerprint(contact)
    }

    private fun generateVerificationData(contact: User) {
        // QR
        val qrData = viewModel.generateQRData(contact)
        binding.imgQrCode.setImageBitmap(createQRCode(qrData, QR_SIZE))

        // Palabras
        val myKey = sessionManager.getUserPublicKey().orEmpty()
        binding.txtVerificationWords.text = viewModel.generateVerificationWords(myKey, contact.publicKey)

        // Número seguridad
        binding.txtSafetyNumber.text = viewModel.generateSafetyNumber(myKey, contact.publicKey)
    }

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
                binding.btnMarkVerified.visibility = View.GONE
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

    private fun showQRVerification() {
        binding.layoutQrVerification.visibility = View.VISIBLE
        binding.layoutWordsVerification.visibility = View.GONE
        binding.layoutNumberVerification.visibility = View.GONE
    }

    private fun showWordsVerification() {
        binding.layoutQrVerification.visibility = View.GONE
        binding.layoutWordsVerification.visibility = View.VISIBLE
        binding.layoutNumberVerification.visibility = View.GONE
    }

    private fun showNumberVerification() {
        binding.layoutQrVerification.visibility = View.GONE
        binding.layoutWordsVerification.visibility = View.GONE
        binding.layoutNumberVerification.visibility = View.VISIBLE
    }

    private fun createQRCode(data: String, size: Int): Bitmap {
        val bitMatrix: BitMatrix = MultiFormatWriter().encode(
            data, BarcodeFormat.QR_CODE, size, size
        )
        val bmp = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        for (x in 0 until size) for (y in 0 until size) {
            bmp.setPixel(x, y, if (bitMatrix[x, y]) Color.BLACK else Color.WHITE)
        }
        return bmp
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == REQUEST_QR_SCAN && resultCode == RESULT_OK) {
            data?.getStringExtra(QRScannerActivity.EXTRA_QR_RESULT)?.let { qrResult ->
                val ok = viewModel.verifyQRData(qrResult, contactId!!)
                Toast.makeText(
                    this,
                    if (ok) R.string.qr_verification_success else R.string.qr_verification_failed,
                    Toast.LENGTH_SHORT
                ).show()
            }
        }
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return if (item.itemId == android.R.id.home) {
            finish()
            true
        } else super.onOptionsItemSelected(item)
    }

    companion object {
        private const val REQUEST_QR_SCAN = 1001
        private const val QR_SIZE = 500
        const val EXTRA_CONTACT_ID = "extra_contact_id"
        const val EXTRA_CONTACT_NAME = "extra_contact_name"
    }
}

