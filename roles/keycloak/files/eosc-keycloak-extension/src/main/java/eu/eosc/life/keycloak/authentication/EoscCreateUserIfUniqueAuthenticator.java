package eu.eosc.life.keycloak.authentication;
import org.jboss.logging.Logger;
import org.keycloak.authentication.AuthenticationFlowContext;
import org.keycloak.authentication.AuthenticationFlowError;
import org.keycloak.authentication.authenticators.broker.util.ExistingUserInfo;
import org.keycloak.authentication.authenticators.broker.util.SerializedBrokeredIdentityContext;
import org.keycloak.broker.provider.BrokeredIdentityContext;
import org.keycloak.events.Details;
import org.keycloak.events.Errors;
import org.keycloak.models.AuthenticatorConfigModel;
import org.keycloak.models.KeycloakSession;
import org.keycloak.models.RealmModel;
import org.keycloak.models.UserModel;
import org.keycloak.services.ServicesLogger;
import org.keycloak.services.messages.Messages;


import jakarta.ws.rs.core.Response;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Hashtable;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.keycloak.authentication.authenticators.broker.IdpCreateUserIfUniqueAuthenticator;

//ldap stuff
import javax.naming.Context;
import javax.naming.NamingEnumeration;
import javax.naming.NamingException;
import javax.naming.directory.DirContext;
import javax.naming.directory.InitialDirContext;
import javax.naming.directory.SearchControls;
import javax.naming.directory.SearchResult;
import javax.naming.directory.Attribute;
import javax.naming.directory.Attributes;
import javax.naming.directory.BasicAttribute;
import javax.naming.directory.BasicAttributes;

import org.keycloak.storage.UserStorageProvider;
import org.keycloak.storage.UserStorageProviderFactory;
import org.keycloak.storage.UserStorageProviderModel;
import org.keycloak.storage.ldap.LDAPStorageProviderFactory;
import org.keycloak.storage.user.ImportSynchronization;
import org.keycloak.common.util.Time;

import java.nio.charset.StandardCharsets;

/**
 * Keycloak 26 port of the 1.0.0 authenticator (Keycloak 4.5): same behaviour,
 * Jakarta EE and the current storage/user APIs. The LDAP connection is now
 * opened per registration and closed again; 1.0.0 kept one connection in the
 * (singleton) authenticator forever, so every registration failed after LDAP
 * had dropped it, until Keycloak was restarted.
 */
public class EoscCreateUserIfUniqueAuthenticator extends IdpCreateUserIfUniqueAuthenticator{
	private static Logger logger = Logger.getLogger(EoscCreateUserIfUniqueAuthenticator.class);

	// auth note Keycloak's own IdpCreateUserIfUniqueAuthenticator sets for a new user
	private static final String BROKER_REGISTERED_NEW_USER = "BROKER_REGISTERED_NEW_USER";

    @Override
    protected void actionImpl(AuthenticationFlowContext context, SerializedBrokeredIdentityContext serializedCtx, BrokeredIdentityContext brokerContext) {
    }

