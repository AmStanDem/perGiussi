package com.droiduino.bluetoothconn;

import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.content.res.AppCompatResources;
import androidx.appcompat.widget.Toolbar;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.Activity;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothSocket;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
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
import android.widget.SeekBar;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;


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
    public static final int MODE_ARROWS = 0, MODE_JOYSTICK = 1, MODE_LEAVERS = 2;
    public static final int TOTAL_MODES = 3;
    private int PROGRESS_BARS_STOP_TOLERANCE = 10;
    public static String arrowsText, joystickText, leaversText;

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
    private VerticalSeekBar seekBarL, seekBarR;
    private TextView textSeekL, textSeekR;
    private int seekBarLVal, seekBarRVal;
    private int currMode;
    private View arrowsContainer, joystickContainer, leaversContainer;

    @SuppressLint("ClickableViewAccessibility")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        arrowsText = getResources().getString(R.string.switch_mode_arrows);
        joystickText = getResources().getString(R.string.switch_mode_joystick);
        leaversText = getResources().getString(R.string.switch_mode_levers);
        currMode = MODE_ARROWS;

        // UI Initialization
        gridLayout = findViewById(R.id.layout_arrows);
        joystick = findViewById(R.id.joystick);
        seekBarL = findViewById(R.id.sBarLeft);
        seekBarR = findViewById(R.id.sBarRight);
        textSeekL = findViewById(R.id.text_seek_l);
        textSeekR = findViewById(R.id.text_seek_r);
        seekBarLVal = seekBarL.getProgress();
        seekBarRVal = seekBarR.getProgress();
        textSeekL.setText(""+seekBarLVal);
        textSeekR.setText(""+seekBarRVal);
        final Button buttonConnect = findViewById(R.id.buttonConnect);
        final Button btnSwitchModes = findViewById(R.id.btn_modes);
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

        arrowsContainer = findViewById(R.id.layout_arrows);
        joystickContainer = findViewById(R.id.joystick);
        leaversContainer = findViewById(R.id.layout_leavers);

        //other initializations
        pressedDirections = new Vector<>();


        // If a bluetooth device has been selected from SelectDeviceActivity
        deviceName = getIntent().getStringExtra("deviceName");
        if (deviceName != null) {
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
            createConnectThread = new CreateConnectThread(this, bluetoothAdapter, deviceAddress);
            createConnectThread.start();
        }

        /*
        Second most important piece of Code. GUI Handler
         */
        handler = new Handler(Looper.getMainLooper()) {
            @Override
            public void handleMessage(Message msg) {
                switch (msg.what) {
                    case CONNECTING_STATUS:
                        switch (msg.arg1) {
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
                        switch (arduinoMsg.toLowerCase()) {
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
                //Log.e(TAG,"onClick 1");
                if (checkCallingOrSelfPermission(Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
                    ActivityCompat.requestPermissions(MainActivity.this, new String[]{Manifest.permission.BLUETOOTH_CONNECT}, 2);
                    //Log.e(TAG,"onClick asked permission");
                    Toast.makeText(MainActivity.this,"dovresti autorizzare il bluetooth",Toast.LENGTH_LONG).show();
                }

                if(checkCallingOrSelfPermission(Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED){
                    // Move to adapter list
                    Intent intent = new Intent(MainActivity.this, SelectDeviceActivity.class);
                    //Log.e(TAG,"Intent intent");
                    startActivity(intent);
                    //Log.e(TAG,"startActivity");
                }
            }
        });
        btnSwitchModes.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                currMode = (currMode+1)%TOTAL_MODES;

                graphicUpdate();
            }

        });
        joystick.setOnMoveListener(new JoystickView.OnMoveListener() {
            @Override
            public void onMove(int angle, int strength) {
                float sxV = WELL_STOP, dxV = WELL_STOP;
                float sinPercent = 0;
                float angleRad = (float) Math.toRadians(angle);
                if (angle >= 0 && angle < 45) {
                    sxV = SX_WELL_MAX;
                    dxV = DX_WELL_MIN;
                }
                if (angle >= 45 && angle < 90) {
                    sinPercent = (float) ((Math.sin(angleRad) - Math.sin(Math.toRadians(45))) / (Math.sin(Math.toRadians(90)) - Math.sin(Math.toRadians(45))));
                    sinPercent = sinPercent * sinPercent;
                    sxV = SX_WELL_MAX;
                    dxV = (1 - sinPercent) * DX_WELL_MIN;
                }
                if (angle >= 90 && angle < 90 + 45) {
                    sinPercent = (float) ((Math.sin(angleRad) - Math.sin(Math.toRadians(45))) / (Math.sin(Math.toRadians(90)) - Math.sin(Math.toRadians(45))));
                    sinPercent = sinPercent * sinPercent;
                    sxV = sinPercent * SX_WELL_MAX;
                    dxV = DX_WELL_MAX;
                }
                if (angle >= 90 + 45 && angle < 180) {
                    sxV = SX_WELL_MIN;
                    dxV = DX_WELL_MAX;
                }
                if (angle >= 180 && angle < 180 + 45) {
                    sxV = SX_WELL_MAX;
                    dxV = DX_WELL_MIN;
                }
                if (angle >= 180 + 45 && angle < 270) {
                    sinPercent = (float) ((-Math.sin(angleRad) - Math.sin(Math.toRadians(45))) / (Math.sin(Math.toRadians(90)) - Math.sin(Math.toRadians(45))));
                    sinPercent = sinPercent * sinPercent;
                    sxV = (1 - sinPercent) * SX_WELL_MAX;
                    dxV = DX_WELL_MIN;
                }
                if (angle >= 270 && angle < 270 + 45) {
                    sinPercent = (float) ((-Math.sin(angleRad) - Math.sin(Math.toRadians(45))) / (Math.sin(Math.toRadians(90)) - Math.sin(Math.toRadians(45))));
                    sinPercent = sinPercent * sinPercent;
                    sxV = SX_WELL_MIN;
                    dxV = sinPercent * DX_WELL_MIN;
                }
                if (angle >= 270 + 45 && angle <= 360) {
                    sxV = SX_WELL_MIN;
                    dxV = DX_WELL_MAX;
                }

                sxV = sxV >= WELL_STOP ? sxV - WELL_STOP * (100 - strength) / 100 : sxV + WELL_STOP * (100 - strength) / 100;
                dxV = dxV >= WELL_STOP ? dxV - WELL_STOP * (100 - strength) / 100 : dxV + WELL_STOP * (100 - strength) / 100;

                //Log.d("Status", "sxV:"+sxV+"    dxV"+dxV);
                //Log.d("Status", "angle:"+angle+"   angleRad:"+angleRad);
                //Log.d("Status", "sin%:"+sinPercent);
                //Log.d("Status", "-------------------------------");

                if (connectedThread != null) {
                    connectedThread.writeVelocity((int) sxV, (int) dxV);
                }

            }
        });
        SeekBar.OnSeekBarChangeListener sBarrListener = new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                if(Math.abs(seekBar.getProgress()-(seekBar.getMax()/2)) <= PROGRESS_BARS_STOP_TOLERANCE){
                    progress = seekBar.getMax()/2;
                }

                if(seekBar == seekBarR){
                    if(progress != seekBarRVal){
                        seekBarRVal = progress;
                        textSeekR.setText(""+seekBarRVal);
                        //Log.println(Log.ASSERT,"debug",seekBarLVal+"   "+ (180-seekBarRVal));


                        if (connectedThread != null) {
                            connectedThread.writeVelocity(seekBarLVal, 180-seekBarRVal);
                        }
                    }
                }else {
                    if(progress != seekBarLVal){
                        seekBarLVal = progress;
                        textSeekL.setText(""+seekBarLVal);
                        //Log.println(Log.ASSERT,"debug",seekBarLVal+"   "+ (180-seekBarRVal));


                        if (connectedThread != null) {
                            connectedThread.writeVelocity(seekBarLVal, 180-seekBarRVal);
                        }
                    }
                }

            }
            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {}//not usable
            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {}//not usable
        };
        seekBarL.setOnSeekBarChangeListener(sBarrListener);
        seekBarR.setOnSeekBarChangeListener(sBarrListener);

        //set on touch for movement button
        btnUpLeft.setOnTouchListener(this);
        btnUp.setOnTouchListener(this);
        btnUpRight.setOnTouchListener(this);
        btnLeft.setOnTouchListener(this);
        btnRight.setOnTouchListener(this);
        btnDownLeft.setOnTouchListener(this);
        btnDown.setOnTouchListener(this);
        btnDownRight.setOnTouchListener(this);

        if (checkCallingOrSelfPermission(Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(MainActivity.this, new String[]{Manifest.permission.BLUETOOTH_CONNECT}, 2);
            Toast.makeText(MainActivity.this,"dovresti autorizzare il bluetooth",Toast.LENGTH_LONG).show();
            //Log.d(TAG,"on create asked permission");
        }
        //if (checkCallingOrSelfPermission(Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED) {
        //    Log.d(TAG,"yayyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyy");
        //}
        graphicUpdate();
    }

    /* ============================ Thread to Create Bluetooth Connection =================================== */
    public static class CreateConnectThread extends Thread {
        Context context;

        public CreateConnectThread(Context context, BluetoothAdapter bluetoothAdapter, String address) {
            this.context = context;
            /*
            Use a temporary object that is later assigned to mmSocket
            because mmSocket is final.
             */
            BluetoothDevice bluetoothDevice = bluetoothAdapter.getRemoteDevice(address);
            BluetoothSocket tmp = null;
            UUID uuid = bluetoothDevice.getUuids()[0].getUuid();

            try {
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
        super.onBackPressed();
        if (createConnectThread != null) {
            createConnectThread.cancel();
        }
        Intent a = new Intent(Intent.ACTION_MAIN);
        a.addCategory(Intent.CATEGORY_HOME);
        a.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        startActivity(a);
    }

    public void sendVelocity(){
        if(connectedThread != null){
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

    }
    public void graphicUpdate(){
        arrowsContainer.setVisibility(currMode == MODE_ARROWS ? View.VISIBLE : View.INVISIBLE);
        joystickContainer.setVisibility(currMode == MODE_JOYSTICK ? View.VISIBLE : View.INVISIBLE);
        leaversContainer.setVisibility(currMode == MODE_LEAVERS ? View.VISIBLE : View.INVISIBLE);

    }

    @Override
    public boolean onTouch(View v, MotionEvent event){
        if(connectedThread != null){
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
        }

        return false;
    }
    //commento
}
