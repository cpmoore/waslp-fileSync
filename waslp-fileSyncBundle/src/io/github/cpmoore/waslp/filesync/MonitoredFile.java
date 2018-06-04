package io.github.cpmoore.waslp.filesync;

import java.io.File;
import java.io.FileNotFoundException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.logging.Logger;

import io.github.cpmoore.waslp.filesync.interfaces.FileEventHandler;
import io.github.cpmoore.waslp.filesync.util.PathUtil;

public class MonitoredFile  extends Thread{
	public MonitoredFile(String path,Boolean recursive,FileEventHandler handler){
		this.recursive=recursive;
		register(handler,path,false);
	}
    public MonitoredFile(String path,Boolean recursive,Integer interval,FileEventHandler handler) {
    	this.recursive=recursive;
    	this.interval=interval;
    	register(handler,path,false);
    	this.start();
    }

    public MonitoredFile(String path,FileEventHandler handler) {
		register(handler,path,false);
	}
    
    
    private MonitoredFile(FileEventHandler handler,String path,Boolean isChildFile){
		register(handler,path,isChildFile);	
	}
    

    private void register(FileEventHandler handler,String path,Boolean isChildFile) {
		this.handler=handler;
		this.isChildFile=isChildFile;
		
		path=PathUtil.normalizePath(path);
		this.source=path;
		File file=new File(path);
		if(file.exists()) {
			this.fileExists=true;
			lastModified=file.lastModified();
			isDirectory=file.isDirectory();
			if(isDirectory) {
				if(isChildFile) {
					logger.finer("Monitoring child directory "+path);  
				}else {
				    logger.finer("Monitoring directory "+path);
				}
				if(recursive||!isChildFile) {
					//register sub files if recursive or top most file
					registerChildren(file,false);
				}
			}else if(isChildFile) {
				logger.finer("Monitoring child file "+path);  
			}else {
			    logger.finer("Monitoring file "+path);
			}
			
		}else {
			logger.finer("Path "+path+" does not exist.  Will wait for creation");
		}
		
	}
	public static enum Event {CREATE,DIRECTORY_DELETE,FILE_DELETE,MODIFY}
    private static String klass = MonitoredFile.class.getName();
    private static Logger logger = Logger.getLogger(klass);
    private Boolean isDirectory=false;
    private String source;
    private Boolean recursive=true;
    private long lastModified=0;
    private Boolean fileExists=false;
    private HashMap<String,MonitoredFile> children=new HashMap<String,MonitoredFile>();
    private Boolean isChildFile;
    private Integer interval;
    
    private FileEventHandler handler;
    
    
    public void setInterval(Integer interval) {
    	this.interval=interval;
    }
    public Integer getInterval(Integer interval) {
    	return interval;
    }
	
    
	private void notify(File file,Event event) {
		logger.finer("Detected file event: "+event+" for file "+file);
		handler.handleFileEvent(file, event);
	}
	private void registerChildren(File currentFile,Boolean sendCreateEvent) {
		  //register sub files if recursive or top most file
		  String path=PathUtil.normalizePath(currentFile.getAbsolutePath());
		  for(String subFile:currentFile.list()) {
			   subFile=PathUtil.normalizePath(path+"/"+subFile);
			   //new subfile
			   if(!children.containsKey(subFile)) {
				   //new file created.  call create notification
				   if(sendCreateEvent) {
					   notify(new File(subFile),Event.CREATE);   
				   }
				   children.put(subFile, new MonitoredFile(handler,subFile,true));
			   }
			   
		   }
	}
	public void checkForUpdates() throws FileNotFoundException {
    	   for(String s:new HashSet<String>(children.keySet())) {
    		   try {
    		      children.get(s).checkForUpdates();
    		   }catch(FileNotFoundException e) {
    			   //child file was deleted, remove from map
    			   children.remove(s);
    		   }
    	   }
		   File currentFile=new File(source);

		   if(currentFile.exists()) {
			   long modifiedTime=currentFile.lastModified();
			   if(!fileExists) {
				   //created
				   fileExists=true;
				   isDirectory=currentFile.isDirectory();
				   if(!isDirectory) {
				      notify(currentFile,Event.CREATE);
				   }
			   }else if(modifiedTime==lastModified){
				   //not updated
				   return;
			   }
			   
			   
			   
			   
			   lastModified=modifiedTime;
			   //if is directory check for new or deleted subfiles
			   if(currentFile.isDirectory()) {
				   
				   if(!isDirectory) {
					   //was a file, now a directory
					   notify(currentFile,Event.FILE_DELETE);
					   //notify(currentFile,Event.CREATE);
				   }
				   if(recursive||!isChildFile) {
					  registerChildren(currentFile,true);
				   }
				   
			   }else {
				   if(isDirectory) {
					   //was a directory, now it's a file
					   isDirectory=false;
					   notify(currentFile,Event.DIRECTORY_DELETE);
					   notify(currentFile,Event.CREATE);
				   }else {
					   notify(currentFile,Event.MODIFY);
				   }
			   }
			   
			   
		   }else if(fileExists) {
			   //only notify on deletes if not a sub file or parent dir still exists
			   if(!isChildFile||currentFile.getParentFile().isDirectory()) {
				   if(isDirectory) {
					   notify(currentFile,Event.DIRECTORY_DELETE);
				   }else {
				       notify(currentFile,Event.FILE_DELETE);
				   }
			   }
			   if(!isChildFile) {
 			       //parent directory is not monitored.  Keep monitoring
			       fileExists=false;
			   }else {
				   //throw exception to break thread
				   throw new FileNotFoundException("Deleted");
			   } 
		   }
	} 
	
    
    public void run() {
 	   while(true) {
 		   try {
 			 Thread.sleep(interval);
 			 try {
 			     checkForUpdates();
 			 }catch(FileNotFoundException e) {
 				 break;
 			 }
 		   } catch (InterruptedException e) {
 			 break;
 		   }
 	   }
 	}

}