    @Override
    protected void authenticateImpl(AuthenticationFlowContext context, SerializedBrokeredIdentityContext serializedCtx, BrokeredIdentityContext brokerContext) {
        KeycloakSession session = context.getSession();
        RealmModel realm = context.getRealm();

        if (context.getAuthenticationSession().getAuthNote(EXISTING_USER_INFO) != null) {
            context.attempted();
            return;
        }

        String username = getUsername(context, serializedCtx, brokerContext);
        if (username == null) {
            ServicesLogger.LOGGER.resetFlow(realm.isRegistrationEmailAsUsername() ? "Email" : "Username");
            context.getAuthenticationSession().setAuthNote(ENFORCE_UPDATE_PROFILE, "true");
            context.resetFlow();
            return;
        }
        try {
			this.addUserToLdap(context, username, brokerContext.getEmail(),
					brokerContext.getFirstName(), brokerContext.getLastName());
			List<UserStorageProviderModel> providers = realm.getStorageProviders(UserStorageProvider.class)
					.map(UserStorageProviderModel::new).toList();
            for (final UserStorageProviderModel provider : providers) {
            	UserStorageProviderFactory<?> factory =
                		(UserStorageProviderFactory<?>) session.getKeycloakSessionFactory().getProviderFactory(UserStorageProvider.class, provider.getProviderId());
                if (provider.isImportEnabled() && factory instanceof LDAPStorageProviderFactory) {
            		logger.debugf("Syncing LDAP provider '%s' after adding '%s'", provider.getName(), username);
            		int oldLastSync = provider.getLastSync();
            		((ImportSynchronization)factory).syncSince(Time.toDate(oldLastSync), session.getKeycloakSessionFactory(), realm.getId(), provider);
                }
            }

            ExistingUserInfo duplication = checkExistingUser(context, username, serializedCtx, brokerContext);

            if (duplication == null) {
                logger.debugf("No duplication detected. Creating account for user '%s' and linking with identity provider '%s' .",
                        username, brokerContext.getIdpConfig().getAlias());

                UserModel federatedUser = session.users().addUser(realm, username);
                federatedUser.setEnabled(true);
                federatedUser.setEmail(brokerContext.getEmail());
                federatedUser.setFirstName(brokerContext.getFirstName());
                federatedUser.setLastName(brokerContext.getLastName());

                for (Map.Entry<String, List<String>> attr : serializedCtx.getAttributes().entrySet()) {
                    federatedUser.setAttribute(attr.getKey(), attr.getValue());
                }
                AuthenticatorConfigModel config = context.getAuthenticatorConfig();
                if (config != null && Boolean.parseBoolean(config.getConfig().get(EoscCreateUserIfUniqueAuthenticatorFactory.REQUIRE_PASSWORD_UPDATE_AFTER_REGISTRATION))) {
                    logger.debugf("User '%s' required to update password", federatedUser.getUsername());
                    federatedUser.addRequiredAction(UserModel.RequiredAction.UPDATE_PASSWORD);
                }
                	userRegisteredSuccess(context, federatedUser, serializedCtx, brokerContext);
                	context.setUser(federatedUser);
                	context.getAuthenticationSession().setAuthNote(BROKER_REGISTERED_NEW_USER, "true");
                	context.success();

            } else {
                logger.debugf("Duplication detected. There is already existing user with %s '%s' .",
                        duplication.getDuplicateAttributeName(), duplication.getDuplicateAttributeValue());

                // Set duplicated user, so next authenticators can deal with it
                context.getAuthenticationSession().setAuthNote(EXISTING_USER_INFO, duplication.serialize());

                Response challengeResponse = context.form()
                        .setError(Messages.FEDERATED_IDENTITY_EXISTS, duplication.getDuplicateAttributeName(), duplication.getDuplicateAttributeValue())
                        .createErrorPage(Response.Status.CONFLICT);
                context.challenge(challengeResponse);

                if (context.getExecution().isRequired()) {
                    context.getEvent()
                            .user(duplication.getExistingUserId())
                            .detail("existing_" + duplication.getDuplicateAttributeName(), duplication.getDuplicateAttributeValue())
                            .removeDetail(Details.AUTH_METHOD)
                            .removeDetail(Details.AUTH_TYPE)
                            .error(Errors.FEDERATED_IDENTITY_EXISTS);
                }
            }


        }
		catch(Exception e){
			logger.errorf(e, "Registration of '%s' failed", username);
        	this.sendFailureChallenge(context, Response.Status.NOT_MODIFIED, "", e.getMessage(), AuthenticationFlowError.INTERNAL_ERROR);
        }


    }

    /**
     * This model creates CoESRA username regardless of selection to use email as username or not
     */
    @Override
    protected String getUsername(AuthenticationFlowContext context, SerializedBrokeredIdentityContext serializedCtx, BrokeredIdentityContext brokerContext) {
        //RealmModel realm = context.getRealm();
        String email = brokerContext.getEmail();
        String eoscUserName = null;
        if(email != null && !email.trim().isEmpty()) {
        	String[] emailParts = email.split("@");
        	eoscUserName = emailParts[0] + "_" + emailParts[1].split("\\.")[0];
        }
    	return eoscUserName;
    }

