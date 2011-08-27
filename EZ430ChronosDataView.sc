EZ430ChronosDataView : QWindow {

	var updateFunc;

	var graphWin, graph;

	*new { |bounds|
		^super.new("Chronos Data Details", bounds ? 300@430).init;
	}

	init {
		//this.addFlowLayout;
		this.view.decorator = FlowLayout(this.view.bounds);

		graphWin = Window("", Rect(0, 0, 200, 200)).front;
		graph = SimpleGraph(graphWin, graphWin.view.bounds, helpLines: [[-10,2, 10, 50, 100, 150, 200],[10,20,30]]);
		graph.yScaleFactor = 30;
		graph.centerY = 200;

		this.createGui;

	}

	createGui {
		var col, lblSize;
		var rawLblArr = Array(3);
		var rawMinLblArr = Array(3);
		var rawMaxLblArr = Array(3);
		var calLblArr = Array(3);
		var calMinLblArr = Array(3);
		var calMaxLblArr = Array(3);
		var gLblArr = Array(3);
		var gMinLblArr = Array(3);
		var gMaxLblArr = Array(3);
		var meanGLblArr = Array(3);
		var normLbl;
		var makeTitleLabel = { |text, col, size|
			StaticText(col, size).string_(text).align_(\left);
		};
		// column for a group of data
		var makeColumn = { |size|
			var col;
			col = CompositeView(this, size);
			col.decorator = FlowLayout(col.bounds, 4@4, 2@2);
			col;
		};
		var addLbl = { |str, col, size|
			StaticText(col, size).align_(\right)
			    .string_(str).background_(Color.white)
		};
		var addThreeLabels = { |lblArr, col, size|
			3.do { |i| lblArr.add(addLbl.(i, col, size)) };
		};

		lblSize = Rect(0, 0, 80, 20);

		// raw data labels
		col = makeColumn.(90@100);
		makeTitleLabel.("Raw data:", col, lblSize);
		addThreeLabels.(rawLblArr, col, lblSize);

		// raw min labels
		col = makeColumn.(90@100);
		makeTitleLabel.("Raw min:", col, lblSize);
		addThreeLabels.(rawMinLblArr, col, lblSize);
	
		// raw max labels
		col = makeColumn.(90@100);
		makeTitleLabel.("Raw max:", col, lblSize);
		addThreeLabels.(rawMaxLblArr, col, lblSize);

		// calibrated data labels
		col = makeColumn.(90@100);
		makeTitleLabel.("Calibr. data:", col, lblSize);
		addThreeLabels.(calLblArr, col, lblSize);

		// calibrated min labels
		col = makeColumn.(90@100);
		makeTitleLabel.("Calibr. min:", col, lblSize);
		addThreeLabels.(calMinLblArr, col, lblSize);
	
		// calibrated max labels
		col = makeColumn.(90@100);
		makeTitleLabel.("Calibr. max:", col, lblSize);
		addThreeLabels.(calMaxLblArr, col, lblSize);

		// g labels
		col = makeColumn.(90@100);
		makeTitleLabel.("G:", col, lblSize);
		addThreeLabels.(gLblArr, col, lblSize);

		// g min labels
		col = makeColumn.(90@100);
		makeTitleLabel.("G min:", col, lblSize);
		addThreeLabels.(gMinLblArr, col, lblSize);

		// g max labels
		col = makeColumn.(90@100);
		makeTitleLabel.("G max:", col, lblSize);
		addThreeLabels.(gMaxLblArr, col, lblSize);

		// mean g labels
		col = makeColumn.(90@100);
		makeTitleLabel.("Mean G:", col, lblSize);
		addThreeLabels.(meanGLblArr, col, lblSize);

		col = makeColumn.(90@100);
		makeTitleLabel.("G norm:", col, lblSize);
		normLbl = addLbl.("0", col, lblSize);

		updateFunc = { |d|
			
			Task({
				3.do { |i|
					rawLblArr[i].string = d.raw[i].round(0.01);
					rawMinLblArr[i].string = d.rawMin[i].round(0.01);
					rawMaxLblArr[i].string = d.rawMax[i].round(0.01);
					calLblArr[i].string = d.cal[i].round(0.01);
					calMinLblArr[i].string = d.calMin[i].round(0.01);
					calMaxLblArr[i].string = d.calMax[i].round(0.01);
					gLblArr[i].string = d.g[i].round(0.01);
					gMinLblArr[i].string = d.gMin[i].round(0.01);
					gMaxLblArr[i].string = d.gMax[i].round(0.01);
					meanGLblArr[i].string = d.meanG[i].round(0.01);
					normLbl.string = d.normG.round(0.01);
				};

				graph.addData(d.normG).refresh;

			}).start(AppClock);

		};
	}

	update { |data|
		updateFunc.value(data);
	}
	
}