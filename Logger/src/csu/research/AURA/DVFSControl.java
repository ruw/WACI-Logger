package csu.research.AURA;
//Author: Brad Kyoshi Donohoo
import java.io.BufferedReader;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import android.util.Log;

public class DVFSControl {
	private final String FREQ_MODES_FILE = "/sys/devices/system/cpu/cpu0/cpufreq/scaling_available_frequencies";
	private final String SCALE_FREQ_FILE = "/sys/devices/system/cpu/cpu0/cpufreq/scaling_setspeed";
	private final String MAX_FREQ_FILE = "/sys/devices/system/cpu/cpu0/cpufreq/cpuinfo_max_freq";
	private final String MIN_FREQ_FILE = "/sys/devices/system/cpu/cpu0/cpufreq/cpuinfo_min_freq";
	private final String SET_MAX_FREQ_FILE = "/sys/devices/system/cpu/cpu0/cpufreq/scaling_max_freq";
	private final String SET_MIN_FREQ_FILE = "/sys/devices/system/cpu/cpu0/cpufreq/scaling_min_freq";
	private final String GOV_FILE = "/sys/devices/system/cpu/cpu0/cpufreq/scaling_governor";
	private final String CUR_FREQ_FILE = "/sys/devices/system/cpu/cpu0/cpufreq/scaling_cur_freq";
	private final String AVA_GOV_FILE = "/sys/devices/system/cpu/cpu0/cpufreq/scaling_available_governors";
	// private final String[] SET_NOMINAL_FREQ_CMD =
	// {"su","-c","echo \"528000\" > /sys/devices/system/cpu/cpu0/cpufreq/scaling_setspeed"};
	// private final String[] SET_LOWEST_FREQ_CMD =
	// {"su","-c","echo \"128000\" > /sys/devices/system/cpu/cpu0/cpufreq/scaling_setspeed"};
	// private final String[] SET_LOWNOM_FREQ_CMD =
	// {"su","-c","echo \"245760\" > /sys/devices/system/cpu/cpu0/cpufreq/scaling_setspeed"};

	// NEW FREQUENCIES FOR NEXUS ONE
	private final String[] SET_NOMINAL_FREQ_CMD = { "su", "-c",
			"echo \"729600\" > /sys/devices/system/cpu/cpu0/cpufreq/scaling_setspeed" };
	private final String[] SET_LOWEST_FREQ_CMD = { "su", "-c",
			"echo \"245000\" > /sys/devices/system/cpu/cpu0/cpufreq/scaling_setspeed" };
	private final String[] SET_LOWNOM_FREQ_CMD = { "su", "-c",
			"echo \"537600\" > /sys/devices/system/cpu/cpu0/cpufreq/scaling_setspeed" };
	private FrequencyGovernors governor = FrequencyGovernors.Unknown;
	private boolean errorStatus = true;
	private String error = "UNKNOWN";
	private ArrayList<Integer> freqModes;
	private int minFreq = 0;
	private int maxFreq = 0;

	public DVFSControl() {
		freqModes = new ArrayList<Integer>();
		try {
			initializeMonitor();
		} catch (FileNotFoundException e) {

			error = "Could not find/access frequency modes file!";
			e.printStackTrace();
		} catch (IOException e) {
			error = "Could not read frequency modes file!";
			e.printStackTrace();
		} catch (NumberFormatException e) {
			error = "Error    parsing    frequency    modes    file    (Number    format exception)!";
			e.printStackTrace();
		}
	}

	/**
	 * Called at instantiation to find all available frequency levels, min/max
	 * supported frequency levels, and available scaling govenors
	 * 
	 * @throws IOException
	 */
	private void initializeMonitor() throws IOException {
		boolean freqGovStat;
		readCPUMinMaxFrequencies();
		String temp = new BufferedReader(new FileReader(FREQ_MODES_FILE), 1000)
				.readLine();
		String[] vals = temp.split(" ");
		int iTemp;
		for (int i = 0; i < vals.length; i++) {
			iTemp = Integer.parseInt(vals[i]);
			if (iTemp >= minFreq && iTemp <= maxFreq) {
				freqModes.add(iTemp);
			}
		}
		errorStatus = false;
		error = "NO ERROR";
		
		System.out.println("Init DVFS STuff here!");
		freqGovStat = setFrequencyGovernor(FrequencyGovernors.Userspace);
		System.out.println("Stat: "+freqGovStat);
		
		if (getCurrentGovernor() == FrequencyGovernors.Userspace) {
			setMinMaxScaleFrequencies();
		}
	}