    @Override
    protected ExistingUserInfo checkExistingUser(AuthenticationFlowContext context, String username, SerializedBrokeredIdentityContext serializedCtx, BrokeredIdentityContext brokerContext) {
    	// only check by username
        UserModel existingUser = context.getSession().users().getUserByUsername(context.getRealm(), username);
        if (existingUser != null) {
            return new ExistingUserInfo(existingUser.getId(), UserModel.USERNAME, existingUser.getUsername());
        }

        return null;
    }

    private void addUserToLdap(AuthenticationFlowContext context, String username, String email,
    		String firstname, String lastname) throws Exception{
    	AuthenticatorConfigModel config = context.getAuthenticatorConfig();
    	String ldapBaseDN = config.getConfig().get(EoscCreateUserIfUniqueAuthenticatorFactory.LDAP_SECURITY_BASE_DN);
    	int ldapDefaultGid = Integer.parseInt(config.getConfig().get(EoscCreateUserIfUniqueAuthenticatorFactory.LDAP_DEFAULT_GID));
    	int ldapStartingUid = Integer.parseInt(config.getConfig().get(EoscCreateUserIfUniqueAuthenticatorFactory.LDAP_UID_START));
    	DirContext ctx = this.initLdapContext(context);
    	try {
    	if(this.userExistInLDap(ctx, email, ldapBaseDN))
    		return;
		Attributes attributes=new BasicAttributes();
		//object class
		Attribute objectClass=new BasicAttribute("objectClass");
		objectClass.add("top");
		objectClass.add("inetOrgPerson");
		objectClass.add("posixAccount");
		objectClass.add("shadowAccount");
		attributes.put(objectClass);
		username = username.toLowerCase();
		//sn
		attributes.put("sn", lastname);
		//cn
		attributes.put("cn", username);
		//mail
		attributes.put("mail", email);
		//organisation
		attributes.put("o", "EOSC");
		//givenName
		attributes.put("givenName", firstname);
		//uid
                MessageDigest digest = MessageDigest.getInstance("SHA-256");
                byte[] bytes = digest.digest(username.getBytes(StandardCharsets.UTF_8));
                StringBuilder sb = new StringBuilder();
                for(int i=0; i< bytes.length ;i++)
                {
                  sb.append(Integer.toString((bytes[i] & 0xff) + 0x100, 16).substring(1));
                }
                String hashed_un = sb.toString();

		attributes.put("uid", username);
		//homedir

		attributes.put("homeDirectory", "/home/"+hashed_un.substring(0,16));
		//shadowLastChange
		attributes.put("shadowLastChange", 15140+"");
		//shadowMin
		attributes.put("shadowMin", 0+"");
		//shadowMax
		attributes.put("shadowMax", 99999+"");
		//shadowWarning
		attributes.put("shadowWarning", 3+"");
		//gidNumber
		attributes.put("gidNumber",  ldapDefaultGid + "");
		//loginShell
		attributes.put("loginShell", "/bin/bash");
		//uidNumber
		attributes.put("uidNumber", this.getNextUid(ctx, ldapBaseDN, ldapStartingUid) + "");
		//userPassword
		//password are generated randomly
		String _ramdomPassword = UUID.randomUUID().toString();
		attributes.put("userPassword", this.harshPassword(_ramdomPassword));
//		//businessCategory
//		attributes.put("businessCategory", user.getAreaOfResearch());
		String ldapPath = "cn=" + username + "," + ldapBaseDN;
		ctx.createSubcontext(ldapPath,attributes);
    	}
    	finally {
    		ctx.close();
    	}
    }

    /**
	 * whether user is in ldap
	 * @param user
	 * @return
	 * @throws Exception
	 */
	private boolean userExistInLDap(DirContext ctx, String email, String ldapUserBaseDN) throws Exception{
		String searchFilder = email;
		String searchFilter = String.format("mail=%s", escapeLDAPSearchFilter(searchFilder));
		NamingEnumeration<SearchResult> results = ctx.search(ldapUserBaseDN, searchFilter, new SearchControls());
		return results.hasMore();
	}

