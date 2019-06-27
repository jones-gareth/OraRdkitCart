package com.cairn.common;

public class ModelException extends RuntimeException {
	public ModelException(String message) {
		super(message);
	}

	public ModelException(Throwable ex) {
		super(ex);
	}
}
