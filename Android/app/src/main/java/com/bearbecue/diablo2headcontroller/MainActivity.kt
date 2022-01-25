package com.bearbecue.diablo2headcontroller

import android.app.Activity
import android.app.ProgressDialog
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothSocket
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.AsyncTask
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.*
import com.bearbecue.diablo2headcontroller.databinding.ActivityMainBinding
import java.io.IOException
import java.util.*
import kotlin.collections.ArrayList
import kotlin.concurrent.timerTask

class MainActivity : AppCompatActivity() {

    private lateinit var m_PairedDevices: Set<BluetoothDevice>
    val REQUEST_ENABLE_BLUETOOTH = 1

    companion object {
        var m_UUID : UUID = UUID.fromString("00001101-0000-1000-8000-00805f9b34fb")
        private var m_BluetoothAdapter: BluetoothAdapter? = null
        private var m_BluetoothSocket: BluetoothSocket? = null
        private lateinit var m_progress: ProgressDialog
        private lateinit var binding: ActivityMainBinding
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Load image
        loadAnimation()

        // Refresh devices
        refreshPairedDevices()

        binding.refreshDeviceButton.setOnClickListener { refreshPairedDevices() }

        binding.buttonOff.setOnClickListener {
            binding.seekBarSoulstone.setProgress(0)
            binding.seekBarEyes.setProgress(0)
            binding.seekBarMouth.setProgress(0)
            setDeviceOff()
            binding.sampleText.text = "Everything Off"
        }
        binding.buttonSoulstone.setOnClickListener {
            binding.seekBarSoulstone.setProgress(255)
            setDeviceIntensity_Soulstone(255)
            binding.sampleText.text = "Soulstone at 100% power !"
        }
        binding.buttonEyes.setOnClickListener {
            binding.seekBarEyes.setProgress(255)
            setDeviceIntensity_Eyes(255)
            binding.sampleText.text = "Eyes at 100% power !"
        }
        binding.buttonMouth.setOnClickListener {
            binding.seekBarMouth.setProgress(255)
            setDeviceIntensity_Mouth(255)
            binding.sampleText.text = "Mouth at 100% power !"
        }

        // Set all sliders to full brightness
        // TODO: Load from a config ?
        binding.seekBarSoulstone.setProgress(255)
        binding.seekBarEyes.setProgress(255)
        binding.seekBarMouth.setProgress(255)
        binding.seekBarVariation.setProgress(255)
        binding.seekBarBaselineSync.setProgress(0)

        // Bind change listeners when user changes the slider values
        binding.seekBarSoulstone.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(p0: SeekBar?, v: Int, p2: Boolean) {
                setDeviceIntensity_Soulstone(v)
                binding.sampleText.text = "Soulstone at ${(v.toFloat() * 100.0f / 255.0f).toInt()}% power !"
            }
            override fun onStartTrackingTouch(p0: SeekBar?) {}
            override fun onStopTrackingTouch(p0: SeekBar?) {}
        })
        binding.seekBarEyes.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(p0: SeekBar?, v: Int, p2: Boolean) {
                setDeviceIntensity_Eyes(v)
                binding.sampleText.text = "Eyes at ${(v.toFloat() * 100.0f / 255.0f).toInt()}% power !"
            }
            override fun onStartTrackingTouch(p0: SeekBar?) {}
            override fun onStopTrackingTouch(p0: SeekBar?) {}
        })
        binding.seekBarMouth.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(p0: SeekBar?, v: Int, p2: Boolean) {
                setDeviceIntensity_Mouth(v)
                binding.sampleText.text = "Mouth at ${(v.toFloat() * 100.0f / 255.0f).toInt()}% power !"
            }
            override fun onStartTrackingTouch(p0: SeekBar?) {}
            override fun onStopTrackingTouch(p0: SeekBar?) {}
        })
        binding.seekBarVariation.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(p0: SeekBar?, v: Int, p2: Boolean) {
                setDeviceVariation(v)
                binding.sampleText.text = "Variation at ${(v.toFloat() * 100.0f / 255.0f).toInt()}%"
            }
            override fun onStartTrackingTouch(p0: SeekBar?) {}
            override fun onStopTrackingTouch(p0: SeekBar?) {}
        })
        binding.seekBarBaselineSync.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(p0: SeekBar?, v: Int, p2: Boolean) {
                setDeviceBaselineSync(v)
                binding.sampleText.text = "Baseline sync at ${(v.toFloat() * 100.0f / 255.0f).toInt()}%"
            }
            override fun onStartTrackingTouch(p0: SeekBar?) {}
            override fun onStopTrackingTouch(p0: SeekBar?) {}
        })

        onTick()

        autoConnect()
    }

    // Animation & tick
    private var m_CurrentFrame : Int = 0
    private var m_Frames : Vector<Bitmap> = Vector()
    private fun onTick() {
        if (!m_Frames.isEmpty()) {
            m_CurrentFrame++
            if (m_CurrentFrame >= m_Frames.count())
                m_CurrentFrame = 0
            binding.imageView2.setImageBitmap(m_Frames[m_CurrentFrame])
        }

        Timer().schedule(timerTask { onTick() }, 60)    // tick every 60ms
    }

    private fun loadAnimation() {
        m_Frames.clear()
        var resId = resources.getIdentifier("diablo_ii_resurrected_anim", "drawable", packageName)
        if (resId == 0)
            return
        var sourceImg = BitmapFactory.decodeResource(resources, resId)
        var size = sourceImg!!.width    // should never be zero
        var numFrames = sourceImg!!.height / size;
        var lastFrame = numFrames - 1
        var vMargin = size / 6;
        for (i : Int in 0..lastFrame) {
            var frame: Bitmap = Bitmap.createBitmap(sourceImg!!, 0, i * size + vMargin, size, size - vMargin*2)
            m_Frames.add(frame)
        }
    }

    // Force-update device with current values
    public fun updateDevice() {
        setDeviceIntensity_Soulstone(binding.seekBarSoulstone.progress)
        setDeviceIntensity_Eyes(binding.seekBarEyes.progress)
        setDeviceIntensity_Mouth(binding.seekBarMouth.progress)
        setDeviceVariation(binding.seekBarVariation.progress)
    }

    // Compile-safe wrappers to set the intensity of each element in the target device.
    // This is the single place where the command syntax is specified and must match the
    // program living on the device's arduino.
    public fun setDeviceOff() { sendCommandToDevice("off") }
    public fun setDeviceIntensity_Soulstone(intensity: Int) { sendCommandToDevice("s=$intensity") }
    public fun setDeviceIntensity_Eyes(intensity: Int) { sendCommandToDevice("e=$intensity") }
    public fun setDeviceIntensity_Mouth(intensity: Int) { sendCommandToDevice("m=$intensity") }
    public fun setDeviceVariation(intensity: Int) { sendCommandToDevice("v=$intensity") }
    public fun setDeviceBaselineSync(intensity: Int) { sendCommandToDevice("b=$intensity") }

    // Function to send a raw command.
    // Appends the terminating '\n' used by the device to detect command boundaries.
    private fun sendCommandToDevice(command: String) {
        if (m_BluetoothSocket == null)
            return
        try {
            var realCmd : String = command + "\n";
            m_BluetoothSocket!!.outputStream.write(realCmd.toByteArray())
        } catch (e: IOException) {
            Log.e("BTARDUINO", "Failed sending command to device")
            e.printStackTrace()
        }
    }

    // Device connection helpers
    private fun autoConnect() {
        if (m_PairedDevices.isEmpty() ||
            m_BluetoothAdapter == null)
            return

        connectToDevice(m_PairedDevices.elementAt(0).address)
    }

    private fun connectToDevice(address : String) {
        if (m_BluetoothSocket != null)
            disconnectFromDevice()
        if (m_BluetoothAdapter == null)
            return

        ConnectToDevice(this, address, this::updateDevice).execute()
    }

    private fun disconnectFromDevice() {
        if (m_BluetoothSocket == null)
            return
        try {
            m_BluetoothSocket!!.close()
            m_BluetoothSocket = null
        } catch (e: IOException) {
            Log.e("BTARDUINO", "Failed disconnecting from device")
            e.printStackTrace()
        }
    }

    private fun refreshPairedDevices() {
        m_BluetoothAdapter = BluetoothAdapter.getDefaultAdapter()
        if (m_BluetoothAdapter == null) {
            binding.sampleText.text = "Bluetooth not supported"
            return
        }
        else if (!m_BluetoothAdapter!!.isEnabled) {
            binding.sampleText.text = "Bluetooth Not enabled"
            val enableBluetoothIntent = android.content.Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE)
            startActivityForResult(enableBluetoothIntent, REQUEST_ENABLE_BLUETOOTH)
        }
        else
            binding.sampleText.text = "Device ready"

        m_PairedDevices = m_BluetoothAdapter!!.bondedDevices
        val list : ArrayList<BluetoothDevice> = ArrayList()

        if (!m_PairedDevices.isEmpty()) {
            for (device : BluetoothDevice in m_PairedDevices) {
                list.add(device)
            }
            binding.sampleText.text = "Device ready: " + m_PairedDevices.size + " paired"
        } else {
            binding.sampleText.text = "No paired devices"
        }

        //val adapter = ArrayAdapter(this, android.R.layout.simple_list_item_1, list)
        val adapter = DeviceDisplayAdapter(this, list)
        binding.deviceList.adapter = adapter
        binding.deviceList.onItemClickListener = AdapterView.OnItemClickListener { _, _, position, _ ->
            val device: BluetoothDevice = list[position]
            connectToDevice(device.address) // Connect to the device we clicked on
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == REQUEST_ENABLE_BLUETOOTH) {
            if (resultCode == Activity.RESULT_OK) {
                if (m_BluetoothAdapter!!.isEnabled) {
                    binding.sampleText.text = "Device ready"
                    refreshPairedDevices()
                } else {
                    binding.sampleText.text = "Bluetooth Not enabled"
                }
            } else if (resultCode == Activity.RESULT_CANCELED) {
                binding.sampleText.text = "Bluetooth enable was cancelled"
            }
        }
    }

    fun toast(msg: String) {
        Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
    }

    private class DeviceDisplayAdapter(private val context: Context,
                                       private val dataSource: ArrayList<BluetoothDevice>) : BaseAdapter() {
        private val inflater: LayoutInflater = context.getSystemService(Context.LAYOUT_INFLATER_SERVICE) as LayoutInflater

        override fun getCount(): Int { return dataSource.size }
        override fun getItem(position: Int): Any { return dataSource[position] }
        override fun getItemId(position: Int): Long { return position.toLong() }

        override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
            val rowView = inflater.inflate(R.layout.device_list_layout, parent, false)
            var deviceName = rowView.findViewById<TextView>(R.id.deviceName)
            var deviceAddress = rowView.findViewById<TextView>(R.id.deviceAddress)
            deviceName.text = dataSource[position].name
            deviceAddress.text = dataSource[position].address
            return rowView
        }
    }

    private class ConnectToDevice(c: Context, address: String, onConnected: () -> Unit) : AsyncTask<Void, Void, String>() {
        private var connectSuccess: Boolean = true
        private val context: Context
        private val m_Address: String
        private val m_OnConnected: () -> Unit

        init {
            this.context = c
            this.m_Address = address
            this.m_OnConnected = onConnected
        }

        override fun onPreExecute() {
            super.onPreExecute()
            m_progress = ProgressDialog.show(context, "Connecting...", "please wait")
        }

        override fun doInBackground(vararg p0: Void?): String? {
            try {
                if (m_BluetoothSocket == null) {
                    m_BluetoothAdapter = BluetoothAdapter.getDefaultAdapter()
                    val device: BluetoothDevice = m_BluetoothAdapter!!.getRemoteDevice(m_Address)
                    m_BluetoothSocket = device.createInsecureRfcommSocketToServiceRecord(m_UUID)
                    BluetoothAdapter.getDefaultAdapter().cancelDiscovery()
                    m_BluetoothSocket!!.connect()
//                    Toast.makeText(context, "Connected to device \"" + device.name + "\"", Toast.LENGTH_SHORT).show()
                    Log.i("BTARDUINO", "CONNECTED")
                }
            } catch (e: IOException) {
                connectSuccess = false
                Log.e("BTARDUINO", "FAILED CONNECTING")
                e.printStackTrace()
                if (m_BluetoothSocket != null)
                    m_BluetoothSocket!!.close()
            }
            return null
        }

        override fun onPostExecute(result: String?) {
            super.onPostExecute(result)
            if (!connectSuccess) {
                Toast.makeText(context, "Failed connecting to device \"" + m_Address + "\"", Toast.LENGTH_SHORT).show()
                Log.i("data", "couldn't connect")
            } else {
                Toast.makeText(context, "Connected to device \"" + m_Address + "\"", Toast.LENGTH_SHORT).show()

                // We are successfully connected to the device. Update with current values:
                m_OnConnected()
            }
            m_progress.dismiss()
        }
    }
}