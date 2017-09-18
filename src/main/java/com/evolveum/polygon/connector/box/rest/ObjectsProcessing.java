package com.evolveum.polygon.connector.box.rest;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.List;
import java.util.Set;

import org.apache.http.HttpEntity;
import org.apache.http.StatusLine;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpEntityEnclosingRequestBase;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.client.methods.HttpRequestBase;
import org.apache.http.client.methods.HttpUriRequest;
import org.apache.http.client.utils.URIBuilder;
import org.apache.http.entity.ByteArrayEntity;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClientBuilder;
import org.apache.http.util.EntityUtils;
import org.identityconnectors.common.logging.Log;
import org.identityconnectors.common.security.GuardedString;
import org.identityconnectors.framework.common.exceptions.AlreadyExistsException;
import org.identityconnectors.framework.common.exceptions.ConnectorException;
import org.identityconnectors.framework.common.exceptions.ConnectorIOException;
import org.identityconnectors.framework.common.exceptions.InvalidAttributeValueException;
import org.identityconnectors.framework.common.exceptions.InvalidCredentialException;
import org.identityconnectors.framework.common.exceptions.OperationTimeoutException;
import org.identityconnectors.framework.common.exceptions.PermissionDeniedException;
import org.identityconnectors.framework.common.exceptions.PreconditionFailedException;
import org.identityconnectors.framework.common.exceptions.UnknownUidException;
import org.identityconnectors.framework.common.objects.Attribute;
import org.identityconnectors.framework.common.objects.ConnectorObjectBuilder;
import org.json.JSONObject;

import com.evolveum.polygon.common.GuardedStringAccessor;
import com.evolveum.polygon.connector.box.rest.BoxConnectorConfiguration;

public class ObjectsProcessing {

	private static final Log LOG = Log.getLog(BoxConnector.class);
	private static final String ATTR_AVATAR = "avatar_url";
	private static final String CONTENT_TYPE = "application/json; charset=utf-8";
	private static final String grantType = "refresh_token";
	private static final String TOKEN_ENDPOINT = "/oauth2/token";
	private String accessToken = "";

	BoxConnectorConfiguration configuration;

	public ObjectsProcessing(BoxConnectorConfiguration conf) {
		this.configuration = conf;

	}

	private URIBuilder uri;

	protected URIBuilder getURIBuilder() {
		this.uri = new URIBuilder().setScheme("https").setHost(configuration.getUri());
		return this.uri;
	}

	protected void getAvatarPhoto(JSONObject user, ConnectorObjectBuilder builder, String avatarURL) {

		if (user == null) {
			throw new InvalidAttributeValueException("JSONObject value not provided");
		}
		LOG.info("GET_AVATAR_PHOTO METHOD JSONOBJECT VALUE: {0}", user);

		if (avatarURL == null || avatarURL.isEmpty()) {
			throw new InvalidAttributeValueException("AvatarURL value not provided");
		}
		LOG.info("GET_AVATAR_PHOTO METHOD AVATAR_URL VALUE: {0}", avatarURL);

		String avatar = user.getString(avatarURL);
		GuardedString accessToken = configuration.getAccessToken();

		GuardedStringAccessor accessorToken = new GuardedStringAccessor();
		accessToken.access(accessorToken);

		HttpGet request = new HttpGet(avatar);
		request.setHeader("Content-Type", CONTENT_TYPE);
		request.addHeader("Authorization", accessorToken.getClearString());
		request.addHeader("Accept", "image/jpeg");

		CloseableHttpClient client = HttpClientBuilder.create().build();
		CloseableHttpResponse response = null;

		try {
			response = client.execute(request);
		} catch (IOException e) {
			StringBuilder sb = new StringBuilder();
			sb.append("It is not possible to execute request:").append(request.toString()).append(";")
					.append(e.getLocalizedMessage());
			throw new ConnectorIOException(sb.toString(), e);
		}

		StatusLine statusLine = response.getStatusLine();
		int statusCode = statusLine.getStatusCode();
		LOG.info("StatusCode {0}", String.valueOf(statusCode));

		if (statusCode == 401) {
			LOG.info("ACCESS TOKEN: {0}", "not valid anymore, requesting new access token");
			response = executeRequestWithRefresh(request);
		}
		processResponseErrors(response);
		HttpEntity responseEntity = response.getEntity();
		byte[] byteJPEG = null;
		try {
			byteJPEG = EntityUtils.toByteArray(responseEntity);
		} catch (IOException e) {
			throw new ConnectorIOException();
		}

		builder.addAttribute(ATTR_AVATAR, byteJPEG);

	}

