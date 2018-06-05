package io.github.cpmoore.waslp.filesync;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Dictionary;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.osgi.framework.BundleContext;
import org.osgi.framework.ServiceReference;
import org.osgi.service.cm.Configuration;
import org.osgi.service.cm.ConfigurationAdmin;
import org.osgi.service.cm.ConfigurationException;
import org.osgi.service.cm.ManagedService;

import io.github.cpmoore.waslp.filesync.interfaces.FileTransferHandler;
import io.github.cpmoore.waslp.filesync.util.PropertyUtil;


//TODO Implement file registry
//TODO Auto expand ear, war, and zip file option
//TODO Add option to delete remote apps before send
//TODO Add rest api for adding files, targets, and performing manual sync.  Full sync=sync all sources
public class FileSyncService implements ManagedService{
		public FileSyncService(BundleContext bContext) {
			this.bContext=bContext;
		}
 
	    private static String klass = FileSyncService.class.getName();
	    private static Logger logger = Logger.getLogger(klass);
	    private String fileRegistry=null;
	    private BundleContext bContext;
	    private static ArrayList<MonitorPolicy> monitorPolicies=new ArrayList<MonitorPolicy>();
	    public void unregisterPolicies() {
	    	for(MonitorPolicy policy:monitorPolicies) {
	    		if(policy.isAlive()) {
	    		  policy.interrupt();
	    		}
	    	}
	    	monitorPolicies.clear();
	    	
	    }
		@SuppressWarnings({ "rawtypes", "unchecked" })
		@Override
		public void updated(Dictionary<String, ?> properties) throws ConfigurationException {
				if(monitorPolicies.size()>0) {
					unregisterPolicies();
				}
				
				ServiceReference configurationAdminReference = bContext
						.getServiceReference(ConfigurationAdmin.class.getName());
				
		    	this.fileRegistry=(String) properties.get("fileRegistry");

				FileTransferHandler fileTransferHandler=new JaxrsFileTransfer(
						(String) properties.get("baseURL"),
						(String) properties.get("user"),
						(String) properties.get("password"),
						(String) properties.get("sslProtocol")); 
				
				String[] policies=(String[]) properties.get("policy");
				if (configurationAdminReference != null) {
					ConfigurationAdmin configAdmin = (ConfigurationAdmin) bContext
							.getService(configurationAdminReference);
					

					for(String policy:policies) {  
						Configuration policyConfig; 
						try {
							policyConfig = configAdmin.getConfiguration(policy);
							Dictionary<String,?> policyProps = policyConfig.getProperties();
							monitorPolicies.add(new MonitorPolicy(fileTransferHandler,configAdmin,policyProps));
						} catch (IOException e) {
							logger.log(Level.SEVERE,"Uncaught exception in "+klass+".updated",e);
							continue;
						}
							
					}
				}
			
		}
		
			

			

		
}
