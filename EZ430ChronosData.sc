EZ430ChronosData {

	//
	var <raw, <rawMin, <rawMax;
	var <cal, <calMin, <calMax;
	var <historyG;
	var <g, <gMin, <gMax;
	var <angle;
	var <smoothedG;
	var <slopeG;
	var <stdDevG;
	var <meanG;
	var <normG;

	var <>calculateExtendedData;

	*new { |historySize = 10|
		^super.new.init(historySize);
	}

	init { |historySize|
		rawMin = 9999.dup(3);
		rawMax = -9999.dup(3);
		calMin = 9999.dup(3);
		calMax = -9999.dup(3);		
		gMin = 10.dup(3);
		gMax = -10.dup(3);
		historyG = WrapList(historySize, [0, 0, 0]);
		calculateExtendedData = false;
	}

	update { |xyz, rawXYZ|

		g = xyz / 50;
		historyG.addValue(g);

		calculateExtendedData.if {
			raw = rawXYZ;
			rawMin = rawMin.min(raw);
			rawMax = rawMax.max(raw);

			cal = xyz;
			calMin = calMin.min(cal);
			calMax = calMax.max(cal);

			gMin = gMin.min(g);
			gMax = gMax.max(g);
			
			slopeG = g - historyG[historyG.size - 1];

			smoothedG = (g + historyG[historyG.size - 2]) / 2;

			meanG = historyG.sum / historyG.size;

			stdDevG = (historyG.arr.sum { |v| (meanG - v).squared } / historyG.size).sqrt;

			normG = g.squared.sum.sqrt;
		}
	}

	calibChanged {
		calMin = 9999.dup(3);
		calMax = -9999.dup(3);
		gMin = 3.dup(3);
		gMax = -3.dup(3);
	}

}