	protected void putFieldIfExists(Set<Attribute> attributes, String fieldName,Class <?> type, JSONObject jo) {
		if (attributes == null || attributes.isEmpty()) {
			throw new InvalidAttributeValueException("Attributes not provided or empty");
		}
		if (fieldName == null || fieldName.isEmpty()) {
			throw new InvalidAttributeValueException("FieldName not provided or empty");
		}
		LOG.info("Fattributes: {0} ", attributes);
		LOG.info("FNAME: {0} ", fieldName);
		if (jo == null) {
			throw new InvalidAttributeValueException("JSONObject not provided or empty");
		}

		Object fieldValue = null;
		if (type == String.class){
			LOG.info("String");
			 fieldValue = getAttr(attributes, fieldName, String.class, null);
		}
		else if (type == Integer.class){
			 fieldValue = getAttr(attributes, fieldName, Integer.class, null);
		}
		else if (type == Boolean.class){
			 fieldValue = getAttr(attributes, fieldName, Boolean.class, null);
		}
		
		LOG.info("FVALUE: {0}", fieldValue);
		if (fieldValue != null) {
			LOG.info("FNAME: {0} ", fieldName);
			LOG.info("FVALUE: {0}", fieldValue);
			jo.put(fieldName, fieldValue);
		}
	}
	
	protected void putChildFieldIfExists(Set<Attribute> attributes, String fieldName, String attrName, Class <?> type, JSONObject jo) {
		if (attributes == null || attributes.isEmpty()) {
			throw new InvalidAttributeValueException("Attributes not provided or empty");
		}
		if (fieldName == null || fieldName.isEmpty()) {
			throw new InvalidAttributeValueException("FieldName not provided or empty");
		}
		LOG.info("Fattributes: {0} ", attributes);
		LOG.info("FNAME: {0} ", fieldName);
		if (jo == null) {
			throw new InvalidAttributeValueException("JSONObject not provided or empty");
		}
		if (attrName == null || attrName.isEmpty()) {
			throw new InvalidAttributeValueException("ATTRName not provided or empty");
		}

		
		Object fieldValue = null;
		if (type == String.class){
			 fieldValue = getAttr(attributes, attrName, String.class, null);
		}
		else if (type == Integer.class){
			 fieldValue = getAttr(attributes, attrName, Integer.class, null);
		}
		else if (type == Boolean.class){
			 fieldValue = getAttr(attributes, attrName, Boolean.class, null);
		}
		LOG.info("FVALUE: {0}", fieldValue);
		if (fieldValue != null) {
			LOG.info("FNAME: {0} ", fieldName);
			LOG.info("FVALUE: {0}", fieldValue);
			jo.put(fieldName, fieldValue);
			
		}
	}

	protected JSONObject callRequest(HttpEntityEnclosingRequestBase request, JSONObject json) {
		if (request == null) {
			throw new InvalidAttributeValueException("Request not provided or empty");
		}
		if (json == null) {
			throw new InvalidAttributeValueException("JSONObject not provided or empty");
		}

		HttpEntity entity = null;
		String result = null;
		LOG.info("REQUEST {0}", request);
		LOG.info("JSON {0}", json);
		try {
			entity = new ByteArrayEntity(json.toString().getBytes("UTF-8"));
		} catch (UnsupportedEncodingException e) {
			StringBuilder sb = new StringBuilder();
			sb.append("Unsupported Encoding when creating object in Box").append(";").append(e.getLocalizedMessage());
			throw new ConnectorException(sb.toString(), e);
		}
		request.setEntity(entity);
		CloseableHttpResponse response = executeRequest(request);
		processResponseErrors(response);

		try {
			result = EntityUtils.toString(response.getEntity());
		} catch (org.apache.http.ParseException e) {
			throw new ConnectorException();
		} catch (IOException e) {
			throw new ConnectorIOException();
		}

		return new JSONObject(result);
	}