	/**
	 * Prevent LDAP injection
	 * @param filter LDAP filter string to escape
	 * @return escaped string
	 */
	private String escapeLDAPSearchFilter(String filter) {
	       StringBuilder sb = new StringBuilder();
	       for (int i = 0; i < filter.length(); i++) {
	           char curChar = filter.charAt(i);
	           switch (curChar) {
	               case '\\':
	                   sb.append("\\5c");
	                   break;
	               case '*':
	                   sb.append("\\2a");
	                   break;
	               case '(':
	                   sb.append("\\28");
	                   break;
	               case ')':
	                   sb.append("\\29");
	                   break;
	               case '\u0000':
	                   sb.append("\\00");
	                   break;
	               default:
	                   sb.append(curChar);
	           }
	       }
	       return sb.toString();
	}

    /**
     * open an ldap connection; the caller closes it
     * @param context
     * @throws NamingException
     */
    private DirContext initLdapContext(AuthenticationFlowContext context) throws NamingException{
    	AuthenticatorConfigModel config = context.getAuthenticatorConfig();
    	String ldapProviderUrl = config.getConfig().get(EoscCreateUserIfUniqueAuthenticatorFactory.LDAP_SERVER_URL);
    	String ldapSecurityPrinciple = config.getConfig().get(EoscCreateUserIfUniqueAuthenticatorFactory.LDAP_SECURITY_PRINCIPLE);
    	String ldapSecurityAuthentication= config.getConfig().get(EoscCreateUserIfUniqueAuthenticatorFactory.LDAP_SECURITY_AUTHENTICATION);
    	String ldapSecurityCredentials= config.getConfig().get(EoscCreateUserIfUniqueAuthenticatorFactory.LDAP_SECURITY_CREDENTIALS);

		Hashtable<String, String> env = new Hashtable<String, String>();
		env.put(Context.INITIAL_CONTEXT_FACTORY, "com.sun.jndi.ldap.LdapCtxFactory");
		env.put(Context.PROVIDER_URL, ldapProviderUrl);

		// Authenticate if credentials were provided
		if (ldapSecurityPrinciple != null) {
			env.put(Context.SECURITY_AUTHENTICATION, ldapSecurityAuthentication);
			env.put(Context.SECURITY_PRINCIPAL, ldapSecurityPrinciple);
			env.put(Context.SECURITY_CREDENTIALS, ldapSecurityCredentials);
		}
		else
			throw new NamingException("Ldap Principle must not be null");
		return new InitialDirContext(env);
	}

	private int getNextUid(DirContext ctx, String userBaseDN, int startingUid) throws NamingException{
		String searchFilter = "(cn=*)";
		SearchControls searchControls = new SearchControls();
		searchControls.setReturningAttributes(new String[]{"uidNumber"});
		NamingEnumeration<SearchResult> results = ctx.search(userBaseDN, searchFilter, searchControls);
		//is there any better way ?
		int highestUid = startingUid;
		while(results.hasMore()){
			SearchResult result = results.next();
			Attribute uidNumberAtt = result.getAttributes().get("uidNumber");
			if(uidNumberAtt!=null) {
				try {
					int uid = Integer.parseInt((String)uidNumberAtt.get());
					if(uid > highestUid)
						highestUid = uid;
				}
				catch(NumberFormatException e) {

				}
			}
		}
		return (highestUid + 1);
	}

	/**
	 * harsh a password
	 * @param s
	 * @return
	 */
	private String harshPassword(String s){
		String generatedPassword = null;
        try {
        	SecureRandom sr = SecureRandom.getInstance("SHA1PRNG");
            byte[] salt = new byte[16];
            sr.nextBytes(salt);

        	MessageDigest md = MessageDigest.getInstance("SHA-256");
            md.update(salt);
            byte[] bytes = md.digest(s.getBytes());
            StringBuilder sb = new StringBuilder();
            for(int i=0; i< bytes.length ;i++)
            {
                sb.append(Integer.toString((bytes[i] & 0xff) + 0x100, 16).substring(1));
            }
            generatedPassword = sb.toString();
        }
        catch (NoSuchAlgorithmException e)
        {
            //ig nore
        }
        return generatedPassword;
	}



}
