To build this project, the Android Developer Tools must be installed. It can be
added as a project to the Android-specific Eclipse.

Once installed on an Anddroid phone, the logger can be run by selecting
either none (just logs data), performance, or power. The user should then tap
the button which currently says "Off". A toast notification should pop up
stating it is now running. In order for the optimizations to have any effect, 
the smartphone being used must be "rooted". The Galaxy S3 we are returning
already has root access.

All of the code we wrote is in the cmu.waci.logger package. The other packages
include open source code from other projects.

The main relevant files are as follows:
	LoggerActivity.java: the main acitivity, sets up the window and buttons
	LoggerService.java: Android service which performs the logging. Logs are saved
											to /mnt/sdcard/Android/data/cmu.waci.logger/files and are 
											named according to when logging started
	InteractivityService.java: Service to detect the user touches. The performance
														 and power optimizations are also performed here
	MDP_3States.java: Class to manage the state transitions for markov
	