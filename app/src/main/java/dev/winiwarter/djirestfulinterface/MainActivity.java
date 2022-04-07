package dev.winiwarter.djirestfulinterface;

import androidx.annotation.Keep;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;

import android.app.Activity;
import android.graphics.SurfaceTexture;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.os.Build;
import android.os.Bundle;
import android.os.SystemClock;
import android.text.format.Formatter;
import android.util.Log;
import android.view.TextureView;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.MapView;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.Marker;
import com.google.android.gms.maps.model.MarkerOptions;
import com.google.gson.Gson;

import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Timer;
import java.util.TimerTask;

import dji.common.battery.BatteryState;
import dji.common.camera.SettingsDefinitions;
import dji.common.flightcontroller.FlightControllerState;
import dji.common.gimbal.GimbalState;
import dji.common.gimbal.Rotation;
import dji.common.gimbal.RotationMode;
import dji.common.product.Model;
import dji.common.rtk.PPKModeState;
import dji.sdk.base.BaseProduct;
import dji.sdk.camera.Camera;
import dji.common.error.DJIError;
import dji.common.flightcontroller.Attitude;
import dji.common.flightcontroller.LocationCoordinate3D;
import dji.common.flightcontroller.virtualstick.FlightControlData;
import dji.common.flightcontroller.virtualstick.FlightCoordinateSystem;
import dji.common.flightcontroller.virtualstick.RollPitchControlMode;
import dji.common.flightcontroller.virtualstick.VerticalControlMode;
import dji.common.flightcontroller.virtualstick.YawControlMode;
import dji.common.realname.AircraftBindingState;
import dji.common.realname.AppActivationState;
import dji.common.useraccount.UserAccountState;
import dji.common.util.CommonCallbacks;
import dji.sdk.camera.VideoFeeder;
import dji.sdk.codec.DJICodecManager;
import dji.sdk.flightcontroller.FlightController;
import dji.sdk.realname.AppActivationManager;
import dji.sdk.sdkmanager.DJISDKManager;
import dji.sdk.useraccount.UserAccountManager;
import fi.iki.elonen.NanoHTTPD;


public class MainActivity extends AppCompatActivity implements  TextureView.SurfaceTextureListener, View.OnClickListener {

    private static final String TAG = MainActivity.class.getName();

    protected VideoFeeder.VideoDataListener mReceivedVideoDataListener = null;
    protected DJICodecManager mCodecManager = null;

    protected Button loginBtn;
    protected Button logoutBtn;
    protected Button startBtn;
    protected Button stopBtn;
    protected Button startRTKBtn;
    protected Button stopRTKBtn;
    protected Button photoBtn;
    protected TextView bindingStateTV;
    protected TextView appActivationStateTV;
    protected TextView infoTV;
    protected ProgressBar battBar;
    protected EditText battText;

    protected TextureView mVideoSurface = null;
    protected MapView mapView = null;

    private AppActivationManager appActivationManager;
    private AppActivationState.AppActivationStateListener activationStateListener;
    private AircraftBindingState.AircraftBindingStateListener bindingStateListener;



    private Timer sendVirtualStickDataTimer;
    private SendVirtualStickDataTask sendVirtualStickDataTask;
    private LandingTask landingTask;

    private Timer sendKeepPPKTimer;
    private Timer sendStopPPKTimer;
    private SendKeepPPKTask sendKeepPPKTask;
    private SendStopPPKTask sendStopPPKTask;

    private float pitch;
    private float roll;
    private float yaw;
    private float throttle;
    private float batt_perc;
    private float gimbal_pitch;


    private Marker droneMarker;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        setContentView(R.layout.activity_main);

        initUI();
        initData();

        FlightController flightController = ModuleVerificationUtil.getFlightController();

        flightController.setRollPitchCoordinateSystem(FlightCoordinateSystem.GROUND);
        flightController.setRollPitchControlMode(RollPitchControlMode.VELOCITY);
        flightController.setYawControlMode(YawControlMode.ANGLE);
        flightController.setVerticalControlMode(VerticalControlMode.VELOCITY);
        pitch = (float) (0.0);
        roll = (float) (0.0);
        yaw = (float) (0.0);
        throttle = (float) (0.0);
        if (null == sendVirtualStickDataTimer) {
            sendVirtualStickDataTask = new SendVirtualStickDataTask();
            sendVirtualStickDataTimer = new Timer();
            sendVirtualStickDataTimer.schedule(sendVirtualStickDataTask, 100, 200);
        }

