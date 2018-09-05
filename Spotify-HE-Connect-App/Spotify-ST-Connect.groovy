/**
 *  Spotify-HE-ST Connect
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

definition(
    name:           "Spotify-HE Connect",
    namespace:      "mitchpond",
    author:         "Mitch Pond",
    description:    "Service manager app for Hubitat Elevation and SmartThings to enable status display and control of Spotify Connect players",
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
private getSpotifyListDevicesEndpoint() { "/v1/me/player/devices" }
private getSpotifyNowPlayingEndpoint() { "/v1/me/player/currently-playing" }

mappings {
    path("/oauth/initialize")   {action:    [GET:  "oauthInitUrl"]}
    path("/oauth/callback")     {action:    [GET:  "callback"]}
}

preferences {
    page(name: "setup", title: "Spotify (Connect) for Hubitat Elevation/SmartThings", nextPage: "", install: true, uninstall: true)
    page(name: "Credentials", title: "Log in to Spotify", content: "authPage", nextPage: "")
}

def setup() {
    dynamicPage(name: "setup", install: true, uninstall: true){
        section{
            href(page: "Credentials", title: "Manage Spotify permissions")
            //href(page: "Devices", title: "")
        }
    }
}
      
def authPage() {
    if(!state.accessToken) createAccessToken()

    def redirectUrl = "${getApiServerUrl()}/oauth/initialize?appId=${app.id}&access_token=${state.accessToken}&apiServerUrl=${getApiServerUrl()}"
    
    if(!state.authToken){
    	log.debug "No authToken yet, sending user to Spotify..."
   	 	return dynamicPage(name: "Credentials", title: "Login", nextPage: "") {
            section {
                paragraph "Click below to connect with Spotify"
                href(url: redirectUrl, style: "embedded", required: true, title: "Spotify Connect", description: "Click to connect")
            }
    	}
    }
    else {
    	log.debug "authToken found"
        return dynamicPage(name: "Credentials", title: "Connected to Spotify") {
            section {
                paragraph "You are logged in to Spotify"
                href(url:"", title: "Log out of Spotify", description: "")
            }
        	section {
            	paragraph "Select devices to install.\nYou may return to this menu to add or remove devices at any time."
                input(name: "devices", type: "enum", title: "Devices", multiple: true, options: state.spotifyDevices.devices*.name)
            }
        }
    }
}

def oauthInitUrl() {
	log.debug "Entered OAuth init..."
    state.oAuthInitState = UUID.randomUUID().toString()
    //log.debug "Sending oAuth state of: ${state.oAuthInitState}"
    
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
        
        //log.debug "API access token: ${state.authToken}"
        //log.debug "API scope: ${state.scope}"
        //log.debug "API refresh token: ${state.refreshToken}"
        
        if (state.authToken) "Successfully connected!"
    }
}

def installed() {
    log.debug("Installed Spotify-HE Connect service manager")
    //TODO
}

def updated() {
    initialize()
}

def initialize() {

}

def getSpotifyDevices() {
	refreshAuthToken()

	def reqUri = apiUrl + spotifyListDevicesEndpoint
    def reqHeader = [Authorization: "Bearer ${state.authToken}"]

    httpGet(uri: reqUri, headers: reqHeader) { resp ->
    	//log.debug resp.data
        state.spotifyDevices = resp.data
    }
}

def getSpotifyNowPlaying() {
    refreshAuthToken()

    def reqUri = apiUrl + spotifyNowPlayingEndpoint
    def reqHeader = [Authorization: "Bearer ${state.authToken}"]

    httpGet(uri: reqUri, headers: reqHeader) { resp ->
    	log.debug resp.data
        state.spotifyNowPlaying = resp.data
    }
}

def refreshAuthToken() {
	def reqBody = [	grant_type:		"refresh_token",
                    refresh_token:	state.refreshToken]
    def reqHeader = [Authorization: "Basic ${(spotifyClientId + ":" + spotifyClientSecret).bytes.encodeBase64()}"]

    httpPost(uri: tokenUrl, body: reqBody, headers: reqHeader) { resp ->
    	if(resp.status == 200){
        	log.debug "Refreshed Spotify auth token"
    		state.authToken = resp.data.access_token
            state.expiresIn = resp.data.expires_in
        } else log.debug "Error refreshing Spotify auth token"
    }
}

def getChildDevices() {
    //TODO
}

def createChildDevice() {
    //TODO
}

def removeChildDevice() {
    //TODO
}

def removeAllChildDevices() {
    //TODO
}

def getAccessToken(){
    state.accessToken = state.accessToken? it : createAccessToken()
}

String toQueryString(Map m) {
	return m.collect { k, v -> "${k}=${URLEncoder.encode(v.toString())}" }.sort().join("&")
}