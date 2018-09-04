/**
Spotify-HE Connect - (c) 2018 Mitch Pond

Service manager app for Hubitat Elevation to enable status display and control of Spotify Connect players

**/

definition(
    name:           "Spotify-HE Connect",
    namespace:      "mitchpond",
    author:         "Mitch Pond",
    description:    "Service manager app for Hubitat Elevation to enable status display and control of Spotify Connect players",
    category:       "Music",
    iconUrl:        "",
    iconX2Url:      "",
    oauth:          true,
    singleInstance: true
)

private getSpotifyClientId() { "3777d6e19dad4b46851423d34ffee2a0" }

mappings {
    path("/oauth/callback")     {action:    [GET:  "callback"]}
    //path("/oauth/initialize")   {action:    [GET:   "oAuthInit"]}
}

preferences {
    page(name: "Authorize", title: "Log in to Spotify", content: "authPage", nextPage: "", install: "true")
}

def installed() {
    log.debug("Installed Spotify-HE Connect service manager")
    //TODO
}

def updated() {
    //TODO
}

def initOAuth(){
    //TODO
}
        
def authPage() {
    getAccessToken()
    log.debug "Full API Server URL: ${getFullApiServerUrl()}"
    log.debug "API Server URL: ${getApiServerUrl()}"
    log.debug state.accessToken
    def redirectUrl = "https://oauth.cloud.hubitat.com/oauth/initialize?app_id=${app.id}&access_token=${state.accessToken}&apiServerUrl=${getApiServerUrl()}"
    
    return dynamicPage(name: "auth", title: "Login", nextPage: "", uninstall: false) {
            section() {
                paragraph "Click below to connect with Spotify"
                href url: oAuthInit(), style: "embedded", required: true, title: "Spotify Connect", description: "Click to connect"
            }
    }
}
//This is clearly not the proper way to redirect, but I'm throwing things at the wall to see if anything sticks until I can get clarification
def oAuthInit() {
    state.oAuthInitState = UUID.randomUUID().toString()
    log.debug "Sending oAuth state of: ${state.oAuthInitState}"
    log.debug "Cloud API URL: ${getFullApiServerUrl()}"
    
    def oAuthParams = [ response_type:  "code",
                        client_id:      getSpotifyClientId(),
                        redirect_uri:   "https://cloud.hubitat.com/api/cffd7747-9fa5-4fd9-97c8-7de7027f4425/apps/110/oauth/callback",
                        state:          "${state.oAuthInitState}",
                        scope:          "user-read-playback-state",
                        show_dialog:    "false"]
    def redirectUrl = "https://accounts.spotify.com/authorize?${toQueryString(oAuthParams)}"
    return redirectUrl
}

def callback(){
    log.debug response
    "Hello"
}

def getAccessToken(){
    state.accessToken = state.accessToken? it : createAccessToken()
}

String toQueryString(Map m) {
	return m.collect { k, v -> "${k}=${URLEncoder.encode(v.toString())}" }.sort().join("&")
}