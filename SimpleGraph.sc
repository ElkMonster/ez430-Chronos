SimpleGraph : QUserView {
	var <>lineColor, <>axisColor, <>frameColor, <>framed, <helpLines, <>helpLinesColor, <>pointColor;
	var <>yScaleFactor;
	var data, dataInsIdx;
	var <>centerY;

	*new { |parent, bounds, graphSize = 30, framed = false, helpLines, yScaleFactor = 1|
		^super.new( parent, bounds )
		    .initGraph( graphSize, framed, helpLines, yScaleFactor );
	}

	initGraph { |graphSize, framed, helpLines, yScaleFactor|
		
		this.framed = framed;
		this.helpLines = helpLines;
		this.yScaleFactor = yScaleFactor;

		super.background = Color.grey(1.0);
		lineColor = Color.black;
		axisColor = Color.grey(0.5);
		frameColor = Color.black;
		helpLinesColor = Color.grey(0.9);
		pointColor = Color.red;

		data = Array.fill(graphSize, 0);
		dataInsIdx = 0;
		centerY = this.bounds.height / 2;

		this.drawFunc = { this.drawGraph };
	}

	drawGraph {
		var y, pixelstep, frameOffset = 0;

		framed.if { frameOffset = 1 };
		// Determines the size of one unit on the x-axis in pixels
		pixelstep = (this.bounds.width - 1 - (2 * frameOffset)) / (data.size - 1);

		Pen.smoothing_(false);

		// Fill frame
		Pen.fillColor = super.background;
		Pen.addRect(Rect.fromPoints(frameOffset @ frameOffset, this.bounds.extent - 1));
		Pen.fill;

		Pen.use {
			// Translation moves x-axis to the right position, scale sets the
			// horizontal distance between data points
			Pen.translate(frameOffset, frameOffset + centerY);
			Pen.scale(pixelstep, 1);

			// Draw helper lines
			helpLines.notNil.if {
				Pen.strokeColor = helpLinesColor;
				// horizontal
				helpLines[0].do { |yOffset|
					// yOffset is relative to centerY, therefore we need to
					// invert offset in order to let a helpLine with a positive
					// value appear in above the x-axis (due to top-left
					// coordinate system)
					var y = (0 - yOffset) * yScaleFactor;
					Pen.line( 0 @ y, (data.size - 1) @ y );
				};
				Pen.stroke;
				// vertical
				Pen.use {
					Pen.translate(0, 0 - centerY);
					helpLines[1].do { |xOffset|
						// xOffset is relative to right edge (where the latest values appear)
						var x = data.size - xOffset;
						Pen.line( x @ 0, x @ this.bounds.height );
					};
					Pen.stroke;
				};
				
			};

			// Draw y axis
			Pen.strokeColor = axisColor;
			Pen.line( 0 @ 0, (data.size - 1) @ 0 );
			Pen.stroke;

			// Draw graph
			Pen.use {
				Pen.strokeColor = lineColor;
				Pen.fillColor = pointColor;
				(data.size - 1).do { |i|
					var px, py, pos = dataInsIdx + i;
					px = (i + 1);
					py = 0 - data.wrapAt(pos + 1) * yScaleFactor;
				
					// Draw line segment connecting previous with current value
					Pen.moveTo( i @ (0 - data.wrapAt(pos) * yScaleFactor) );
					Pen.lineTo( px @ py);
					Pen.stroke;
					
					// Draw
					Pen.addRect(Rect(px - (1 / pixelstep), py - 1, 3 / pixelstep, 3 ));
					Pen.fill;
				};
			};
		}; /* Pen.use */

		// Draw frame last so any it cannot be covered by anything
		framed.if {
			Pen.strokeColor = frameColor;
			Pen.addRect(Rect(0, 0, this.bounds.width - 1, this.bounds.height - 1));
			Pen.stroke;
		};
	}

	addData { |n|
		data[dataInsIdx] = n;
		dataInsIdx = (dataInsIdx + 1) % data.size;
	}

	helpLines_ { |h|
		helpLines = case
		    { h.isNil } { nil } // Remove lines
    		// Otherwise: ensure correct array layout
		    { h.isKindOf(Collection) } {
			    case
			    { h.size == 0 } { nil }
			    { h.size == 1 } {
				    // Make sure we have an array with two arrays in it
				    case
				        { h[0].isKindOf(Collection) } { h.add([]) }
					    { h[0].isKindOf(SimpleNumber) } { [h, []] }
				}
				{ h.size > 1 } {
					case
					    { h.every(_.isKindOf(Collection)) } { h }
					    { h.every(_.isKindOf(SimpleNumber)) } { [h, []] }
				}
			}
		    { h.isKindOf(SimpleNumber) } { [[h], []] }
	}

	setValueRange { |v1, v2|
		var vmin = min(v1, v2), vmax = max(v1, v2), d = vmax - vmin;
		var h = this.bounds.height;

		if (d == 0) {
			"SimpleGraph: setValueRange: Warning - min = max, ignoring!".postln;
			^this;
		};

		yScaleFactor = h / d;
		centerY = vmax * yScaleFactor;
		this.refresh;
	}
}
