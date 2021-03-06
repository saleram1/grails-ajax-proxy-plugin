package net.edwardstx;


import java.io.BufferedInputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;

import javax.servlet.ServletConfig;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;

import org.apache.commons.httpclient.Header;
import org.apache.commons.httpclient.HttpClient;
import org.apache.commons.httpclient.HttpMethod;
import org.apache.commons.httpclient.NameValuePair;
import org.apache.commons.httpclient.methods.GetMethod;
import org.apache.commons.httpclient.methods.PostMethod;

// modified to check for valid Shiro User on every GET and POST request
// see member  SHIRO_LOGIN_SESSION_KEY
public class ProxyServlet extends HttpServlet {
	/**
	 * Serialization UID.
	 */
	private static final long serialVersionUID = 1L;
    /**
     * Logger (use java.util to avoid dependencies - or should we use log4j because of Grails?).
     */
    private static Logger logger = Logger.getLogger(ProxyServlet.class.getName());
    /**
	 * Key for redirect location header.
	 */
    private static final String STRING_LOCATION_HEADER = "Location";
    /**
     * Key for content type header.
     */
    private static final String STRING_CONTENT_TYPE_HEADER_NAME = "Content-Type";

    /**
     * Key for content length header.
     */
    private static final String STRING_CONTENT_LENGTH_HEADER_NAME = "Content-Length";
    /**
     * Key for host header
     */
    private static final String STRING_HOST_HEADER_NAME = "Host";
    /**
     * The directory to use to temporarily store uploaded files
     */
    private static final File FILE_UPLOAD_TEMP_DIRECTORY = new File(System.getProperty("java.io.tmpdir"));
    /**
     * How to tell if a User has already been logged in to Apache Shiro - this fork checks for it on each request
     */
    private static final String SHIRO_LOGIN_SESSION_KEY = "org.apache.shiro.subject.support.DefaultSubjectContext_AUTHENTICATED_SESSION_KEY";

    // Proxy host params
    /**
     * The scheme to which we are proxying requests "http://" or "https://"
     */
	private String stringProxyScheme;
    /**
     * The host to which we are proxying requests
     */
	private String stringProxyHost;
	/**
	 * The port on the proxy host to wihch we are proxying requests. Default value is 80.
	 */
	private int intProxyPort = 80;
	/**
	 * The (optional) path on the proxy host to wihch we are proxying requests. Default value is "".
	 */
	private String stringProxyPath = "";
    /**
     * Optional config variable where if set will redirect request to Login page (Apache Shiro specific)
     */
    private String loginUri;
	/**
	 * The maximum size for uploaded files in bytes. Default value is 5MB.
	 */
	private int intMaxFileUploadSize = 5 * 1024 * 1024;
	
	/**
	 * Initialize the <code>ProxyServlet</code>
	 * @param servletConfig The Servlet configuration passed in by the servlet conatiner
	 */
	public void init(ServletConfig servletConfig) {
		// Get the proxy scheme (http:// or https://)
		String stringProxySchemeNew = servletConfig.getInitParameter("proxyScheme");
		if(stringProxySchemeNew == null || stringProxySchemeNew.length() == 0 ||
		    !(stringProxySchemeNew.equals("http://") ||  stringProxySchemeNew.equals("https://"))) { 
		    stringProxySchemeNew = "http://";       // Default to http if blank or invalid
		}
		this.setProxyScheme(stringProxySchemeNew);
		// Get the proxy host
		String stringProxyHostNew = servletConfig.getInitParameter("proxyHost");
		if(stringProxyHostNew == null || stringProxyHostNew.length() == 0) { 
			throw new IllegalArgumentException("Proxy host not set, please set init-param 'proxyHost' in web.xml");
		}
		this.setProxyHost(stringProxyHostNew);
		// Get the proxy port if specified
		String stringProxyPortNew = servletConfig.getInitParameter("proxyPort");
		if(stringProxyPortNew != null && stringProxyPortNew.length() > 0) {
			this.setProxyPort(Integer.parseInt(stringProxyPortNew));
		}
		// Get the proxy path if specified
		String stringProxyPathNew = servletConfig.getInitParameter("proxyPath");
		if(stringProxyPathNew != null && stringProxyPathNew.length() > 0) {
			this.setProxyPath(stringProxyPathNew);
		}
		// Get the maximum file upload size if specified
		String stringMaxFileUploadSize = servletConfig.getInitParameter("maxFileUploadSize");
		if(stringMaxFileUploadSize != null && stringMaxFileUploadSize.length() > 0) {
			this.setMaxFileUploadSize(Integer.parseInt(stringMaxFileUploadSize));
		}
        // Get login URI if specified
        String stringLogin = servletConfig.getInitParameter("login");
        if (stringLogin != null && stringLogin.trim().length() > 0) {
            this.setLoginUri(stringLogin);
        }
	}
	