        DroneServer server = new DroneServer(9000);
        try {
            server.start();
        } catch (IOException e) {
            e.printStackTrace();
        }


        // The callback for receiving the raw H264 video data for camera live view
        mReceivedVideoDataListener = new VideoFeeder.VideoDataListener() {

            @Override
            public void onReceive(byte[] videoBuffer, int size) {
                if (mCodecManager != null) {
                    mCodecManager.sendDataToDecoder(videoBuffer, size);
                }
            }

        };

        DJIApplication.getAircraftInstance().getBattery().setStateCallback(new BatteryState.Callback() {
            @Override
            public void onUpdate(BatteryState djiBatteryState) {
                    batt_perc = djiBatteryState.getChargeRemainingInPercent();

                        MainActivity.this.runOnUiThread(() -> {
                            battBar.setProgress((int) batt_perc);
                            battText.setText(String.format("Battery: %.2f", batt_perc));
                        });

            }
        });
        DJIApplication.getProductInstance().getGimbal().setStateCallback(new GimbalState.Callback() {
            @Override
            public void onUpdate(@NonNull GimbalState gimbalState) {
                gimbal_pitch = gimbalState.getAttitudeInDegrees().getPitch();
            }
        });


        flightController.setStateCallback(new FlightControllerState.Callback() {
            @Override
            public void onUpdate(@NonNull FlightControllerState flightControllerState) {
                PhotoPhlyState state = handleGetState();
                if (droneMarker != null) {

                    MainActivity.this.runOnUiThread(() ->
                            droneMarker.setPosition(new LatLng(state.locatt.lat, state.locatt.lon)));
                }
            }

        });

