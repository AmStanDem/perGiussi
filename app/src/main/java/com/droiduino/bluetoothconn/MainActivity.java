package com.droiduino.bluetoothconn;

import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.content.res.AppCompatResources;
import androidx.appcompat.widget.Toolbar;

import android.annotation.SuppressLint;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothSocket;
import android.content.Intent;
import android.graphics.Color;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.util.Log;
import android.view.MotionEvent;
import android.view.View;
import android.widget.Button;
import android.widget.GridLayout;
import android.widget.ImageButton;
import android.widget.ProgressBar;
import android.widget.Switch;
import android.widget.TextView;


import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.UUID;
import java.util.Vector;

import static android.content.ContentValues.TAG;

public class MainActivity extends AppCompatActivity implements View.OnTouchListener {
    public static final int SX_WELL_MAX = 180, SX_WELL_MIN = 0;
    public static final int DX_WELL_MAX = 0, DX_WELL_MIN = 180;
    public static final int WELL_STOP = 90;
    public static String arrowsText;
    public static String joystickText;

    private String deviceName = null;
    private String deviceAddress;
    public static Handler handler;
    public static BluetoothSocket mmSocket;
    public static ConnectedThread connectedThread;
    public static CreateConnectThread createConnectThread;
    private Vector<View> pressedDirections;


    private final static int CONNECTING_STATUS = 1; // used in bluetooth handler to identify message status
    private final static int MESSAGE_READ = 2; // used in bluetooth handler to identify message update
    private ImageButton btnUpLeft, btnUp, btnUpRight;
    private ImageButton btnLeft, btnRight;
    private ImageButton btnDownLeft, btnDown, btnDownRight;
    private GridLayout gridLayout;
    private JoystickView joystick;

    @SuppressLint("ClickableViewAccessibility")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        arrowsText = getString(R.string.switch_mode_arrows);
        joystickText = getString(R.string.switch_mode_joystick);

        // UI Initialization
        gridLayout = findViewById(R.id.greed_layout);
        joystick = findViewById(R.id.joystick);
        final Button buttonConnect = findViewById(R.id.buttonConnect);
        final Switch btnSwitch = findViewById(R.id.switch_mode);
        final Toolbar toolbar = findViewById(R.id.toolbar);
        final ProgressBar progressBar = findViewById(R.id.progressBar);
        progressBar.setVisibility(View.GONE);
        final TextView textViewInfo = findViewById(R.id.textViewInfo);
        btnUpLeft = findViewById(R.id.btn_up_left);
        btnUp = findViewById(R.id.btn_up);
        btnUpRight = findViewById(R.id.btn_up_right);
        btnLeft = findViewById(R.id.btn_left);
        btnRight = findViewById(R.id.btn_right);
        btnDownLeft = findViewById(R.id.btn_down_left);
        btnDown = findViewById(R.id.btn_down);
        btnDownRight = findViewById(R.id.btn_down_right);

        //other initializations
        pressedDirections = new Vector<>();



        // If a bluetooth device has been selected from SelectDeviceActivity
        deviceName = getIntent().getStringExtra("deviceName");
        if (deviceName != null){
            // Get the device address to make BT Connection
            deviceAddress = getIntent().getStringExtra("deviceAddress");
            // Show progree and connection status
            toolbar.setSubtitle("Connecting to " + deviceName + "...");
            progressBar.setVisibility(View.VISIBLE);
            buttonConnect.setEnabled(false);

            /*
            This is the most important piece of code. When "deviceName" is found
            the code will call a new thread to create a bluetooth connection to the
            selected device (see the thread code below)
             */
            BluetoothAdapter bluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
            createConnectThread = new CreateConnectThread(bluetoothAdapter,deviceAddress);
            createConnectThread.start();
        }

