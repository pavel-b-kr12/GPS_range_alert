package com.example.gps_range_alert;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.ClipData;
import android.content.ClipDescription;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.content.IntentSender;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.location.Location;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.media.ToneGenerator;
import android.net.Uri;
import android.os.Bundle;
import android.os.Looper;
import android.provider.Settings;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;


import com.google.android.gms.common.api.ApiException;
import com.google.android.gms.common.api.ResolvableApiException;
import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationCallback;
import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.location.LocationResult;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.location.LocationSettingsRequest;
import com.google.android.gms.location.LocationSettingsResponse;
import com.google.android.gms.location.LocationSettingsStatusCodes;
import com.google.android.gms.location.SettingsClient;
import com.google.android.gms.tasks.OnCompleteListener;
import com.google.android.gms.tasks.OnFailureListener;
import com.google.android.gms.tasks.OnSuccessListener;
import com.google.android.gms.tasks.Task;
import com.karumi.dexter.BuildConfig;
import com.karumi.dexter.Dexter;
import com.karumi.dexter.PermissionToken;
import com.karumi.dexter.listener.PermissionDeniedResponse;
import com.karumi.dexter.listener.PermissionGrantedResponse;
import com.karumi.dexter.listener.PermissionRequest;
import com.karumi.dexter.listener.single.PermissionListener;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.text.DateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import butterknife.BindView;
import butterknife.ButterKnife;
import butterknife.OnClick;

public class MainActivity extends AppCompatActivity {
    boolean bHideDebugElements=true; //hide all except start button

    private static final String TAG = MainActivity.class.getSimpleName();

    SeekBar seekBar1;
    SeekBar seekBar2;

    @BindView(R.id.location_result)
    TextView txtLocationResult;
    @BindView(R.id.textView_load_target)
    TextView textView_load_target;

    @BindView(R.id.TargetLocationLat)
    TextView txtTargetLocationLat;

    @BindView(R.id.TargetLocationLon)
    TextView txtTargetLocationLon;

    @BindView(R.id.distance_current_to_target)
    TextView distance_current_to_target_el;

    @BindView(R.id.updated_on)
    TextView txtUpdatedOn;

    @BindView(R.id.btn_start_location_updates)
    Button btnStartUpdates;

    @BindView(R.id.btn_stop_location_updates)
    Button btnStopUpdates;

    // location last updated time
    private String mLastUpdateTime;

    // location updates interval - 10sec
    private static final long UPDATE_INTERVAL_IN_MILLISECONDS = 10000;

    // fastest updates interval - 5 sec
    // location updates will be received if another app is requesting the locations
    // than your app can handle
    private static final long FASTEST_UPDATE_INTERVAL_IN_MILLISECONDS = 5000;

    private static final int REQUEST_CHECK_SETTINGS = 100;


    // bunch of location related apis
    private FusedLocationProviderClient mFusedLocationClient;
    private SettingsClient mSettingsClient;
    private LocationRequest mLocationRequest;
    private LocationSettingsRequest mLocationSettingsRequest;
    private LocationCallback mLocationCallback;
    private Location location_current;
    private Location location_target;
    public void setLocation_current(Location location_current) {
        this.location_current = location_current;
        distance_calc();
    }

    public void setLocation_target(Location location_target) {
        this.location_target = location_target;
        distance_calc();

    }
    private float range0;
    private float range1;
    private float distance_current_to_target=-1;

    // boolean flag to toggle the ui
    private Boolean mRequestingLocationUpdates;
    private int inRange=9;

