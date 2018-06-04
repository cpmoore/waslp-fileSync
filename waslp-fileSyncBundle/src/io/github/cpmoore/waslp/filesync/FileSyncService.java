package io.github.cpmoore.waslp.filesync;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Dictionary;
import java.util.logging.Logger;

import org.osgi.framework.BundleContext;
import org.osgi.framework.ServiceReference;
import org.osgi.service.cm.Configuration;
import org.osgi.service.cm.ConfigurationAdmin;
import org.osgi.service.cm.ConfigurationException;
import org.osgi.service.cm.ManagedService;

import io.github.cpmoore.waslp.filesync.interfaces.FileTransferHandler;
 
public class FileSyncService implements ManagedService{
		public FileSyncService(BundleContext bContext) {
			logger.info("fileSync init ServiceImpl");
			this.bContext=bContext;
		}
		
		
		 
	    private static String klass = FileSyncService.class.getName();
	    private static Logger logger = Logger.getLogger(klass);
	    private String user;
	    private String password;
	    private String baseURL;
	    
	    
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
				
				this.user=(String) properties.get("user");
				this.password=(String) properties.get("password");
				this.baseURL=(String) properties.get("baseURL");
				FileTransferHandler fileTransferHnadler=new JaxrsFileTransfer(baseURL,user,password);
				
				String[] policies=(String[]) properties.get("policy");
				if (configurationAdminReference != null) {
					ConfigurationAdmin configAdmin = (ConfigurationAdmin) bContext
							.getService(configurationAdminReference);
					
					
					for(String policy:policies) {  
						Configuration policyConfig; 
						try {
							policyConfig = configAdmin.getConfiguration(policy);
							Dictionary<String,?> policyProps = policyConfig.getProperties();
							monitorPolicies.add(new MonitorPolicy(fileTransferHnadler,configAdmin,policyProps));
						} catch (IOException e) {
							logger.throwing(klass, "updated",e);
							continue;
						}
							
					}
				}
			
		}
		
			

			

		
}
