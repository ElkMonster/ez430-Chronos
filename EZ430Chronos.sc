/*

*/
EZ430Chronos {

	// byte codes for serial communication with chronos
	classvar eZ430StartAPMsg;
	classvar eZ430CmdSuccessMsg;
	classvar eZ430StopAPMsg;
	classvar eZ430ReqDataMsg;    

	classvar allEZ430; // Array holding all EZ430Chronos instances

	var <name; // The name of the EZ430Chronos instance (Chronos0, Chronos1, ...)
	var <portname; // The name of the port, since SerialPort doesn't store it
	var <port; // The serial port to read from
	var <apStarted; // True when access point has started

	var <x, <y, <z; // The (calibration-corrected) sensor values
	var <rawXYZ; // The raw, unchanged sensor values

	// Offset of data range found during calibration, added to raw data in order
	// to correct values. E.g., while slowly rotating, raw min and max could be
	// -30 and 70; offset is then (max - ((max - min) / 2)) = 20, so
	// corrected value = rawValue - 20.
	var calibOffset;
	// Factors that are used for calibrated value correction
	var calibPosCorrFac, calibNegCorrFac;
	var calibRawMin, calibRawMax; // Min and max raw values, used during calibration

	// An EZ430ChronosData object that is updated by this EZ430Chronos instance
	var <data;

	// Arrays that hold the callback functions to be executed each time data is
	// read from the watch
	var callbacks, dataCallbacks, onConnectCallbacks, onStopReadingCallbacks;

	var accDataQueryTask; // A thread that reads data from the chronos in a loop
	var accDataQueryStopReq; // True when stopReadingAccData() has been called
	var accDataSemaphore, portSemaphore; // 

	var <isCalibrated; // Tells if instance is calibrated
	var isCalibrating; // True during calibration
	var calibCallbackFunc; // Callback function used for calibration
	var vminTmp, vmaxTmp; // Helpers for calibration

	*initClass {
		allEZ430 = Array[];
		eZ430StartAPMsg = Int8Array[255, 7, 3];
		eZ430CmdSuccessMsg = [255, 6, 3];
		eZ430StopAPMsg = Int8Array[255, 9, 3];
		eZ430ReqDataMsg = Int8Array[255, 8, 7, 0, 0, 0, 0];
	}

	*getChronosByPortname { |portname|
		^allEZ430.detect { |chronos|
			chronos.portname == portname;
		};
	}

	*getSerialPortNameByPlatform {
		var pattern = Platform.case(
			\linux, { "^/dev/ttyACM" },
			\osx, { "^/dev/tty.usbmodem" },
			\windows, { "Not implemented" }
		);
		^SerialPort.devices.detect { |devName|
			var patternMatches = pattern.matchRegexp(devName);
			var portAvailable = true;
			allEZ430.isEmpty.not.if {
				portAvailable = this.getChronosByPortname(devName).isNil;
			};
			patternMatches and: portAvailable;
		};
	}

	// Creates a EZ430Chronos instance. If no port is specified, /dev/ttyACMn is
	// opened. A custom name for the instance can be specified, otherwise name
	// is set to "ChronosN". n/N in ttyACMn and ChronosN is a consecutive
	// number, beginning at 0 for the first chronos.
	*new { |portname, name|
		portname = portname ? this.getSerialPortNameByPlatform;
		name = name ? ("Chronos" ++ allEZ430.size);
		this.log("Creating new eZ430 Chronos (" ++ name ++ ") at " ++ portname);
		^super.newCopyArgs(name, portname).init;
	}

	// Initialises all instance members, attempts to load existing calibration
	// data, add this instance to the global instance array (allEZ430), and
	// opens the serial port specified by portname. If opening the port fails with an
	// exception, error messages are printed and the exception is re-thrown.
	init {
		try {
			port = SerialPort(
				portname,
				baudrate: 115200,
				databits: 8,
				stopbit: true,
				crtscts: false
			);
		} { |error|
			"\n".postln; // SerialPort doesn't break line in case of error msg
			this.log("Opening port " ++ portname ++ " failed. Check if port is valid.");
			this.log("Ports currently available:");
			SerialPort.devices.do { |devName|
				var takenBy = EZ430Chronos.getChronosByPortname(devName);
				var takenInfo = takenBy !? { " (taken by " ++ takenBy.name ++ ")" } ? "";
				this.log("    " ++ devName ++ takenInfo);
			};
			this.log("Exiting... (and re-throwing exception)");
			error.throw;
		};

		data = EZ430ChronosData.new;

		rawXYZ = [0, 0, 0];
		isCalibrating = false;
		isCalibrated = false;
		calibOffset = [0, 0, 0];
		calibPosCorrFac = [1, 1, 1];
		calibNegCorrFac = [1, 1, 1];
		calibRawMin = -127.dup(3);
		calibRawMax = 127.dup(3);
		this.loadCalibData;
		
		portSemaphore = Semaphore(1);
		accDataSemaphore = Semaphore(1);

		dataCallbacks = FunctionList.new;

		apStarted = false;

		allEZ430 = allEZ430.add(this);
	}

	// Converts an unsigned byte to a signed byte
    toByte { |ubyte|
        ^(ubyte > 127).if { -255 + ubyte } { ubyte };
    }

	// Requests data by sending the according message to the serial port and
	// reading the answer.
	// If new data is available, it is written to the according global
	// variables, and true is returned, otherwise false is returned.
	queryAccData {	
		var c, hasNewData, tmp;

		portSemaphore.wait;

		port.putAll(eZ430ReqDataMsg);

		// Wait a little to not poll serial port too often; acc data is
		// delivered at ~33 Hz, so actually 0.03 would also be an
		// appropriate value. However, we poll more often in order to prevent
		// delays created when callback functions take too long to execute
		// (an effect that occured during testing)
		0.01.wait;
		c = Array.fill(4, { port.read });

		if (c[3] == 1) {
			// Read new data
			accDataSemaphore.wait;
			rawXYZ[0] = this.toByte(port.read);
			rawXYZ[1] = this.toByte(port.read);
			rawXYZ[2] = this.toByte(port.read);
			# x, y, z = this.applyCalibCorrection(rawXYZ);

			accDataSemaphore.signal;
			hasNewData = true;
		} {
			// No new data, so read, but discard
			3.do { port.read };
			hasNewData = false;
		};

		portSemaphore.signal;
			
		^hasNewData;
	}

	// Corrects raw data for offset and range errors and returns the corrected
	// result.
	// Correction takes the following steps:
	// 1. Subtract calibration offset.
	//    A perfect chronos acceleration sensor in upright position would report
	//    values of (0, 0, 50) for x, y, and z, respectively. However, a real
	//    sensor has a certain offset, moving its minimal and maximal values by
	//    the same amount when rotating (e.g., it could report x=20 in upright
	//    position and -30 and 70 when rotated by +/-90 degrees). By subtracting
	//    the offset, the middle values are moved to 0.
	// 2. Correct values greater/smaller than +/-50. (Only if calibrated)
	//    If a value after calibration offset subtraction is greater or smaller
	//    than +/-50, respectively, the value needs to be corrected. This
	//    becomes clear when considering the case of the greatest measurable
	//    raw value, +/-127. In the above example constellation, this would
	//    result in a value range of -147 to 107 after offset subtraction for
	//    the x axis. In order to keep the -127 to 127 range, the values need to
	//    be scaled. The scale factors are calculated after the calibration has
	//    determined the centre offset. The scale factors are applied such that
	//    the following mapping is achieved:
	//        [(-127 - offset), -51] -> [-127, -51]
	//        [51, (127 - offset)] -> [51, 127]
	//    Values in the [-50, 50] range remains unchanged.
	applyCalibCorrection { |xyz|
		xyz = xyz - calibOffset;
		
		isCalibrated.if {
			xyz.do { |v, i|
				case
				    { v < -50 } { xyz[i] = -50 + ((v + 50) * calibNegCorrFac[i]) }
				    { v > 50 } { xyz[i] = 50 + ((v - 50) * calibPosCorrFac[i]) };
			};
		};
		^xyz.round(1);
	}

	// Starts a thread that constantly reads data from the chronos access point
	// and executes corresponding callbacks.
	startReadingAccData {
		accDataQueryStopReq = false;
		
		accDataQueryTask = Task({
			// Loop until connection established, i.e. until we receive useful
			// data, or until stopReadingAccData() has been called
			while {
				var connectionEst = this.queryAccData;
				connectionEst.if {
					onConnectCallbacks.do(_.value(this, name));
					connectionEst = true;
				};
				accDataQueryStopReq.not && connectionEst.not;
			};

			// Proceed with normal acc data acquisition, perform data updates
			// and execute callback functions each time the sensor provides new
			// values
			while { accDataQueryStopReq.not }
			{
				this.queryAccData.if {
					accDataSemaphore.wait;

					dataCallbacks !? {
						data.update([x, y, z], rawXYZ)
					};
					callbacks.do(_.value(x, y, z));
					dataCallbacks.do(_.value(data));

					accDataSemaphore.signal;
				};
			};
			
			accDataQueryStopReq = false;
			onStopReadingCallbacks.do(_.value(this, name));
			this.log("Data reading loop stopped");
		}).start;
	}

	// Requests that the thread that polls values from the chronos is stopped
	stopReadingAccData {
		accDataQueryStopReq = true;
	}

	// Adds the given callback function. It is called each time the chronos
	// provides new data. The function's arguments are x, y and z.
	addCallback { |callback_f|
		callbacks = callbacks.addFunc(callback_f);
	}

	// Removes the given callback function.
	removeCallback { |callback_f|
		callbacks = callbacks.removeFunc(callback_f);
	}

	// Adds the given callback function. It is called each time the chronos
	// provides new data. The argument passed to the function is this instance's
	// EZ430ChronosData object.
	addDataCallback { |callback_f|
		dataCallbacks = dataCallbacks.addFunc(callback_f);
	}

	// Removes the given callback function.
	removeDataCallback { |callback_f|
		dataCallbacks = dataCallbacks.removeFunc(callback_f);
	}

	// Adds the given callback function. It is called when the chronos has
	// established the connection with the access point. The arguments passed to
	// the function are EZ430Chronos instance and name (Chronos0, Chronos1, ...)
	addOnConnectCallback { |callback_f|
		onConnectCallbacks = onConnectCallbacks.addFunc(callback_f);
	}

	// Removes the given callback function.
	removeOnConnectCallback { |callback_f|
		onConnectCallbacks = onConnectCallbacks.removeFunc(callback_f);
	}

	// Adds the given callback function. It is called when the loop that polls
	// the chronos for new data has been left. The arguments passed to
	// the function are EZ430Chronos instance and name (Chronos0, Chronos1, ...)
	addOnStopReadingCallback { |callback_f|
		onStopReadingCallbacks = onStopReadingCallbacks.addFunc(callback_f);
	}

	// Removes the given callback function.
	removeOnStopReadingCallback { |callback_f|
		onStopReadingCallbacks = onStopReadingCallbacks.removeFunc(callback_f);
	}

	// Starts or stops calibration. When called the first time, calibration mode
	// is entered and raw data received from the chronos is recorded. When
	// called a second time, calibration mode is left and the recorded data is
	// used to calculate sensor offset and correction factors. (See
	// applyCalibCorrection() for further information.)
	// When in calibration mode, isCalibrating is set to true.
	calibrate {
		var vlast = [0, 0, 0].dup(5);

		isCalibrating.if { // Stop calibration
			isCalibrating = false;
			accDataSemaphore.wait;
			this.removeCallback(calibCallbackFunc);
			accDataSemaphore.signal;

			calibRawMin = vminTmp;
			calibRawMax = vmaxTmp;

			this.calculateCalibVars;
			isCalibrated = true;

			// Notify data to update to new calibration values
			data.calibChanged;

			// Write calibration results to disk for later reuse
			this.saveCalibData;
		} {
			// Start calibration
			isCalibrating = true;
			vminTmp = 127.dup(3);
			vmaxTmp = -127.dup(3);

			calibCallbackFunc = { |x, y, z|
				var mean = [0, 0, 0];
				vlast = vlast.shift(-1);
				vlast[vlast.size - 1] = [x, y, z];
				mean = vlast.sum / vlast.size;
				vminTmp = min(mean, vminTmp);
				vmaxTmp = max(mean, vmaxTmp);
			};

			this.addCallback(calibCallbackFunc);
		};
	}

	// Calculates sensor offset and correction factors from data recorded during
	// calibration.
	calculateCalibVars {
		3.do { |i|
			var d = calibRawMax[i] - calibRawMin[i];
			calibOffset[i] = (calibRawMin[i] + (d / 2)).round(1);

			// Calculate factor that fits any sum of raw value plus its
			// calibOffset greater than 50 to
			// 127 - 50 = 77
			calibPosCorrFac[i] = 77 / (77 - calibOffset[i]);
			calibNegCorrFac[i] = 77 / (77 + calibOffset[i]);
		};
		this.log("calib offsets: " ++ calibOffset);
		this.log("calib corr factors:\n" ++ calibPosCorrFac ++ "\n" ++ calibNegCorrFac);
	}

	// Saves the calibration data to a file in order to re-use it. If no path is
	// given, the file is created in the system's temporary directory. If not
	// specified, the file name is 'ez430-ChronosN.calib', where N is the
	// EZ430Chronos instance name.
	saveCalibData { |path, filename = ("ez430-" ++ name ++ ".calib")|
		var f = PathName(path ? PathName.tmp) +/+ filename;
		[calibRawMin, calibRawMax].writeArchive(f.fullPath);
		this.log("Saved calibration data to " ++ f.fullPath);
	}

	// Loads calibration data from a file and uses it to configure the
	// calibration of this instance. With no arguments given, the system's
	// temporary directory is checked for a file named 'ez430-ChronosN.calib',
	// where N is the EZ430Chronos instance name. If no file is found, this
	// method fails silently.
	loadCalibData { |path, filename = ("ez430-" ++ name ++ ".calib")|
		var f = PathName(path ? PathName.tmp) +/+ filename;

		if (File.exists(f.fullPath)) {
			this.log("Reading calibration data from " ++ f.fullPath);
			# calibRawMin, calibRawMax = Object.readArchive(f.fullPath);
			this.calculateCalibVars;
			isCalibrated = true;
			data.calibChanged(calibRawMin, calibRawMax);
		};
	}
	
	// Attempts to start the chronos access point. If successful, the apStarted
	// variable is set to true.
	startAP {
		var answer;

		apStarted.if { ^this };
		
		portSemaphore.wait;
		
		this.log("Trying to init Access Point... ", false);
		port.putAll(eZ430StartAPMsg);
		0.1.wait;

		answer = Array.fill( eZ430CmdSuccessMsg.size, { port.read } );
		portSemaphore.signal;

		if (answer == eZ430CmdSuccessMsg) {
			"Success".postln;
			apStarted = true;
		} {
			"Failure (answer was: ".post;
			(answer.size - 1).do { |n| (answer[n] ++ " ").post };
			(answer[answer.size - 1] ++ ")").postln;
		};
	}

	// Attempts to stop the chronos access point. If successful, the apStarted
	// variable is set to false.
	stopAP {
		var answer;

		apStarted.not.if { ^this };

		this.stopReadingAccData;

		portSemaphore.wait;
		this.log("Stopping Access Point... ", false);
		port.putAll(eZ430StopAPMsg);
		0.1.wait;
		answer = Array.fill( eZ430CmdSuccessMsg.size, { port.read } );
		portSemaphore.signal;

		if (answer == eZ430CmdSuccessMsg) {
			"Success".postln;
			apStarted = false;
		} {
			"Failure (answer was: ".post;
			(answer.size - 1).do { |n| (answer[n] ++ " ").post };
			(answer[answer.size - 1] ++ ")").postln;
		};
	}

	// Terminates all activities and established connections to any devices
	// held by the instance and removes it from the global EZ430Chronos instance
	// array.
	quit {
		this.log("Quitting...");
		apStarted.if { this.stopAP };
		port.close;
		allEZ430.remove(this);
		this.log("Done quitting");
	}

	// Logs the given message, prepended by the instance name.
	log { |msg, appendNewLine = true|
		var txt = "EZ430Chronos: " ++ name ++ ": " ++ msg;
		appendNewLine.if { txt.postln } { txt.post };
	}

	// Logs the given message, prepended by the class name.
	*log { |msg|
		("EZ430Chronos: " ++ msg).postln;
	}

	// Removes all EZ430Chronos instances by calling their quit() methods.
	*removeAll {
		var all, quitTask;
		var quitDone = false;
		var quitCheckerTask = Task({
			3.0.wait;
			quitDone.not.if {
				this.log("Closing serial port seems to hang, forcing cleanup...");
				SerialPort.cleanupAll;
				quitTask.stop;
				this.log("Forced cleanup done");
			};
		});
		all = allEZ430;
		allEZ430 = Array[];
		quitTask = Task({
			quitCheckerTask.start;
			all.do(_.quit);
			quitDone = true;
		}).start;
	}

}