	/**
	 * Performs an HTTP GET request
	 * @param httpServletRequest The {@link HttpServletRequest} object passed
	 *                            in by the servlet engine representing the
	 *                            client request to be proxied
	 * @param httpServletResponse The {@link HttpServletResponse} object by which
	 *                             we can send a proxied response to the client 
	 */
	public void doGet (HttpServletRequest httpServletRequest, HttpServletResponse httpServletResponse)
    		throws IOException, ServletException {
		// Create a GET request
		GetMethod getMethodProxyRequest = new GetMethod(this.getProxyURL(httpServletRequest));
		// Forward the request headers
		setProxyRequestHeaders(httpServletRequest, getMethodProxyRequest);

        if (this.isAuthenticated(httpServletRequest)) {
            // Execute the proxy request
            this.executeProxyRequest(getMethodProxyRequest, httpServletRequest, httpServletResponse);
        }
        else {
            String redirectPath = httpServletRequest.getContextPath() + this.getLoginUri() + httpServletRequest.getRequestURI().substring(httpServletRequest.getContextPath().length());
            httpServletResponse.sendRedirect(redirectPath);
        }
	}
	
	/**
	 * Performs an HTTP POST request
	 * @param httpServletRequest The {@link HttpServletRequest} object passed
	 *                            in by the servlet engine representing the
	 *                            client request to be proxied
	 * @param httpServletResponse The {@link HttpServletResponse} object by which
	 *                             we can send a proxied response to the client 
	 */
	public void doPost(HttpServletRequest httpServletRequest, HttpServletResponse httpServletResponse)
        	throws IOException, ServletException {
    	// Create a standard POST request
    	PostMethod postMethodProxyRequest = new PostMethod(this.getProxyURL(httpServletRequest));
		// Forward the request headers
		setProxyRequestHeaders(httpServletRequest, postMethodProxyRequest);

        if (this.isAuthenticated(httpServletRequest)) {
            this.handleStandardPost(postMethodProxyRequest, httpServletRequest);
            // Execute the proxy request
            this.executeProxyRequest(postMethodProxyRequest, httpServletRequest, httpServletResponse);
        }
    }


    /**
     * Check Session for a known Shiro session variable - if true then the User Principal should be set as well
     *
     * @param httpServletRequest The {@link HttpServletRequest} object passed
     *                            in by the servlet engine representing the
     *                            client request to be proxied
     * @return true if logged in to Shiro
     */
    private boolean isAuthenticated(HttpServletRequest httpServletRequest) {
        try {
            HttpSession session = httpServletRequest.getSession(false);
            if (null == session) {
                return false;
            }

            Object sessionFlag = session.getAttribute(SHIRO_LOGIN_SESSION_KEY);
            if (null != sessionFlag && sessionFlag.equals(Boolean.TRUE)) {
                return true;
            }
            else {
                return false;
            }
        }
        catch (Exception ex) {
            return false;
        }
    }

