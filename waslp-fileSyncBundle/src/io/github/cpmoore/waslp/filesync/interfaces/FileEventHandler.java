package io.github.cpmoore.waslp.filesync.interfaces;

import java.io.File;

import io.github.cpmoore.waslp.filesync.MonitoredFile.Event;

public interface FileEventHandler {
	public void handleFileEvent(File file,Event event);
}