	/**
	 * Instantiation error description
	 * 
	 * @return A description of the error that occurred at instantiation
	 */
	public String getError() {
		return error;
	}

	/**
	 * Instantiation error status
	 * 
	 * @return true if an error has occurred<br>
	 *         false if an error has not occurred
	 */
	public boolean hasErrorOccurred() {
		return errorStatus;
	}

	/**
	 * List of supported DVFS levels
	 * 
	 * @return ArrayList of available frequency levels (voltage is not
	 *         explicitly controlled)
	 */
	public ArrayList<Integer> getFrequencyScaleModes() {
		return freqModes;
	}

	/**
	 * Number of supported DVFS levels
	 * 
	 * @return The number of supported DVFS levels (voltage is not explicitly
	 *         controlled)
	 */
	public int getNumberOfFrequencyScaleModes() {
		return freqModes.size();
	}

	/**
	 * Changes the DVFS level by setting a desired frequency set-point (voltage
	 * is not explicitly controlled)<br>
	 * This is only supported if the frequency governor is "userspace"<br>
	 * <br>
	 * NOTE: This will return false even if the frequency level was changed, if
	 * the input frequency was rounded due to incorrect DVFS level support
	 * 
	 * @param freqkHz
	 *            Integer in units of kHz to set the desired frequency level.
	 *            Value will be rounded to nearest DVFS level
	 * @return true if frequency level was successfully changed<br>
	 *         false if frequency level was not successfully changed
	 */
	public boolean setCPUFrequency(int freqkHz) {
		try {
			if (getCurrentGovernor() == FrequencyGovernors.Userspace) {
				String[] cmdStr = {
						"su",
						"-c",
						String.format("echo \"%d\" > %s", freqkHz,
								SCALE_FREQ_FILE) };
				Runtime.getRuntime().exec(cmdStr);
				return getCPUFrequency() == freqkHz;
			}
			return false;
		} catch (IOException e) {
			Log.d("SETCPUFREQ:", e.getMessage());
			error = "Set frequency IO exception!";

			e.printStackTrace();
			return false;
		} catch (Exception e) {
			Log.d("SETCPUFREQ*:", e.getMessage());
			error = e.getMessage();
			e.printStackTrace();
			return false;
		}
	}

	public boolean setNominalCPUFrequency() {
		try {
			Runtime.getRuntime().exec(SET_NOMINAL_FREQ_CMD);
			return true;
		} catch (IOException e) {
			Log.d("SETCPUFREQ:", e.getMessage());
			error = "Set frequency IO exception!";
			e.printStackTrace();
			return false;
		} catch (Exception e) {
			Log.d("SETCPUFREQ*:", e.getMessage());
			error = e.getMessage();
			e.printStackTrace();
			return false;
		}
	}

	public boolean setLowestCPUFrequency() {
		try {
			Runtime.getRuntime().exec(SET_LOWEST_FREQ_CMD);
			return true;
		} catch (IOException e) {
			Log.d("SETCPUFREQ:", e.getMessage());
			error = "Set frequency IO exception!";
			e.printStackTrace();
			return false;
		} catch (Exception e) {
			Log.d("SETCPUFREQ*:", e.getMessage());
			error = e.getMessage();
			e.printStackTrace();
			return false;
		}
	}

	public boolean setLowNominalCPUFrequency() {
		try {
			Runtime.getRuntime().exec(SET_LOWNOM_FREQ_CMD);

			return true;
		} catch (IOException e) {
			Log.d("SETCPUFREQ:", e.getMessage());
			error = "Set frequency IO exception!";
			e.printStackTrace();
			return false;
		} catch (Exception e) {
			Log.d("SETCPUFREQ*:", e.getMessage());
			error = e.getMessage();
			e.printStackTrace();
			return false;
		}
	}

