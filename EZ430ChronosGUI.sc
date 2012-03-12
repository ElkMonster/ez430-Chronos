EZ430ChronosGUI {

	var win, xlabel, ylabel, zlabel, graphwins, graphs, dataDetailView;
	var graphUpdateFunc, detailDispUpdateFunc;
	var vmin, vmax;

	var displayAccDataFunc;

	var <startStopButtons;

	var <chronos;

	var updateQueue, updateLock, updateNeeded, updateTask, updateLoopEnabled;

	*new { |chronosCount = 1, ports, names, calibPath, doneFunc|
		var nameCount = 0, portCount = 0, count;
		if (names.notNil) {
			if (names.isArray && names.isString.not) {
				nameCount = names.size;
			} {
				nameCount = 1;
				names = [names];
			};
		} {
			names = [];
		};
		if (ports.notNil) {
			if (ports.isArray && ports.isString.not) {
				portCount = ports.size;
			} {
				portCount = 1;
				ports = [ports];
			};
		} {
			ports = [];
		};

		count = max(chronosCount, max(portCount, nameCount));

		^super.new.init( count, ports, names, calibPath, doneFunc );
	}
	
	init { |chronosCount, ports, names, calibPath, doneFunc|
		updateLock = Semaphore(1);
		updateNeeded = Condition(false);

		{
			var err;

			updateLock.wait;
			startStopButtons = [];
			err = this.createChronos(chronosCount, ports, names, calibPath);
			if (err.isKindOf(Error))
			{
				this.log("createChronos() error, calling doneFunc with exception");
				doneFunc.value(this, err);
			} {
				displayAccDataFunc = nil ! chronosCount;
				this.createGui;

				graphwins = nil ! chronosCount;

				win.onClose = { this.onClose };
				win.front;

				updateLock.signal;

				doneFunc.value(this);
			};
		}.defer;

		this.startUpdateLoop;
	}
	
	createChronos { |count, ports, names, calibPath|
		chronos = Array(count);
		try {
			count.do { |i|
				chronos = chronos.add(case
					{ ports[i].notNil && names[i].notNil } {
						EZ430Chronos(ports[i], names[i])
					}
					{ ports[i].notNil } { EZ430Chronos(portname: ports[i]) }
					{ names[i].notNil } { EZ430Chronos(name: names[i]) }
					{ EZ430Chronos.new }
				);
			};
			calibPath !? {
				chronos.do { |chr|
					chr.loadCalibData(path: calibPath);
				};
			};
		} { |error|
			this.log("returning error");
			^error;
		};
	}

	createControlColumn { |colIdx, window|
		var chr, ctrlCol, apBut, ledBut, graphBut, calibBut, dataDetailBut, label, line;

		chr = chronos[colIdx];

		ctrlCol = CompositeView(window, 190@290);
		ctrlCol.decorator = FlowLayout(ctrlCol.bounds, 4@4, 4@4);
		ctrlCol.background = Color.grey;
		
		label = StaticText(ctrlCol, 180@20).align_(\center).string_(chr.name);

		apBut = Button(ctrlCol, 180@20);
		apBut.states = [["Start Access Point"],
			["Stop Access Point"]];
		apBut.addAction({
			chr.apStarted.if {
				this.runTask({
					apBut.enabled = false;
					chr.stopReadingAccData;
					chr.stopAP;
					ledBut.value = 0;
					apBut.enabled = true;
				});
			} {
				this.runTask({
					apBut.enabled = false;
					chr.startAP;
					chr.startReadingAccData;
					apBut.enabled = true;
				});
			}
		});
		startStopButtons = startStopButtons.add(apBut);

		displayAccDataFunc[colIdx] = { |x, y, z|
			this.displayAccData(x, y, z, colIdx)
		};
		chr.addCallback(displayAccDataFunc[colIdx]);
		chr.addOnConnectCallback({
			("Connection to " ++ chr.name ++ " established").postln;
			this.runTask({ ledBut.value = 1 });
		});

		line = CompositeView(ctrlCol, 180@20);
		line.decorator = FlowLayout(line.bounds);

		ledBut = Button(line, 10@16)
		.states_([["", nil, Color.red], ["", nil, Color.green]])
		.enabled_(false);
		label = StaticText(line, 15@20).align_(\right).string_("x:");
		xlabel.add(StaticText(line, 30@20).align_(\right).string_("-"));
		label = StaticText(line, 15@20).align_(\right).string_("y:");
		ylabel.add(StaticText(line, 30@20).align_(\right).string_("-"));
		label = StaticText(line, 15@20).align_(\right).string_("z:");
		zlabel.add(StaticText(line, 30@20).align_(\right).string_("-"));

		graphBut = Button(ctrlCol, 50@20);
		graphBut.states = [["Graph", nil, Color.grey(0.9)],
			["Graph", nil, Color.grey(0.6)]];
		graphBut.addAction({
			if (graphwins[colIdx].notNil) {
				this.enqueueForUpdate({
					chr.removeCallback(graphUpdateFunc);
					graphwins[colIdx].close;
					graphwins[colIdx] = nil;
				});
			} {
				this.openGraph(colIdx);
			};
		});


		calibBut = Button(ctrlCol, 126@20);
		calibBut.states = [["Calibrate", nil, Color.grey(0.9)],
			["Finish Calibration", nil, Color.grey(0.6)]];
		calibBut.addAction({ |button| chr.calibrate });


		dataDetailBut = Button(ctrlCol, 140@20);
		dataDetailBut.states = [["Show detailed data"], ["Hide detailed data", ]];
		detailDispUpdateFunc = { |data|
			this.enqueueForUpdate({ dataDetailView.update(data) });
		};
		dataDetailBut.addAction({ |button|
			var w;
			if (button.value == 1) {
				this.enqueueForUpdate { chr.addDataCallback(detailDispUpdateFunc) };
				dataDetailView = EZ430ChronosDataView.new.front;
			} {
				this.enqueueForUpdate { chr.removeDataCallback(detailDispUpdateFunc) };
				dataDetailView.close;
				dataDetailView = nil;
			};
		});

	}

	createGui {
		var cols = chronos.size;

		// Controls window
		win = Window("eZ430 Chronos Controls", Rect(50, 50, cols * 200, 300));
		win.view.decorator = FlowLayout(win.view.bounds);

		xlabel = Array(cols);
		ylabel = Array(cols);
		zlabel = Array(cols);

		// Create a controls column for each chronos
		cols.do { |i| this.createControlColumn(i, win) };
	}

	openGraph { |i|
		var chr, w, gx, gy, gz, label, helpLines;
		
		// horizontal, vertical
		helpLines = [[-100, -50, 50, 100], [15, 30, 45, 60]];

		graphwins[i] = w = Window("Chronos" ++ i ++ " Graphs", 600@776);
		label = StaticText(w, Rect(0, 0, 50, 258)).align_(\center).string_("x");
		gx = SimpleGraph(w, Rect(50, 0, 550, 258), 66, framed: true, helpLines: helpLines);
		label = StaticText(w, Rect(0, 259, 50, 258)).align_(\center).string_("y");
		gy = SimpleGraph(w, Rect(50, 259, 550, 258), 66, framed: true, helpLines: helpLines);
		label = StaticText(w, Rect(0, 518, 50, 258)).align_(\center).string_("z");
		gz = SimpleGraph(w, Rect(50, 518, 550, 258), 66, framed: true, helpLines: helpLines);

		graphUpdateFunc = { |x, y, z|
			graphwins[i] !? {
				gx.addData(x);
				gy.addData(y);
				gz.addData(z);
				this.enqueueForUpdate({
					graphwins[i] !? { [gx, gy, gz].do(_.refresh) };
				});
			};
		};

		chr = chronos[i];
		chr.addCallback(graphUpdateFunc);
		w.front;
	}

	enqueueForUpdate { |f|
		updateLock.wait;
		updateQueue = updateQueue.addFunc(f);
		updateNeeded.test = true;
		updateNeeded.signal;
		updateLock.signal;
	}

	// Perform operations that were enqueued for processing via enqueueForUpdate
	startUpdateLoop {
		updateLoopEnabled = true;
		updateTask = Task({
			while {updateLoopEnabled}
			{
				updateLock.wait;
				updateQueue.do(_.value);
				updateQueue = nil;
				updateLock.signal;
				
				// Wait for update, but wait at least some time in order to prevent
				// immediate resumes (thus also letting updateQueue grow a little)
				updateNeeded.wait;
				updateNeeded.test = false;
				0.01.wait;
			};
		}).start(AppClock);
	}

	displayAccData { |x, y, z, chronosIdx|
		this.runTask({
			win.isClosed.not.if {
				xlabel[chronosIdx].string = x;
				ylabel[chronosIdx].string = y;
				zlabel[chronosIdx].string = z;
			}
		});
	}

	close {
		this.log("close");
		// win will call onClose
		win !? { win.close };
	}

	onClose {
		this.runTask({
			this.log("onClose");
			updateLoopEnabled = false;
			updateNeeded.test = true;
			updateNeeded.signal;
			updateLock.wait;
			updateTask.stop;
			
			chronos.do(_.quit);
			graphwins.do { |w| w !? { w.close } };
			dataDetailView !? { dataDetailView.close };
			
			updateLock.signal;
		});
	}

	runTask { |...f|
		Task({
			f.do { |func| func.value }
		}).start(AppClock);
	}

	visible_ { |v|
		v.if {
			chronos.do { |c, i| c.addCallback(displayAccDataFunc[i]) };
		} {
			chronos.do { |c, i| c.removeCallback(displayAccDataFunc[i]) };
		};
		win.visible = v;
	}

	log { |msg|
		("EZ430ChronosGUI: " ++ msg).postln;
	}
}