        mapView.getMapAsync(this::setUpMaps);
    }

    private void setUpMaps(GoogleMap googleMap) {
        PhotoPhlyState state = handleGetState();
        googleMap.moveCamera(CameraUpdateFactory.newLatLngZoom(new LatLng(state.locatt.lat, state.locatt.lon), 15));

        MainActivity.this.runOnUiThread(() ->
                droneMarker = googleMap.addMarker(new MarkerOptions()
                .position(new LatLng(state.locatt.lat, state.locatt.lon))
                .title("drone")));
    }

    @Override
    public void onResume() {
        Log.e(TAG, "onResume");

        initPreviewer();
        if(mVideoSurface == null) {
            Log.e(TAG, "mVideoSurface is null");
        }
        setUpListener();
        super.onResume();
    }

    @Override
    public void onPause() {
        Log.e(TAG, "onPause");
        super.onPause();
    }

    @Override
    public void onStop() {
        Log.e(TAG, "onStop");
        super.onStop();
    }

    public void onReturn(View view){
        Log.e(TAG, "onReturn");
        this.finish();
    }

    @Override
    protected void onDestroy() {
        Log.e(TAG, "onDestroy");
        tearDownListener();
        super.onDestroy();
    }

    private void initUI(){

        bindingStateTV = (TextView) findViewById(R.id.tv_binding_state_info);
        appActivationStateTV = (TextView) findViewById(R.id.tv_activation_state_info);
        loginBtn = (Button) findViewById(R.id.btn_login);
        logoutBtn = (Button) findViewById(R.id.btn_logout);
        startBtn = (Button) findViewById(R.id.btn_start);
        stopBtn = (Button) findViewById(R.id.btn_stop);
        startRTKBtn = (Button) findViewById(R.id.btn_RINEX_start);
        stopRTKBtn = (Button) findViewById(R.id.btn_RINEX_stop);
        infoTV = (TextView) findViewById(R.id.tv_info_text);
        photoBtn = (Button) findViewById(R.id.btn_photo);
        battBar = (ProgressBar) findViewById(R.id.battBar);
        battBar.setMax(100);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            battBar.setMin(0);
        }
        battText = (EditText) findViewById(R.id.battLevelText);

        mVideoSurface = (TextureView)findViewById(R.id.video_previewer_surface);
        mapView = (MapView) findViewById(R.id.mapView);
        if (null != mVideoSurface) {
            mVideoSurface.setSurfaceTextureListener(this);
        }

        loginBtn.setOnClickListener(this);
        logoutBtn.setOnClickListener(this);
        startBtn.setOnClickListener(this);
        stopBtn.setOnClickListener(this);
        startRTKBtn.setOnClickListener(this);
        stopRTKBtn.setOnClickListener(this);
        photoBtn.setOnClickListener(this);

    }

    protected void onProductChange() {
        initPreviewer();
    }

    private void initPreviewer() {

        BaseProduct product = DJIApplication.getProductInstance();

        if (product == null || !product.isConnected()) {
            showToast(getString(R.string.disconnected));
        } else {
            if (null != mVideoSurface) {
                mVideoSurface.setSurfaceTextureListener(this);
            }
            if (!product.getModel().equals(Model.UNKNOWN_AIRCRAFT)) {
                VideoFeeder.getInstance().getPrimaryVideoFeed().addVideoDataListener(mReceivedVideoDataListener);
            }
        }
    }

    private void uninitPreviewer() {
        Camera camera = DJIApplication.getCameraInstance();
        if (camera != null){
            // Reset the callback
            VideoFeeder.getInstance().getPrimaryVideoFeed().addVideoDataListener(null);
        }
    }

    @Override
    public void onSurfaceTextureAvailable(SurfaceTexture surface, int width, int height) {
        Log.e(TAG, "onSurfaceTextureAvailable");
        if (mCodecManager == null) {
            mCodecManager = new DJICodecManager(this, surface, width, height);
        }
    }

    @Override
    public void onSurfaceTextureSizeChanged(SurfaceTexture surface, int width, int height) {
        Log.e(TAG, "onSurfaceTextureSizeChanged");
    }

    @Override
    public boolean onSurfaceTextureDestroyed(SurfaceTexture surface) {
        Log.e(TAG,"onSurfaceTextureDestroyed");
        if (mCodecManager != null) {
            mCodecManager.cleanSurface();
            mCodecManager = null;
        }

        return false;
    }

    @Override
    public void onSurfaceTextureUpdated(SurfaceTexture surface) {
    }


    private void initData(){
        setUpListener();

        appActivationManager = DJISDKManager.getInstance().getAppActivationManager();

        if (appActivationManager != null) {
            appActivationManager.addAppActivationStateListener(activationStateListener);
            appActivationManager.addAircraftBindingStateListener(bindingStateListener);
            MainActivity.this.runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    appActivationStateTV.setText("" + appActivationManager.getAppActivationState());
                    bindingStateTV.setText("" + appActivationManager.getAircraftBindingState());

                    WifiManager wifiMgr = (WifiManager) getApplicationContext().getSystemService(WIFI_SERVICE);
                    WifiInfo wifiInfo = wifiMgr.getConnectionInfo();
                    int ip = wifiInfo.getIpAddress();
                    String ipAddress = Formatter.formatIpAddress(ip);
                    infoTV.setText("Running on "+ ipAddress);

                }
            });
        }
    }

    private void setUpListener() {
        // Example of Listener
        activationStateListener = new AppActivationState.AppActivationStateListener() {
            @Override
            public void onUpdate(final AppActivationState appActivationState) {
                MainActivity.this.runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        appActivationStateTV.setText("" + appActivationState);
                    }
                });
            }
        };

        bindingStateListener = new AircraftBindingState.AircraftBindingStateListener() {

            @Override
            public void onUpdate(final AircraftBindingState bindingState) {
                MainActivity.this.runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        bindingStateTV.setText("" + bindingState);
                    }
                });
            }
        };
    }

    private void tearDownListener() {
        if (activationStateListener != null) {
            appActivationManager.removeAppActivationStateListener(activationStateListener);
            MainActivity.this.runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    appActivationStateTV.setText("Unknown");
                }
            });
        }
        if (bindingStateListener !=null) {
            appActivationManager.removeAircraftBindingStateListener(bindingStateListener);
            MainActivity.this.runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    bindingStateTV.setText("Unknown");
                }
            });
        }
    }

    public void showToast(final String msg) {
        runOnUiThread(new Runnable() {
            public void run() {
                Toast.makeText(MainActivity.this, msg, Toast.LENGTH_SHORT).show();
            }
        });
    }

    @Override
    public void onClick(View v) {

        switch (v.getId()) {
            case R.id.btn_login:{
                loginAccount();
                break;
            }
            case R.id.btn_logout:{
                logoutAccount();
                break;
            }
            case R.id.btn_start:{
                startVirtualSticks();
                break;
            }
            case R.id.btn_stop:{
                stopVirtualSticks();
                break;
            }
            case R.id.btn_RINEX_start:{
                startRINEX();
                break;
            }
            case R.id.btn_RINEX_stop:{
                stopRINEX();
                break;
            }
            case R.id.btn_photo:{
                takeSinglePhoto();
                break;
            }
            default:
                break;
        }
    }

    private void loginAccount(){

        UserAccountManager.getInstance().logIntoDJIUserAccount(this,
                new CommonCallbacks.CompletionCallbackWith<UserAccountState>() {
                    @Override
                    public void onSuccess(final UserAccountState userAccountState) {
                        showToast("Login Success");
                    }
                    @Override
                    public void onFailure(DJIError error) {
                        showToast("Login Error:"
                                + error.getDescription());
                    }
                });

    }

    private void logoutAccount(){
        UserAccountManager.getInstance().logoutOfDJIUserAccount(new CommonCallbacks.CompletionCallback() {
            @Override
            public void onResult(DJIError error) {
                if (null == error) {
                    showToast("Logout Success");
                } else {
                    showToast("Logout Error:"
                            + error.getDescription());
                }
            }
        });
    }

    private void startVirtualSticks(){
        FlightController flightController = ModuleVerificationUtil.getFlightController();
        flightController.setVirtualStickModeEnabled(true, new CommonCallbacks.CompletionCallback() {
            @Override
            public void onResult(DJIError djiError) {
                showToast("Virtual stick mode enabled.");
            }
        });
    }

    private void stopVirtualSticks(){
        FlightController flightController = ModuleVerificationUtil.getFlightController();
        flightController.setVirtualStickModeEnabled(false, new CommonCallbacks.CompletionCallback() {
            @Override
            public void onResult(DJIError djiError) {
                showToast("Virtual stick mode disabled.");
            }
        });
    }

    private void startRINEX(){
        Camera camera = DJIApplication.getProductInstance().getCamera();
        camera.setMode(SettingsDefinitions.CameraMode.SHOOT_PHOTO,
                djiError ->{

                    if (null == sendKeepPPKTimer) {
                        sendKeepPPKTask = new SendKeepPPKTask();
                        //sendStopPPKTask = new SendStopPPKTask();
                        sendKeepPPKTimer = new Timer();
                        //sendStopPPKTimer = new Timer();
                    }
                    sendKeepPPKTimer.schedule(sendKeepPPKTask, 10, 200);
                    //sendStopPPKTimer.schedule(sendStopPPKTask, 9600, 10000);
        });

    }

    private void stopRINEX(){
        FlightController flightController = ModuleVerificationUtil.getFlightController();
        flightController.getRTK().setPPKModeEnabled(false, new CommonCallbacks.CompletionCallback() {
            @Override
            public void onResult(DJIError djiError) {
                showToast("RINEX logging mode disabled.");
                MainActivity.this.runOnUiThread(() ->
                        ((TextView) findViewById(R.id.tv_RINEX_text)).setText("RTK stop"));
                sendKeepPPKTimer.cancel();
                //sendStopPPKTimer.cancel();
            }
        });
    }


    private class SendKeepPPKTask extends TimerTask {
        private boolean waiting = false;

        @Override
        public void run(){
            run(0);
        }

        public void run(int counter) {
            if (!waiting) {
                waiting = true;

                DJIApplication.getAircraftInstance()
                        .getFlightController().getRTK().setPPKModeEnabled(true, new CommonCallbacks.CompletionCallback() {
                    @Override
                    public void onResult(DJIError djiError) {
                        if (djiError != null) {
                            //showToast("Error. " + djiError.getDescription() + djiError.getErrorCode());
                            //run(counter+1);
                            MainActivity.this.runOnUiThread(() ->
                                    ((TextView) findViewById(R.id.tv_RINEX_text)).setText("RTK on (" + djiError.getErrorCode() + ")"));
                        } else {
                            MainActivity.this.runOnUiThread(() ->
                                    ((TextView) findViewById(R.id.tv_RINEX_text)).setText("RTK on"));
                            // SendKeepPPKTask nextTask = new SendKeepPPKTask();
                            // sendKeepPPKTimer.schedule(nextTask, 10000);
                        }
                        waiting = false;
                    }
                });
            }
        }
    }


    private class SendStopPPKTask extends TimerTask {

        @Override
        public void run() {
            if (ModuleVerificationUtil.isFlightControllerAvailable()) {
                DJIApplication.getAircraftInstance()
                        .getFlightController().getRTK().setPPKModeEnabled(false, new CommonCallbacks.CompletionCallback() {
                    @Override
                    public void onResult(DJIError djiError) {
                        if (djiError != null) {
                            showToast("Error. " + djiError.getDescription() + djiError.getErrorCode());
                        }
                        MainActivity.this.runOnUiThread(() ->
                                ((TextView) findViewById(R.id.tv_RINEX_text)).setText("RTK off"));
                    }
                   });
            }
        }

    }

    private class SendVirtualStickDataTask extends TimerTask {

        @Override
        public void run() {
            if (ModuleVerificationUtil.isFlightControllerAvailable()) {
                PhotoPhlyState pps = handleGetState();
                float alt = (float)pps.locatt.height;
                float throttle_speed = 0;
                if (throttle - alt > 0.5){
                    throttle_speed = 1;
                } else if (throttle - alt < - 0.5){
                    throttle_speed = -1;
                }

                if (throttle - alt > 2){ // large difference - correct more quickly
                    throttle_speed = 4;
                } else if (throttle - alt < -2){
                    throttle_speed = -4;
                }

                DJIApplication.getAircraftInstance()
                        .getFlightController()
                        .sendVirtualStickFlightControlData(new FlightControlData(pitch,
                                        roll,
                                        yaw,
                                        throttle_speed),
                                new CommonCallbacks.CompletionCallback() {
                                    @Override
                                    public void onResult(DJIError djiError) {

                                    }
                                });
            }
        }
    }

    private class ChangeControlTask extends TimerTask {
        private float b_pitch, b_roll, b_yaw, b_throttle;

        protected ChangeControlTask(float pitch, float roll, float yaw, float throttle) {
            super();
            this.b_pitch = pitch;
            this.b_roll = roll;
            this.b_yaw = yaw;
            this.b_throttle = throttle;
        }

        @Override
        public void run() {
            pitch = this.b_pitch;
            roll = this.b_roll;
            yaw = this.b_yaw;
            throttle = this.b_throttle;
            System.out.println(String.format("%.3f, %.3f, %.3f, %.3f", pitch, roll, yaw, throttle));
        }
    }

    private class LandingTask extends TimerTask {

        @Override
        public void run() {
            if (ModuleVerificationUtil.isFlightControllerAvailable()) {
                DJIApplication.getAircraftInstance()
                        .getFlightController()
                        .startLanding(
                                new CommonCallbacks.CompletionCallback() {
                                    @Override
                                    public void onResult(DJIError djiError) {

                                    }
                                });
                SystemClock.sleep(100);
                DJIApplication.getAircraftInstance()
                        .getFlightController().confirmLanding(
                        new CommonCallbacks.CompletionCallback() {
                            @Override
                            public void onResult(DJIError djiError) {
                            }
                        }
                );
            }
        }
    }

    public PhotoPhlyState handleGetState() {
        LocationCoordinate3D loc = DJIApplication.getAircraftInstance()
                .getFlightController().getState().getAircraftLocation();
        Attitude att = DJIApplication.getAircraftInstance()
                .getFlightController().getState().getAttitude();
        LocAtt locAtt = new LocAtt();
        locAtt.lat = !Double.isNaN(loc.getLatitude()) ? loc.getLatitude() : -9999.0;
        locAtt.lon = !Double.isNaN(loc.getLongitude()) ? loc.getLongitude() : -9999.0;
        locAtt.height = loc.getAltitude();
        locAtt.yaw = att.yaw;
        locAtt.pitch = att.pitch;
        locAtt.roll = att.roll;

        PhotoPhlyState pps = new PhotoPhlyState();
        pps.locatt = locAtt;
        pps.battery_remaining = batt_perc;
        pps.gimbal_rot = gimbal_pitch;
        return pps;
    }

    public String handleSetState(State state) {
        System.out.println(String.format("Updated state: Yaw %.4f Pitch %.4f Roll %.4f Throttle %.4f Gimbal %.4f", state.yaw, state.pitch, state.roll, state.throttle, state.gimbal));
        pitch = state.pitch;
        roll = state.roll;
        yaw = state.yaw;
        throttle = state.throttle;
        DJIApplication.getProductInstance().getGimbal().
                rotate(new Rotation.Builder().pitch(state.gimbal)
                        .mode(RotationMode.ABSOLUTE_ANGLE)
                        .yaw(Rotation.NO_ROTATION)
                        .roll(Rotation.NO_ROTATION)
                        .time(0)
                        .build(), error -> {

                        });
        return "";
    }

    public void handleTakePhoto(){
        Camera camera = DJIApplication.getProductInstance().getCamera();
        camera.setMode(SettingsDefinitions.CameraMode.SHOOT_PHOTO,
                djiError -> camera.setShootPhotoMode(SettingsDefinitions.ShootPhotoMode.SINGLE,
                        djiError1 -> takeSinglePhoto()));
    }

    public void takeSinglePhoto(){
        Camera camera = DJIApplication.getProductInstance().getCamera();
        camera.startShootPhoto(djiError -> {
                        if(djiError != null){
                            showToast("Error taking photo:" + djiError.getDescription());
                        }
                    }
        );
    }

    public void handleStartPhotoInterval(int interval){
        SettingsDefinitions.PhotoTimeIntervalSettings settings = new SettingsDefinitions.PhotoTimeIntervalSettings(255, interval);
        Camera camera = DJIApplication.getProductInstance().getCamera();
        camera.setMode(SettingsDefinitions.CameraMode.SHOOT_PHOTO,
                djiError -> camera.setShootPhotoMode(SettingsDefinitions.ShootPhotoMode.INTERVAL,
                        djiError1 -> camera.setPhotoTimeIntervalSettings(settings,
                                djiError2 -> camera.startShootPhoto(
                                    djiError3 -> {}))));
    }
    public void handleStopPhotoInterval(){
        Camera camera = DJIApplication.getProductInstance().getCamera();
        camera.stopShootPhoto(djiError2 -> {});
    }

    class LocAtt {
        @Keep
        double lat, lon, height, yaw, pitch, roll = 0.0;
    }

    class PhotoPhlyState {
        @Keep
        public LocAtt locatt;
        double battery_remaining;
        double gimbal_rot;
    }

    class State {
        @Keep
        float yaw, pitch, roll, throttle, gimbal = (float) 0.0;
    }

    public class DroneServer extends NanoHTTPD {

        private static final String MIME_JSON = "application/json";
        public DroneServer(int port) {
            super(port);
        }

        @Override
        public Response serve(IHTTPSession session) {
            try {
                Method method = session.getMethod();
                String uri = session.getUri();
                //Map<String, String> parms = session.getParms();
                if(uri.equals("/hello")){
                    String response="Hello World";
                    return  newFixedLengthResponse(response);
                }
                else if (uri.equals("/cgi/getState")){
                    PhotoPhlyState compState = handleGetState();
                    Gson gson = new Gson();
                    String response = gson.toJson(compState);
                    return newFixedLengthResponse(Response.Status.OK, MIME_JSON, response);
                }
                else if (uri.equals("/cgi/setState") && method == Method.POST){
                    session.parseBody(new HashMap<String, String>());
                    State state = new State();
                    Map<String, List<String>> requestBody = session.getParameters();
                    state.pitch = Float.parseFloat(requestBody.get("pitch").get(0));
                    state.roll =  Float.parseFloat(requestBody.get("roll").get(0));
                    state.yaw =  Float.parseFloat(requestBody.get("yaw").get(0));
                    state.throttle =  Float.parseFloat(requestBody.get("throttle").get(0));
                    state.gimbal =  Float.parseFloat(requestBody.get("gimbal").get(0));
                    handleSetState(state);
                    return newFixedLengthResponse(Response.Status.OK, MIME_PLAINTEXT, "ok");
                }
                else if (uri.equals("/cgi/takePhoto") && method == Method.PUT){
                    handleTakePhoto();
                    return newFixedLengthResponse(Response.Status.OK, MIME_PLAINTEXT, "ok");
                }
                else if (uri.equals("/cgi/takeSinglePhoto") && method == Method.PUT){
                    takeSinglePhoto();
                    return newFixedLengthResponse(Response.Status.OK, MIME_PLAINTEXT, "ok");
                }
                else if (uri.equals("/cgi/startInterval") && method == Method.PUT){
                    session.parseBody(new HashMap<String, String>());
                    Map<String, List<String>> requestBody = session.getParameters();
                    int interval = Integer.parseInt(requestBody.get("interval").get(0));
                    handleStartPhotoInterval(interval);
                    return newFixedLengthResponse(Response.Status.OK, MIME_PLAINTEXT, "ok");
                }
                else if (uri.equals("/cgi/stopInterval") && method == Method.PUT) {
                    handleStopPhotoInterval();
                    return newFixedLengthResponse(Response.Status.OK, MIME_PLAINTEXT, "ok");
                }
                else {
                    return newFixedLengthResponse(Response.Status.NOT_IMPLEMENTED, MIME_PLAINTEXT, "Unable to locate " + uri);
                }
            }
            catch (Exception ex) {
                return newFixedLengthResponse(Response.Status.INTERNAL_ERROR, MIME_HTML, "<html><body><h1>Error</h1>" + ex.toString() + "</body></html>");
            }
        }


    }

}
