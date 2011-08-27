AngleDisplay : QUserView {

	var <>angle;
	var size, center, radius;

	*new { |parent, bounds|
		^super.new( parent, bounds ).init;
	}

	init {
		this.drawFunc_({ this.drawAngle });
		angle = 0;
		this.setup;
	}

	setup {
		var baseSize;
		size = this.bounds.extent;
		baseSize = min(size.x, size.y);
		center = size / 2;
		radius = (baseSize / 2) - (baseSize * 0.2);
	}

	drawAngle {
		Pen.strokeColor = Color.black;
		Pen.addArc(center, radius, 0, pi);
		Pen.addArc(center, radius, pi, 2pi);
		Pen.line(center, (center - (0 @ radius).rotate(angle)));
		Pen.stroke;
	}
}