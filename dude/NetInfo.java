package csu.research.AURA;


import java.io.BufferedReader;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.lang.reflect.Method;
import java.util.Vector;
import android.location.LocationManager;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.net.wifi.WifiManager;
import android.telephony.TelephonyManager;
import android.util.Log;


public class NetInfo {
    // final private String NET_STAT_FILE = "/proc/net/netstat";
    final private String DEV_FILE = "/proc/self/net/dev";
    final private String WIFI_INTERFACE = "tiwlan";
    final private String CELL_INTERFACE = "rmnet";
    final private String ETH_INTERFACE = "eth";
    private WifiManager mWifiManager;
    private TelephonyManager mTelephonyManager;
    private ConnectivityManager mConnectivityManager;
    private static int mCellIn = 0;
    private static int mCellOut = 0;
    private static int mWifiIn = 0;
    private static int mWifiOut = 0;
    private static int mEthIn = 0;
    private static int mEthOut = 0;
    Method dataConnSwitchmethod;
    Class telephonyManagerClass;
    Object ITelephonyStub;
    Class ITelephonyClass;
    public NetInfo(WifiManager wifiManager, ConnectivityManager connectivityManager, 
            TelephonyManager telephonyManager) {
        this.mWifiManager = wifiManager;
        this.mConnectivityManager = connectivityManager;
        this.mTelephonyManager = telephonyManager;
    }
   
    public long getCellTraffic() {        
        FileReader fstream;

        try {
            fstream = new FileReader(DEV_FILE);
        } catch (FileNotFoundException e) {
            Log.e("MonNet", "Could not read " + DEV_FILE);
            return -1;
        }
        BufferedReader in = new BufferedReader(fstream, 500);
        String line;
        String[] segs;

        try {
            while ((line = in.readLine()) != null) {
                if (line.startsWith(CELL_INTERFACE)) {
                    segs = line.trim().split("[: ]+");
                    // cell in
                    mCellIn += Integer.parseInt(segs[1]);
                    // cell out
                    mCellOut += Integer.parseInt(segs[9]);
                }
            }
            return mCellIn + mCellOut;
        } catch (IOException e) {
            Log.e("MonNet", e.toString());
            return -1;
        }
    }

    public long getWifiTraffic() {      
        FileReader fstream;

        try {
            fstream = new FileReader(DEV_FILE);
        } catch (FileNotFoundException e) {
            Log.e("MonNet", "Could not read " + DEV_FILE);
            return -1;
        }
        BufferedReader in = new BufferedReader(fstream, 500);
        String line;
        String[] segs;

        try {
            while ((line = in.readLine()) != null) {
                if (line.startsWith(WIFI_INTERFACE)) {
                    segs = line.trim().split("[: ]+");
                    // wifi in
                    mWifiIn += Integer.parseInt(segs[1]);
                    // wifi out
                    mWifiOut += Integer.parseInt(segs[9]);
                }
            }
            return mWifiIn + mWifiOut;
        } catch (IOException e) {
            Log.e("MonNet", e.toString());
            return -1;
        }
    }

    public long getEthernetTraffic() {      
        FileReader fstream;

        try {
            fstream = new FileReader(DEV_FILE);
        } catch (FileNotFoundException e) {
            Log.e("MonNet", "Could not read " + DEV_FILE);
            return -1;
        }
        BufferedReader in = new BufferedReader(fstream, 500);
        String line;
        String[] segs;

        try {
            while ((line = in.readLine()) != null) {
                if (line.startsWith(ETH_INTERFACE)) {
                    segs = line.trim().split("[: ]+");
                    // ethernet in
                    mEthIn += Integer.parseInt(segs[1]);
                    // ethernet out
                    mEthOut += Integer.parseInt(segs[9]);
                }
            }
            return mEthIn + mEthOut;
        } catch (IOException e) {
            Log.e("MonNet", e.toString());
            return -1;
        }
    }

