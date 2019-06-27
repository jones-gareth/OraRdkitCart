package com.cairn.rmi;

public class TaskException extends Exception {
	/**
	 * 
	 */
	private static final long serialVersionUID = 1000L;

	public TaskException(String message) {
		super("Task Run Time Error "+message);
	}

	public TaskException(String message, Exception ex) {
		super(message, ex);
	}
	
}
