package cmu.waci.logger;

public class PowerModels {

	// private static final float CPU_POWER_MODEL_M = 0.000002F; // mW/%/kHz
	// private static final float CPU_POWER_MODEL_B = 3.0511F; // mW/%
	//
	// private static final float COST_MODEL_COEFFICIENT = 250.84F; //mW/kHz
	// private static final float COST_MODEL_EXPONENT = -0.447F;
	//
	// private static final float SCREEN_POWER_MODEL_M = 5.968F; // mW/%
	// NEW VALUES FOR NEXUS ONE
	private static final float CPU_POWER_MODEL_M = 0.0021F; // mW/%/kHz
	private static final float CPU_POWER_MODEL_B = -0.2834F; // mW/%

	private static final float COST_MODEL_COEFFICIENT = 0.0448F; // mW/kHz
	private static final float COST_MODEL_EXPONENT = 0.4482F;

	private static final float SCREEN_POWER_MODEL_M = 3.3296F; // mW/%

	public static float getMWCPUPower(int freqKHz, int cpuUtilPct) {
		return ((CPU_POWER_MODEL_M * freqKHz) + CPU_POWER_MODEL_B) * cpuUtilPct;
	}

	public static float getMWFrequencySwitchPower(int freqKHz) {
		return (float) (COST_MODEL_COEFFICIENT * Math.pow(freqKHz,
				COST_MODEL_EXPONENT));
	}

	public static float getMWCPUPowerSavings(int fromFreqKHz, int toFreqKHz,
			int cpuUtilPct) {
		return (getMWCPUPower(fromFreqKHz, cpuUtilPct)
				- getMWFrequencySwitchPower(fromFreqKHz) 
				- getMWCPUPower(toFreqKHz, cpuUtilPct));
	}

	public static float getPercentCPUPowerSavings(int fromFreqKHz,
			int toFreqKHz, int cpuUtilPct) {
		float fromPowerMinusCost = getMWCPUPower(fromFreqKHz, cpuUtilPct)
				- getMWFrequencySwitchPower(fromFreqKHz);
		float toPower = getMWCPUPower(toFreqKHz, cpuUtilPct);

		if (fromPowerMinusCost > 0F) {
			return ((fromPowerMinusCost - toPower) * 100) / fromPowerMinusCost;
		}
		return 0F;
	}

	public static float getMJCPUEnergySavings(int fromFreqKHz, int toFreqKHz,
			int cpuUtilPct, float deltaTimeMsec) {
		return getMWCPUPowerSavings(fromFreqKHz, toFreqKHz, cpuUtilPct)
				* deltaTimeMsec / 1000F;
	}

	/* Screen Stuff */
	public static float getMWScreenPower(int brightnessPct) {
		return SCREEN_POWER_MODEL_M * brightnessPct;
	}

	public static float getMWScreenPowerSavings(int fromBrightnessPct,
			int toBrightnessPct) {
		return getMWScreenPower(fromBrightnessPct)
				- getMWScreenPower(toBrightnessPct);
	}

	public static float getMWScreenPowerSavings(float fromBrightnessPct,
			float toBrightnessPct) {
		return getMWScreenPowerSavings((int) (100 * fromBrightnessPct),
				(int) (100 * toBrightnessPct));
	}

	public static float getPercentScreenPowerSavings(
			float fromBrightnessFraction, float toBrightnessFraction) {
		float fromPower = getMWScreenPower((int) (100 * fromBrightnessFraction));
		float toPower = getMWScreenPower((int) (100 * toBrightnessFraction));

		if (fromPower > 0F) {
			return ((fromPower - toPower) * 100) / fromPower;
		}
		return 0F;
	}

	public static float getMJScreenEnergySavings(int fromBrightnessPct,
			int toBrightnessPct, float deltaTimeMsec) {
		return getMWScreenPowerSavings(fromBrightnessPct, toBrightnessPct)
				* deltaTimeMsec / 1000F;
	}
}
