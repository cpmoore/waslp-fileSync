package io.github.cpmoore.waslp.filesync;

import java.io.BufferedReader;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.security.NoSuchAlgorithmException;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.util.Base64;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.net.ssl.HostnameVerifier;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSession;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;
import javax.ws.rs.client.Client;
import javax.ws.rs.client.ClientBuilder;
import javax.ws.rs.client.Entity;
import javax.ws.rs.client.Invocation;
import javax.ws.rs.client.WebTarget;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;


import com.ibm.json.java.JSONObject;
import com.ibm.websphere.crypto.InvalidPasswordDecodingException;
import com.ibm.websphere.crypto.UnsupportedCryptoAlgorithmException;


import io.github.cpmoore.waslp.filesync.interfaces.FileTransferHandler;


public class JaxrsFileTransfer implements FileTransferHandler {
	public JaxrsFileTransfer(String baseURI, String user, String password,String sslProtcol) {
		while (baseURI.endsWith("/")) {
			baseURI = baseURI.substring(0, baseURI.length() - 1);
		}
		baseURI = baseURI + "/IBMJMXConnectorREST/file";
		if (password != null && password.toLowerCase().startsWith("{xor}")) {
			try {
				password = com.ibm.websphere.crypto.PasswordUtil.decode(password);
			} catch (InvalidPasswordDecodingException e) {
				logger.log(Level.SEVERE,"Uncaught exception in "+klass+".updated", e);
			} catch (UnsupportedCryptoAlgorithmException e) {
				logger.log(Level.SEVERE,"Uncaught exception in "+klass+".updated", e);
			}
		}
		this.baseURL = baseURI;
		this.user = user;
		this.password = password;
		this.inputSSLProtocol=sslProtcol;

	}

	private static String klass = FileSyncService.class.getName();
	private static Logger logger = Logger.getLogger(klass);
	private Client client=null;
	private String user;
	private String password;
	private String baseURL;
	public static Boolean isCollectiveController = true;
	private String authorizationHeaderValue=null;
	private String inputSSLProtocol;
	private String getSSLProtocol(String ssl) throws NoSuchAlgorithmException{
			if(ssl!=null && !ssl.equals("")) {
				try {
				    if(SSLContext.getInstance(ssl)==null) {
				    	throw new Exception("SSLContext for "+ssl+" is null.");
				    }else {
				    	return ssl;
				    }
				}catch(Exception e) {
					logger.log(Level.SEVERE,"Could not get ssl context for protocol "+ssl,e);
				}
			}
			String[] x=SSLContext.getDefault().getSupportedSSLParameters().getProtocols();
			return x[x.length-1];
	}
	
	private Client getClient() throws Exception {
		if(this.client!=null) {
			return this.client;
		}
		
		if(!baseURL.toLowerCase().startsWith("https")) {
			this.client=ClientBuilder.newClient();
		}else {
			try {
				logger.info("Initializing all trusting all context");
				SSLContext sslcontext = SSLContext.getInstance(getSSLProtocol(this.inputSSLProtocol));	
			    sslcontext.init(null, new TrustManager[]{new X509TrustManager() {
			        public void checkClientTrusted(X509Certificate[] arg0, String arg1) throws CertificateException {}
			        public void checkServerTrusted(X509Certificate[] arg0, String arg1) throws CertificateException {}
					public X509Certificate[] getAcceptedIssuers() { return new X509Certificate[0]; }
			    }}, new java.security.SecureRandom());
	
			    this.client=ClientBuilder.newBuilder()
			                        .sslContext(sslcontext)
			                        .hostnameVerifier(new HostnameVerifier() {
										@Override
										public boolean verify(String arg0, SSLSession arg1) {
											return true;
										}
			                        })
			                        .build();
			} catch (Exception e) {
				logger.log(Level.SEVERE,"Could not get default ssl context",e);
				this.client=ClientBuilder.newClient();
			}
		}
		if (user != null && password != null) {
		  logger.info("Generating authentication for user "+this.user);
		  this.authorizationHeaderValue = "Basic " + Base64.getEncoder().encodeToString((user+":"+password).getBytes());
		}

	    return this.client;
	}
	public void header(Invocation.Builder builder,String arg1,Object arg2) {
		logger.finer("Setting header "+arg1+": "+arg2);
		builder.header(arg1,arg2);
	}
	public Invocation.Builder getBuilder(String url, Target target) throws Exception {
		WebTarget webtarget = getClient().target(url);
		Invocation.Builder builder = webtarget.request();
		header(builder,"com.ibm.websphere.jmx.connector.rest.asyncExecution", false);
		if(authorizationHeaderValue!=null) {
			header(builder,"Authorization", authorizationHeaderValue);
		}
		if (target.getHostName().contains(",")) {
			header(builder,"com.ibm.websphere.collective.hostNames", target.getHostName());
		} else {
			header(builder,"com.ibm.websphere.jmx.connector.rest.routing.hostName", target.getHostName());
			if (target.getServerName() != null && target.getServerUserDir() != null) {
				header(builder,"com.ibm.websphere.jmx.connector.rest.routing.serverName", target.getServerName());
				header(builder,"com.ibm.websphere.jmx.connector.rest.routing.serverUserDir", target.getServerUserDir());
			}

		}

		return builder;

	}
	private Boolean isSuccessful(Response response) {
		if(hasError(response)) {
			return false;
		}		
		String output = response.readEntity(String.class);
		try {
			JSONObject json=JSONObject.parse(output);
			logger.info(json.serialize(true));
			return true;
		} catch (Exception e) {
			logger.severe("JSONObject output could not be read.  Actual output was " + output);
			return false;
		}

	}
	private Boolean hasError(Response response) {
		if(response==null) {
			return true;
		}
		if(response.getStatus()==200) {
			return false;
		}
		
	    String output=null;
		try {
			output=response.readEntity(String.class);
			try {
				JSONObject json=JSONObject.parse(output);
				logger.severe("Could not send file.  Status Code: " + response.getStatus() + " "
						+ response.getStatusInfo().getReasonPhrase());
				logger.severe(json.get("stackTrace").toString());
				return true;
			}catch(Exception e2) {}
		}catch(Exception e) {}
		logger.severe("Could not send file.  Status Code: " + response.getStatus() + " "
				+ response.getStatusInfo().getReasonPhrase()+"  Output: "+output);
		return true;
	    
	}
   