	/**
	 * Sets up the given {@link PostMethod} to send the same standard POST
	 * data as was sent in the given {@link HttpServletRequest}
	 * @param postMethodProxyRequest The {@link PostMethod} that we are
	 *                                configuring to send a standard POST request
	 * @param httpServletRequest The {@link HttpServletRequest} that contains
	 *                            the POST data to be sent via the {@link PostMethod}
	 */    
    @SuppressWarnings("unchecked")
	private void handleStandardPost(PostMethod postMethodProxyRequest, HttpServletRequest httpServletRequest) {
		// Get the client POST data as a Map
		Map<String, String[]> mapPostParameters = (Map<String,String[]>) httpServletRequest.getParameterMap();
		// Create a List to hold the NameValuePairs to be passed to the PostMethod
		List<NameValuePair> listNameValuePairs = new ArrayList<NameValuePair>();
		// Iterate the parameter names
		for(String stringParameterName : mapPostParameters.keySet()) {
			// Iterate the values for each parameter name
			String[] stringArrayParameterValues = mapPostParameters.get(stringParameterName);
			for(String stringParamterValue : stringArrayParameterValues) {
				// Create a NameValuePair and store in list
				NameValuePair nameValuePair = new NameValuePair(stringParameterName, stringParamterValue);
				listNameValuePairs.add(nameValuePair);
			}
		}
		// Set the proxy request POST data 
		postMethodProxyRequest.setRequestBody(listNameValuePairs.toArray(new NameValuePair[] { }));
    }
    
    /**
     * Executes the {@link HttpMethod} passed in and sends the proxy response
     * back to the client via the given {@link HttpServletResponse}
     * @param httpMethodProxyRequest An object representing the proxy request to be made
     * @param httpServletResponse An object by which we can send the proxied
     *                             response back to the client
     * @throws IOException Can be thrown by the {@link HttpClient}.executeMethod
     * @throws ServletException Can be thrown to indicate that another error has occurred
     */
    private void executeProxyRequest(
    		HttpMethod httpMethodProxyRequest,
    		HttpServletRequest httpServletRequest,
    		HttpServletResponse httpServletResponse)
    			throws IOException, ServletException {
		// Create a default HttpClient
    	HttpClient httpClient = new HttpClient();
		httpMethodProxyRequest.setFollowRedirects(false);
		// Execute the request
		int intProxyResponseCode = httpClient.executeMethod(httpMethodProxyRequest);

		// Check if the proxy response is a redirect
		// The following code is adapted from org.tigris.noodle.filters.CheckForRedirect
		// Hooray for open source software
		if(intProxyResponseCode >= HttpServletResponse.SC_MULTIPLE_CHOICES /* 300 */
				&& intProxyResponseCode < HttpServletResponse.SC_NOT_MODIFIED /* 304 */) {
			String stringStatusCode = Integer.toString(intProxyResponseCode);
			String stringLocation = httpMethodProxyRequest.getResponseHeader(STRING_LOCATION_HEADER).getValue();
			if(stringLocation == null) {
					throw new ServletException("Recieved status code: " + stringStatusCode 
							+ " but no " +  STRING_LOCATION_HEADER + " header was found in the response");
			}
			// Modify the redirect to go to this proxy servlet rather that the proxied host
			String stringMyHostName = httpServletRequest.getServerName();
			if(httpServletRequest.getServerPort() != 80) {
				stringMyHostName += ":" + httpServletRequest.getServerPort();
			}
			stringMyHostName += httpServletRequest.getContextPath();
			httpServletResponse.sendRedirect(stringLocation.replace(getProxyHostAndPort() + this.getProxyPath(), stringMyHostName));
			return;
		} else if(intProxyResponseCode == HttpServletResponse.SC_NOT_MODIFIED) {
			// 304 needs special handling.  See:
			// http://www.ics.uci.edu/pub/ietf/http/rfc1945.html#Code304
			// We get a 304 whenever passed an 'If-Modified-Since'
			// header and the data on disk has not changed; server
			// responds w/ a 304 saying I'm not going to send the
			// body because the file has not changed.
			httpServletResponse.setIntHeader(STRING_CONTENT_LENGTH_HEADER_NAME, 0);
			httpServletResponse.setStatus(HttpServletResponse.SC_NOT_MODIFIED);
			return;
		}
		
		// Pass the response code back to the client
		httpServletResponse.setStatus(intProxyResponseCode);

        // Pass response headers back to the client
        Header[] headerArrayResponse = httpMethodProxyRequest.getResponseHeaders();
        for(Header header : headerArrayResponse) {
            if (!header.getName().equals("Transfer-Encoding")) {
       		    httpServletResponse.setHeader(header.getName(), header.getValue());
       		}
        }
        
        // Send the content to the client
        InputStream inputStreamProxyResponse = httpMethodProxyRequest.getResponseBodyAsStream();
        BufferedInputStream bufferedInputStream = new BufferedInputStream(inputStreamProxyResponse);
        OutputStream outputStreamClientResponse = httpServletResponse.getOutputStream();
        int intNextByte;
        while ( ( intNextByte = bufferedInputStream.read() ) != -1 ) {
        	outputStreamClientResponse.write(intNextByte);
        }
    }
    
