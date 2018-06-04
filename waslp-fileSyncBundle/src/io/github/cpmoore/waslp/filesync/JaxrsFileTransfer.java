package io.github.cpmoore.waslp.filesync;

import java.io.BufferedReader;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.util.logging.Logger;

import javax.ws.rs.client.Client;
import javax.ws.rs.client.ClientBuilder;
import javax.ws.rs.client.Entity;
import javax.ws.rs.client.Invocation;
import javax.ws.rs.client.WebTarget;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import javax.xml.bind.DatatypeConverter;

import com.ibm.websphere.crypto.InvalidPasswordDecodingException;
import com.ibm.websphere.crypto.UnsupportedCryptoAlgorithmException;

import io.github.cpmoore.waslp.filesync.interfaces.FileTransferHandler;
import io.github.cpmoore.waslp.filesync.util.PathUtil;

public class JaxrsFileTransfer  implements FileTransferHandler{
		public JaxrsFileTransfer(String baseURI,String user,String password) {
			while(baseURI.endsWith("/")) {
				baseURI=baseURI.substring(0, baseURI.length()-1);
			}
			baseURI=baseURI+"/IBMJMXConnectorREST/file";
			if(password!=null && password.toLowerCase().startsWith("{xor}")) {
			    try {
				   password=com.ibm.websphere.crypto.PasswordUtil.decode(password);
				} catch (InvalidPasswordDecodingException e) {
					logger.throwing(klass, "updated", e);
				} catch (UnsupportedCryptoAlgorithmException e) {
					logger.throwing(klass, "updated", e);
				}
			}
			this.baseURI=baseURI;
			this.user=user;
			this.password=password;
			
			
		}
		private static String klass = FileSyncService.class.getName();
	    private static Logger logger = Logger.getLogger(klass);
		private Client client = ClientBuilder.newClient();	
		private String user;
		private String password;
		private String baseURI;
		public  static Boolean isCollectiveController=true;
		
		private String getBasicAuthentication() {
		        String token = this.user + ":" + this.password;
		        try {
		            return "BASIC " + DatatypeConverter.printBase64Binary(token.getBytes("UTF-8"));
		        } catch (UnsupportedEncodingException e) {
		            logger.throwing(klass, "getBasicAuthentication", e);
		            return null;
		        }
		}
		
		public Invocation.Builder getBuilder(String url,Target target){
			WebTarget webtarget = client.target(url);
		    Invocation.Builder builder = webtarget.request();
		    builder.header("com.ibm.websphere.jmx.connector.rest.asyncExecution",true);
		    if(user!=null&&password!=null) {
		      builder.header("Authorization", getBasicAuthentication());
		    }

			if(target.getHostName().contains(",")) {
			    builder.header("com.ibm.websphere.collective.hostNames",target.getHostName());
			}else {
				builder.header("com.ibm.websphere.jmx.connector.rest.routing.hostName",target.getHostName());
				if(target.getServerName()!=null&&target.getServerUserDir()!=null) {
					builder.header("com.ibm.websphere.jmx.connector.rest.routing.serverName",target.getServerName());
					builder.header("com.ibm.websphere.jmx.connector.rest.routing.serverUserDir",target.getServerUserDir());
				}
				
			}
			
		    return builder;
		    
		}
		private String readString(Response result) {
			
			    BufferedReader br = new BufferedReader(new InputStreamReader((InputStream) result.getEntity()));
			    String line;
			    StringBuffer sb=new StringBuffer();
			    try {
					    while (((line = br.readLine()) != null)) {
					    	if(sb.length()>0) {
					    	  sb.append("\n");
					    	}
					    	sb.append(line);
					    }
			    }catch(IOException e) {
			    	logger.throwing(klass, "getFileTransferMbean", e);
			    }finally {
			    	if(br!=null) {
				    	try {
							br.close();
						} catch (IOException e) {}
			    	}
			    }
			    
			    return sb.toString();

		}
		
		@Override
		public Boolean sendFile(String sourcePath,String relativeFile,Target target) {
			String file=relativeFile;
			if(!relativeFile.startsWith("/")) {
			    file=sourcePath+"/"+relativeFile;
			}else {
				relativeFile=relativeFile.substring(sourcePath.length()+1);
			}
			
			logger.info("Sending file "+file+" to "+target+",outputPath="+target.getOutputDir()+"/"+relativeFile);
			if(true) {
				return true;
			}
			String parms;
			try {
				parms = URLEncoder.encode(target.getOutputDir()+"/"+relativeFile, "UTF-8")+"?expandOnCompletion=false";
			} catch (UnsupportedEncodingException e) {
				logger.throwing(klass, "sendFile", e);
				return false;
			}
			Entity<?> data = null;
			if(isCollectiveController) {
				parms+="&local=true";
				data=Entity.entity(file, MediaType.TEXT_PLAIN);
			}else {
				parms+="&local=false";
				try {
					data=Entity.entity(new FileInputStream(file), MediaType.APPLICATION_OCTET_STREAM);
				} catch (FileNotFoundException e) {
					logger.throwing(klass, "sendFile", e);
				}
			}

			
			Invocation.Builder builder=getBuilder(baseURI+"/"+parms,target);
			Response response=builder.post(data);
			String output=readString(response);
			logger.info(output);
			 
			
						
			return true;
		}

		@Override
		public Boolean deleteFile(String sourcePath,String relativeFile,Target target) {
			String file=relativeFile;
			if(!relativeFile.startsWith("/")) {
			    file=sourcePath+"/"+relativeFile;
			}else {
				relativeFile=relativeFile.substring(sourcePath.length()+1);
			}
			logger.info("Deleting file "+file+" from "+target+",outputPath="+target.getOutputDir()+"/"+relativeFile);
			// TODO Auto-generated method stub
			return true;
		}
		
	}
