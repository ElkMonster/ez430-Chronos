EZ430ChronosData {

	//
	var <raw, <rawMin, <rawMax;
	var <cal, <calMin, <calMax;
	var historyG, currIdx;
	var <g, <gMin, <gMax;
	var <angle;
	var <smoothedG;
	var <slopeG;
	var <stdDevG;
	var <meanG;
	var <normG;

	*new { |historySize = 15|
		^super.new.init(historySize);
	}

	init { |historySize|
		rawMin = 9999.dup(3);
		rawMax = -9999.dup(3);
		calMin = 9999.dup(3);
		calMax = -9999.dup(3);		
		gMin = 10.dup(3);
		gMax = -10.dup(3);
		this.createHistoryBuffer(historySize);
	}

	update { |xyz, rawXYZ|

		raw = rawXYZ;
		rawMin = rawMin.min(raw);
		rawMax = rawMax.max(raw);

		cal = xyz;
		calMin = calMin.min(cal);
		calMax = calMax.max(cal);

		g = xyz / 50;
		gMin = gMin.min(g);
		gMax = gMax.max(g);

		currIdx = (currIdx + 1) % historyG.size;
		historyG[currIdx] = g;

		angle = g.clip(-1, 1).asin;
		
		slopeG = g - this.previousG;

		smoothedG = (g + this.previousG) / 2;

		meanG = historyG.sum / historyG.size;

		stdDevG = (historyG.sum { |v| (meanG - v).squared } / historyG.size).sqrt;

		normG = g.squared.sum.sqrt;

	}

	calibChanged {
		calMin = 9999.dup(3);
		calMax = -9999.dup(3);
		gMin = 3.dup(3);
		gMax = -3.dup(3);
	}

	createHistoryBuffer { |size|
		historyG = Array.fill(size, [0, 0, 0]);
		currIdx = -1;
	}

	previousG {
		^historyG.wrapAt(currIdx - 1);
	}

}