package org.openmrs.api.db.hibernate;

import java.io.IOException;
import java.io.InputStream;
import java.sql.Connection;
import java.sql.DriverManager;
import java.util.HashSet;
import java.util.Properties;
import java.util.Set;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.hibernate.HibernateException;
import org.hibernate.SessionFactory;
import org.hibernate.cfg.Configuration;
import org.hibernate.util.ConfigHelper;
import org.openmrs.api.APIException;
import org.openmrs.api.context.Context;
import org.openmrs.module.Module;
import org.openmrs.module.ModuleFactory;
import org.springframework.orm.hibernate3.LocalSessionFactoryBean;

public class HibernateSessionFactoryBean extends LocalSessionFactoryBean {
	
	private static Log log = LogFactory.getLog(HibernateSessionFactoryBean.class);
	
	protected Set<String> tmpMappingResources = new HashSet<String>(); 
	
	public SessionFactory newSessionFactory(Configuration config) throws HibernateException {
		log.debug("Configuring hibernate sessionFactory properties");
		
		Properties properties = Context.getRuntimeProperties();
		
		// loop over runtime properties and override each in the configuration
		for (Object key : properties.keySet()) {
			String prop = (String)key;
			String value = (String)properties.get(key);
			log.debug("Setting property: " + prop + ":" + value);
			config.setProperty(prop, value);
			if (!prop.startsWith("hibernate"))
				config.setProperty("hibernate." + prop, value);
		}
		
		// load in the default hibernate properties
		try {
			InputStream propertyStream = ConfigHelper.getResourceAsStream("/hibernate.default.properties");
			Properties props = new Properties();
			props.load(propertyStream);
			propertyStream.close();
	
			// Only load in the default properties if they don't exist
			config.mergeProperties(props);
		}
		catch (IOException e) {
			log.fatal("Unable to load default hibernate properties", e);
		}
		
		// check database connection before configuring session factory
		// If not done, Hibernate blocks until a sucessful connection is made
		String driver = config.getProperty("hibernate.connection.driver_class");
		String username = config.getProperty("hibernate.connection.username");
		String password = config.getProperty("hibernate.connection.password");
		String url = config.getProperty("hibernate.connection.url");
		int check = checkDatabaseConnection(driver, username, password, url);
		
		if (check == 0)
			return config.buildSessionFactory();
		else
			throw new APIException("Error connecting to database");
		
	}
	
	/**
	 * Non zero represents an error and should prevent hibernate from starting
	 * 
	 * @param driver
	 * @param user
	 * @param pw
	 * @param url
	 * @return int
	 */
	private int checkDatabaseConnection(String driver, String user, String pw, String url) {
		
		try {
			Class.forName(driver).newInstance();
		}
		catch (Exception e) {
			log.error("Error while starting up. Bad driver class: " + driver, e);
			System.err.println(e.getMessage());
			System.err.println("Could not find driver_class '" + driver + "'.  Can be set with runtime property: 'connection.driver_class'");
			return 1;
		}
		
		log.debug("checking database connection");
		try {
			@SuppressWarnings("unused")
			Connection db_connection = DriverManager.getConnection(url, user, pw);
			log.debug("Successful database connection");
			db_connection.close();
		}
		catch (Exception e) {
			log.error("Error while starting up.  Unable to connection using ", e);
			System.err.println(e.getMessage());
			pw = pw.replaceAll(".", "*");
			System.err.println("Could not connect to database using url '" + url + "', username '" + user + "', and pw '" + pw + "'. Connection properties can be set with runtime property: 'connection.username', 'connection.password', and 'connection.url'");
			return 1;
		}
		
		return 0;
	}

	/** 
	 * Collect the mapping resources for future use because the mappingResources object is
	 * defined as 'private' instead of 'protected'
	 * 
	 * @see org.springframework.orm.hibernate3.LocalSessionFactoryBean#setMappingResources(java.lang.String[])
	 */
	@Override
	public void setMappingResources(String[] mappingResources) {
		for (String resource : mappingResources) {
			tmpMappingResources.add(resource);
		}
		
		super.setMappingResources(tmpMappingResources.toArray(new String[] {}));
	}
	
	public Set<String> getModuleMappingResources() {
		for (Module mod : ModuleFactory.getStartedModules()) {
			for (String s : mod.getMappingFiles()) {
				tmpMappingResources.add(s);
			}
		}
		return tmpMappingResources;
	}

	/* (non-Javadoc)
	 * @see org.springframework.orm.hibernate3.AbstractSessionFactoryBean#afterPropertiesSet()
	 */
	@Override
	public void afterPropertiesSet() throws Exception {
		// adding each module's mapping file to the list of mapping resources
		super.setMappingResources(getModuleMappingResources().toArray(new String[] {}));
		
		super.afterPropertiesSet();
			
	}

}