	/**
	 * Minimum CPU DVFS level
	 * 
	 * @return The minimum supported DVFS level
	 */
	public int getMinCPUFrequency() {
		return minFreq;
	}

	/**
	 * Maximum CPU DVFS level
	 * 
	 * @return The maximum supported DVFS level
	 */
	public int getMaxCPUFrequency() {
		return maxFreq;
	}

	/**
	 * Median CPU DVFS level
	 * 
	 * @return The median supported DVFS level
	 */
	public int getMedianCPUFrequency() {
		int median = minFreq;
		if (freqModes.size() > 2) {
			median = freqModes.get(freqModes.size() / 2);
		}
		return median;
	}

	/**
	 * The current DVFS level
	 * 
	 * @return The current DVFS level of the processor if parsing was
	 *         successful; otherwise, will return -1
	 */
	public int getCPUFrequency() {
		try {
			byte[] b = new byte[1024];

			Runtime.getRuntime().exec(String.format("cat %s", CUR_FREQ_FILE))
					.getInputStream().read(b);
			String freq = new String(b).replace("\n", "").trim();
			if (freq.length() > 0) {
				return Integer.parseInt(freq);
			}
			return -1;
		} catch (FileNotFoundException e) {
			error = "Error reading cpu frequency file!";
			e.printStackTrace();
			return -1;
		} catch (IOException e) {
			error = "Error reading cpu frequency file!";
			e.printStackTrace();
			return -1;
		}
	}

	public boolean stepDownFrequency() {
		System.out.println("Getting Curr Freq");
		System.out.println("Curr Freq: "+getCPUFrequency());
		int idx = indexOfFrequency(getCPUFrequency());
		System.out.println("Index of curr: "+idx);
		if (getCPUFrequency() > minFreq && idx > 0) {
			System.out.println("Step Down: "+freqModes.get(idx - 1));
			return setCPUFrequency(freqModes.get(idx - 1));
		}
		return false;
	}

	public boolean stepUpFrequency() {
		int idx = indexOfFrequency(getCPUFrequency());
		if (getCPUFrequency() < maxFreq && idx < freqModes.size() - 1) {
			return setCPUFrequency(freqModes.get(idx + 1));
		}
		return false;
	}

	public int indexOfFrequency(int frequency) {
		if (freqModes.contains(frequency)) {
			return freqModes.indexOf(frequency);
		}
		return -1;
	}

	/**
	 * Called at instantiation to parse/set CPU min/max supported DVFS levels
	 * (frequencies)
	 */
	private void readCPUMinMaxFrequencies() {
		try {
			// Read Max Frequency
			String result = null;
			String cmdStr = String.format("cat  %s", MAX_FREQ_FILE);

			Process process = Runtime.getRuntime().exec(cmdStr);
			InputStream in = process.getInputStream();
			byte[] read = new byte[1024];
			if (in.read(read) > 0) {
				result = new String(read);
			} else {
				error = "Error  reading  max  frequency!";
			}
			in.close();
			maxFreq = Integer.parseInt(result.replace("\n", "").trim()); // Read
																			// Min
																			// Frequency
			cmdStr = String.format("cat %s", MIN_FREQ_FILE);
			process = Runtime.getRuntime().exec(cmdStr);
			in = process.getInputStream();
			read = new byte[1024];
			if (in.read(read) > 0) {
				result = new String(read);
			} else {
				error = "Error  reading  min  frequency!";
			}
			in.close();
			minFreq = Integer.parseInt(result.replace("\n", "").trim());
		} catch (IOException e) {
			error = "Error occurred reading min/max frequencies!";
			e.printStackTrace();
		} catch (NumberFormatException e) {
			error = "Error occurred parsing min/max frequencies!";
			e.printStackTrace();
		}
	}

