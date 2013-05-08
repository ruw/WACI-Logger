package csu.research.AURA;
//Author: Brad Kyoshi Donohoos
import java.io.BufferedReader;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;


public class CPUInfo {
    private static int mNormalUserProcessTime = -1;
    private static int mNicedUserProcessTime = -1;
    private static int mSystemProcessTime = -1;
    private static int mIdleProcessTime = -1;
    private static int mIoWaitTime = -1;
    private static int mIrqTime = -1;
    private static int mSoftIRQTime = -1;
    private static int mStealTime = -1;
    private static int mGuestTime = -1;
    private static int mIdlePct = 0;
    private static int mSystemPct = 0;
    private static int mUserPct = 0;
    private static int mIrqPct = 0;
    private static int mIoWaitPct = 0;
    private static int mTotalPct = 0;
    private static int mContextSwitches = -1;
    private static int mProcessesCreated = -1;
    private static int mProcessesRunning = -1;
    private static int mProcessesBlocked = -1;
    
    private static String readCPUInfo() {
        FileReader fstream;

        try {
            fstream = new FileReader("/proc/stat");
        } catch (FileNotFoundException e) {
            e.printStackTrace();
            return "error";
        }
        BufferedReader in = new BufferedReader(fstream, 500);
        String result = "";
        String line;

        try {
            while ((line = in.readLine()) != null) {
                result = String.format("%s%s\n", result, line);
            }
            in.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
        return result;
    }

    public static int[] getCPUStats() {
        int[] stats = null;

        try {
            String info = readCPUInfo();

            if (info.length() > 0) {
                String[] lines = info.split("\n");
                String line;
                String[] buf;
                int temp, temp1, temp2, temp3, temp4, temp5, temp6;
                int total, totalDiff;
                int deltaContextSwitches = 0;

                for (int i = 0; i < lines.length; i++) {
                    line = lines[i];
                    if (line.startsWith("cpu ")) {
                        buf = line.replaceAll("cpu ", "").trim().split(" ");
                        if (buf.length >= 9) {
                            temp = mNormalUserProcessTime;
                            mNormalUserProcessTime = Integer.parseInt(buf[0]);
                            temp1 = mNicedUserProcessTime;
                            mNicedUserProcessTime = Integer.parseInt(buf[1]);
                            temp2 = mSystemProcessTime;
                            mSystemProcessTime = Integer.parseInt(buf[2]);
                            temp3 = mIdleProcessTime;
                            mIdleProcessTime = Integer.parseInt(buf[3]);
                            temp4 = mIoWaitTime;
                            mIoWaitTime = Integer.parseInt(buf[4]);
                            temp5 = mIrqTime + mSoftIRQTime;
                            mIrqTime = Integer.parseInt(buf[5]);
                            mSoftIRQTime = Integer.parseInt(buf[6]);
                            temp6 = mStealTime + mGuestTime;
                            mStealTime = Integer.parseInt(buf[7]);
                            mGuestTime = Integer.parseInt(buf[8]);
                            
                            total = mNormalUserProcessTime
                                    + mNicedUserProcessTime + mSystemProcessTime
                                    + mIdleProcessTime + mIoWaitTime + mIrqTime
                                    + mSoftIRQTime + mStealTime + mGuestTime;
                            totalDiff = total
                                    - (temp + temp1 + temp2 + temp3 + temp4 
                                    + temp5 + temp6);
                            
                            /*
                            total = mNormalUserProcessTime
                                    + mNicedUserProcessTime + mSystemProcessTime
                                    + mIdleProcessTime + mIoWaitTime;
                            totalDiff = total
                                    - (temp + temp1 + temp2 + temp3 + temp4);
                            */
                            if (totalDiff!=0) {
                            	mIdlePct = ((mIdleProcessTime - temp3) * 100)
                            		/ totalDiff;
                            	mSystemPct = ((mSystemProcessTime - temp2) * 100)
                                        / totalDiff;
                                mUserPct = (((mNormalUserProcessTime
                                        + mNicedUserProcessTime)
                                                - (temp + temp1))
                                                        * 100)
                                                                / totalDiff;
                                mIrqPct = (((mIrqTime + mSoftIRQTime) - temp5) * 100)
                                        / totalDiff;
                                mIoWaitPct = ((mIoWaitTime - temp4) * 100)
                                        / totalDiff;
                                mTotalPct = (((mNormalUserProcessTime
                                        + mNicedUserProcessTime)
                                                - (temp + temp1))
                                                        + (mSystemProcessTime
                                                                - temp2)
                                                                        * 100)
                                                                                / totalDiff;
                            } else {
                            	System.out.println("ERR: mIdlePct / 0");
                            }
                            
                        }
                    } else if (line.startsWith("ctxt")) {
                        buf = line.replaceAll("ctxt ", "").trim().split(" ");
                        if (buf.length == 1) {
                            temp = mContextSwitches;
                            mContextSwitches = Integer.parseInt(buf[0]);
                            deltaContextSwitches = mContextSwitches - temp;
                        }
                    } else if (line.startsWith("processes")) {
                        buf = line.replaceAll("processes ", "").trim().split(" ");
                        if (buf.length == 1) {
                            mProcessesCreated = Integer.parseInt(buf[0]);
                        }
                    } else if (line.startsWith("procs_running")) {
                        buf = line.replaceAll("procs_running ", "").trim().split(
                                " ");
                        if (buf.length == 1) {
                            mProcessesRunning = Integer.parseInt(buf[0]);
                        }
                    } else if (line.startsWith("procs_blocked")) {
                        buf = line.replaceAll("procs_blocked ", "").trim().split(
                                " ");
                        if (buf.length == 1) {
                            mProcessesBlocked = Integer.parseInt(buf[0]);
                        }
                    }
                }
                stats = new int[] {
                    mIdlePct, mSystemPct, mUserPct, mIrqPct, 
                    mIoWaitPct, mContextSwitches, deltaContextSwitches,
                    mProcessesCreated, mProcessesRunning, mProcessesBlocked };
            }
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
        return stats;
    }

    public static int getCPUIdlePct() {
        int[] stats = getCPUStats();

        return stats[0];
    }

    public static int getCPUUtilizationPct() {
        return 100 - getCPUIdlePct();
    }
    
    public static int getIdlePct() {
        return mIdlePct;
    }

    public static int getSystemPct() {
        return mSystemPct;
    }

    public static int getUserPct() {
        return mUserPct;
    }

    public static int getIrqPct() {
        return mIrqPct; 
    }

    public static int getIoWaitPct() {
        return mIoWaitPct; 
    }

    public static int getTotalPct() {
        // total = user + system + idle + io_wait
        return mTotalPct;
    }

    public static int getContextSwitches() {
        return mContextSwitches;
    }

    public static int getProcessesCreated() {
        return mProcessesCreated;
    }

    public static int getProcessesRunning() {
        return mProcessesRunning;
    }

    public static int getProcessesBlocked() {
        return mProcessesBlocked;
    }
}
