/**
 *  Spotify-HE Connect
 *
 *  Copyright 2021 Mitch Pond
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
import groovy.json.JsonOutput

definition(
    name:           "Spotify-HE Connect",
    namespace:      "mitchpond",
    author:         "Mitch Pond",
    description:    "Service manager app for Hubitat Elevation to enable status display and control of Spotify Connect players",
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
private getHubitatRedirectURI() {"https://cloud.hubitat.com/oauth/stateredirect"}
private getCallbackUrl() { "${fullApiServerUrl}/oauth/callback" }
private getInitUrl() { "${fullApiServerUrl}/oauth/initialize" }
private getApiScopes() { "user-read-playback-state user-modify-playback-state" }
private getApiUrl()	{ "https://api.spotify.com" }
private getTokenUrl() { "https://accounts.spotify.com/api/token" }
private getSpotifyListDevicesEndpoint() { "/v1/me/player/devices" }
private getSpotifyNowPlayingEndpoint() { "/v1/me/player" }
private getSpotifyVolumeEndpoint() { "/v1/me/player/volume"}
private getOAuthStateString() {"${getHubUID()}/apps/${app.id}/oauth/callback?access_token=${state.accessToken}"}

mappings {
    path("/oauth/initialize")   {action:    [GET:  "oauthInitUrl"]}
    path("/oauth/callback")     {action:    [GET:  "callback"]}
    path("/callback")     {action:    [GET:  "callback"]}
}

preferences {
    page(name: "setup")
    page(name: "authPage")
}

def setup() {
    dynamicPage(name: "setup", title: "Spotify (Connect) for Hubitat Elevation/SmartThings", install: true, uninstall: true){
        section{
			paragraph "You will need to create a Spotify Developer account to use this app.\n" +
					  "Your redirect URL and access token can be found in the OAuth section below."
            href(page: "authPage", title: "Manage Spotify permissions")
        }
    }
}
      
def authPage() {
    log.debug "Entering Auth page..."
    if(!state.accessToken) createAccessToken()

    if(!state.authToken){
    	log.debug "No authToken yet, sending user to Spotify..."
        log.debug "OAuth state string: ${OAuthStateString}"
    	def oAuthParams = [ 
                        response_type:  "code",
                        client_id:      spotifyClientId,
					    redirect_uri:   hubitatRedirectURI,
                        state:          OAuthStateString,
                        scope:          apiScopes,
                        show_dialog:    "true"
        ]
        
   	 	return dynamicPage(name: "authPage", title: "Login", nextPage: "") {
            section {
                paragraph "Click below to connect with Spotify"
                href(url: "https://accounts.spotify.com/authorize?${toQueryString(oAuthParams)}", style: "external", title: "Spotify Connect", description: "Click to connect")
            }
    	}
    }
    else {
    	log.debug "authToken found"
        getSpotifyDevices()
        return dynamicPage(name: "authPage", title: "Connected to Spotify", nextPage: "") {
            section {
                paragraph "You are logged in to Spotify"
                href(url:"", title: "Log out of Spotify",enabled: false, description: "")
            }
        	section {
            	paragraph "Select devices to install.\nYou may return to this menu to add or remove devices at any time."
                input(name: "selectedDevices", type: "enum", title: "Devices", multiple: true, required: false, options: getSettingsDeviceList())
            }
        }
    }
}

def callback(){
    log.debug "Entering OAuth callback..."
    log.debug "Received code: ${params.code}"
    log.debug "Recevied state: ${params.state}"
    
    if (params.state==OAuthStateString) {
    	def reqURI = "https://accounts.spotify.com/api/token"
    	def reqBody = [	grant_type:		"authorization_code",
                       code:			params.code,
                       redirect_uri:   	hubitatRedirectURI,
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
        
		if (state.authToken) {
			log.debug "Successfully connected!"
			render contentType: "text/html", data: "<P>Spotify auth successful! Close this window to continue setup.</P>"
		}
		else {
			log.debug "Something went wrong!"
			render contentType: "text/html", data: "<P>Spotify auth failed! Close this window to return to setup.</P>"
		}
    }
	else {
		log.debug "Params state does not match state returned from Spotify!"
		render contentType: "text/html", data: "<P>Spotify auth failed! CLose this window to return to setup.</P>"
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

def installed() {
    log.debug("Installed Spotify-HE Connect service manager")
    initialize()
}

def updated() {
    unschedule()
    initialize()
}

def initialize() {
    createChildDevices()
    poll()
    runEvery5Minutes(poll)
}

def poll() {
    updateNowPlaying()
}

def getSpotifyDevices() {
	refreshAuthToken()

	def reqUri = apiUrl + spotifyListDevicesEndpoint
    def reqHeader = [Authorization: "Bearer ${state.authToken}"]

    httpGet(uri: reqUri, headers: reqHeader) { resp ->
    	//log.debug resp.data
        state.spotifyDevices = resp.data
    }
    updateDeviceMap()
    //updateNowPlaying()
}

def updateDeviceMap() {
	if (!state.deviceMap) state.deviceMap = [:]
    state.spotifyDevices?.devices.each { device ->
        state.deviceMap[device.id] = device.name
    }
}

def updateNowPlaying(){
    log.debug "Updating Now Playing..."
	getSpotifyNowPlaying()
    def playingDevice = state.spotifyNowPlaying.device?.id ? getChildDevice(state.spotifyNowPlaying.device.id) : null
    if  (state.spotifyNowPlaying.is_playing && playingDevice) {
    	log.debug "We're playing, so update the playing device"
        playingDevice.generateEvent(["status":"playing"])
        playingDevice.generateEvent(["trackDescription":"${state.spotifyNowPlaying.item.name}\n${state.spotifyNowPlaying.item.album.name}\n${state.spotifyNowPlaying.item.artists[0].name}"])
        playingDevice.generateEvent(["level":state.spotifyNowPlaying.device.volume_percent])

        runIn((state.spotifyNowPlaying.item.duration_ms - state.spotifyNowPlaying.progress_ms)/1000, updateNowPlaying)
    } else {
    	log.debug "Not playing. Updating devices."
        getAllChildDevices()*.generateEvent(["status":"stopped"])
    }
}

def getSettingsDeviceList(){
    def devMap = [:]
    state.spotifyDevices?.devices.each { device ->
        devMap[device.id] = device.name
    }
    return devMap
}

def getSpotifyNowPlaying() {
    refreshAuthToken()

    def reqUri = apiUrl + spotifyNowPlayingEndpoint
    def reqHeader = [Authorization: "Bearer ${state.authToken}"]

    httpGet(uri: reqUri, headers: reqHeader) { resp ->
        if (resp.data) state.spotifyNowPlaying = resp.data
        else state.spotifyNowPlaying = [is_playing: false]
    }
}

boolean setSpotifyPlaybackState(playbackState, device = null, uri = null) {
	//def itemToPlay = [:]
    if (uri) itemToPlay = parseSpotifyUri(uri)
    refreshAuthToken()
	// log.debug "Device: "+device
    def reqUri = apiUrl + spotifyNowPlayingEndpoint + "/${playbackState}"
    def reqHeader = [Authorization: "Bearer ${state.authToken}"]
    def dni = device?.device.deviceNetworkId?: null
    def reqParams = dni? [device_id: dni] : null
    def reqBody = [:]
    // log.debug dni
    // log.debug "Params "+reqParams
    if (itemToPlay?.type in ["album","artist","playlist"]) {
        reqBody = [context_uri: "spotify:${itemToPlay.type}:${itemToPlay.id}"]
    } 
    else if (itemToPlay?.type == "track") {
        reqBody = [uris: ["spotify:${itemToPlay.type}:${itemToPlay.id}"]]
    }
    try {
        if (playbackState in ["play","pause"]) {
            httpPut(uri: reqUri, 
                    headers: reqHeader, 
                    query: reqParams, 
                    body: JsonOutput.toJson(reqBody), 
                    requestContentType: "application/json") 
            { resp ->
                if(resp.status != 204) log.debug "Failed to set playback state!: ${resp.message}"
            }
        } else if (playbackState in ["next","previous"]) {
            
            httpPost(uri: reqUri, headers: reqHeader, query: reqParams) { resp ->
                if(resp.status != 204) log.debug "Failed to set playback state!: ${resp.message}"
            }
        }
    } catch(groovyx.net.http.HttpResponseException e) {
        if (playbackState in ["next","previous"] && e.statusCode == 403)
            log.debug "Could not get ${playbackState} track. End of list."
        else log.debug e
        return false
    }
    return true
}

boolean setSpotifyVolumeLevel(volLevel, device) {
    refreshAuthToken()

    def reqUri = apiUrl + spotifyVolumeEndpoint
    def reqHeader = [Authorization: "Bearer ${state.authToken}"]

    def dni = device?.device.deviceNetworkId?: null
    def reqParams = [device_id: dni, volume_percent: volLevel]

    try {
        httpPut(uri: reqUri, 
                headers: reqHeader, 
                query: reqParams) 
        { resp ->
            if(resp.status == 204) return true
        }
    } catch(groovyx.net.http.HttpResponseException e) {
        if (e.statusCode == 404)
            log.debug "Device ${dni} not found."
        else log.debug e
        return false
    }
    return true
}

def playTrack(device, uri = null) {
    if(setSpotifyPlaybackState("play", device, uri)) device.generateEvent(["status":"playing"])
    updateNowPlaying()
}
//Can't use pause() as this is reserved
def pauseTrack(device) {
    if(setSpotifyPlaybackState("pause", device)) device.generateEvent(["status":"paused"])
    updateNowPlaying()
}

def nextTrack(device) {
    setSpotifyPlaybackState("next", device)
    updateNowPlaying()
}

def previousTrack(device) {
    setSpotifyPlaybackState("previous", device)
    updateNowPlaying()
}

def setLevel(device, volLevel) {
    if (setSpotifyVolumeLevel(volLevel, device)) device.generateEvent(["level":volLevel])
}

def createChildDevices() {
    settings.selectedDevices.each { devId ->
        //log.debug "Checking for device with DNI ${devId}"
        if (getChildDevice(devId)) log.debug "Child device with DNI ${devId} already exists."
        else addChildDevice("mitchpond", "Spotify-Connect-Device", devId, null, [name: "Spotify.${devId}", label: state.deviceMap[devId], completedSetup: true])
    }
}

def removeChildDevice(dni) {
    //TODO
}

def removeAllChildDevices() {
    getAllChildDevices().each { dev ->
    	deleteChildDevice(dev.deviceNetworkId)
    }
}

def getAccessToken(){
    state.accessToken = state.accessToken? it : createAccessToken()
}

//Returns: Map (type: String, id: String)
Map parseSpotifyUri(uri) {
    def webUri = new URI(uri)
    if (webUri.host == "open.spotify.com") {
        def type = webUri.getPath().split("/").getAt(1)
        def id = webUri.getPath().split("/").getAt(2)
        return ["type": type, "id": id]
    }
    return null
}

String toQueryString(Map m) {
	return m.collect { k, v -> "${k}=${URLEncoder.encode(v.toString())}" }.sort().join("&")
}

//SmartThings does not support runInMillis, so this delegates to the proper method for the platform
void runIn(BigDecimal t, String m) {
    if (isHubitat) runInMillis(t*1000,m)
    else runIn(t,m)
}

private isHubitat(){
 	return hubUID != null   
}