	@Override
	public Boolean sendFile(String sourcePath, String relativeFile, Target target) {
		String file = relativeFile;
		if (!relativeFile.startsWith("/")) {
			file = sourcePath + "/" + relativeFile;
		} else {
			relativeFile = relativeFile.substring(sourcePath.length() + 1);
		}

		logger.info(
				"Sending file " + file + " to " + target + ",outputPath=" + target.getOutputDir() + "/" + relativeFile);
		String parms;
		try {
			parms = URLEncoder.encode(target.getOutputDir() + "/" + relativeFile, "UTF-8")
					+ "?expandOnCompletion=false";
		} catch (UnsupportedEncodingException e) {
			logger.log(Level.SEVERE,"Uncaught exception in "+klass+".sendFile", e);
			return false;
		}
		Entity<?> data = null;
		FileInputStream fis = null;
		if (isCollectiveController) {
			parms += "&local=true";
			data = Entity.entity(file, MediaType.TEXT_PLAIN);
		} else {
			parms += "&local=false";
			try {
				fis = new FileInputStream(file);
				data = Entity.entity(fis, MediaType.APPLICATION_OCTET_STREAM);
			} catch (FileNotFoundException e) {
				logger.log(Level.SEVERE,"Uncaught exception in "+klass+".sendFile", e);
			}
		}

		Response response=null;
		try {
			
			
			Invocation.Builder builder = getBuilder(baseURL + "/" + parms, target);
		    response = builder.post(data);
		}catch(Exception e) {
			logger.log(Level.SEVERE,"Uncaught exception in "+klass+".sendFile", e);
		}

		if (fis != null) { 
			try {
				fis.close();
			} catch (IOException e) {
			}
		}
		if(hasError(response)) {
			return false;
		}
		return isSuccessful(response);
	}

	@Override
	public Boolean deleteFile(String sourcePath, String relativeFile, Target target) {
		String file = relativeFile;
		if (!relativeFile.startsWith("/")) {
			file = sourcePath + "/" + relativeFile;
		} else {
			relativeFile = relativeFile.substring(sourcePath.length() + 1);
		}
		logger.info("Deleting file " + file + " from " + target + ",outputPath=" + target.getOutputDir() + "/"
				+ relativeFile);
		String parms;
		try {
			parms = URLEncoder.encode(target.getOutputDir() + "/" + relativeFile, "UTF-8")
					+ "?recursiveDelete=true";
		} catch (UnsupportedEncodingException e) {
			logger.log(Level.SEVERE,"Uncaught exception in "+klass+".deleteFile", e);
			return false;
		}
		
		Response response=null;
		try {
			Invocation.Builder builder = getBuilder(baseURL + "/" + parms, target);
		    response = builder.delete();
		}catch(Exception e) {
			logger.log(Level.SEVERE,"Uncaught exception in "+klass+".deleteFile", e);
		}
		return isSuccessful(response);
		
	}

}
