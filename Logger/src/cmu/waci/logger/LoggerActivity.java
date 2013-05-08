package cmu.waci.logger;

import java.util.Arrays;

import cmu.waci.logger.InteractivityService.InteractBinder;
import cmu.waci.logger.LoggerService.LocalBinder;

import android.os.Binder;
import android.os.Bundle;
import android.os.IBinder;
import android.app.Activity;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.widget.Button;
import android.widget.CompoundButton;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.Toast;
import android.widget.ToggleButton;

public class LoggerActivity extends Activity {
	private static final String TAG = "LoggerActivity";
	
	private LoggerService mLogger;
	private InteractivityService mInteract;
	private boolean mBound, mBound2;
	private Button startButton;
	private Button stopButton;
	private RadioGroup radioOptGroup;
	private ToggleButton toggleButton;
	
	private boolean started = false;

	
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_logger);
        
        setupButtons();
 
               
        System.out.println("Start Activity!");
        Toast.makeText(this, "Start Activity! ",1).show();
 //       System.out.println(Arrays.toString(CPUInfo.getCPUStats()));
        
  //      mLogger.bindService(new Intent(this, 
  //              LoggerService.class), mConnection, Context.BIND_AUTO_CREATE);

    }
    
    private void setupButtons() {
    	radioOptGroup = (RadioGroup)this.findViewById(R.id.optState);
        //startButton = (Button)this.findViewById(R.id.Start);
        //stopButton = (Button)this.findViewById(R.id.Stop);
        toggleButton = (ToggleButton)this.findViewById(R.id.toggleButton);
        
        toggleButton.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
        	public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                if (isChecked) {
                	int selectedId = radioOptGroup.getCheckedRadioButtonId();
                    // The toggle is enabled
                	System.out.println("Pressed On");
                	if (!started)
                		startLogging();
                	
                  	if(selectedId == R.id.optPerf) {  
                		System.out.println("Selected Performance");
                		InteractivityService.startOptPerf();
                	}
                	else if(selectedId == R.id.optPow) {
                		System.out.println("Selected Power");
                		InteractivityService.startOptPow();
                	} else if(selectedId == R.id.optNone) {
                		System.out.println("Selected None");
                		InteractivityService.stopOptPow();
                		InteractivityService.stopOptPerf();
                	}
                } else {
                    // The toggle is disabled
                	System.out.println("Pressed Off");
                	InteractivityService.stopOptPerf();
                	InteractivityService.stopOptPow();
                	stopLogging();
                }
            }
        });
        
        
        radioOptGroup.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
            	//TODO
            	int selectedId = radioOptGroup.getCheckedRadioButtonId();
            	if(selectedId == R.id.optPerf) {  
            		System.out.println("Selected Performance");
            		InteractivityService.startOptPerf();
            	} else if(selectedId == R.id.optPow) {
            		System.out.println("Selected Power");
            		InteractivityService.startOptPow();
            	} else if(selectedId == R.id.optNone) {
            		System.out.println("Selected None");
            		InteractivityService.stopOptPow();
            		InteractivityService.stopOptPerf();
            	}
            	
            }
        });
        
        /*
        startButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
            	//TODO
            	int selectedId = radioOptGroup.getCheckedRadioButtonId();
            	if (!started)
            		startLogging();
            	if(selectedId == R.id.optPerf) {  	
            		InteractivityService.startOptPerf();
            		System.out.println("perf");
            	}
            	else if(selectedId == R.id.optPow) {
            		InteractivityService.startOptPow();
            		System.out.println("pow");
            	}
            	
            }
        });
        stopButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
            	//TODO
            	InteractivityService.stopOptPerf();
            	InteractivityService.stopOptPow();
            	stopLogging();
            }
        }); 
        */     
    }
    
    /*
    public void onToggleClicked(View view) {
        // Is the toggle on?
        boolean on = ((ToggleButton) view).isChecked();
        
        if (on) {
            // Enable CPU scaling
        	Toast.makeText(this.getApplicationContext(), "Toggle TURN ON! ",1).show();
        	//mInteract.savePowerOn = true;
        } else {
            // Disable CPU scaling
        	Toast.makeText(this.getApplicationContext(), "Toggle TURN OFF! ",1).show();
        	//mInteract.savePowerOn = false;
        }
    }

*/

    private void startLogging() {
    	started = true;
    	Toast.makeText(this, "Start Logging! ",1).show();
        Intent intent = new Intent(this, LoggerService.class);
        Intent intent2 = new Intent(this,  InteractivityService.class);
        bindService(intent2, mConnection2, Context.BIND_AUTO_CREATE);
        bindService(intent, mConnection, Context.BIND_AUTO_CREATE);   
    }
    
    private void stopLogging() {
    	started = false;
    	Toast.makeText(this, "Stop Logging! ",1).show();
        // Unbind from the service
        if (mBound) {
            unbindService(mConnection);
            mBound = false;
        }
        
        if (mBound2) {
            unbindService(mConnection2);
            mBound2 = false;
        }   	
    }

    /*
    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        //getMenuInflater().inflate(R.menu.logger, menu);       
        MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.logger, menu);
        return true;
    }
    
    public boolean onOptionsItemSelected(MenuItem item) {
    	switch (item.getItemId()) 
    	{
    		case R.id.action_settings:
    			return true;
    		default:
    			return super.onOptionsItemSelected(item);
    	}
    }
    */
    
    @Override
    protected void onStart() {
        super.onStart();
        // Bind to LocalService

    }

    @Override
    protected void onStop() {
        super.onStop();
    }


    /** Defines callbacks for service binding, passed to bindService() */
    private ServiceConnection mConnection = new ServiceConnection() {

        @Override
        public void onServiceConnected(ComponentName className,
                IBinder service) {
            // We've bound to LocalService, cast the IBinder and get LocalService instance
            LocalBinder binder = (LocalBinder) service;
            mLogger = binder.getService();
            mBound = true;
        }

        @Override
        public void onServiceDisconnected(ComponentName arg0) {
            mBound = false;
        }
    };
    
    /** Defines callbacks for service binding, passed to bindService() */
    private ServiceConnection mConnection2 = new ServiceConnection() {

        @Override
        public void onServiceConnected(ComponentName className,
                IBinder service) {
            // We've bound to LocalService, cast the IBinder and get LocalService instance
            InteractBinder binder = (InteractBinder) service;
            mInteract = binder.getService();
            mBound2 = true;
        }

        @Override
        public void onServiceDisconnected(ComponentName arg0) {
            mBound2 = false;
        }
    };
    
    @Override
    public void onBackPressed() {
    }
}
