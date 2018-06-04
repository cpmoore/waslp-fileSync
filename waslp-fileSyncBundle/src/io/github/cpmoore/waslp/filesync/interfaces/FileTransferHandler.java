package io.github.cpmoore.waslp.filesync.interfaces;

import io.github.cpmoore.waslp.filesync.Target;

public interface FileTransferHandler {
	public Boolean sendFile(String sourcePath,String relativeFile,Target target);
	public Boolean deleteFile(String sourcePath,String relativeFile,Target target);
}