    public String getServletInfo() {
        return "Jason's Proxy Servlet";
    }

    /**
     * Retreives all of the headers from the servlet request and sets them on
     * the proxy request
     * 
     * @param httpServletRequest The request object representing the client's
     *                            request to the servlet engine
     * @param httpMethodProxyRequest The request that we are about to send to
     *                                the proxy host
     */
    @SuppressWarnings("unchecked")
	private void setProxyRequestHeaders(HttpServletRequest httpServletRequest, HttpMethod httpMethodProxyRequest) {
    	// Get an Enumeration of all of the header names sent by the client
		Enumeration enumerationOfHeaderNames = httpServletRequest.getHeaderNames();
		while(enumerationOfHeaderNames.hasMoreElements()) {
			String stringHeaderName = (String) enumerationOfHeaderNames.nextElement();
			if(stringHeaderName.equalsIgnoreCase(STRING_CONTENT_LENGTH_HEADER_NAME))
				continue;
			// As per the Java Servlet API 2.5 documentation:
			//		Some headers, such as Accept-Language can be sent by clients
			//		as several headers each with a different value rather than
			//		sending the header as a comma separated list.
			// Thus, we get an Enumeration of the header values sent by the client
			Enumeration enumerationOfHeaderValues = httpServletRequest.getHeaders(stringHeaderName);
			while(enumerationOfHeaderValues.hasMoreElements()) {
				String stringHeaderValue = (String) enumerationOfHeaderValues.nextElement();
				// In case the proxy host is running multiple virtual servers,
				// rewrite the Host header to ensure that we get content from
				// the correct virtual server
				if(stringHeaderName.equalsIgnoreCase(STRING_HOST_HEADER_NAME)){
					stringHeaderValue = getProxyHostAndPort();
				}
				Header header = new Header(stringHeaderName, stringHeaderValue);
				// Set the same header on the proxy request
				httpMethodProxyRequest.setRequestHeader(header);
			}
		}
    }
    
	// Accessors
    private String getProxyURL(HttpServletRequest httpServletRequest) {
		// Set the protocol to HTTP
		String stringProxyURL = this.getProxyScheme() + this.getProxyHostAndPort();
		// Check if we are proxying to a path other that the document root
		if(!this.getProxyPath().equalsIgnoreCase("")){
			stringProxyURL += this.getProxyPath();
		}
		// Handle the path given to the servlet
		stringProxyURL += httpServletRequest.getPathInfo();
		// Handle the query string
		if(httpServletRequest.getQueryString() != null) {
			stringProxyURL += "?" + httpServletRequest.getQueryString();
		}
		return stringProxyURL;
    }
    
    private String getProxyHostAndPort() {
    	if(this.getProxyPort() == 80) {
    		return this.getProxyHost();
    	} else {
    		return this.getProxyHost() + ":" + this.getProxyPort();
    	}
	}
    
	private String getProxyScheme() {
		return this.stringProxyScheme;
	}
	private void setProxyScheme(String stringProxySchemeNew) {
		this.stringProxyScheme = stringProxySchemeNew;
	}
	private String getProxyHost() {
		return this.stringProxyHost;
	}
	private void setProxyHost(String stringProxyHostNew) {
		this.stringProxyHost = stringProxyHostNew;
	}
	private int getProxyPort() {
		return this.intProxyPort;
	}
	private void setProxyPort(int intProxyPortNew) {
		this.intProxyPort = intProxyPortNew;
	}
	private String getProxyPath() {
		return this.stringProxyPath;
	}
	private void setProxyPath(String stringProxyPathNew) {
		this.stringProxyPath = stringProxyPathNew;
	}
	private int getMaxFileUploadSize() {
		return this.intMaxFileUploadSize;
	}
    private void setMaxFileUploadSize(int intMaxFileUploadSizeNew) {
		this.intMaxFileUploadSize = intMaxFileUploadSizeNew;
	}
    public String getLoginUri() {
        return loginUri;
    }
    public void setLoginUri(String loginUri) {
        this.loginUri = loginUri;
    }
}