	/**
	 * Used internally to set min/max scaling frequencies for the current
	 * governor (only useful for OnDemand and UserSpace governors)
	 */
	private void setMinMaxScaleFrequencies() {
		String[] maxStr = { "su", "-c",
				String.format("echo \"%d\" > %s", maxFreq, SET_MAX_FREQ_FILE) };
		String[] minStr = { "su", "-c",
				String.format("echo \"%d\" > %s", minFreq, SET_MIN_FREQ_FILE) };
		try {
			Runtime.getRuntime().exec(maxStr);
			Runtime.getRuntime().exec(minStr);
		} catch (IOException e) {
			e.printStackTrace();
			error = "Error  setting  scaling  min/max  frequencies!";
		}
	}

	/**
	 * Change the current frequency governor
	 * 
	 * @param gov
	 *            The desired frequency governor
	 * @return true if governor was successfully changed<br>
	 *         false if governor was not changed
	 */
	public boolean setFrequencyGovernor(FrequencyGovernors gov) {
		try {
			byte[] b = new byte[1024];
			String[] str = {
					"su",
					"-c",
					String.format("echo \"%s\" > %s", gov.toString()
							.toLowerCase(), GOV_FILE) };
			System.out.println(str[2]);
			System.out.println(getCurrentGovernor().toString());
			
			Runtime.getRuntime().exec(str).getErrorStream().read(b);
			if (getCurrentGovernor() == gov) {
				return true;
			} else if (new String(b).toLowerCase()
					.contains("permission denied")) {
				error = "Error     settings     frequency     governor     due     to permissions!";
			}
			return false;
		} catch (IOException e) {
			error = "Error reading current frequency governor setting!";
			e.printStackTrace();
			return false;
		} catch (Exception e) {
			error = e.getMessage();
			e.printStackTrace();
			return false;
		}
	}

	/**
	 * List of available governors as String[]
	 * 
	 * @return An array of available governors
	 */
	public String getAvailableGovernors() {
		try {
			byte[] b = new byte[1024];
			InputStream in = Runtime.getRuntime()
					.exec(String.format("cat %s", AVA_GOV_FILE))
					.getInputStream();
			if (in.read(b) > 0) {
				String result = new String(b);
				result = result.replace("\n", "").trim();
				in.close();
				return result;
			}
			in.close();
			return "";

		} catch (FileNotFoundException e) {
			e.printStackTrace();
			error = "Error reading available governors!";
			return "";
		} catch (IOException e) {
			e.printStackTrace();
			error = "Error reading available governors!";
			return "";
		}
	}

	/**
	 * Current frequency governor
	 * 
	 * @return The current frequency governor
	 */
	public FrequencyGovernors getCurrentGovernor() {
		try {
			String result = null;
			String cmdStr = String.format("cat   %s",
					GOV_FILE.replace(".txt", ""));
			InputStream in = Runtime.getRuntime().exec(cmdStr).getInputStream();
			byte[] read = new byte[1024];
			if (in.read(read) > 0) {
				result = new String(read);
				result = result.replace("\n", "").trim().toLowerCase();
				if (result.equals("ondemand")) {
					governor = FrequencyGovernors.OnDemand;
				} else if (result.equals("conservative")) {
					governor = FrequencyGovernors.Conservative;
				} else if (result.equals("powersave")) {
					governor = FrequencyGovernors.Powersave;
				} else if (result.equals("userspace")) {
					governor = FrequencyGovernors.Userspace;
				} else if (result.equals("performance")) {
					governor = FrequencyGovernors.Performance;
				} else if (result.equals("interactive")) {
					governor = FrequencyGovernors.Interactive;
				} else {
					governor = FrequencyGovernors.Unknown;
					throw new Exception("Unknown  frequency  governor!");
				}

			} else {
				error = "Error      reading      current      frequency      governor  setting!";
			}
			in.close();
			return governor;
		} catch (IOException e) {
			error = "Error reading current frequency governor setting!";
			e.printStackTrace();
			return governor;
		} catch (Exception e) {
			error = e.getMessage();
			e.printStackTrace();
			return governor;
		}
	}
}