    public String getCallStatus() {
        if (mTelephonyManager.getCallState()
                == TelephonyManager.CALL_STATE_OFFHOOK) {
            return "off hook";
        } else if (mTelephonyManager.getCallState()
                == TelephonyManager.CALL_STATE_RINGING) {
            return "ringing";
        }
        return "idle";
    }

    public String getDataStatus() {
        if (mTelephonyManager.getDataState() == TelephonyManager.DATA_CONNECTED) {
            if (mTelephonyManager.getNetworkType()
                    == TelephonyManager.NETWORK_TYPE_UMTS) {
                return "3g connected";
            } else if (mTelephonyManager.getNetworkType()
                    == TelephonyManager.NETWORK_TYPE_CDMA
                            || mTelephonyManager.getNetworkType()
                                    == TelephonyManager.NETWORK_TYPE_EDGE) {
                return "edge connected";
            }
        }
        return "disconnected";
    }

    public void enableData() {
        if (mTelephonyManager.getDataState() == TelephonyManager.DATA_CONNECTED) {
            return;
        }
        try {
            telephonyManagerClass = Class.forName(
                    mTelephonyManager.getClass().getName());
            Method getITelephonyMethod = telephonyManagerClass.getDeclaredMethod(
                    "getITelephony");

            getITelephonyMethod.setAccessible(true);
            ITelephonyStub = getITelephonyMethod.invoke(mTelephonyManager);
            ITelephonyClass = Class.forName(ITelephonyStub.getClass().getName());
    
            dataConnSwitchmethod = ITelephonyClass.getDeclaredMethod(
                    "enableDataConnectivity");   
            dataConnSwitchmethod.setAccessible(true);
            dataConnSwitchmethod.invoke(ITelephonyStub); 
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public void disableData() {
        if (mTelephonyManager.getDataState() != TelephonyManager.DATA_CONNECTED) {
            return;
        } 
        try {
            telephonyManagerClass = Class.forName(
                    mTelephonyManager.getClass().getName());
            Method getITelephonyMethod = telephonyManagerClass.getDeclaredMethod(
                    "getITelephony");

            getITelephonyMethod.setAccessible(true);
            ITelephonyStub = getITelephonyMethod.invoke(mTelephonyManager);
            ITelephonyClass = Class.forName(ITelephonyStub.getClass().getName());
    
            dataConnSwitchmethod = ITelephonyClass.getDeclaredMethod(
                    "disableDataConnectivity");
            
            dataConnSwitchmethod.setAccessible(true);
            dataConnSwitchmethod.invoke(ITelephonyStub); 
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public boolean isDataConnected() {
        if (mTelephonyManager.getDataState() == TelephonyManager.DATA_CONNECTED) {
            return true;
        } 
        return false;
    }

    public void enableWifi() {
        try {
            mWifiManager.setWifiEnabled(true);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public void disableWifi() {
        mWifiManager.setWifiEnabled(false);
    }

    public boolean isWifiConnected() {
        NetworkInfo.State state = getWifiState();

        if (state != null && state == NetworkInfo.State.CONNECTED) {
            return true;
        }
        return false;
    }

    public boolean isWifiEnabled() {
        return mWifiManager.isWifiEnabled();
    }

    public boolean isWifiDisconnected() {
        NetworkInfo.State state = getWifiState();

        if (state != null && state == NetworkInfo.State.DISCONNECTED) {
            return true;
        }
        return false;
    }

    public NetworkInfo.State getWifiState() {
        try {
            NetworkInfo state = mConnectivityManager.getNetworkInfo(
                    ConnectivityManager.TYPE_WIFI); 

            // WifiInfo.getDetailedStateOf(wifiManager.getConnectionInfo().getSupplicantState());
            return state.getState();
        } catch (Exception e) {
            return null;
        }
    }

    public int getWifiSignalLevel() {
        return mWifiManager.getConnectionInfo().getRssi();
    }
}
