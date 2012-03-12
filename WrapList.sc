WrapList {

	var <arr;
	var <size;
	var <insIdx;
	var iniVal;

	*new { |size = 10, initialValue = nil|
		^super.new.init(size, initialValue);
	}

	init { |size, initialValue|
		iniVal = initialValue;
		this.size = size;
	}

	size_ { |newSize|
		var arrRef;
		
		if (newSize == 0) { Error("WrapList size must be > 0").throw };

		arr.isNil.if {
			arr = iniVal.dup(newSize);
			size = newSize;
			insIdx = 0
		} {
			if (newSize == size) { ^this };

			arrRef = `arr;
			arr = iniVal.dup(newSize);
			case
    			{ newSize < size } {
					var d = size - newSize;
					newSize.do { |i|
						arr[i] = arrRef.dereference.wrapAt(insIdx + i + d);
					};
					insIdx = 0;
				}
			    { newSize > size } {
					size.do { |i|
						arr[i] = arrRef.dereference.wrapAt(insIdx + i);
					};
					insIdx = size;
				};
			size = newSize;
		}
	}

	addValue { |val|
		arr[insIdx] = val;
		insIdx = if (insIdx == (size - 1)) { 0 } { insIdx + 1 };
	}

	// Gets element at the specified index. The index is organised as follows:
	// At index
	// - 0: the current oldest element 
	// - 1: the second-oldest element
	// - size-1: the newest element
	// - size-2: the element before the newest element
	// etc.
	at { |i|
		if ((i >= size) || (i < 0)) {
			// Let out-of-bounds accesses fail
			^arr[i];
		} {
			^arr.wrapAt(insIdx + i);
		};
	}

	sum {
		^arr.sum;
	}

	// Prints the array after the class name with a * to mark the insert index
	printOn { |stream|
		var arrCopy = arr.copy;
		arrCopy[insIdx] = arrCopy[insIdx].asString ++ "*";
		stream << "WrapList " << arrCopy;
	}

}