	protected JSONObject callRequest(HttpRequestBase request, boolean parseResult) {
		if (request == null) {
			throw new InvalidAttributeValueException("Request not provided or empty");
		}

		CloseableHttpResponse response = executeRequest(request);
		processResponseErrors(response);
		if (!parseResult) {
			return null;
		}

		String result = null;
		try {
			result = EntityUtils.toString(response.getEntity());
		} catch (org.apache.http.ParseException e) {
			throw new ConnectorException();
		} catch (IOException e) {
			throw new ConnectorIOException();
		}

		return new JSONObject(result);

	}

	protected JSONObject callRequest(HttpRequestBase request) {
		if (request == null) {
			throw new InvalidAttributeValueException("Request not provided or empty");
		}

		CloseableHttpResponse response = executeRequest(request);
		processResponseErrors(response);
		String result = null;
		try {
			result = EntityUtils.toString(response.getEntity());
		} catch (org.apache.http.ParseException e) {
			throw new ConnectorException();
		} catch (IOException e) {

			throw new ConnectorIOException();
		}

		return new JSONObject(result);

	}

	protected void getIfExists(JSONObject object, String attrGet, String attrSet, ConnectorObjectBuilder builder) {
		if (object == null) {
			throw new InvalidAttributeValueException("JSONObject not provided or empty");
		}
		if (attrGet == null || attrGet.isEmpty()) {
			throw new InvalidAttributeValueException("AttributeToGet not provided or empty");
		}
		if (attrSet == null || attrSet.isEmpty()) {
			throw new InvalidAttributeValueException("AttributeToSet not provided or empty");
		}

		if (object.has(attrGet)) {
			if (object.get(attrGet) != null && !JSONObject.NULL.equals(object.get(attrGet))) {
				addAttr(builder, attrSet, object.get(attrGet));
			}

		}

	}

	protected void getIfExists(JSONObject object, String attrName, ConnectorObjectBuilder builder) {
		if (object == null) {
			throw new InvalidAttributeValueException("JSONObject not provided or empty");
		}
		if (attrName == null || attrName.isEmpty()) {
			throw new InvalidAttributeValueException("AttributeName not provided or empty");
		}
		if (object.has(attrName)) {
			if (object.get(attrName) != null && !JSONObject.NULL.equals(object.get(attrName))) {
				addAttr(builder, attrName, object.get(attrName));
			}

		}

	}

