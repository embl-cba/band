package eu.eosc.life.keycloak.authentication;
import org.keycloak.Config;
import org.keycloak.authentication.Authenticator;
import org.keycloak.authentication.AuthenticatorFactory;
import org.keycloak.models.AuthenticationExecutionModel;
import org.keycloak.models.KeycloakSession;
import org.keycloak.models.KeycloakSessionFactory;
import org.keycloak.provider.ProviderConfigProperty;

import java.util.ArrayList;
import java.util.List;

public class EoscCreateUserIfUniqueAuthenticatorFactory implements AuthenticatorFactory {

    public static final String PROVIDER_ID = "eosc-idp-create-user-if-unique";
    static EoscCreateUserIfUniqueAuthenticator SINGLETON = new EoscCreateUserIfUniqueAuthenticator();

    public static final String REQUIRE_PASSWORD_UPDATE_AFTER_REGISTRATION = "require.password.update.after.registration";
    public static final String LDAP_SERVER_URL = "eosc.ldap.server.url";
    public static final String LDAP_SECURITY_AUTHENTICATION = "eosc.ldap.security.authentication";
    public static final String LDAP_SECURITY_PRINCIPLE = "eosc.ldap.security.principle";
    public static final String LDAP_SECURITY_CREDENTIALS = "eosc.ldap.security.credentials";
    public static final String LDAP_DEFAULT_GID = "eosc.ldap.security.default.gid";
    public static final String LDAP_UID_START  = "eosc.ldap.security.uid.start";
    public static final String LDAP_SECURITY_BASE_DN = "eosc.ldap.security.base.dn";
    
    @Override
    public Authenticator create(KeycloakSession session) {
        return SINGLETON;
    }

    @Override
    public void init(Config.Scope config) {

    }

    @Override
    public void postInit(KeycloakSessionFactory factory) {

    }

    @Override
    public void close() {

    }

    @Override
    public String getId() {
        return PROVIDER_ID;
    }

    @Override
    public String getReferenceCategory() {
        return "createEoscUserIfUnique";
    }

    @Override
    public boolean isConfigurable() {
        return true;
    }

    public static final AuthenticationExecutionModel.Requirement[] REQUIREMENT_CHOICES = {
            AuthenticationExecutionModel.Requirement.ALTERNATIVE,
            AuthenticationExecutionModel.Requirement.REQUIRED,
            AuthenticationExecutionModel.Requirement.DISABLED};

    @Override
    public AuthenticationExecutionModel.Requirement[] getRequirementChoices() {
        return REQUIREMENT_CHOICES;
    }

    @Override
    public String getDisplayType() {
        return "Create EOSC User If Unique";
    }

    @Override
    public String getHelpText() {
        return "Detect if there is existing Keycloak account with same email like identity provider. If no, create new user";
    }

    @Override
    public boolean isUserSetupAllowed() {
        return false;
    }

    private static final List<ProviderConfigProperty> configProperties = new ArrayList<ProviderConfigProperty>();

    static {
    	// add properties
        ProviderConfigProperty requiredPasswordUpdateProperty;
        requiredPasswordUpdateProperty = new ProviderConfigProperty();
        requiredPasswordUpdateProperty.setName(REQUIRE_PASSWORD_UPDATE_AFTER_REGISTRATION);
        requiredPasswordUpdateProperty.setLabel("Require Password Update After Registration");
        requiredPasswordUpdateProperty.setType(ProviderConfigProperty.BOOLEAN_TYPE);
        requiredPasswordUpdateProperty.setHelpText("If this option is true and new user is successfully imported from Identity Provider to Keycloak (there is no duplicated email or username detected in Keycloak DB), then this user is required to update his password");
        configProperties.add(requiredPasswordUpdateProperty);
        //ldap connection url
        ProviderConfigProperty LdapServerProperty;
        LdapServerProperty = new ProviderConfigProperty();
        LdapServerProperty.setName(LDAP_SERVER_URL);
        LdapServerProperty.setLabel("LDAP Server URL");
        LdapServerProperty.setType(ProviderConfigProperty.STRING_TYPE);
        LdapServerProperty.setHelpText("LDAP Server URL");
        configProperties.add(LdapServerProperty);
        //security authentication
        ProviderConfigProperty LdapSecurityAuthentication;
        LdapSecurityAuthentication = new ProviderConfigProperty();
        LdapSecurityAuthentication.setName(LDAP_SECURITY_AUTHENTICATION);
        LdapSecurityAuthentication.setLabel("LDAP Security Authentication");
        LdapSecurityAuthentication.setType(ProviderConfigProperty.STRING_TYPE);
        LdapSecurityAuthentication.setDefaultValue("simple");
        LdapSecurityAuthentication.setHelpText("LDAP Security Authentication");
        configProperties.add(LdapSecurityAuthentication);
        //security principle
        ProviderConfigProperty LdapSecurityPrinciple;
        LdapSecurityPrinciple = new ProviderConfigProperty();
        LdapSecurityPrinciple.setName(LDAP_SECURITY_PRINCIPLE);
        LdapSecurityPrinciple.setLabel("LDAP Security Principle");
        LdapSecurityPrinciple.setType(ProviderConfigProperty.STRING_TYPE);
        LdapSecurityPrinciple.setHelpText("LDAP Security Principle");
        configProperties.add(LdapSecurityPrinciple);
        //security credentials
        ProviderConfigProperty LdapSecurityCredentials;
        LdapSecurityCredentials = new ProviderConfigProperty();
        LdapSecurityCredentials.setName(LDAP_SECURITY_CREDENTIALS);
        LdapSecurityCredentials.setLabel("LDAP Security Credentials");
        LdapSecurityCredentials.setType(ProviderConfigProperty.STRING_TYPE);
        LdapSecurityCredentials.setHelpText("LDAP Security Credentials");
        configProperties.add(LdapSecurityCredentials);
        //default gid
        ProviderConfigProperty LdapDefaultGid;
        LdapDefaultGid = new ProviderConfigProperty();
        LdapDefaultGid.setName(LDAP_DEFAULT_GID);
        LdapDefaultGid.setLabel("LDAP Default GID");
        LdapDefaultGid.setType(ProviderConfigProperty.STRING_TYPE);
        LdapDefaultGid.setHelpText("LDAP Default GID");
        configProperties.add(LdapDefaultGid);
        //starting uid number
        ProviderConfigProperty LdapUidStart;
        LdapUidStart = new ProviderConfigProperty();
        LdapUidStart.setName(LDAP_UID_START);
        LdapUidStart.setLabel("LDAP Starting Uid Number");
        LdapUidStart.setType(ProviderConfigProperty.STRING_TYPE);
        LdapUidStart.setHelpText("LDAP Starting Uid Number");
        configProperties.add(LdapUidStart);
        //basedn 
        ProviderConfigProperty LdapBaseDN;
        LdapBaseDN = new ProviderConfigProperty();
        LdapBaseDN.setName(LDAP_SECURITY_BASE_DN);
        LdapBaseDN.setLabel("LDAP User Base DN");
        LdapBaseDN.setType(ProviderConfigProperty.STRING_TYPE);
        LdapBaseDN.setHelpText("LDAP User Base DN");
        configProperties.add(LdapBaseDN);
    }


    @Override
    public List<ProviderConfigProperty> getConfigProperties() {
        return configProperties;
    }
}