        /*
        Second most important piece of Code. GUI Handler
         */
        handler = new Handler(Looper.getMainLooper()) {
            @Override
            public void handleMessage(Message msg){
                switch (msg.what){
                    case CONNECTING_STATUS:
                        switch(msg.arg1){
                            case 1:
                                toolbar.setSubtitle("Connected to " + deviceName);
                                progressBar.setVisibility(View.GONE);
                                buttonConnect.setEnabled(true);

                                break;
                            case -1:
                                toolbar.setSubtitle("Device fails to connect");
                                progressBar.setVisibility(View.GONE);
                                buttonConnect.setEnabled(true);
                                break;
                        }
                        break;

                    case MESSAGE_READ:
                        String arduinoMsg = msg.obj.toString(); // Read message from Arduino
                        switch (arduinoMsg.toLowerCase()){
                            case "led is turned on":

                                textViewInfo.setText("Arduino Message : " + arduinoMsg);
                                break;
                            case "led is turned off":

                                textViewInfo.setText("Arduino Message : " + arduinoMsg);
                                break;
                        }
                        break;
                }
            }
        };

        // Select Bluetooth Device
        buttonConnect.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                // Move to adapter list
                Intent intent = new Intent(MainActivity.this, SelectDeviceActivity.class);
                startActivity(intent);
            }
        });
        btnSwitch.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                if(btnSwitch.getText().toString().equals(arrowsText)){
                    btnSwitch.setText(joystickText);
                    gridLayout.setVisibility(View.INVISIBLE);
                    joystick.setVisibility(View.VISIBLE);
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                        btnSwitch.setThumbTintList(AppCompatResources.getColorStateList(MainActivity.this,R.color.color_joystick));
                    }
                }else {
                    btnSwitch.setText(arrowsText);
                    gridLayout.setVisibility(View.VISIBLE);
                    joystick.setVisibility(View.INVISIBLE);
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                        btnSwitch.setThumbTintList(AppCompatResources.getColorStateList(MainActivity.this,R.color.color_arrows));
                    }
                }
            }

        });
        joystick.setOnMoveListener(new JoystickView.OnMoveListener() {
            @Override
            public void onMove(int angle, int strength) {
                //Log.d("Status", angle+"  " +strength);
                int sxV = WELL_STOP, dxV = WELL_STOP;
                float sinPercent = 0;
                float angleRad = (float) Math.toRadians(angle);
                if(angle >= 0 && angle < 45){
                    sxV = SX_WELL_MAX;
                    dxV = DX_WELL_MIN;
                }
                if(angle >= 45  && angle < 90){
                    sinPercent = (float)((Math.sin(angleRad)-Math.sin(Math.toRadians(45)))/(Math.sin(Math.toRadians(90))-Math.sin(Math.toRadians(45))));
                    sxV = SX_WELL_MAX;
                    dxV = (int)(sinPercent*DX_WELL_MIN);
                }
                if(angle >= 90  && angle < 90+45){
                    sinPercent = (float)(Math.sin(angle)*Math.sin(90+45)/Math.sin(90));
                    sxV = (int)(sinPercent*SX_WELL_MAX);
                    dxV = DX_WELL_MAX;
                }
                if(angle >= 90+45  && angle < 180){
                    sxV = SX_WELL_MIN;
                    dxV = DX_WELL_MAX;
                }
                if(angle >= 180  && angle < 180+45){
                    sxV = SX_WELL_MAX;
                    dxV = DX_WELL_MIN;
                }
                if(angle >= 180+45  && angle < 270){
                    sinPercent = -(float)(Math.sin(angle)*Math.sin(270)/Math.sin(180+45));
                    sxV = (int)(sinPercent*SX_WELL_MIN);
                    dxV = DX_WELL_MIN;
                }
                if(angle >= 270  && angle < 270+45){
                    sinPercent = -(float)(Math.sin(angle)*Math.sin(270+45)/Math.sin(270));
                    sxV = SX_WELL_MIN;
                    dxV = (int)(sinPercent*DX_WELL_MIN);
                }
                if(angle >= 270+45  && angle <= 360){
                    sxV = SX_WELL_MIN;
                    dxV = DX_WELL_MAX;
                }

                sxV = sxV >= WELL_STOP ? sxV - WELL_STOP*(100-strength)/100 : sxV + WELL_STOP*(100-strength)/100;
                dxV = dxV >= WELL_STOP ? dxV - WELL_STOP*(100-strength)/100 : dxV + WELL_STOP*(100-strength)/100;

                Log.d("Status", sxV+"    "+dxV);
                Log.d("Status", sinPercent+"");
                Log.d("Status", Math.sin(Math.toRadians(90))-Math.sin(Math.toRadians(45))+"b");
                Log.d("Status", Math.sin(Math.toRadians(angle))-Math.sin(Math.toRadians(45))+"d");
                Log.d("Status", angle+"e");
                Log.d("Status", Math.sin(Math.toRadians(angle))+"  "+Math.sin(Math.toRadians(90))+"   "+Math.sin(Math.toRadians(45))+"f");

                //connectedThread.writeVelocity(sxV, dxV);
            }
        });

        //set on touch for movement button
        btnUpLeft.setOnTouchListener(this);
        btnUp.setOnTouchListener(this);
        btnUpRight.setOnTouchListener(this);
        btnLeft.setOnTouchListener(this);
        btnRight.setOnTouchListener(this);
        btnDownLeft.setOnTouchListener(this);
        btnDown.setOnTouchListener(this);
        btnDownRight.setOnTouchListener(this);


    }

    /* ============================ Thread to Create Bluetooth Connection =================================== */
    public static class CreateConnectThread extends Thread {

        public CreateConnectThread(BluetoothAdapter bluetoothAdapter, String address) {
            /*
            Use a temporary object that is later assigned to mmSocket
            because mmSocket is final.
             */
            BluetoothDevice bluetoothDevice = bluetoothAdapter.getRemoteDevice(address);
            BluetoothSocket tmp = null;
            UUID uuid = bluetoothDevice.getUuids()[0].getUuid();

            try {
                /*
                Get a BluetoothSocket to connect with the given BluetoothDevice.
                Due to Android device varieties,the method below may not work fo different devices.
                You should try using other methods i.e. :
                tmp = device.createRfcommSocketToServiceRecord(MY_UUID);
                 */
                tmp = bluetoothDevice.createInsecureRfcommSocketToServiceRecord(uuid);

            } catch (IOException e) {
                Log.e(TAG, "Socket's create() method failed", e);
            }
            mmSocket = tmp;
        }

        public void run() {
            // Cancel discovery because it otherwise slows down the connection.
            BluetoothAdapter bluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
            bluetoothAdapter.cancelDiscovery();
            try {
                // Connect to the remote device through the socket. This call blocks
                // until it succeeds or throws an exception.
                mmSocket.connect();
                Log.e("Status", "Device connected");
                handler.obtainMessage(CONNECTING_STATUS, 1, -1).sendToTarget();
            } catch (IOException connectException) {
                // Unable to connect; close the socket and return.
                try {
                    mmSocket.close();
                    Log.e("Status", "Cannot connect to device");
                    handler.obtainMessage(CONNECTING_STATUS, -1, -1).sendToTarget();
                } catch (IOException closeException) {
                    Log.e(TAG, "Could not close the client socket", closeException);
                }
                return;
            }

            // The connection attempt succeeded. Perform work associated with
            // the connection in a separate thread.
            connectedThread = new ConnectedThread(mmSocket);
            connectedThread.run();
        }

        // Closes the client socket and causes the thread to finish.
        public void cancel() {
            try {
                mmSocket.close();
            } catch (IOException e) {
                Log.e(TAG, "Could not close the client socket", e);
            }
        }
    }

    /* =============================== Thread for Data Transfer =========================================== */
    public static class ConnectedThread extends Thread {
        private final BluetoothSocket mmSocket;
        private final InputStream mmInStream;
        private final OutputStream mmOutStream;

        public ConnectedThread(BluetoothSocket socket) {
            mmSocket = socket;
            InputStream tmpIn = null;
            OutputStream tmpOut = null;

            // Get the input and output streams, using temp objects because
            // member streams are final
            try {
                tmpIn = socket.getInputStream();
                tmpOut = socket.getOutputStream();
            } catch (IOException e) { }

            mmInStream = tmpIn;
            mmOutStream = tmpOut;
        }

        public void run() {
            byte[] buffer = new byte[1024];  // buffer store for the stream
            int bytes = 0; // bytes returned from read()
            // Keep listening to the InputStream until an exception occurs
            while (true) {
                try {
                    /*
                    Read from the InputStream from Arduino until termination character is reached.
                    Then send the whole String message to GUI Handler.
                     */
                    buffer[bytes] = (byte) mmInStream.read();
                    String readMessage;
                    if (buffer[bytes] == '\n'){
                        readMessage = new String(buffer,0,bytes);
                        Log.e("Arduino Message",readMessage);
                        handler.obtainMessage(MESSAGE_READ,readMessage).sendToTarget();
                        bytes = 0;
                    } else {
                        bytes++;
                    }
                } catch (IOException e) {
                    e.printStackTrace();
                    break;
                }
            }
        }

        /* Call this from the main activity to send data to the remote device */
        public void writeVelocity(int sxV, int dxV) {
            byte[] bytes = new byte[2];
            bytes[0] = new Integer(sxV).byteValue();
            bytes[1] = new Integer(dxV).byteValue();
            try {
                mmOutStream.write(bytes);
            } catch (IOException e) {
                Log.e("Send Error","Unable to send message",e);
            }
        }

        /* Call this from the main activity to shutdown the connection */
        public void cancel() {
            try {
                mmSocket.close();
            } catch (IOException e) { }
        }
    }

    /* ============================ Terminate Connection at BackPress ====================== */
    @Override
    public void onBackPressed() {
        // Terminate Bluetooth Connection and close app
        if (createConnectThread != null){
            createConnectThread.cancel();
        }
        Intent a = new Intent(Intent.ACTION_MAIN);
        a.addCategory(Intent.CATEGORY_HOME);
        a.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        startActivity(a);
    }

    public void sendVelocity(){
        if(pressedDirections.size() > 0){
            View v = pressedDirections.lastElement();
            if(v.equals(btnUpLeft)){
                connectedThread.writeVelocity(WELL_STOP, DX_WELL_MAX);
            }
            if(v.equals(btnUp)){
                connectedThread.writeVelocity(SX_WELL_MAX, DX_WELL_MAX);
            }
            if(v.equals(btnUpRight)){
                connectedThread.writeVelocity(SX_WELL_MAX, WELL_STOP);
            }
            if(v.equals(btnLeft)){
                connectedThread.writeVelocity(SX_WELL_MIN, DX_WELL_MAX);
            }
            if(v.equals(btnRight)){
                connectedThread.writeVelocity(SX_WELL_MAX, DX_WELL_MIN);
            }
            if(v.equals(btnDownLeft)){
                connectedThread.writeVelocity(WELL_STOP, DX_WELL_MIN);
            }
            if(v.equals(btnDown)){
                connectedThread.writeVelocity(SX_WELL_MIN, DX_WELL_MIN);
            }
            if(v.equals(btnDownRight)){
                connectedThread.writeVelocity(SX_WELL_MIN, WELL_STOP);
            }
        }else{
            connectedThread.writeVelocity(WELL_STOP, WELL_STOP);
        }

    }

    @Override
    public boolean onTouch(View v, MotionEvent event){
        if(event.getAction() == MotionEvent.ACTION_DOWN){
            if(pressedDirections.size() == 0 || !pressedDirections.lastElement().equals(v)){
                pressedDirections.add(v);
                sendVelocity();
            }

        }
        if(event.getAction() == MotionEvent.ACTION_UP || event.getAction() == MotionEvent.ACTION_CANCEL){
            pressedDirections.remove(v);
            sendVelocity();
        }
        return false;
    }
}