    void distance_calc()
    {
        //point chain mode
        if(target_file_nm!=null) {
            if (location_target != null && location_current != null)
                distance_current_to_target = location_current.distanceTo(location_target);
            else distance_current_to_target = -1;
        }

        //check all points mode
        final int size = target_locations_to_check_each.size();
        for (int i=0;i<size;i++) {
            float distance = location_current.distanceTo(target_locations_to_check_each.get(i));
            if(distance<50) target_reached(i);
        }


        toggleButtons();


        updateLocationUI();
    }
    int target_multi_last_reached=-1;
    private void target_reached(int i) {
        if(i!=target_multi_last_reached || (mediaPlayer!=null && !mediaPlayer.isPlaying())) //play if last was different or playback is over //TODO range histeresis
        {
            target_multi_last_reached=i;
            String mp3_nm = target_locations_to_check_each_mp3_nm.get(i);
            if (mediaPlayer == null) mediaPlayer = new MediaPlayer();
            try {
                if (mediaPlayer.isPlaying()) {
                    mediaPlayer.stop();
                    mediaPlayer.reset();
                }
                mediaPlayer.setDataSource(target_file_dir+ mp3_nm);
                mediaPlayer.prepare();
                mediaPlayer.start();
            } catch (Exception e) {
                e.printStackTrace();
                mediaPlayer.reset();
            }
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        ButterKnife.bind(this);

        init(); // initialize the necessary libraries

        restoreValuesFromBundle(savedInstanceState);

        verifyStoragePermissions(this); //and next load 1st file async
    }

    private void init() {
        mFusedLocationClient = LocationServices.getFusedLocationProviderClient(this);
        mSettingsClient = LocationServices.getSettingsClient(this);

        mLocationCallback = new LocationCallback() {
            @Override
            public void onLocationResult(LocationResult locationResult) {
                super.onLocationResult(locationResult);
                // location is received
                setLocation_current(locationResult.getLastLocation());
                mLastUpdateTime = DateFormat.getTimeInstance().format(new Date());



                updateLocationUI();
            }
        };

        mRequestingLocationUpdates = false;

        mLocationRequest = new LocationRequest();
        mLocationRequest.setInterval(UPDATE_INTERVAL_IN_MILLISECONDS);
        mLocationRequest.setFastestInterval(FASTEST_UPDATE_INTERVAL_IN_MILLISECONDS);
        mLocationRequest.setPriority(LocationRequest.PRIORITY_HIGH_ACCURACY);

        LocationSettingsRequest.Builder builder = new LocationSettingsRequest.Builder();
        builder.addLocationRequest(mLocationRequest);
        mLocationSettingsRequest = builder.build();
//------------------------------
        //final TextView t1=new TextView(this);
        seekBar1=(SeekBar) findViewById(R.id.seekBar1);
        //seekBar1.setMax(1000);
        seekBar1.setProgress(10);
        seekBar1.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
                //tv1.setText(String.valueOf(seekBar.getProgress()));
                range0=seekBar.getProgress();
                updateLocationUI();
            }
            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {            }
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                //t1.setTextSize();
				//t1.setText(...);
                //Toast.makeText(getApplicationContext(), String.valueOf(progress),Toast.LENGTH_LONG).show();
                range0=seekBar.getProgress();
                updateLocationUI();
            }
        });
