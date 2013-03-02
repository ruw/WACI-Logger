package csu.research;


import android.app.Activity;
import android.app.AlarmManager;
import android.app.AlertDialog;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.location.Location;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.SystemClock;
import android.util.Log;
import android.view.KeyEvent;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.Button;
import android.widget.Chronometer;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.ToggleButton;
import java.io.File;
import java.io.FileInputStream;
import java.io.ObjectInputStream;
import java.util.LinkedList;


public class ContextLogger extends Activity {
    private final int OPTIONS_GROUP_DEFAULT = 0;
    private final int OPTIONS_GROUP_TEST = 1;
    private final int OPTIONS_MENU_SHOWLOCATIONS = 1;
    private LocationDatabase mLocDatabase;
    private TextView mMainTextView;
    Button mStartButton;
    Button mStopButton;
    private PendingIntent mAlarmSender;
    private boolean mLoggingActive;
    private int mLoggingInterval; // Logging interval for periods of no user interaction
    private int mLoggingInterval2; // Logging interval when screen is on
    BroadcastReceiver mScreenReceiver;

    /** Called when the activity is first created. */
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.main);
        // VARIABLES
        mLoggingActive = false; 
        mLoggingInterval = 10 * 60 * 1000; // 10 minutes
        mLoggingInterval2 = 60 * 1000; // 1 minute
        // UI ELEMENTS
        mMainTextView = (TextView) findViewById(R.id.DisplayTextView);
        registerForContextMenu(mMainTextView);
        mStartButton = (Button) findViewById(R.id.StartButton);
        mStartButton.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                startLogging();
            }
        });
        mStopButton = (Button) findViewById(R.id.StopButton);
        mStopButton.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                stopLogging();
            }
        });
        // Check if an alarm is already scheduled
        Intent i = new Intent(ContextLogger.this, ContextLoggerService.class);

        mAlarmSender = PendingIntent.getService(ContextLogger.this, 0, i, 
                PendingIntent.FLAG_NO_CREATE);
        if (mAlarmSender != null) {
            // Service running (alarm already scheduled)
            mLoggingActive = true;
            mMainTextView.setText("Logging active!");
            mStartButton.setEnabled(false);
            // // Initialize screen broadcast receiver
            // IntentFilter filter = new IntentFilter(Intent.ACTION_SCREEN_ON);
            // filter.addAction(Intent.ACTION_SCREEN_OFF);
            // mScreenReceiver = new ScreenBroadcastReceiver();
            // registerReceiver(mScreenReceiver, filter);
        } else {
            mLoggingActive = false;
            // Create an IntentSender that will launch our service, to be scheduled
            // with the alarm manager.
            mAlarmSender = PendingIntent.getService(ContextLogger.this, 0, i, 0);
            mMainTextView.setText("Logging disabled...");
            mStopButton.setEnabled(false);
        }
    }

    @Override
    protected void onDestroy() {
        stopLogging();
        super.onDestroy();        
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        menu.add(OPTIONS_GROUP_TEST, OPTIONS_MENU_SHOWLOCATIONS, 0,
                "Show Locations");
        return super.onCreateOptionsMenu(menu);
    }

    @Override
    public boolean onPrepareOptionsMenu(Menu menu) {
        menu.setGroupVisible(OPTIONS_GROUP_DEFAULT, true);
        menu.setGroupVisible(OPTIONS_GROUP_TEST, true);
        return super.onPrepareOptionsMenu(menu);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
        case OPTIONS_MENU_SHOWLOCATIONS:
            // Check for database and get lats and longs here
   
            if (loadDatabase()) {
                Intent intent = new Intent(this, LocationsViewer.class);
                LinkedList<Location> locations;

                locations = mLocDatabase.getLocations();
                int numLocations = mLocDatabase.getNumberOfLocations();
                double[] lats = new double[numLocations];
                double[] longs = new double[numLocations];

                for (int i = 0; i < numLocations; i++) {
                    lats[i] = locations.get(i).getLatitude();
                    longs[i] = locations.get(i).getLongitude();
                }
                intent.putExtra("latitudes", lats);
                intent.putExtra("longitudes", longs);
                    
                startActivity(intent);
            } else {
                Toast.makeText(getApplicationContext(), "Locations N/A", Toast.LENGTH_LONG).show();
            }
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (mLoggingActive && keyCode == KeyEvent.KEYCODE_BACK) {
            AlertDialog.Builder builder = new AlertDialog.Builder(this);

            builder.setMessage(
                    String.format(
                            "Logging is currently active!\n"
                                    + "Please press CANCEL, then the HOME button if you would "
                                    + "like the logger to run in the background. Otherwise, press "
                                    + "EXIT to stop logging and close the application."));
            builder.setCancelable(true);
            builder.setTitle("!EXIT WARNING!");
            builder.setPositiveButton("Save/Exit", new 
                    DialogInterface.OnClickListener() {
                public void onClick(DialogInterface dialog, int id) {
                    stopLogging(); // IMPLEMENT ADDITIONAL EXIT CODE IF NEEDED!
                    ContextLogger.this.finish();
                }
            });
            builder.setNeutralButton("EXIT",
                    new DialogInterface.OnClickListener() {
                public void onClick(DialogInterface dialog, int which) {
                    ContextLogger.this.finish();
                }
            });
            builder.setNegativeButton("CANCEL",
                    new DialogInterface.OnClickListener() {
                public void onClick(DialogInterface dialog, int which) {
                    dialog.cancel();
                }
            });
            builder.show();
            return false;
        } else {
            return super.onKeyDown(keyCode, event);
        }
    }

    public void startLogging() {
        mStartButton.setEnabled(false);
        mStopButton.setEnabled(true);
        mMainTextView.setText("Logging active!");
        // We want the alarm to go off immediately
        long firstTime = SystemClock.elapsedRealtime();

        Toast.makeText(this, R.string.logging_started, Toast.LENGTH_SHORT).show();
        // Schedule the alarm with the faster logging interval (because the screen is on)
        AlarmManager am = (AlarmManager) getSystemService(ALARM_SERVICE);

        am.setRepeating(AlarmManager.ELAPSED_REALTIME_WAKEUP, firstTime,
                mLoggingInterval2, mAlarmSender);
        
        // // Initialize screen broadcast receiver
        // IntentFilter filter = new IntentFilter(Intent.ACTION_SCREEN_ON);
        // filter.addAction(Intent.ACTION_SCREEN_OFF);
        // mScreenReceiver = new ScreenBroadcastReceiver();
        // registerReceiver(mScreenReceiver, filter);
        
        mLoggingActive = true;
    }

    public void stopLogging() {
        mStartButton.setEnabled(true);
        mStopButton.setEnabled(false);
        mMainTextView.setText("Logging disabled...");
        // unregisterReceiver(mScreenReceiver);
        // Cancel the alarm.
        AlarmManager am = (AlarmManager) getSystemService(ALARM_SERVICE);

        am.cancel(mAlarmSender);
        Toast.makeText(this, R.string.logging_stopped, Toast.LENGTH_SHORT).show();
        
        mLoggingActive = false;
    }

    private boolean loadDatabase() {
        try {
            if (Environment.getExternalStorageState().equals(
                    Environment.MEDIA_MOUNTED)) {
                String filePath = String.format("%s/ContextLogger/Locations.ser", 
                        Environment.getExternalStorageDirectory());

                if (new File(filePath).exists()) {
                    FileInputStream fis = new FileInputStream(filePath);
                    ObjectInputStream in = new ObjectInputStream(fis);

                    mLocDatabase = (LocationDatabase) in.readObject();
                    in.close();
                    return true;
                }
            }
            mLocDatabase = new LocationDatabase();
            return false;
        } catch (Exception e) {
            mLocDatabase = new LocationDatabase();
            return false;
        }
    }

    private boolean deleteDatabase() {
        try {
            if (Environment.getExternalStorageState().equals(
                    Environment.MEDIA_MOUNTED)) {
                String filePath = String.format("%s/ContextLogger", 
                        Environment.getExternalStorageDirectory());

                if (new File(filePath).exists()) {
                    mLocDatabase.deleteDatabase(filePath);
                    mLocDatabase.deleteExport(filePath);
                    return true;
                }
            }
            return false;
        } catch (Exception e) {
            return false;
        }
    }
}
