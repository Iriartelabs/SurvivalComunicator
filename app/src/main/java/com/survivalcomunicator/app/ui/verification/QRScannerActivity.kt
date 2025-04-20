package com.survivalcomunicator.app.ui.verification

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.util.Log
import android.view.MenuItem
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.google.mlkit.vision.barcode.BarcodeScanner
import com.google.mlkit.vision.barcode.BarcodeScannerOptions
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.common.InputImage
import com.survivalcomunicator.app.R
import com.survivalcomunicator.app.databinding.ActivityQrScannerBinding
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

/**
 * Actividad para escanear códigos QR para la verificación de contactos
 */
class QRScannerActivity : AppCompatActivity() {
    private val TAG = "QRScannerActivity"
    
    private lateinit var binding: ActivityQrScannerBinding
    
    private var cameraExecutor: ExecutorService? = null
    private var cameraProvider: ProcessCameraProvider? = null
    private var camera: Camera? = null
    private var imageAnalyzer: ImageAnalysis? = null
    private var preview: Preview? = null
    
    private lateinit var barcodeScanner: BarcodeScanner
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityQrScannerBinding.inflate(layoutInflater)
        setContentView(binding.root)
        
        // Configurar Toolbar
        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = getString(R.string.scan_qr_code)
        
        // Inicializar escáner de códigos de barras
        val options = BarcodeScannerOptions.Builder()
            .setBarcodeFormats(Barcode.FORMAT_QR_CODE)
            .build()
        
        barcodeScanner = BarcodeScanning.getClient(options)
        
        // Inicializar ejecutor para la cámara
        cameraExecutor = Executors.newSingleThreadExecutor()
        
        // Verificar permisos y comenzar si están concedidos
        if (allPermissionsGranted()) {
            startCamera()
        } else {
            ActivityCompat.requestPermissions(
                this, REQUIRED_PERMISSIONS, REQUEST_CODE_PERMISSIONS
            )
        }
        
        // Botón para cancelar
        binding.btnCancel.setOnClickListener {
            setResult(RESULT_CANCELED)
            finish()
        }
    }
    
    /**
     * Inicia la cámara y el análisis para el escaneo QR
     */
    private fun startCamera() {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(this)
        
        cameraProviderFuture.addListener({
            try {
                // Obtener proveedor de cámara
                cameraProvider = cameraProviderFuture.get()
                
                // Configurar vista previa
                preview = Preview.Builder().build()
                
                // Configurar analizador de imágenes
                imageAnalyzer = ImageAnalysis.Builder()
                    .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                    .build()
                    .also {
                        it.setAnalyzer(cameraExecutor!!, QRCodeAnalyzer())
                    }
                
                // Seleccionar cámara trasera por defecto
                val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA
                
                // Vincular todo al ciclo de vida
                camera = cameraProvider?.bindToLifecycle(
                    this, cameraSelector, preview, imageAnalyzer
                )
                
                // Adjuntar vista previa al viewfinder
                preview?.setSurfaceProvider(binding.viewFinder.surfaceProvider)
                
            } catch (e: Exception) {
                Log.e(TAG, "Error iniciando cámara: ${e.message}")
                Toast.makeText(this, R.string.camera_error, Toast.LENGTH_SHORT).show()
            }
        }, ContextCompat.getMainExecutor(this))
    }
    
    /**
     * Maneja el resultado del escaneo QR
     */
    private fun handleQRResult(rawValue: String) {
        // Detener cámara y análisis
        imageAnalyzer?.clearAnalyzer()
        cameraProvider?.unbindAll()
        
        // Devolver resultado a la actividad que lo llamó
        val resultIntent = Intent()
        resultIntent.putExtra(EXTRA_QR_RESULT, rawValue)
        setResult(RESULT_OK, resultIntent)
        
        // Finalizar actividad
        finish()
    }
    
    /**
     * Analizador de imágenes para detectar códigos QR
     */
    private inner class QRCodeAnalyzer : ImageAnalysis.Analyzer {
        override fun analyze(imageProxy: ImageProxy) {
            val mediaImage = imageProxy.image
            if (mediaImage != null) {
                val image = InputImage.fromMediaImage(
                    mediaImage,
                    imageProxy.imageInfo.rotationDegrees
                )
                
                // Procesar la imagen
                barcodeScanner.process(image)
                    .addOnSuccessListener { barcodes ->
                        if (barcodes.isNotEmpty()) {
                            // Procesar el primer código QR encontrado
                            val barcode = barcodes[0]
                            barcode.rawValue?.let { rawValue ->
                                handleQRResult(rawValue)
                            }
                        }
                    }
                    .addOnFailureListener { e ->
                        Log.e(TAG, "Error en escaneo de QR: ${e.message}")
                    }
                    .addOnCompleteListener {
                        // Cerrar la imagen cuando terminemos el análisis
                        imageProxy.close()
                    }
            } else {
                imageProxy.close()
            }
        }
    }
    
    /**
     * Verifica si todos los permisos requeridos están concedidos
     */
    private fun allPermissionsGranted() = REQUIRED_PERMISSIONS.all {
        ContextCompat.checkSelfPermission(baseContext, it) == PackageManager.PERMISSION_GRANTED
    }
    
    /**
     * Maneja el resultado de la solicitud de permisos
     */
    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        
        if (requestCode == REQUEST_CODE_PERMISSIONS) {
            if (allPermissionsGranted()) {
                startCamera()
            } else {
                Toast.makeText(
                    this,
                    R.string.camera_permission_required,
                    Toast.LENGTH_SHORT
                ).show()
                finish()
            }
        }
    }
    
    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (item.itemId == android.R.id.home) {
            setResult(RESULT_CANCELED)
            finish()
            return true
        }
        return super.onOptionsItemSelected(item)
    }
    
    override fun onDestroy() {
        super.onDestroy()
        cameraExecutor?.shutdown()
    }
    
    companion object {
        private const val REQUEST_CODE_PERMISSIONS = 10
        const val EXTRA_QR_RESULT = "qr_result"
        
        private val REQUIRED_PERMISSIONS = arrayOf(Manifest.permission.CAMERA)
    }
}