//------------------------------
        seekBar2=(SeekBar) findViewById(R.id.seekBar2);
        seekBar2.setProgress(10);
        seekBar2.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
                //tv1.setText(String.valueOf(seekBar.getProgress()));
                range1=seekBar.getProgress();
                updateLocationUI();
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {            }

            @Override
            public void onProgressChanged(SeekBar seekBar, int progress,boolean fromUser) {
                range1=seekBar.getProgress();
                updateLocationUI();
            }
        });

        if(bHideDebugElements)
        {
            seekBar2.setVisibility(View.GONE);
            seekBar1.setVisibility(View.GONE);
            findViewById(R.id.btn_loc_from_target).setVisibility(View.GONE);
            findViewById(R.id.btn_get_last_location).setVisibility(View.GONE);
            findViewById(R.id.btn_pasteLatLon).setVisibility(View.GONE);
            findViewById(R.id.btn_set_target_location_from_current).setVisibility(View.GONE);
            //txtLocationResult.setVisibility(View.GONE);
            textView_load_target.setVisibility(View.GONE);
            txtTargetLocationLat.setVisibility(View.GONE);
            txtTargetLocationLon.setVisibility(View.GONE);
            distance_current_to_target_el.setVisibility(View.GONE);
            txtUpdatedOn.setVisibility(View.GONE);
            //btnStartUpdates.setVisibility(View.GONE);
            btnStopUpdates.setVisibility(View.GONE);

        }
    }

    String target_file_dir="/storage/emulated/0/Download/GPS_range_alert_targets/";
    String target_file_nm="2021-02-15"; // .txt and other = access deny //set this to null to disable point chain mode
    String target_file_nm_mp3=null;

    List<String> target_locations_to_check_each_mp3_nm = new ArrayList<String>();
    List<Location> target_locations_to_check_each = new ArrayList<Location>();


    private void target_file_nm_load() { // arrived to target     ///storage/sdcard0/
        if(target_file_nm!=null) {
            //play target mp3 and load next file
            if (target_file_nm_mp3 != null) {
                if (new File(target_file_nm_mp3).exists()) {
                    if (mediaPlayer == null) mediaPlayer = new MediaPlayer();
                    try {
                        if (mediaPlayer.isPlaying()) {
                            mediaPlayer.stop();
                            mediaPlayer.reset();
                        }
                        mediaPlayer.setDataSource(target_file_nm_mp3);
                        mediaPlayer.prepare();
                        mediaPlayer.start();
                    } catch (Exception e) {
                        e.printStackTrace();
                        mediaPlayer.reset();
                    }
                } else textView_load_target.setText("no " + target_file_nm_mp3);
            }

            File file = new File(target_file_dir + target_file_nm + ".png");
            target_file_nm_mp3 = target_file_dir + target_file_nm + ".mp3";
            StringBuilder text = new StringBuilder();
            try {
                BufferedReader br = new BufferedReader(new FileReader(file));

                String[] arr = br.readLine().split("\t");
                //textView_load_target.setText(file.getParentFile().getAbsolutePath()+" "+arr[0]+" "+ arr[1]);
                textView_load_target.setText(arr[0] + " " + arr[1]);
                if (arr[0].length() != 0) {
                    parseLatLon_set_target_loc(arr[0]);
                }
                target_file_nm = arr[1];

            /*
            String line;
            while ((line = br.readLine()) != null) {
                text.append(line);
                text.append('\n');
            }
            */
                br.close();
            } catch (IOException e) {
                textView_load_target.setText("no " + file.getAbsolutePath());
            }
        }
        //search all files with name of lat\slon.*mp3
        File directory = new File(target_file_dir);
        File[] files = directory.listFiles();
        Pattern p = Pattern.compile("([+-]?([0-9]*[.])?[0-9]+)\\s[+-]?(([0-9]*[.])?[0-9]+).*");
        for (int i = 0; i < files.length; i++) {

            String nm=files[i].getName();
            Matcher m = p.matcher(nm);
            if (m.matches()) {

                Location l=new Location("");
                l.setLatitude(Double.parseDouble(m.group(1))); //Kiev, Ukraine. Latitude: 50.4547 Longitude: 30.5238.
                l.setLongitude(Double.parseDouble(m.group(3)));

                target_locations_to_check_each.add(l);
                target_locations_to_check_each_mp3_nm.add(nm);
            }
        }
        Log.d("Files", "FileName:" + target_locations_to_check_each_mp3_nm.toString());
       /* if (string.matches("[A-Z]{2}\\-[0-9]{1,2}\\-[A-Z]{1,2}\\-[0-9]{1,4}"))
        {
            // Yes it matches
        }
        */
    }

    MediaPlayer mediaPlayer=null;

    private void restoreValuesFromBundle(Bundle savedInstanceState) {
        if (savedInstanceState != null) {
            if (savedInstanceState.containsKey("is_requesting_updates"))
                mRequestingLocationUpdates = savedInstanceState.getBoolean("is_requesting_updates");

            if (savedInstanceState.containsKey("last_known_location"))
                setLocation_current( savedInstanceState.getParcelable("last_known_location"));

            if (savedInstanceState.containsKey("target_location"))
                setLocation_target ( savedInstanceState.getParcelable("target_location"));
            else
            {
                Location t = new Location("");
                t.setLatitude(50.4547d); //Kiev, Ukraine. Latitude: 50.4547 Longitude: 30.5238.
                t.setLongitude(30.5238d);
                setLocation_target (t);
            }

            if (savedInstanceState.containsKey("range0")) {
                range0 = savedInstanceState.getFloat("range0", 20);
            }

            if (savedInstanceState.containsKey("range1")) {
                range1 = savedInstanceState.getFloat("range1", 100);
            }

            if (savedInstanceState.containsKey("last_updated_on"))
                mLastUpdateTime = savedInstanceState.getString("last_updated_on");
        }
        else
        {
            Location t = new Location("");
            t.setLatitude(50.4547d); //Kiev, Ukraine. Latitude: 50.4547 Longitude: 30.5238.
            t.setLongitude(30.5238d);
            setLocation_target (t);
            range0=20;
            range1=100;
        }
        seekBar1.setProgress((int) range0);
        seekBar2.setProgress((int) range1);

        updateLocationUI();
    }

