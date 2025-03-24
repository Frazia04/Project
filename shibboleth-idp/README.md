Shibboleth Identity Provider (IdP)
==================================

This is a Docker container with Shibboleth IdP.
To be used to test the RHRK sign in process in ExClaim.


Build the Docker Image
----------------------
```
docker buildx build -t shibboleth-idp .
```


Run Shibboleth IdP
------------------
```
docker run --rm -p 8080:8080 shibboleth-idp
```

### Verify that the IdP works
- Visit http://localhost:8080/idp/profile/admin/hello
- Log in with one of the [accounts](#accounts)
- You should see a page with attributes for that user


Configure ExClaim
-----------------
In the `app` directory, create a self-signed certificate:
```
openssl req -x509 -newkey rsa:4096 -keyout saml-rp.key -out saml-rp.crt -nodes -subj '/CN=localhost' -days 3650
```

In `app/application.properties` (not `app/src/main/resources/application.properties`), add:
```properties
spring.security.saml2.relyingparty.registration.RHRK.assertingparty.metadata-uri=http://localhost:8080/idp/shibboleth
spring.security.saml2.relyingparty.registration.RHRK.decryption.credentials[0].private-key-location=file:./saml-rp.key
spring.security.saml2.relyingparty.registration.RHRK.decryption.credentials[0].certificate-location=file:./saml-rp.crt
spring.security.saml2.relyingparty.registration.RHRK.signing.credentials[0].private-key-location=file:./saml-rp.key
spring.security.saml2.relyingparty.registration.RHRK.signing.credentials[0].certificate-location=file:./saml-rp.crt
server.port=3000
server.servlet.session.cookie.same-site=none
server.servlet.session.cookie.secure=true
server.ssl.certificate=./saml-rp.crt
server.ssl.certificate-private-key=./saml-rp.key
#server.address // remove or comment out if it is set
```

**Explanation:**
- `server.port`: ExClaim cannot run on its default port 8080 because that one is used by the IdP container.
- `server.servlet.session.cookie.*`: For the session cookie to be available in the HTTP POST request issued from the IdP, the cookie must have `SameSite=None`.
  In modern browsers, `SameSite=None` also requires the `Secure` attribute to be set.
- `server.ssl.*`: For the `Secure` cookie attribute, we need to use HTTPS.
- `server.address`: ExClaim must be accessible from inside the IdP Docker container.
  Therefore, the Spring Boot application must not bind only to localhost.

**Note:**
Once configured, ExClaim tries to load the IdP's metadata from the configured URL.
If the IdP is not running, then the log is spammed with errors from periodic retries to load the metadata.
So always start the IdP container before starting ExClaim.
When you're done testing, remove or comment the added properties.

Start the application and then access it through an IP address that is reachable from inside the Docker container.
You can use the IP assigned to the Docker interface: `ip -4 addr show dev docker0`.
Remember that you need to use the https protocol and accept the self-signed certificate.


Accounts
--------
Accounts are configured in the [`shibboleth-idp/credentials/htpasswd`](shibboleth-idp/credentials/htpasswd) file.
Their SAML attributes are defined in `shibboleth-idp/conf/attribute-resolver.xml`.


Logout
------
- http://localhost:8080/idp/profile/Logout