	private String refreshToken() {
		URI uri = null;
		URIBuilder uriBuilder = getURIBuilder();

		GuardedString refreshToken = configuration.getRefreshToken();
		GuardedStringAccessor accessorToken = new GuardedStringAccessor();
		refreshToken.access(accessorToken);

		GuardedString clientSecret = configuration.getClientSecret();
		GuardedStringAccessor accessorSecret = new GuardedStringAccessor();
		clientSecret.access(accessorSecret);

		CloseableHttpClient client = HttpClientBuilder.create().build();

		uriBuilder.setPath(TOKEN_ENDPOINT);

		try {
			uri = uriBuilder.build();
		} catch (URISyntaxException e) {
			StringBuilder sb = new StringBuilder();
			sb.append("It is not possible to create URI from URIBuilder:").append(getURIBuilder().toString())
					.append(";").append(e.getLocalizedMessage());
			throw new ConnectorException(sb.toString(), e);
		}

		HttpPost post = new HttpPost(uri);
		post.setHeader("Content-Type", CONTENT_TYPE);

		StringBuilder sb = new StringBuilder();
		sb.append("grant_type").append('=').append(grantType).append('&').append("refresh_token").append('=')
				.append(accessorToken.getClearString()).append('&').append("client_id").append('=')
				.append(configuration.getClientId()).append('&').append("client_secret").append('=')
				.append(accessorSecret.getClearString());

		StringEntity entity = null;

		try {
			entity = new StringEntity(sb.toString());
		} catch (UnsupportedEncodingException e) {
			StringBuilder sbuilder = new StringBuilder();
			sbuilder.append("Unsupported Encoding when creating object in Box").append(";")
					.append(e.getLocalizedMessage());
			throw new ConnectorException(sb.toString(), e);
		}

		post.setEntity(entity);
		CloseableHttpResponse response = null;

		try {
			response = (CloseableHttpResponse) client.execute(post);
		} catch (IOException e) {
			StringBuilder sbPost = new StringBuilder();
			sb.append("It was not possible execute HttpUriRequest:").append(post).append(";")
					.append(e.getLocalizedMessage());
			throw new ConnectorIOException(sbPost.toString(), e);
		}

		processResponseErrors(response);

		JSONObject json = null;
		try {
			json = new JSONObject(EntityUtils.toString(response.getEntity()));
		} catch (IOException e) {
			StringBuilder sbJSON = new StringBuilder();
			sb.append("Not possible to get response").append(response).append(";").append(e.getLocalizedMessage());
			throw new ConnectorIOException(sbJSON.toString(), e);
		}
		String accessTokenJson = (String) json.get("access_token");
		String token = (String) json.get("refresh_token");

		GuardedString refreshTokenSet = new GuardedString(new String(token).toCharArray());
		configuration.setRefreshToken(refreshTokenSet);
		LOG.info("NewRefreshToken: {0}", token);

		GuardedString accessTokenSet = new GuardedString(new String(accessTokenJson).toCharArray());
		configuration.setAccessToken(accessTokenSet);

		accessToken = "Bearer " + accessTokenJson;
		LOG.info("accessToken: {0}", accessToken);

		return accessToken;

	}

	protected String getStringAttr(Set<Attribute> attributes, String attrName) {
		if (attributes == null || attributes.isEmpty()) {
			throw new InvalidAttributeValueException("Attributes not provided or empty");
		}
		if (attrName == null || attrName.isEmpty()) {
			throw new InvalidAttributeValueException("AttrName not provided or empty");
		}
		return getAttr(attributes, attrName, String.class);
	}

	protected <T> T getAttr(Set<Attribute> attributes, String attrName, Class<T> type) {
		if (attributes == null || attributes.isEmpty()) {
			throw new InvalidAttributeValueException("Attributes not provided or empty");
		}
		if (attrName == null || attrName.isEmpty()) {
			throw new InvalidAttributeValueException("AttrName not provided or empty");
		}
		return getAttr(attributes, attrName, type, null);
	}

	protected <T> T getAttr(Set<Attribute> attributes, String attrName, Class<T> type, T defaultVal) {
		if (attributes == null || attributes.isEmpty()) {
			throw new InvalidAttributeValueException("Attributes not provided or empty");
		}
		if (attrName == null || attrName.isEmpty()) {
			throw new InvalidAttributeValueException("AttrName not provided or empty");
		}
		for (Attribute attr : attributes) {
			if (attrName.equals(attr.getName())) {
				LOG.info("1");
				List<Object> vals = attr.getValue();
				if (vals == null || vals.isEmpty()) {
					return defaultVal;
				}
				if (vals.size() == 1) {
					LOG.info("2");
					Object val = vals.get(0);
					if (val == null) {
						return defaultVal;
					}
					if (type.isAssignableFrom(val.getClass())) {
						return (T) val;
					}
					throw new InvalidAttributeValueException(
							"Unsupported type " + val.getClass() + " for attribute " + attrName);
				}
				throw new InvalidAttributeValueException("More than one value for attribute " + attrName);
			}
		}
		return defaultVal;
	}

	protected <T> void addAttr(ConnectorObjectBuilder builder, String attrName, T attrVal) {
		if (attrName == null || attrName.isEmpty()) {
			throw new InvalidAttributeValueException("AttrName not provided or empty");
		}
		if (attrVal == null) {
			throw new InvalidAttributeValueException("AttrName not provided or empty");
		}
		builder.addAttribute(attrName, attrVal);

	}

