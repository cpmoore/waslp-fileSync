package io.github.cpmoore.waslp.filesync;

import java.io.File;
import java.io.IOException;
import java.util.Arrays;
import java.util.Comparator;
import java.util.Dictionary;
import java.util.HashMap;
import java.util.HashSet;
import java.util.logging.Level;
import java.util.logging.Logger;

import io.github.cpmoore.waslp.filesync.interfaces.FileTransferHandler;
import io.github.cpmoore.waslp.filesync.util.PathUtil;
import io.github.cpmoore.waslp.filesync.util.PropertyUtil;

public class Target {
    private static String klass = Target.class.getName();
    private static Logger logger = Logger.getLogger(klass);
    public String hostName;
    public String serverName;
    public String serverUserDir;
    public String outputDir;
    
	public Target(Dictionary<String,?> properties) throws IOException {
		this.outputDir=PathUtil.normalizePath(((String) properties.get("outputDir")).replace("%{", "${"));
		this.hostName=(String) properties.get("hostName");
		this.serverName=(String) properties.get("serverName");
		if(this.serverName!=null&&this.serverName.trim().equals("")) {
			this.serverName=null;
		}
		this.serverUserDir=(String) properties.get("serverUserDir");
		if(this.serverUserDir!=null&&this.serverUserDir.trim().equals("")) {
			this.serverUserDir=null;
		}
		
	}
	public Target(String hostName,FileTransferHandler handler) {
		this.hostName=hostName;
		this.fileTransferHandler=handler;
	}
	public Target() {
		
	}

    final private HashMap<String,HashSet<String>> filesToDelete=new HashMap<String,HashSet<String>>();
    final private HashMap<String,HashSet<String>> directoriesToDelete=new HashMap<String,HashSet<String>>();
    final private HashMap<String,HashSet<String>> filesToSync=new HashMap<String,HashSet<String>>();
    private FileTransferHandler fileTransferHandler=null;
    
    @Override
    public String toString() {
    	return "hostName="+hostName+",serverUserDir="+serverUserDir+",serverName="+serverName;
    }
    
    public void setFileTransferHandler(FileTransferHandler fileTransferHandler) {
    	this.fileTransferHandler=fileTransferHandler;
    }
    public FileTransferHandler getFileTransferHandler() {
    	return fileTransferHandler;
    }
    
	public void addFilesToSync(HashMap<String,HashSet<String>> file) {
		PropertyUtil.mergeAll(filesToSync,file);
	} 
	public void addFilesToDelete(HashMap<String,HashSet<String>> file) {
		PropertyUtil.mergeAll(filesToDelete,file,filesToSync);
	}	 
   public void addDirectoriesToDelete(HashMap<String,HashSet<String>> file) {
	    PropertyUtil.mergeAll(directoriesToDelete,file,filesToSync);
	}	
    
    
    public void synchronizeFiles() {
    	 HashSet<String> deletedDirectories=new HashSet<String>();
    	 for(String sourcePath:directoriesToDelete.keySet()) {
    		 
    		 //convert to array and sort by length of directory, ex /dir,/dir/dir2,/dir/dir2/dir3
    		 String[] dirs=directoriesToDelete.get(sourcePath)
    				 .toArray(new String[directoriesToDelete.get(sourcePath).size()]);
    		 Arrays.sort(dirs, new Comparator<String>(){
				@Override
				 public int compare(String s1, String s2) {
				 return s1.length() - s2.length(); // compare length of Strings
				 }
			 });

    		 for(String directory:dirs) {
    			 String relative=PathUtil.getRelative(directory,sourcePath);
    			 //do not delete if it's the source path. other wise whole output dir will be deleted
    			 if(relative.length()==0){continue;}
    			     			 
    			 //ensure parent directory hasn't already been deleted
    			 Boolean alreadyDeleted=false;
    			 for(String s:deletedDirectories) {
    				 if(directory.startsWith(s+"/")) {
    					 alreadyDeleted=true;
    					 break;
    				 }
    			 }
    			 try {
	    			 if(alreadyDeleted||fileTransferHandler.deleteFile(sourcePath,relative, this)) {
	    				 directoriesToDelete.get(sourcePath).remove(directory);
	    				 if(!alreadyDeleted) {
	    				   deletedDirectories.add(directory);
	    				 }
	    			 }else {
	    				 logger.info("Directory "+directory+" failed to delete from "+toString());
	    			 }
    		     }catch(Exception e) {
			    	 logger.log(Level.SEVERE,"Uncaught exception in attempting to delete remote directory",e);
			     }
    		 }
    	 }
    	
    	 
    	 for(String sourcePath:filesToDelete.keySet()) {
    		 for(String file:new HashSet<String>(filesToDelete.get(sourcePath))) {
    			 Boolean alreadyDeleted=false;
    			 for(String s:deletedDirectories) {
    				 if(file.startsWith(s+"/")) {
    					 alreadyDeleted=true;
    					 break;
    				 }
    			 }
    			 
    			 try {
    				 if(alreadyDeleted) {
    					 filesToDelete.get(sourcePath).remove(file); 
    				 }else {
						 String relative=PathUtil.getRelative(file,sourcePath);
	        			 if(relative.length()==0) {
	        			   relative=PathUtil.getFileName(file);
	        			 }
	    			     if(fileTransferHandler.deleteFile(sourcePath,relative, this)) {
	    			       filesToDelete.get(sourcePath).remove(file);
	   			         }else {
	   			    	   logger.info("File "+file+" failed to delete from "+toString());
	   			         }
    				 }
    			 }catch(Exception e) {
   			    	 logger.log(Level.SEVERE,"Uncaught exception in attempting to delete remote file",e);
   			     }
    			 
    		 }
    	 }
    	
    	 
    	 for(String sourcePath:filesToSync.keySet()) {
    		 for(String file:new HashSet<String>(filesToSync.get(sourcePath))) {
    			 String relative=PathUtil.getRelative(file,sourcePath);
    			 if(relative.length()==0) {
    				 relative=PathUtil.getFileName(file);
    			 }
    			 try {
	    			 if(!fileTransferHandler.sendFile(sourcePath,relative, this)) { 
	   				    logger.info("File "+file+" failed to transfer to "+toString());
	   			     }else {
	   			    	filesToSync.get(sourcePath).remove(file);
	   			     }
    			 }catch(Exception e) {
   			    	 logger.log(Level.SEVERE,"Uncaught exception in attempting to send file",e);
   			     }
    		 }
    	 }
		 
	
    }
	
	public String getHostName() {
		return hostName;
	}
	public String getServerName() {
		return serverName;
	}
	public String getServerUserDir() {
		return serverUserDir;
	}
	public String getOutputDir() {
		return outputDir;
	}
	public void setHostName(String hostName) {
		this.hostName=hostName;
	}
	public void setServerName(String serverName) {
		this.serverName=serverName;
	}
	public void setServerUserDir(String serverUserDir) {
		this.serverUserDir=serverUserDir;
	}
	public void setOutputDir(String outputDir) {
		this.outputDir=outputDir;
	}

}
