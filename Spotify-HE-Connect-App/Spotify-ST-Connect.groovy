/**
 *  Spotify-HE Connect
 *
 *  Copyright 2018 Mitch Pond
 *
 *  Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
 *  in compliance with the License. You may obtain a copy of the License at:
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software distributed under the License is distributed
 *  on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License
 *  for the specific language governing permissions and limitations under the License.
 *
 */
 /**
Spotify-HE Connect - (c) 2018 Mitch Pond

Service manager app for Hubitat Elevation to enable status display and control of Spotify Connect players

**/

definition(
    name:           "Spotify-HE Connect",
    namespace:      "mitchpond",
    author:         "Mitch Pond",
    description:    "Service manager app for Hubitat Elevation to enable status display and control of Spotify Connect players",
    category:       "Fun & Social",
    iconUrl: 		"https://s3.amazonaws.com/smartapp-icons/Convenience/Cat-Convenience.png",
    iconX2Url: 		"https://s3.amazonaws.com/smartapp-icons/Convenience/Cat-Convenience@2x.png",
    iconX3Url: 		"https://s3.amazonaws.com/smartapp-icons/Convenience/Cat-Convenience@2x.png",
    oauth:          true,
    usesThirdPartyAuthentication: true,
    singleInstance: true
) {
	appSetting "clientId"
    appSetting "clientSecret"
    appSetting "serverUrl"
}

private getSpotifyClientId() { "3777d6e19dad4b46851423d34ffee2a0" }
private getSpotifyClientSecret() { "eb9a1caad5f34ababb6bf494b9ce0364" }
private getCallbackUrl() { "https://graph.api.smartthings.com/oauth/callback" }
private getApiScopes() { "user-read-playback-state" }
private getApiUrl()	{ "https://api.spotify.com" }
private getTokenUrl() { "https://accounts.spotify.com/api/token" }
private getListDevicesEndpoint() { "/v1/me/player/devices" }

mappings {
    path("/oauth/initialize")   {action:    [GET:  "oauthInitUrl"]}
    path("/oauth/callback")     {action:    [GET:  "callback"]}
}

preferences {
    page(name: "Authorize", title: "Log in to Spotify", content: "authPage", nextPage: "", install: "false")
}

def installed() {
    log.debug("Installed Spotify-HE Connect service manager")
    //TODO
}

def updated() {
    //TODO
}
      
def authPage() {
    if(!state.accessToken) createAccessToken()
    log.debug "API Server URL: ${getApiServerUrl()}"
    log.debug state.accessToken
    def redirectUrl = "${getApiServerUrl()}/oauth/initialize?appId=${app.id}&access_token=${state.accessToken}&apiServerUrl=${getApiServerUrl()}"
    if(!state.authToken){
    	log.debug "No authToken yet, sending user to Spotify..."
   	 	return dynamicPage(name: "Authorize", title: "Login", nextPage: "", uninstall: true) {
            section {
                paragraph "Click below to connect with Spotify"
                log.debug "Redirect URL: ${redirectUrl}"
                href(url: redirectUrl, style: "embedded", required: true, title: "Spotify Connect", description: "Click to connect")
            }
    	}
    }
    else {
    	log.debug "authToken found"
        getSpotifyDevices()
        return dynamicPage(name: "Authorize", title: "Connected to Spotify", install: true, uninstall: true) {
        	section {
            	paragraph "Select your devices"
                input(name: "devices", type: "enum", title: "Devices", multiple: true, options: state.spotifyDevices.devices*.name)
            }
        }
    }
}

def oauthInitUrl() {
	log.debug "Entered OAuth init..."
    state.oAuthInitState = UUID.randomUUID().toString()
    log.debug "Sending oAuth state of: ${state.oAuthInitState}"
    
    def oAuthParams = [ response_type:  "code",
                        client_id:      spotifyClientId,
                        redirect_uri:   callbackUrl,
                        state:          "${state.oAuthInitState}",
                        scope:          "user-read-playback-state",
                        show_dialog:    "false"]
    redirect(location: "https://accounts.spotify.com/authorize?${toQueryString(oAuthParams)}")
}

def callback(){
    log.debug "Entered callback..."
    //log.debug "Received code: ${params.code}"
    //log.debug "Recevied state: ${params.state}"
    
    if (params.state==state.oAuthInitState) {
    	def reqURI = "https://accounts.spotify.com/api/token"
    	def reqBody = [	grant_type:		"authorization_code",
                       code:			params.code,
                       redirect_uri:   	callbackUrl,
                       client_id:		spotifyClientId,
                       client_secret:	spotifyClientSecret]
        
        httpPost(uri: reqURI, body:	reqBody) { resp ->
        	state.authToken = resp.data.access_token
            state.scope = resp.data.scope
            state.refreshToken = resp.data.refresh_token
            state.expiresIn = resp.data.expires_in
        }
        
        log.debug "API access token: ${state.authToken}"
        log.debug "API scope: ${state.scope}"
        log.debug "API refresh token: ${state.refreshToken}"
        
        if (state.authToken) "Successfully connected!"
    }
}

def getSpotifyDevices() {
	getNewAuthToken()

	def reqUri = apiUrl + listDevicesEndpoint
    def reqHeader = [Authorization: "Bearer ${state.authToken}"]
    
    //log.debug reqUri
    //log.debug reqHeader
    
    httpGet(uri: reqUri, headers: reqHeader) { resp ->
    	//log.debug resp.data
        state.spotifyDevices = resp.data
    }
}

def getNewAuthToken() {
	def reqBody = [	grant_type:		"refresh_token",
                    refresh_token:	state.refreshToken]
    def reqHeader = [Authorization: "Basic ${(spotifyClientId + ":" + spotifyClientSecret).bytes.encodeBase64()}"]
    //log.debug reqHeader
    httpPost(uri: tokenUrl, body: reqBody, headers: reqHeader) { resp ->
    	if(resp.status == 200){
        	log.debug "Refreshed Spotify auth token"
    		state.authToken = resp.data.access_token
            state.expiresIn = resp.data.expires_in
        } else log.debug "Error refreshing Spotify auth token"
    }
}

def getAccessToken(){
    state.accessToken = state.accessToken? it : createAccessToken()
}

String toQueryString(Map m) {
	return m.collect { k, v -> "${k}=${URLEncoder.encode(v.toString())}" }.sort().join("&")
}