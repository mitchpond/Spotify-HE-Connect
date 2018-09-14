/**
 *  Spotify-Connect-Device
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
metadata {
	definition (name: "Spotify-Connect-Device", namespace: "mitchpond", author: "Mitch Pond") {
		//capability "Audio Mute"
		//capability "Audio Track Data"
		//capability "Media Playback"
		//capability "Media Playback Repeat"
		//capability "Media Playback Shuffle"
		//capability "Media Track Control"
		capability "Music Player"
		capability "Refresh"
        capability "Polling"
        
        command "refresh"
	}

	tiles {
		tiles(scale: 2) {
			multiAttributeTile(name: "mediaMulti", type:"mediaPlayer", width:6, height:4) {
				tileAttribute("device.status", key: "PRIMARY_CONTROL") {
					attributeState("paused", label:"Paused",)
					attributeState("playing", label:"Playing")
					attributeState("stopped", label:"Stopped")
				}
				tileAttribute("device.status", key: "MEDIA_STATUS") {
					attributeState("paused", label:"Paused", action:"musicPlayer.play")
					attributeState("playing", label:"Playing", action:"musicPlayer.pause")
					attributeState("stopped", label:"Stopped", action:"musicPlayer.play")
				}
				tileAttribute("device.status", key: "PREVIOUS_TRACK") {
					attributeState("status", action:"musicPlayer.previousTrack", defaultState: true)
				}
				tileAttribute("device.status", key: "NEXT_TRACK") {
					attributeState("status", action:"musicPlayer.nextTrack", defaultState: true)
				}
				tileAttribute ("device.level", key: "SLIDER_CONTROL") {
					attributeState("level", action:"musicPlayer.setLevel")
				}
				tileAttribute ("device.mute", key: "MEDIA_MUTED") {
					attributeState("unmuted", action:"musicPlayer.mute", nextState: "muted")
					attributeState("muted", action:"musicPlayer.unmute", nextState: "unmuted")
				}
				tileAttribute("device.trackDescription", key: "MARQUEE") {
					attributeState("trackDescription", label:"${currentValue}", defaultState: true)
       			}
            }
      		standardTile("refresh", "device.refresh", inactiveLabel: false, decoration: "flat") {
				state "default", label:"", action:"refresh.refresh", icon:"st.secondary.refresh", backgroundColor:"#ffffff"
			}

    main "mediaMulti"
    details(["mediaMulti","refresh"])
}

	}
}

// parse events into attributes
def parse(String description) {
	log.debug "Parsing '${description}'"
	// TODO: handle 'mute' attribute
	// TODO: handle 'audioTrackData' attribute
	// TODO: handle 'playbackStatus' attribute
	// TODO: handle 'supportedPlaybackCommands' attribute
	// TODO: handle 'playbackRepeatMode' attribute
	// TODO: handle 'playbackShuffle' attribute
	// TODO: handle 'supportedTrackControlCommands' attribute
	// TODO: handle 'status' attribute
	// TODO: handle 'level' attribute
	// TODO: handle 'trackDescription' attribute
	// TODO: handle 'trackData' attribute

}

def generateEvent(Map results) {
  results.each { name, value ->
    sendEvent(name: name, value: value)
  }
  return null
}

def refresh(){
	parent.updateNowPlaying()
}

def poll() {
	refresh()
}

// handle commands
void setMute() {
	log.debug "Executing 'setMute'"
	// TODO: handle 'setMute' command
}

void mute() {
	//parent.mute(this)
}

void unmute() {
	log.debug "Executing 'unmute'"
	// TODO: handle 'unmute' command
}

void setPlaybackStatus() {
	log.debug "Executing 'setPlaybackStatus'"
	// TODO: handle 'setPlaybackStatus' command
}

void play() {
	parent.playTrack(this)
}

void pause() {
	parent.pauseTrack(this)
}

void stop() {
	log.debug "Executing 'stop'"
	// TODO: handle 'stop' command
}

void setPlaybackRepeatMode() {
	log.debug "Executing 'setPlaybackRepeatMode'"
	// TODO: handle 'setPlaybackRepeatMode' command
}

void setPlaybackShuffle() {
	log.debug "Executing 'setPlaybackShuffle'"
	// TODO: handle 'setPlaybackShuffle' command
}

void nextTrack() {
	parent.nextTrack(this)
}

void previousTrack() {
	parent.previousTrack(this)
}

//Play Spotify Album/Playlist/Track URI
void playTrack(track) {
	parent.playTrack(this, track)
}

void setLevel() {
	log.debug "Executing 'setLevel'"
	// TODO: handle 'setLevel' command
}

void setTrack() {
	log.debug "Executing 'setTrack'"
	// TODO: handle 'setTrack' command
}

void resumeTrack() {
	log.debug "Executing 'resumeTrack'"
	// TODO: handle 'resumeTrack' command
}

void restoreTrack() {
	log.debug "Executing 'restoreTrack'"
	// TODO: handle 'restoreTrack' command
}