package io.github.cpmoore.waslp.filesync;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Dictionary;
import java.util.HashMap;
import java.util.HashSet;
import java.util.logging.Logger;

import org.osgi.service.cm.ConfigurationAdmin;

import io.github.cpmoore.waslp.filesync.MonitoredFile.Event;
import io.github.cpmoore.waslp.filesync.interfaces.FileEventHandler;
import io.github.cpmoore.waslp.filesync.interfaces.FileTransferHandler;
import io.github.cpmoore.waslp.filesync.util.PathUtil;
import io.github.cpmoore.waslp.filesync.util.PropertyUtil;

public class MonitorPolicy extends Thread{ 
    private static String klass = MonitorPolicy.class.getName();
    private static Logger logger = Logger.getLogger(klass);
    private long lastSyncTime=0;    
    private ArrayList<Target> targets=new ArrayList<Target>();
    private ArrayList<MonitoredFile> monitoredFiles=new ArrayList<MonitoredFile>();
    private Integer pollInterval=5000;
    private Integer minSyncInterval=15000;
    private Boolean paused=false;
    private Boolean recursive=true;
    private Boolean isSyncingFiles=false;
    private FileTransferHandler fileTransferHandler;
    private Boolean hasFilesToSync=false;
    final private HashMap<String,HashSet<String>> allFilesToDelete=new HashMap<String,HashSet<String>>();
    final private HashMap<String,HashSet<String>> allDirectoriesToDelete=new HashMap<String,HashSet<String>>();
    final private HashMap<String,HashSet<String>> allFilesToSync=new HashMap<String,HashSet<String>>();
    class RelativeEventHandler implements FileEventHandler {
    	    public RelativeEventHandler(String source) {
    		   this.sourcePath=PathUtil.normalizePath(source);
    		   allFilesToDelete.put(sourcePath, new HashSet<String>());
    		   allDirectoriesToDelete.put(sourcePath, new HashSet<String>());
    		   allFilesToSync.put(sourcePath, new HashSet<String>());
    		   this.filesToDelete=allFilesToDelete.get(sourcePath);
    		   this.directoriesToDelete=allDirectoriesToDelete.get(sourcePath);
    		   this.filesToSync=allFilesToSync.get(sourcePath);
    	    }
    	    String sourcePath;
    	    private HashSet<String> filesToDelete;
    	    private HashSet<String> directoriesToDelete;
    	    private HashSet<String> filesToSync;
    		@Override
    		public void handleFileEvent(File file, Event event) {
    			while(isSyncingFiles) {
    				 try {
    					Thread.sleep(100);
    				} catch (InterruptedException e) {
    					return;
    				}
    			}
    			
    			hasFilesToSync=true;
    			String path=PathUtil.normalizePath(file.getAbsolutePath());
    			
    			
    			logger.info("FILE=>"+path+", EVENT=>"+event);
    			switch(event) {
    			case FILE_DELETE:
    				this.filesToDelete.add(path);
    				break;
    			case DIRECTORY_DELETE:
    				this.directoriesToDelete.add(path);
    				break;
    			case CREATE:
    			case MODIFY:
    				this.filesToSync.add(path);
    				break;
    			default:
    				break;
    			}
    			
    		
    	 }
    }
    
	 public void setFileTransferHandler(FileTransferHandler fileTransferHandler) {
		 this.fileTransferHandler=fileTransferHandler;
	 }
	 public FileTransferHandler getFileTransferHandler() {
		 return fileTransferHandler;
	 }
	    

	 
	 public void setInterval(Integer interval) {
	    	this.pollInterval=interval;
	 }
	 
	 public Integer getInterval(Integer interval) {
	    	return interval;
	 }
		
	 public void pauseSync() {
		 paused=true;
	 }
	 
	 public void unpauseSync() {
		 paused=false;
	 }
	 
	 public Boolean isSyncPaused() {
		 return paused;
	 }

     public Boolean isSyncingFiles() {
    	 return isSyncingFiles();
     }
     
     
	 public void synchronizeTargets() {
		 if(paused||isSyncingFiles||!hasFilesToSync) {return;}
		 long currentTime=System.currentTimeMillis();
		 if((lastSyncTime+minSyncInterval)>currentTime) {
			 return;
		 }
		 isSyncingFiles=true;
		 for(Target target:targets) {
			    logger.info("Synchronizing target "+target);
				target.addDirectoriesToDelete(allDirectoriesToDelete);
				target.addFilesToDelete(allFilesToDelete);
				target.addFilesToSync(allFilesToSync);
				target.synchronizeFiles();
		 }
		 for(String s:allDirectoriesToDelete.keySet()) {
			  allDirectoriesToDelete.get(s).clear();
			  allFilesToDelete.get(s).clear();
			  allFilesToSync.get(s).clear();
		 }
		 lastSyncTime=System.currentTimeMillis();
		 hasFilesToSync=false; 
		 isSyncingFiles=false;
	 }
	 
     public void run() {
 	   while(true) {
 		   try {
 			 Thread.sleep(pollInterval);
 			 for(MonitoredFile file:monitoredFiles) {
 				 try {
 				    file.checkForUpdates();
 				 }catch(FileNotFoundException e) {
 					//ignore 
 				 }
 			 }
 			 synchronizeTargets();
 		   } catch (InterruptedException e) {
 			 break;
 		   }
 	   }
 	}
	
     public MonitorPolicy(FileTransferHandler handler,ConfigurationAdmin configAdmin,Dictionary<String,?> properties) throws IOException {
    	this.fileTransferHandler=handler;
    	this.pollInterval=PropertyUtil.getInteger(properties, "pollInterval",5000);
    	this.minSyncInterval=PropertyUtil.getInteger(properties, "minSyncInterval",5000);
    	this.recursive=PropertyUtil.getBoolean(properties, "recursive",true);
		for(String target:PropertyUtil.getStringArray(properties, "target")) {
			Target t=new Target(configAdmin
		    		.getConfiguration(target)
		    		.getProperties());
			t.setFileTransferHandler(fileTransferHandler);
		    targets.add(t);
		}
		
		registerSources(PropertyUtil.getStringArray(properties, "source"));
	}
	
	public MonitorPolicy(Integer interval,Target[] targetArray,String[] sourceArray) {
		registerTargets(targetArray);
		registerSources(sourceArray);
	}
	
	public void registerTargets(Target... targetArray) {
		for(Target target:targetArray) {
			if(fileTransferHandler!=null) {
		  	  target.setFileTransferHandler(fileTransferHandler);
			}
			targets.add(target);
		}
	}
	
    public void registerSources(String...sources) {
    	for(String source:sources) {
			  logger.info("Monitoring changes to "+source);
			  monitoredFiles.add(new MonitoredFile(source,recursive,new RelativeEventHandler(source)));
		}
    	if(monitoredFiles.size()>0 && !this.isAlive()) {
			this.start();
		} 
    }
   
    
}
