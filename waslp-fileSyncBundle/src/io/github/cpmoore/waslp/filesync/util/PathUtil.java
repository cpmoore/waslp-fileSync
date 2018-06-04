package io.github.cpmoore.waslp.filesync.util;

public class PathUtil {

	public static String normalizePath(String path) {
		path=path.replace("\\","/");
		if(!path.startsWith("/")) {
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
    
}
