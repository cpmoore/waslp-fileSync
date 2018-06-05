package io.github.cpmoore.waslp.filesync.util;

public class PathUtil {

	public static String normalizePath(String path) {
		path=path.replace("\\","/").replaceAll("/+", "/");;
		if(!path.startsWith("/")&&!path.startsWith("${")) {
			path="/"+path;
		}
		while(path.endsWith("/")) {
			path=path.substring(0,path.length()-1);
		}
		return path; 
	}
	public static String getFileName(String file) {
		String[] x=normalizePath(file).split("/");
		return x[x.length-1];
		
	}
    public static String getRelative(String file,String sourcePath) {
    	 if(!file.startsWith("/")) {
    		return file;
    	 }
    	 file=file.substring(sourcePath.length());
		 while(file.endsWith("/")) {
			 file=file.substring(0, file.length()-1);
		 }
		 return file;
    }
}