void location_current_display()
{
    if (location_current != null) {
        txtLocationResult.setText(
                "Lat: " + location_current.getLatitude() + ", " + "Lon: " + location_current.getLongitude()
        );
        // giving a blink animation on TextView
        txtLocationResult.setAlpha(0);
        txtLocationResult.animate().alpha(1).setDuration(300);
        // location last updated time
        txtUpdatedOn.setText("Last updated on: " + mLastUpdateTime);
    }
}
void location_target_display()
{
    txtTargetLocationLat.setText( Double.toString(location_target.getLatitude()));
    txtTargetLocationLon.setText( Double.toString(location_target.getLongitude()));
}
    /**
     * Update the UI displaying the location data
     * and toggling the buttons
     */
    private void updateLocationUI() {
        location_current_display();
        location_target_display();

        if(target_file_nm!=null) {
            distance_current_to_target_el.setText("dist: " + (distance_current_to_target == -1 ? "?" : Double.toString(distance_current_to_target)) + ", ranges:" + range0 + " | " + range1);
            if (distance_current_to_target != -1) {
                if (distance_current_to_target < range0) {
                    if (inRange != 0) {
                        inRange = 0;
                        distance_current_to_target_el.setTextColor(Color.RED);
                        ToneGenerator toneG = new ToneGenerator(AudioManager.STREAM_ALARM, 50);
                        toneG.startTone(ToneGenerator.TONE_CDMA_ALERT_CALL_GUARD, 2000);
                        target_file_nm_load();
                    }
                } else if (distance_current_to_target < range1) {
                    if (inRange != 1) {
                        inRange = 1;
                        distance_current_to_target_el.setTextColor(Color.GREEN);
                        ToneGenerator toneG = new ToneGenerator(AudioManager.STREAM_NOTIFICATION, 40);
                        toneG.startTone(ToneGenerator.TONE_CDMA_ALERT_CALL_GUARD, 200);
                    }
                } else {
                    if (inRange != 9) {
                        inRange = 9;
                        ToneGenerator toneG = new ToneGenerator(AudioManager.STREAM_SYSTEM, 40);
                        toneG.startTone(ToneGenerator.TONE_CDMA_ALERT_CALL_GUARD, 200);
                        distance_current_to_target_el.setTextColor(Color.BLUE);
                    }
                }
            }
        }

    }

    @Override
    public void onSaveInstanceState(@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putBoolean("is_requesting_updates", mRequestingLocationUpdates);
        outState.putParcelable("last_known_location", location_current);
        outState.putParcelable("target_location", location_current);
        outState.putParcelable("range0", location_current);
        outState.putParcelable("range1", location_current);
        outState.putString("last_updated_on", mLastUpdateTime);

    }

    private void toggleButtons() {
        if (mRequestingLocationUpdates) {
            btnStartUpdates.setEnabled(false);
            btnStopUpdates.setEnabled(true);
        } else {
            btnStartUpdates.setEnabled(true);
            btnStopUpdates.setEnabled(false);
        }
    }

    /**
     * Starting location updates
     * Check whether location settings are satisfied and then
     * location updates will be requested
     */
    private void startLocationUpdates() {
        mSettingsClient
                .checkLocationSettings(mLocationSettingsRequest)
                .addOnSuccessListener(this, new OnSuccessListener<LocationSettingsResponse>() {
                    @SuppressLint("MissingPermission")
                    @Override
                    public void onSuccess(LocationSettingsResponse locationSettingsResponse) {
                        Log.i(TAG, "All location settings are satisfied.");

                        Toast.makeText(getApplicationContext(), "Started location updates!", Toast.LENGTH_SHORT).show();

                        //noinspection MissingPermission
                        mFusedLocationClient.requestLocationUpdates(mLocationRequest,
                                mLocationCallback, Looper.myLooper());

                        updateLocationUI();
                    }
                })
                .addOnFailureListener(this, new OnFailureListener() {
                    @Override
                    public void onFailure(@NonNull Exception e) {
                        int statusCode = ((ApiException) e).getStatusCode();
                        switch (statusCode) {
                            case LocationSettingsStatusCodes.RESOLUTION_REQUIRED:
                                Log.i(TAG, "Location settings are not satisfied. Attempting to upgrade " +
                                        "location settings ");
                                try {
                                    // Show the dialog by calling startResolutionForResult(), and check the
                                    // result in onActivityResult().
                                    ResolvableApiException rae = (ResolvableApiException) e;
                                    rae.startResolutionForResult(MainActivity.this, REQUEST_CHECK_SETTINGS);
                                } catch (IntentSender.SendIntentException sie) {
                                    Log.i(TAG, "PendingIntent unable to execute request.");
                                }
                                break;
                            case LocationSettingsStatusCodes.SETTINGS_CHANGE_UNAVAILABLE:
                                String errorMessage = "Location settings are inadequate, and cannot be " +
                                        "fixed here. Fix in Settings.";
                                Log.e(TAG, errorMessage);

                                Toast.makeText(MainActivity.this, errorMessage, Toast.LENGTH_LONG).show();
                        }

                        updateLocationUI();
                    }
                });
    }

    @OnClick(R.id.btn_start_location_updates)
    public void startLocationButtonClick() {
        // Requesting ACCESS_FINE_LOCATION using Dexter library
        Dexter.withActivity(this)
                .withPermission(Manifest.permission.ACCESS_FINE_LOCATION)
                .withListener(new PermissionListener() {
                    @Override
                    public void onPermissionGranted(PermissionGrantedResponse response) {
                        mRequestingLocationUpdates = true;
                        startLocationUpdates();
                    }

                    @Override
                    public void onPermissionDenied(PermissionDeniedResponse response) {
                        if (response.isPermanentlyDenied()) {
                            // open device settings when the permission is denied permanently
                            openSettings();
                        }
                    }

                    @Override
                    public void onPermissionRationaleShouldBeShown(PermissionRequest permission, PermissionToken token) {
                        token.continuePermissionRequest();
                    }
                }).check();
    }

    int iii=0;
    @OnClick(R.id.btn_stop_location_updates)
    public void stopLocationButtonClick() {
        mRequestingLocationUpdates = false;
        stopLocationUpdates();
    }

    public void stopLocationUpdates() {
        // Removing location updates
        mFusedLocationClient
                .removeLocationUpdates(mLocationCallback)
                .addOnCompleteListener(this, new OnCompleteListener<Void>() {
                    @Override
                    public void onComplete(@NonNull Task<Void> task) {
                        Toast.makeText(getApplicationContext(), "Location updates stopped!", Toast.LENGTH_SHORT).show();
                        toggleButtons();
                    }
                });
    }

    @OnClick(R.id.btn_get_last_location)
    public void showLastKnownLocation() {
        if (location_current != null) {
            Toast.makeText(getApplicationContext(), "Lat: " + location_current.getLatitude()
                    + ", Lng: " + location_current.getLongitude(), Toast.LENGTH_LONG).show();
        } else {
            Toast.makeText(getApplicationContext(), "Last known location is not available!", Toast.LENGTH_SHORT).show();
        }




        Location t = new Location("");
        switch (iii)
        {
            case 0:
                t.setLatitude(11.1); //Kiev, Ukraine. Latitude: 50.4547 Longitude: 30.5238.
                t.setLongitude(11.1);
                break;
            case 1:
                t.setLatitude(11.2); //Kiev, Ukraine. Latitude: 50.4547 Longitude: 30.5238.
                t.setLongitude(11.2);
                break;
            case 2:
                t.setLatitude(22.3); //Kiev, Ukraine. Latitude: 50.4547 Longitude: 30.5238.
                t.setLongitude(22.3);
                break;
            case 3:
                t.setLatitude(33.33333); //Kiev, Ukraine. Latitude: 50.4547 Longitude: 30.5238.
                t.setLongitude(33.33333);
                break;
        }
        if(iii<3)
            iii++;


        setLocation_current (t);
    }
    @OnClick(R.id.btn_loc_from_target)
    public void loc_from_target() {
        if(location_target!=null) {
            setLocation_current( new Location(location_target));
        }
    }
    @OnClick(R.id.btn_set_target_location_from_current)
    public void setTarget_location() {
        if (location_current != null) {
            setLocation_target ( new Location(location_current));
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        switch (requestCode) {
            // Check for the integer request code originally supplied to startResolutionForResult().
            case REQUEST_CHECK_SETTINGS:
                switch (resultCode) {
                    case Activity.RESULT_OK:
                        Log.e(TAG, "User agreed to make required location settings changes.");
                        // Nothing to do. startLocationupdates() gets called in onResume again.
                        break;
                    case Activity.RESULT_CANCELED:
                        Log.e(TAG, "User chose not to make required location settings changes.");
                        mRequestingLocationUpdates = false;
                        break;
                }
                break;
        }
    }

    private void openSettings() {
        Intent intent = new Intent();
        intent.setAction(
                Settings.ACTION_APPLICATION_DETAILS_SETTINGS);
        Uri uri = Uri.fromParts("package",
                BuildConfig.APPLICATION_ID, null);
        intent.setData(uri);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        startActivity(intent);
    }

    @Override
    public void onResume() {
        super.onResume();

        // Resuming location updates depending on button state and allowed permissions
        if (mRequestingLocationUpdates && checkPermissions()) {
            startLocationUpdates();
        }

        updateLocationUI();
    }

    private boolean checkPermissions() {
        int permissionState = ActivityCompat.checkSelfPermission(this,
                Manifest.permission.ACCESS_FINE_LOCATION);
        return permissionState == PackageManager.PERMISSION_GRANTED;
    }


    @Override
    protected void onPause() {
        super.onPause();
        if (mRequestingLocationUpdates) { // pausing location updates
            stopLocationUpdates();
        }
    }

    public void btn_paste_target_click(View view) {
        ClipboardManager clipboard = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
        String pasteData = "";
        // If it does contain data, decide if you can handle the data.
        if (!(clipboard.hasPrimaryClip())) {
        } else if (!(clipboard.getPrimaryClipDescription().hasMimeType( ClipDescription.MIMETYPE_TEXT_PLAIN ))) {
           // since the clipboard has data but it is not plain text
        } else {
            //since the clipboard contains plain text.
            ClipData.Item item = clipboard.getPrimaryClip().getItemAt(0);
            // Gets the clipboard as text.
            pasteData = item.getText().toString();
            parseLatLon_set_target_loc(pasteData);
        }
    }
void parseLatLon_set_target_loc(String pasteData) {
    try {
        String[] arr = pasteData.split("[:,;/]");
        Location t = new Location("");
        t.setLatitude(Float.parseFloat(arr[0])); //Kiev, Ukraine. Latitude: 50.4547 Longitude: 30.5238.
        t.setLongitude(Float.parseFloat(arr[1]));
        setLocation_target (t);
    } catch (NumberFormatException e) {
        e.printStackTrace();
    }
}


//====================================
// Storage Permissions
    private static final int REQUEST_EXTERNAL_STORAGE = 1;
    private static String[] PERMISSIONS_STORAGE = {
            Manifest.permission.READ_EXTERNAL_STORAGE,
            Manifest.permission.WRITE_EXTERNAL_STORAGE
    };

    /**
     * Checks if the app has permission to write to device storage
     *
     * If the app does not has permission then the user will be prompted to grant permissions
     *
     * @param activity
     */
    public  void verifyStoragePermissions(Activity activity) {
        // Check if we have write permission
        int permission = ActivityCompat.checkSelfPermission(activity, Manifest.permission.WRITE_EXTERNAL_STORAGE);

        if (permission == PackageManager.PERMISSION_GRANTED) {
            target_file_nm_load();
        }
        else{
            // We don't have permission so prompt the user
            ActivityCompat.requestPermissions(
                    activity,
                    PERMISSIONS_STORAGE,
                    REQUEST_EXTERNAL_STORAGE
            );
        }
    }
}