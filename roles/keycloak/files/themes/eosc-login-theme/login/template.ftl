<#-- Keycloak 26's own login pages (errors, review profile, ...) use this layout
     too and pass displayRequiredFields / showAnotherWayIfPresent; accept them -->
<#macro registrationLayout bodyClass="" displayInfo=false displayMessage=true displayWide=false displayRequiredFields=false showAnotherWayIfPresent=true>
<!DOCTYPE html PUBLIC "-//W3C//DTD XHTML 1.0 Transitional//EN"  "http://www.w3.org/TR/xhtml1/DTD/xhtml1-transitional.dtd">
<html xmlns="http://www.w3.org/1999/xhtml" class="${properties.kcHtmlClass!}">

<head>
    <meta charset="utf-8">
    <meta http-equiv="Content-Type" content="text/html; charset=UTF-8" />
    <meta name="robots" content="noindex, nofollow">

    <#if properties.meta?has_content>
        <#list properties.meta?split(' ') as meta>
            <meta name="${meta?split('==')[0]}" content="${meta?split('==')[1]}"/>
        </#list>
    </#if>
    <title>${msg("loginTitle",(realm.displayName!''))}</title>
    <link rel="icon" href="${url.resourcesPath}/img/favicon.ico" />
    <#if properties.styles?has_content>
        <#list properties.styles?split(' ') as style>
            <link href="${url.resourcesPath}/${style}" rel="stylesheet" />
        </#list>
    </#if>
    <#if properties.scripts?has_content>
        <#list properties.scripts?split(' ') as script>
            <script src="${url.resourcesPath}/${script}" type="text/javascript"></script>
        </#list>
    </#if>
    <#if scripts??>
        <#list scripts as script>
            <script src="${script}" type="text/javascript"></script>
        </#list>
    </#if>
</head>

<body class="${properties.kcBodyClass!}">
  <div class="${properties.kcLoginClass!}">
    <div id="kc-header" class="${properties.kcHeaderClass!}">
      <div id="kc-header-wrapper" class="${properties.kcHeaderWrapperClass!}">
        <div class="row">
          <div class="col-md-12">
            <div class="kc-logo-text">
              <img src="${url.resourcesPath}/img/embl_logo.png" width="110px" height="50px"  alt="${msg("loginTitleHtml",(realm.displayName!''))?no_esc}" />
              <img src="${url.resourcesPath}/img/eubi_logo.png" width="110px" height="75px"  alt="${msg("loginTitleHtml",(realm.displayName!''))?no_esc}" />
              <img src="${url.resourcesPath}/img/logo.png" width="110px" height="90px"  alt="${msg("loginTitleHtml",(realm.displayName!''))?no_esc}" />
              <img src="${url.resourcesPath}/img/denbi-logo-color.svg" width="110px" height="90px"  alt="${msg("loginTitleHtml",(realm.displayName!''))?no_esc}" />
            </div>
          </div>
        </div>
      </div>
    </div>
    <section class="row-fluid" id="main">
      <div class="container">
        <div class="${properties.kcFormCardClass!} <#if displayWide>${properties.kcFormCardAccountClass!}</#if>">
          <header class="${properties.kcFormHeaderClass!}">
            <#if realm.internationalizationEnabled  && locale.supported?size gt 1>
                <div id="kc-locale">
                    <div id="kc-locale-wrapper" class="${properties.kcLocaleWrapperClass!}">
                        <div class="kc-dropdown" id="kc-locale-dropdown">
                            <a href="#" id="kc-current-locale-link">${locale.current}</a>
                            <ul>
                                <#list locale.supported as l>
                                    <li class="kc-dropdown-item"><a href="${l.url}">${l.label}</a></li>
                                </#list>
                            </ul>
                        </div>
                    </div>
                </div>
            </#if>
            <h1 id="kc-page-title"><#nested "header"></h1>
          </header>
          <div id="kc-content">
            <div id="kc-content-wrapper">

              <#if displayMessage && message?has_content>
                  <div class="alert alert-${message.type}">
                      <#if message.type = 'success'><span class="${properties.kcFeedbackSuccessIcon!}"></span></#if>
                      <#if message.type = 'warning'><span class="${properties.kcFeedbackWarningIcon!}"></span></#if>
                      <#if message.type = 'error'><span class="${properties.kcFeedbackErrorIcon!}"></span></#if>
                      <#if message.type = 'info'><span class="${properties.kcFeedbackInfoIcon!}"></span></#if>
                      <span class="kc-feedback-text">${message.summary?no_esc}</span>
                  </div>
              </#if>

              <#nested "form">

              <#if displayInfo>
                  <div id="kc-info" class="${properties.kcSignUpClass!}">
                      <div id="kc-info-wrapper" class="${properties.kcInfoAreaWrapperClass!}">
                          <#nested "info">
                      </div>
                  </div>
              </#if>
            </div>
          </div>
        </div>
    <div style="text-align:center" class="${properties.kcSignUpClass!}">
        <p>By continuing to login, you agree to our <a href="https://www.embl.org/info/privacy-policy/" target="_blank">Privacy Policy</a> and <a href="https://vd-portal.embl.de/#/accesspolicy" target="_blank">Terms and Conditions</a> </p>
    </div>

      </section>
      
    </div>
  </div>
</body>
</html>
</#macro>