	public void processResponseErrors(CloseableHttpResponse response) {
		if (response == null) {
			throw new InvalidAttributeValueException("Response not provided ");
		}
		int statusCode = response.getStatusLine().getStatusCode();
		if (statusCode >= 200 && statusCode <= 299) {
			return;
		}
		String responseBody = null;
		try {
			responseBody = EntityUtils.toString(response.getEntity());
		} catch (IOException e) {
			LOG.warn("cannot read response body: " + e, e);
		}
		LOG.info("ERROR {0}: ", response.getStatusLine().getReasonPhrase());
		String message = "HTTP error " + statusCode + " " + response.getStatusLine().getReasonPhrase() + " : "
				+ responseBody;
		LOG.error("{0}", message);
		if (statusCode == 400 && message.contains("The client credentials are invalid")) {
			closeResponse(response);
			throw new InvalidCredentialException(message);
		}
		if (statusCode == 400 || statusCode == 405 || statusCode == 406) {
			closeResponse(response);
			throw new ConnectorIOException(message);
		}
		if (statusCode == 401 || statusCode == 402 || statusCode == 403 || statusCode == 407) {
			closeResponse(response);
			throw new PermissionDeniedException(message);
		}
		if (statusCode == 404 || statusCode == 410) {
			closeResponse(response);
			throw new UnknownUidException(message);
		}
		if (statusCode == 408) {
			closeResponse(response);
			throw new OperationTimeoutException(message);
		}
		if (statusCode == 409) {
			closeResponse(response);
			throw new AlreadyExistsException(message);
		}
		if (statusCode == 412) {
			closeResponse(response);
			throw new PreconditionFailedException(message);
		}
		if (statusCode == 418) {
			closeResponse(response);
			throw new UnsupportedOperationException("Sorry, no cofee: " + message);
		}

		closeResponse(response);
		throw new ConnectorException(message);
	}

	protected void closeResponse(CloseableHttpResponse response) {
		
		try {
			response.close();
		} catch (IOException e) {
			LOG.warn(e, "Error when trying to close response: " + response);
		}
	}

	public CloseableHttpResponse executeRequest(HttpUriRequest request) {
		if (request == null) {
			throw new InvalidAttributeValueException("Response not provided");
		}

		GuardedString accessTokenConf = configuration.getAccessToken();
		GuardedStringAccessor accessorToken = new GuardedStringAccessor();
		accessTokenConf.access(accessorToken);
		if (accessorToken.getClearString().isEmpty()) {
			refreshToken();

		}

		String accessToken = "Bearer " + accessorToken.getClearString();
		request.setHeader("Content-Type", CONTENT_TYPE);
		request.addHeader("Authorization", accessToken);
		CloseableHttpClient client = HttpClientBuilder.create().build();
		CloseableHttpResponse response = null;
		try {
			response = client.execute(request);
		} catch (IOException e) {
			StringBuilder sb = new StringBuilder();
			sb.append("It is not possible to execute request:").append(request.toString()).append(";")
					.append(e.getLocalizedMessage());
			throw new ConnectorIOException(sb.toString(), e);
		}
		int statusCode = response.getStatusLine().getStatusCode();
		if (statusCode == 401) {
			LOG.info("ACCESS TOKEN: {0}", "not valid anymore, requesting new access token");
			return executeRequestWithRefresh(request);
		}
		return response;

	}

	public CloseableHttpResponse executeRequestWithRefresh(HttpUriRequest request) {
		if (request == null) {
			throw new InvalidAttributeValueException("Response not provided");
		}
		request.setHeader("Content-Type", CONTENT_TYPE);
		request.removeHeaders("Authorization");
		request.addHeader("Authorization", refreshToken());
		CloseableHttpClient client = HttpClientBuilder.create().build();
		CloseableHttpResponse response = null;
		try {
			response = client.execute(request);

		} catch (IOException e) {
			StringBuilder sb = new StringBuilder();
			sb.append("It is not possible to execute request:").append(request.toString()).append(";")
					.append(e.getLocalizedMessage());
			throw new ConnectorIOException(sb.toString(), e);
		}
		return response;
	}

}
