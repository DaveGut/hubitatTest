/*	TP-Link TAPO plug, switches, lights, hub, and hub sensors.
		Copyright Dave Gutheinz
License:  https://github.com/DaveGut/HubitatActive/blob/master/KasaDevices/License.md

Name Change to TpLink Parent.

Verified on TP25(US) and P306(US)
=================================================================================================*/

metadata {
	definition (name: "TpLink Parent", namespace: nameSpace(), author: "Dave Gutheinz", 
				singleThreaded: true,
				importUrl: "https://raw.githubusercontent.com/DaveGut/tpLink_Hubitat/main/Drivers/tpLink_parent.groovy")
	{
	}
	preferences {
		input ("ledRule", "enum", title: "LED Mode (if night mode, set type and times in phone app)",
			   options: ["always", "never", "night_mode"], defaultValue: "always")
		input ("installChild", "bool", title: "Install Child Devices", defaultValue: true)
		commonPreferences()
	}
}

def installed() {
	Map logData = [method: "installed", commonInstalled: commonInstalled()]
	if (installChild) {
		logData << [children: "installing"]
		runIn(5, installChildDevices)
		pauseExecution(5000)
	}
	logInfo(logData)
}

def updated() { 
	Map logData = [method: updated, installChild: installChild,
				   commonUpdated: commonUpdated()]
	if (installChild) {
		runIn(5, installChildDevices)
		pauseExecution(5000)
	}
	logInfo(logData)
}

def parse_get_device_info(result, data) {
	Map logData = [method: "parse_get_device_info", data: data]
	logDebug(logData)
}

//	===== Child Command Response =====
def parse_get_child_device_list(result, data) {
	Map logData = [method: "get_child_device_list",data: data]
	def children = getChildDevices()
	children.each { child ->
		def devId = child.getDataValue("deviceId")
		def childData = result.child_device_list.find{ it.device_id == devId }
		child.parse_get_device_info(childData, data)
	}
	logData << [status: "OK"]
	logDebug(logData)
}

def childRespDist(resp, data) {
	def respData = parseData(resp).cmdResp
	if (respData.error_code== 0) {
		def child = getChildDevice(data.data)
		if (child != null) {
			child.distChildData(respData.result.responseData.result.responses, data)
		} else {
			logWarn([method: "childRespDist", data: data, status: "notChild"])
		}
	} else {
		logWarn([method: "childRespDist", data: data, error: respData.error_code, status: "errorInResp"])
	}
}

//	===== Include Libraries =====






// ~~~~~ start include (35) davegut.tpLinkCommon ~~~~~
library ( // library marker davegut.tpLinkCommon, line 1
	name: "tpLinkCommon", // library marker davegut.tpLinkCommon, line 2
	namespace: "davegut", // library marker davegut.tpLinkCommon, line 3
	author: "Compied by Dave Gutheinz", // library marker davegut.tpLinkCommon, line 4
	description: "Common driver methods including capability Refresh and Configuration methods", // library marker davegut.tpLinkCommon, line 5
	category: "utilities", // library marker davegut.tpLinkCommon, line 6
	documentationLink: "" // library marker davegut.tpLinkCommon, line 7
) // library marker davegut.tpLinkCommon, line 8

capability "Refresh" // library marker davegut.tpLinkCommon, line 10
capability "Configuration" // library marker davegut.tpLinkCommon, line 11
attribute "commsError", "string" // library marker davegut.tpLinkCommon, line 12

def commonPreferences() { // library marker davegut.tpLinkCommon, line 14
	List pollOptions = ["5 sec", "10 sec", "30 sec", "5 min", "10 min", "15 min", "30 min"] // library marker davegut.tpLinkCommon, line 15
	input ("pollInterval", "enum", title: "Poll/Refresh Interval", // library marker davegut.tpLinkCommon, line 16
		   options: pollOptions, defaultValue: "30 min") // library marker davegut.tpLinkCommon, line 17
	input ("logEnable", "bool",  title: "Enable debug logging for 30 minutes", defaultValue: false) // library marker davegut.tpLinkCommon, line 18
	input ("infoLog", "bool", title: "Enable information logging",defaultValue: true) // library marker davegut.tpLinkCommon, line 19
} // library marker davegut.tpLinkCommon, line 20

def commonInstalled() { // library marker davegut.tpLinkCommon, line 22
	updateAttr("commsError", "false") // library marker davegut.tpLinkCommon, line 23
	state.errorCount = 0 // library marker davegut.tpLinkCommon, line 24
	state.lastCmd = "" // library marker davegut.tpLinkCommon, line 25
	state.eventType = "digital" // library marker davegut.tpLinkCommon, line 26
	Map logData = [configure: configure(false)] // library marker davegut.tpLinkCommon, line 27
	return logData // library marker davegut.tpLinkCommon, line 28
} // library marker davegut.tpLinkCommon, line 29

def commonUpdated() { // library marker davegut.tpLinkCommon, line 31
	def commsErr = device.currentValue("commsError") // library marker davegut.tpLinkCommon, line 32
	Map logData = [commsError: commsErr] // library marker davegut.tpLinkCommon, line 33
	if (commsErr == "true") { // library marker davegut.tpLinkCommon, line 34
		logData << [configure: configure(true)] // library marker davegut.tpLinkCommon, line 35
	} // library marker davegut.tpLinkCommon, line 36
	updateAttr("commsError", "false") // library marker davegut.tpLinkCommon, line 37
	state.errorCount = 0 // library marker davegut.tpLinkCommon, line 38
	state.lastCmd = "" // library marker davegut.tpLinkCommon, line 39
	logData << [pollInterval: setPollInterval()] // library marker davegut.tpLinkCommon, line 40
	logData << [logging: setLogsOff()] // library marker davegut.tpLinkCommon, line 41
	logData << [updateDevSettings: updDevSettings()] // library marker davegut.tpLinkCommon, line 42
	pauseExecution(5000) // library marker davegut.tpLinkCommon, line 43
	return logData // library marker davegut.tpLinkCommon, line 44
} // library marker davegut.tpLinkCommon, line 45

def updDevSettings() { // library marker davegut.tpLinkCommon, line 47
	Map logData = [method: "updDevSettings"] // library marker davegut.tpLinkCommon, line 48
	List requests = [] // library marker davegut.tpLinkCommon, line 49
	if (ledRule != null) { // library marker davegut.tpLinkCommon, line 50
		logData << [ledRule: ledRule] // library marker davegut.tpLinkCommon, line 51
		requests << [method: "get_led_info"] // library marker davegut.tpLinkCommon, line 52
	} // library marker davegut.tpLinkCommon, line 53
	requests << [method: "get_device_info"] // library marker davegut.tpLinkCommon, line 54
	asyncSend(createMultiCmd(requests), "updDevSettings", "parseUpdates") // library marker davegut.tpLinkCommon, line 55
	pauseExecution(5000) // library marker davegut.tpLinkCommon, line 56
	return logData // library marker davegut.tpLinkCommon, line 57
} // library marker davegut.tpLinkCommon, line 58

//	===== Capability Configuration ===== // library marker davegut.tpLinkCommon, line 60
def configure(checkApp = true) { // library marker davegut.tpLinkCommon, line 61
	unschedule() // library marker davegut.tpLinkCommon, line 62
	Map logData = [method: "configure"] // library marker davegut.tpLinkCommon, line 63
	if (checkApp == true) { // library marker davegut.tpLinkCommon, line 64
		logData << [updateData: parent.tpLinkCheckForDevices(5)] // library marker davegut.tpLinkCommon, line 65
	} // library marker davegut.tpLinkCommon, line 66
	logData << [handshake: deviceHandshake()] // library marker davegut.tpLinkCommon, line 67
	runEvery3Hours(deviceHandshake) // library marker davegut.tpLinkCommon, line 68
	logData << [handshakeInterval: "3 Hours"] // library marker davegut.tpLinkCommon, line 69
	logData << [pollInterval: setPollInterval()] // library marker davegut.tpLinkCommon, line 70
	logData << [logging: setLogsOff()] // library marker davegut.tpLinkCommon, line 71
	logInfo(logData) // library marker davegut.tpLinkCommon, line 72
	runIn(2, initSettings) // library marker davegut.tpLinkCommon, line 73
	return logData // library marker davegut.tpLinkCommon, line 74
} // library marker davegut.tpLinkCommon, line 75

def initSettings() { // library marker davegut.tpLinkCommon, line 77
	Map logData = [method: "initSettings"] // library marker davegut.tpLinkCommon, line 78
	Map prefs = state.compData // library marker davegut.tpLinkCommon, line 79
	List requests = [] // library marker davegut.tpLinkCommon, line 80
	if (ledRule) { // library marker davegut.tpLinkCommon, line 81
		requests << [method: "get_led_info"] // library marker davegut.tpLinkCommon, line 82
	} // library marker davegut.tpLinkCommon, line 83
	if (getDataValue("type") == "Plug EM") { requests << [method: "get_energy_usage"] } // library marker davegut.tpLinkCommon, line 84
	requests << [method: "get_device_info"] // library marker davegut.tpLinkCommon, line 85
	asyncSend(createMultiCmd(requests), "initSettings", "parseUpdates") // library marker davegut.tpLinkCommon, line 86
	return logData // library marker davegut.tpLinkCommon, line 87
} // library marker davegut.tpLinkCommon, line 88

def setPollInterval(pInterval = pollInterval) { // library marker davegut.tpLinkCommon, line 90
	String devType = getDataValue("type") // library marker davegut.tpLinkCommon, line 91
	def pollMethod = "minRefresh" // library marker davegut.tpLinkCommon, line 92
	if (devType == "Plug EM") { // library marker davegut.tpLinkCommon, line 93
		pollMethod = "plugEmRefresh" // library marker davegut.tpLinkCommon, line 94
	} else if (devType == "Hub"|| devType == "Parent") { // library marker davegut.tpLinkCommon, line 95
		pollMethod = "parentRefresh" // library marker davegut.tpLinkCommon, line 96
	} // library marker davegut.tpLinkCommon, line 97

	if (pInterval.contains("sec")) { // library marker davegut.tpLinkCommon, line 99
		def interval = pInterval.replace(" sec", "").toInteger() // library marker davegut.tpLinkCommon, line 100
		def start = Math.round((interval-1) * Math.random()).toInteger() // library marker davegut.tpLinkCommon, line 101
		schedule("${start}/${interval} * * * * ?", pollMethod) // library marker davegut.tpLinkCommon, line 102
	} else { // library marker davegut.tpLinkCommon, line 103
		def interval= pInterval.replace(" min", "").toInteger() // library marker davegut.tpLinkCommon, line 104
		def start = Math.round(59 * Math.random()).toInteger() // library marker davegut.tpLinkCommon, line 105
		schedule("${start} */${interval} * * * ?", pollMethod) // library marker davegut.tpLinkCommon, line 106
	} // library marker davegut.tpLinkCommon, line 107
	return pInterval // library marker davegut.tpLinkCommon, line 108
} // library marker davegut.tpLinkCommon, line 109

//	===== Data Distribution (and parse) ===== // library marker davegut.tpLinkCommon, line 111
def parseUpdates(resp, data= null) { // library marker davegut.tpLinkCommon, line 112
	Map logData = [method: "parseUpdates", data: data] // library marker davegut.tpLinkCommon, line 113
	def respData = parseData(resp).cmdResp // library marker davegut.tpLinkCommon, line 114
	if (respData != null && respData.error_code == 0) { // library marker davegut.tpLinkCommon, line 115
		respData.result.responses.each { // library marker davegut.tpLinkCommon, line 116
			if (it.error_code == 0) { // library marker davegut.tpLinkCommon, line 117
				if (!it.method.contains("set_")) { // library marker davegut.tpLinkCommon, line 118
					distGetData(it, data) // library marker davegut.tpLinkCommon, line 119
				} else { // library marker davegut.tpLinkCommon, line 120
					logData << [devMethod: it.method] // library marker davegut.tpLinkCommon, line 121
					logDebug(logData) // library marker davegut.tpLinkCommon, line 122
				} // library marker davegut.tpLinkCommon, line 123
			} else { // library marker davegut.tpLinkCommon, line 124
				logData << ["${it.method}": [status: "cmdFailed", data: it]] // library marker davegut.tpLinkCommon, line 125
				logWarn(logData) // library marker davegut.tpLinkCommon, line 126
			} // library marker davegut.tpLinkCommon, line 127
		} // library marker davegut.tpLinkCommon, line 128
	} else { // library marker davegut.tpLinkCommon, line 129
		logData << [status: "invalidRequest", data: respData] // library marker davegut.tpLinkCommon, line 130
		logWarn(logData)				 // library marker davegut.tpLinkCommon, line 131
	} // library marker davegut.tpLinkCommon, line 132
} // library marker davegut.tpLinkCommon, line 133

def distGetData(devResp, data) { // library marker davegut.tpLinkCommon, line 135
	switch(devResp.method) { // library marker davegut.tpLinkCommon, line 136
		case "get_device_info": // library marker davegut.tpLinkCommon, line 137
			parse_get_device_info(devResp.result, data) // library marker davegut.tpLinkCommon, line 138
			break // library marker davegut.tpLinkCommon, line 139
		case "get_energy_usage": // library marker davegut.tpLinkCommon, line 140
			parse_get_energy_usage(devResp.result, data) // library marker davegut.tpLinkCommon, line 141
			break // library marker davegut.tpLinkCommon, line 142
		case "get_child_device_list": // library marker davegut.tpLinkCommon, line 143
			parse_get_child_device_list(devResp.result, data) // library marker davegut.tpLinkCommon, line 144
			break // library marker davegut.tpLinkCommon, line 145
		case "get_alarm_configure": // library marker davegut.tpLinkCommon, line 146
			parse_get_alarm_configure(devResp.result, data) // library marker davegut.tpLinkCommon, line 147
			break // library marker davegut.tpLinkCommon, line 148
		case "get_led_info": // library marker davegut.tpLinkCommon, line 149
			parse_get_led_info(devResp.result, data) // library marker davegut.tpLinkCommon, line 150
			break // library marker davegut.tpLinkCommon, line 151
		default: // library marker davegut.tpLinkCommon, line 152
			Map logData = [method: "distGetData", data: data, // library marker davegut.tpLinkCommon, line 153
						   devMethod: devResp.method, status: "unprocessed"] // library marker davegut.tpLinkCommon, line 154
			logDebug(logData) // library marker davegut.tpLinkCommon, line 155
	} // library marker davegut.tpLinkCommon, line 156
} // library marker davegut.tpLinkCommon, line 157

def parse_get_led_info(result, data) { // library marker davegut.tpLinkCommon, line 159
	Map logData = [method: "parse_get_led_info", data: data] // library marker davegut.tpLinkCommon, line 160
	if (ledRule != result.led_rule) { // library marker davegut.tpLinkCommon, line 161
		Map request = [ // library marker davegut.tpLinkCommon, line 162
			method: "set_led_info", // library marker davegut.tpLinkCommon, line 163
			params: [ // library marker davegut.tpLinkCommon, line 164
				led_rule: ledRule, // library marker davegut.tpLinkCommon, line 165
				//	Uses mode data from device.  This driver does not update these. // library marker davegut.tpLinkCommon, line 166
				night_mode: [ // library marker davegut.tpLinkCommon, line 167
					night_mode_type: result.night_mode.night_mode_type, // library marker davegut.tpLinkCommon, line 168
					sunrise_offset: result.night_mode.sunrise_offset,  // library marker davegut.tpLinkCommon, line 169
					sunset_offset:result.night_mode.sunset_offset, // library marker davegut.tpLinkCommon, line 170
					start_time: result.night_mode.start_time, // library marker davegut.tpLinkCommon, line 171
					end_time: result.night_mode.end_time // library marker davegut.tpLinkCommon, line 172
				]]] // library marker davegut.tpLinkCommon, line 173
		asyncSend(request, "delayedUpdates", "parseUpdates") // library marker davegut.tpLinkCommon, line 174
		device.updateSetting("ledRule", [type:"enum", value: ledRule]) // library marker davegut.tpLinkCommon, line 175
		logData << [status: "updatingLedRule"] // library marker davegut.tpLinkCommon, line 176
	} // library marker davegut.tpLinkCommon, line 177
	logData << [ledRule: ledRule] // library marker davegut.tpLinkCommon, line 178
	logDebug(logData) // library marker davegut.tpLinkCommon, line 179
} // library marker davegut.tpLinkCommon, line 180

//	===== Capability Refresh ===== // library marker davegut.tpLinkCommon, line 182
def refresh() { // library marker davegut.tpLinkCommon, line 183
	def type = getDataValue("type") // library marker davegut.tpLinkCommon, line 184
	if (type == "Plug EM") { // library marker davegut.tpLinkCommon, line 185
		plugEmRefresh() // library marker davegut.tpLinkCommon, line 186
	} else if (type == "Hub" || type == "Parent") { // library marker davegut.tpLinkCommon, line 187
		parentRefresh() // library marker davegut.tpLinkCommon, line 188
	} else { // library marker davegut.tpLinkCommon, line 189
		minRefresh() // library marker davegut.tpLinkCommon, line 190
	} // library marker davegut.tpLinkCommon, line 191
} // library marker davegut.tpLinkCommon, line 192

def plugEmRefresh() { // library marker davegut.tpLinkCommon, line 194
	List requests = [[method: "get_device_info"]] // library marker davegut.tpLinkCommon, line 195
	requests << [method:"get_energy_usage"] // library marker davegut.tpLinkCommon, line 196
	asyncSend(createMultiCmd(requests), "plugEmRefresh", "parseUpdates") // library marker davegut.tpLinkCommon, line 197
} // library marker davegut.tpLinkCommon, line 198

def parentRefresh() { // library marker davegut.tpLinkCommon, line 200
	List requests = [[method: "get_device_info"]] // library marker davegut.tpLinkCommon, line 201
	requests << [method:"get_child_device_list"] // library marker davegut.tpLinkCommon, line 202
	asyncSend(createMultiCmd(requests), "parentRefresh", "parseUpdates") // library marker davegut.tpLinkCommon, line 203
} // library marker davegut.tpLinkCommon, line 204

def minRefresh() { // library marker davegut.tpLinkCommon, line 206
	List requests = [[method: "get_device_info"]] // library marker davegut.tpLinkCommon, line 207
	asyncSend(createMultiCmd(requests), "minRefresh", "parseUpdates") // library marker davegut.tpLinkCommon, line 208
} // library marker davegut.tpLinkCommon, line 209

def sendDevCmd(requests, data, action) { // library marker davegut.tpLinkCommon, line 211
	asyncSend(createMultiCmd(requests), data, action) // library marker davegut.tpLinkCommon, line 212
} // library marker davegut.tpLinkCommon, line 213

def sendSingleCmd(request, data, action) { // library marker davegut.tpLinkCommon, line 215
	asyncSend(request, data, action) // library marker davegut.tpLinkCommon, line 216
} // library marker davegut.tpLinkCommon, line 217

def createMultiCmd(requests) { // library marker davegut.tpLinkCommon, line 219
	Map cmdBody = [ // library marker davegut.tpLinkCommon, line 220
		method: "multipleRequest", // library marker davegut.tpLinkCommon, line 221
		params: [requests: requests]] // library marker davegut.tpLinkCommon, line 222
	return cmdBody // library marker davegut.tpLinkCommon, line 223
} // library marker davegut.tpLinkCommon, line 224

def nullParse(resp, data) { } // library marker davegut.tpLinkCommon, line 226

def updateAttr(attr, value) { // library marker davegut.tpLinkCommon, line 228
	if (device.currentValue(attr) != value) { // library marker davegut.tpLinkCommon, line 229
		sendEvent(name: attr, value: value) // library marker davegut.tpLinkCommon, line 230
	} // library marker davegut.tpLinkCommon, line 231
} // library marker davegut.tpLinkCommon, line 232

//	===== Device Handshake ===== // library marker davegut.tpLinkCommon, line 234
def deviceHandshake() { // library marker davegut.tpLinkCommon, line 235
	def protocol = getDataValue("protocol") // library marker davegut.tpLinkCommon, line 236
	Map logData = [method: "deviceHandshake", protocol: protocol] // library marker davegut.tpLinkCommon, line 237
	if (protocol == "KLAP") { // library marker davegut.tpLinkCommon, line 238
		klapHandshake() // library marker davegut.tpLinkCommon, line 239
	} else if (protocol == "AES") { // library marker davegut.tpLinkCommon, line 240
		aesHandshake() // library marker davegut.tpLinkCommon, line 241
	} else { // library marker davegut.tpLinkCommon, line 242
		logData << [ERROR: "Protocol not supported"] // library marker davegut.tpLinkCommon, line 243
	} // library marker davegut.tpLinkCommon, line 244
	pauseExecution(5000) // library marker davegut.tpLinkCommon, line 245
	return logData // library marker davegut.tpLinkCommon, line 246
} // library marker davegut.tpLinkCommon, line 247

// ~~~~~ end include (35) davegut.tpLinkCommon ~~~~~

// ~~~~~ start include (34) davegut.tpLinkChildInst ~~~~~
library ( // library marker davegut.tpLinkChildInst, line 1
	name: "tpLinkChildInst", // library marker davegut.tpLinkChildInst, line 2
	namespace: "davegut", // library marker davegut.tpLinkChildInst, line 3
	author: "Compiled by Dave Gutheinz", // library marker davegut.tpLinkChildInst, line 4
	description: "Child Installation Methods", // library marker davegut.tpLinkChildInst, line 5
	category: "utilities", // library marker davegut.tpLinkChildInst, line 6
	documentationLink: "" // library marker davegut.tpLinkChildInst, line 7
) // library marker davegut.tpLinkChildInst, line 8

def installChildDevices() { // library marker davegut.tpLinkChildInst, line 10
	Map request = [method: "get_child_device_list"] // library marker davegut.tpLinkChildInst, line 11
	asyncSend(request, "installChildDevices", "installChildren") // library marker davegut.tpLinkChildInst, line 12
} // library marker davegut.tpLinkChildInst, line 13

def installChildren(resp, data=null) { // library marker davegut.tpLinkChildInst, line 15
	Map logData = [method: "installChildren"] // library marker davegut.tpLinkChildInst, line 16
	def respData = parseData(resp) // library marker davegut.tpLinkChildInst, line 17
	def children = respData.cmdResp.result.child_device_list // library marker davegut.tpLinkChildInst, line 18
	children.each { // library marker davegut.tpLinkChildInst, line 19
		String childDni = it.mac // library marker davegut.tpLinkChildInst, line 20
		if (it.position) { // library marker davegut.tpLinkChildInst, line 21
			childDni = childDni + "-" + it.position // library marker davegut.tpLinkChildInst, line 22
		} // library marker davegut.tpLinkChildInst, line 23
		def isChild = getChildDevice(childDni) // library marker davegut.tpLinkChildInst, line 24
		byte[] plainBytes = it.nickname.decodeBase64() // library marker davegut.tpLinkChildInst, line 25
		String alias = new String(plainBytes) // library marker davegut.tpLinkChildInst, line 26
		Map instData = [alias: alias, childDni: childDni] // library marker davegut.tpLinkChildInst, line 27
		if (isChild) { // library marker davegut.tpLinkChildInst, line 28
			instData << [status: "device already installed"] // library marker davegut.tpLinkChildInst, line 29
		} else { // library marker davegut.tpLinkChildInst, line 30
			String devType = getDeviceType(it.category) // library marker davegut.tpLinkChildInst, line 31
			instData << [label: alias, name: it.model, type: devType, deviceId:  // library marker davegut.tpLinkChildInst, line 32
						 it.device_id, category: it.category] // library marker davegut.tpLinkChildInst, line 33
			if (devType == "Child Undefined") { // library marker davegut.tpLinkChildInst, line 34
				instData << [status: "notInstalled", error: "Currently Unsupported"] // library marker davegut.tpLinkChildInst, line 35
				logWarn(instData) // library marker davegut.tpLinkChildInst, line 36
			} else { // library marker davegut.tpLinkChildInst, line 37
				try { // library marker davegut.tpLinkChildInst, line 38
					addChildDevice( // library marker davegut.tpLinkChildInst, line 39
						nameSpace(),  // library marker davegut.tpLinkChildInst, line 40
						"TpLink ${devType}", // library marker davegut.tpLinkChildInst, line 41
						childDni, // library marker davegut.tpLinkChildInst, line 42
						[ // library marker davegut.tpLinkChildInst, line 43
							"label": alias, // library marker davegut.tpLinkChildInst, line 44
							"name": it.model, // library marker davegut.tpLinkChildInst, line 45
							category: it.category, // library marker davegut.tpLinkChildInst, line 46
							deviceId: it.device_id, // library marker davegut.tpLinkChildInst, line 47
							type: devType // library marker davegut.tpLinkChildInst, line 48
						]) // library marker davegut.tpLinkChildInst, line 49
					instData << [status: "Installed"] // library marker davegut.tpLinkChildInst, line 50
				} catch (e) { // library marker davegut.tpLinkChildInst, line 51
					instData << [status: "FAILED", error: err] // library marker davegut.tpLinkChildInst, line 52
					logWarn(instData) // library marker davegut.tpLinkChildInst, line 53
				} // library marker davegut.tpLinkChildInst, line 54
			} // library marker davegut.tpLinkChildInst, line 55
		} // library marker davegut.tpLinkChildInst, line 56
		logData << ["${alias}": instData] // library marker davegut.tpLinkChildInst, line 57
		pauseExecution(2000) // library marker davegut.tpLinkChildInst, line 58
	} // library marker davegut.tpLinkChildInst, line 59
	device.updateSetting("installChild", [type: "bool", value: "false"]) // library marker davegut.tpLinkChildInst, line 60
	logInfo(logData) // library marker davegut.tpLinkChildInst, line 61
} // library marker davegut.tpLinkChildInst, line 62

def getDeviceType(category) { // library marker davegut.tpLinkChildInst, line 64
	String deviceType // library marker davegut.tpLinkChildInst, line 65
	switch(category) { // library marker davegut.tpLinkChildInst, line 66
		case "subg.trigger.contact-sensor": // library marker davegut.tpLinkChildInst, line 67
			deviceType = "Hub Contact"; break // library marker davegut.tpLinkChildInst, line 68
		case "subg.trigger.motion-sensor": // library marker davegut.tpLinkChildInst, line 69
			deviceType = "Hub Motion"; break // library marker davegut.tpLinkChildInst, line 70
		case "subg.trigger.button": // library marker davegut.tpLinkChildInst, line 71
			deviceType = "Hub Button"; break // library marker davegut.tpLinkChildInst, line 72
		case "subg.trigger.temp-hmdt-sensor": // library marker davegut.tpLinkChildInst, line 73
logWarn("TEMP-HUMIDITY Sensor not supported.  Requires TEST Volunteer to finish") // library marker davegut.tpLinkChildInst, line 74
			deviceType = "Child Undefined"; break // library marker davegut.tpLinkChildInst, line 75
//			deviceType = "Hub TempHumidity"; break // library marker davegut.tpLinkChildInst, line 76
		case "subg.trigger.water-leak-sensor": // library marker davegut.tpLinkChildInst, line 77
			deviceType = "Hub Leak"; break // library marker davegut.tpLinkChildInst, line 78
		case "subg.trv": // library marker davegut.tpLinkChildInst, line 79
logWarn("TRV not supported.  Requires TEST Volunteer to finish") // library marker davegut.tpLinkChildInst, line 80
			deviceType = "Child Undefined"; break // library marker davegut.tpLinkChildInst, line 81
//			deviceType = "Hub Trv"; break // library marker davegut.tpLinkChildInst, line 82
		case "plug.powerstrip.sub-plug": // library marker davegut.tpLinkChildInst, line 83
			deviceType = "Child Plug"; break // library marker davegut.tpLinkChildInst, line 84
		case "kasa.switch.outlet.sub-fan": // library marker davegut.tpLinkChildInst, line 85
			deviceType = "Child Fan"; break // library marker davegut.tpLinkChildInst, line 86
		case "kasa.switch.outlet.sub-dimmer": // library marker davegut.tpLinkChildInst, line 87
		case "plug.powerstrip.sub-bulb": // library marker davegut.tpLinkChildInst, line 88
			deviceType = "Child Dimmer"; break // library marker davegut.tpLinkChildInst, line 89
		default: // library marker davegut.tpLinkChildInst, line 90
			deviceType = "Child Undefined" // library marker davegut.tpLinkChildInst, line 91
	} // library marker davegut.tpLinkChildInst, line 92
	return deviceType // library marker davegut.tpLinkChildInst, line 93
} // library marker davegut.tpLinkChildInst, line 94

// ~~~~~ end include (34) davegut.tpLinkChildInst ~~~~~

// ~~~~~ start include (36) davegut.tpLinkComms ~~~~~
library ( // library marker davegut.tpLinkComms, line 1
	name: "tpLinkComms", // library marker davegut.tpLinkComms, line 2
	namespace: "davegut", // library marker davegut.tpLinkComms, line 3
	author: "Compied by Dave Gutheinz", // library marker davegut.tpLinkComms, line 4
	description: "Communication methods for TP-Link Integration", // library marker davegut.tpLinkComms, line 5
	category: "utilities", // library marker davegut.tpLinkComms, line 6
	documentationLink: "" // library marker davegut.tpLinkComms, line 7
) // library marker davegut.tpLinkComms, line 8
import org.json.JSONObject // library marker davegut.tpLinkComms, line 9
import groovy.json.JsonOutput // library marker davegut.tpLinkComms, line 10
import groovy.json.JsonBuilder // library marker davegut.tpLinkComms, line 11
import groovy.json.JsonSlurper // library marker davegut.tpLinkComms, line 12

//	===== Async Commsunications Methods ===== // library marker davegut.tpLinkComms, line 14
def asyncSend(cmdBody, reqData, action) { // library marker davegut.tpLinkComms, line 15
	Map cmdData = [cmdBody: cmdBody, reqData: reqData, action: action] // library marker davegut.tpLinkComms, line 16
	state.lastCmd = cmdData // library marker davegut.tpLinkComms, line 17
	def protocol = getDataValue("protocol") // library marker davegut.tpLinkComms, line 18
	Map reqParams = [:] // library marker davegut.tpLinkComms, line 19
	if (protocol == "KLAP") { // library marker davegut.tpLinkComms, line 20
		reqParams = getKlapParams(cmdBody) // library marker davegut.tpLinkComms, line 21
	} else if (protocol == "AES") { // library marker davegut.tpLinkComms, line 22
		reqParams = getAesParams(cmdBody) // library marker davegut.tpLinkComms, line 23
	} else if (protocol == "vacAes") { // library marker davegut.tpLinkComms, line 24
		reqParams = getVacAesParams(cmdBody) // library marker davegut.tpLinkComms, line 25
	} // library marker davegut.tpLinkComms, line 26
	asyncPost(reqParams, action, reqData) // library marker davegut.tpLinkComms, line 27
} // library marker davegut.tpLinkComms, line 28

def asyncPost(reqParams, parseMethod, reqData=null) { // library marker davegut.tpLinkComms, line 30
	Map logData = [method: "asyncPost", parseMethod: parseMethod, data:reqData] // library marker davegut.tpLinkComms, line 31
	try { // library marker davegut.tpLinkComms, line 32
		asynchttpPost(parseMethod, reqParams, [data: reqData]) // library marker davegut.tpLinkComms, line 33
		logData << [status: "OK"] // library marker davegut.tpLinkComms, line 34
	} catch (err) { // library marker davegut.tpLinkComms, line 35
		logData << [status: "FAILED", reqParams: reqParams, error: err] // library marker davegut.tpLinkComms, line 36
		runIn(1, handleCommsError, [data: logData]) // library marker davegut.tpLinkComms, line 37
	} // library marker davegut.tpLinkComms, line 38
	logDebug(logData) // library marker davegut.tpLinkComms, line 39
} // library marker davegut.tpLinkComms, line 40

def parseData(resp, protocol = getDataValue("protocol")) { // library marker davegut.tpLinkComms, line 42
	Map logData = [method: "parseData"] // library marker davegut.tpLinkComms, line 43
	if (resp.status == 200) { // library marker davegut.tpLinkComms, line 44
		if (protocol == "KLAP") { // library marker davegut.tpLinkComms, line 45
			logData << parseKlapData(resp) // library marker davegut.tpLinkComms, line 46
		} else if (protocol == "AES") { // library marker davegut.tpLinkComms, line 47
			logData << parseAesData(resp) // library marker davegut.tpLinkComms, line 48
		} else if (protocol == "vacAes") { // library marker davegut.tpLinkComms, line 49
			logData << parseVacAesData(resp) // library marker davegut.tpLinkComms, line 50
		} // library marker davegut.tpLinkComms, line 51
	} else { // library marker davegut.tpLinkComms, line 52
		logData << [status: "httpFailure", data: resp.properties] // library marker davegut.tpLinkComms, line 53
		logWarn(logData) // library marker davegut.tpLinkComms, line 54
		handleCommsError("httpFailure") // library marker davegut.tpLinkComms, line 55
	} // library marker davegut.tpLinkComms, line 56
	return logData // library marker davegut.tpLinkComms, line 57
} // library marker davegut.tpLinkComms, line 58

//	===== Communications Error Handling ===== // library marker davegut.tpLinkComms, line 60
def handleCommsError(retryReason) { // library marker davegut.tpLinkComms, line 61
	Map logData = [method: "handleCommsError", retryReason: retryReason] // library marker davegut.tpLinkComms, line 62
	if (state.lastCmd != "") { // library marker davegut.tpLinkComms, line 63
		def count = state.errorCount + 1 // library marker davegut.tpLinkComms, line 64
		state.errorCount = count // library marker davegut.tpLinkComms, line 65
		logData << [count: count, lastCmd: state.lastCmd] // library marker davegut.tpLinkComms, line 66
		switch (count) { // library marker davegut.tpLinkComms, line 67
			case 1: // library marker davegut.tpLinkComms, line 68
				logData << [action: "resendCommand"] // library marker davegut.tpLinkComms, line 69
				runIn(1, delayedPassThrough) // library marker davegut.tpLinkComms, line 70
				break // library marker davegut.tpLinkComms, line 71
			case 2: // library marker davegut.tpLinkComms, line 72
				logData << [attemptHandshake: deviceHandshake(), // library marker davegut.tpLinkComms, line 73
						    action: "resendCommand"] // library marker davegut.tpLinkComms, line 74
				runIn(1, delayedPassThrough) // library marker davegut.tpLinkComms, line 75
				break // library marker davegut.tpLinkComms, line 76
			case 3: // library marker davegut.tpLinkComms, line 77
				logData << [configure: configure(), // library marker davegut.tpLinkComms, line 78
						    action: "resendCommand"] // library marker davegut.tpLinkComms, line 79
				runIn(1, delayedPassThrough) // library marker davegut.tpLinkComms, line 80
			case 4: // library marker davegut.tpLinkComms, line 81
				logData << [setError: setCommsError(true), retries: "disabled"] // library marker davegut.tpLinkComms, line 82
				break // library marker davegut.tpLinkComms, line 83
			default: // library marker davegut.tpLinkComms, line 84
				logData << [retries: "disabled"] // library marker davegut.tpLinkComms, line 85
				break // library marker davegut.tpLinkComms, line 86
		} // library marker davegut.tpLinkComms, line 87
	} else { // library marker davegut.tpLinkComms, line 88
		logData << [status: "noCommandToRetry"] // library marker davegut.tpLinkComms, line 89
	} // library marker davegut.tpLinkComms, line 90
	logDebug(logData) // library marker davegut.tpLinkComms, line 91
} // library marker davegut.tpLinkComms, line 92

def delayedPassThrough() { // library marker davegut.tpLinkComms, line 94
	def cmdData = new JSONObject(state.lastCmd) // library marker davegut.tpLinkComms, line 95
	def cmdBody = parseJson(cmdData.cmdBody.toString()) // library marker davegut.tpLinkComms, line 96
	asyncSend(cmdBody, cmdData.reqData, cmdData.action) // library marker davegut.tpLinkComms, line 97
} // library marker davegut.tpLinkComms, line 98

def setCommsError(status) { // library marker davegut.tpLinkComms, line 100
	if (device.currentValue("commsError") == true && status == false) { // library marker davegut.tpLinkComms, line 101
		updateAttr("commsError", false) // library marker davegut.tpLinkComms, line 102
		setPollInterval() // library marker davegut.tpLinkComms, line 103
		logInfo([method: "setCommsError", result: "Comms Error set to false"]) // library marker davegut.tpLinkComms, line 104
	} else if (device.currentValue("commsError") == false && status == true) { // library marker davegut.tpLinkComms, line 105
		updateAttr("commsError", true) // library marker davegut.tpLinkComms, line 106
		setPollInterval("30 min") // library marker davegut.tpLinkComms, line 107
		logWarn([method: "setCommsError", result: "Comms Error Set to true"]) // library marker davegut.tpLinkComms, line 108
	} // library marker davegut.tpLinkComms, line 109
} // library marker davegut.tpLinkComms, line 110

// ~~~~~ end include (36) davegut.tpLinkComms ~~~~~

// ~~~~~ start include (37) davegut.tpLinkCrypto ~~~~~
library ( // library marker davegut.tpLinkCrypto, line 1
	name: "tpLinkCrypto", // library marker davegut.tpLinkCrypto, line 2
	namespace: "davegut", // library marker davegut.tpLinkCrypto, line 3
	author: "Compiled by Dave Gutheinz", // library marker davegut.tpLinkCrypto, line 4
	description: "Handshake methods for TP-Link Integration", // library marker davegut.tpLinkCrypto, line 5
	category: "utilities", // library marker davegut.tpLinkCrypto, line 6
	documentationLink: "" // library marker davegut.tpLinkCrypto, line 7
) // library marker davegut.tpLinkCrypto, line 8
import java.security.spec.PKCS8EncodedKeySpec // library marker davegut.tpLinkCrypto, line 9
import javax.crypto.Cipher // library marker davegut.tpLinkCrypto, line 10
import java.security.KeyFactory // library marker davegut.tpLinkCrypto, line 11
import java.util.Random // library marker davegut.tpLinkCrypto, line 12
import javax.crypto.spec.SecretKeySpec // library marker davegut.tpLinkCrypto, line 13
import javax.crypto.spec.IvParameterSpec // library marker davegut.tpLinkCrypto, line 14
import java.security.MessageDigest // library marker davegut.tpLinkCrypto, line 15

//	===== AES Handshake and Login ===== // library marker davegut.tpLinkCrypto, line 17
def aesHandshake(baseUrl = getDataValue("baseUrl"), devData = null) { // library marker davegut.tpLinkCrypto, line 18
	Map reqData = [baseUrl: baseUrl, devData: devData] // library marker davegut.tpLinkCrypto, line 19
	Map rsaKey = getRsaKey() // library marker davegut.tpLinkCrypto, line 20
	def pubPem = "-----BEGIN PUBLIC KEY-----\n${rsaKey.public}-----END PUBLIC KEY-----\n" // library marker davegut.tpLinkCrypto, line 21
	Map cmdBody = [ method: "handshake", params: [ key: pubPem]] // library marker davegut.tpLinkCrypto, line 22
	Map reqParams = [uri: baseUrl, // library marker davegut.tpLinkCrypto, line 23
					 body: new groovy.json.JsonBuilder(cmdBody).toString(), // library marker davegut.tpLinkCrypto, line 24
					 requestContentType: "application/json", // library marker davegut.tpLinkCrypto, line 25
					 timeout: 10] // library marker davegut.tpLinkCrypto, line 26
	asyncPost(reqParams, "parseAesHandshake", reqData) // library marker davegut.tpLinkCrypto, line 27
} // library marker davegut.tpLinkCrypto, line 28

def parseAesHandshake(resp, data){ // library marker davegut.tpLinkCrypto, line 30
	Map logData = [method: "parseAesHandshake"] // library marker davegut.tpLinkCrypto, line 31
	if (resp.status == 200 && resp.data != null) { // library marker davegut.tpLinkCrypto, line 32
		try { // library marker davegut.tpLinkCrypto, line 33
			Map reqData = [devData: data.data.devData, baseUrl: data.data.baseUrl] // library marker davegut.tpLinkCrypto, line 34
			Map cmdResp =  new JsonSlurper().parseText(resp.data) // library marker davegut.tpLinkCrypto, line 35
			//	cookie // library marker davegut.tpLinkCrypto, line 36
			def cookieHeader = resp.headers["Set-Cookie"].toString() // library marker davegut.tpLinkCrypto, line 37
			def cookie = cookieHeader.substring(cookieHeader.indexOf(":") +1, cookieHeader.indexOf(";")) // library marker davegut.tpLinkCrypto, line 38
			//	keys // library marker davegut.tpLinkCrypto, line 39
			byte[] privateKeyBytes = getRsaKey().private.decodeBase64() // library marker davegut.tpLinkCrypto, line 40
			byte[] deviceKeyBytes = cmdResp.result.key.getBytes("UTF-8").decodeBase64() // library marker davegut.tpLinkCrypto, line 41
    		Cipher instance = Cipher.getInstance("RSA/ECB/PKCS1Padding") // library marker davegut.tpLinkCrypto, line 42
			instance.init(2, KeyFactory.getInstance("RSA").generatePrivate(new PKCS8EncodedKeySpec(privateKeyBytes))) // library marker davegut.tpLinkCrypto, line 43
			byte[] cryptoArray = instance.doFinal(deviceKeyBytes) // library marker davegut.tpLinkCrypto, line 44
			byte[] encKey = cryptoArray[0..15] // library marker davegut.tpLinkCrypto, line 45
			byte[] encIv = cryptoArray[16..31] // library marker davegut.tpLinkCrypto, line 46
			logData << [respStatus: "Cookies/Keys Updated", cookie: cookie, // library marker davegut.tpLinkCrypto, line 47
						encKey: encKey, encIv: encIv] // library marker davegut.tpLinkCrypto, line 48
			String password = encPassword // library marker davegut.tpLinkCrypto, line 49
			String username = encUsername // library marker davegut.tpLinkCrypto, line 50
			if (device) { // library marker davegut.tpLinkCrypto, line 51
				password = parent.encPassword // library marker davegut.tpLinkCrypto, line 52
				username = parent.encUsername // library marker davegut.tpLinkCrypto, line 53
				device.updateSetting("cookie",[type:"password", value: cookie]) // library marker davegut.tpLinkCrypto, line 54
				device.updateSetting("encKey",[type:"password", value: encKey]) // library marker davegut.tpLinkCrypto, line 55
				device.updateSetting("encIv",[type:"password", value: encIv]) // library marker davegut.tpLinkCrypto, line 56
			} else { // library marker davegut.tpLinkCrypto, line 57
				reqData << [cookie: cookie, encIv: encIv, encKey: encKey] // library marker davegut.tpLinkCrypto, line 58
			} // library marker davegut.tpLinkCrypto, line 59
			Map cmdBody = [method: "login_device", // library marker davegut.tpLinkCrypto, line 60
						   params: [password: password, // library marker davegut.tpLinkCrypto, line 61
									username: username], // library marker davegut.tpLinkCrypto, line 62
						   requestTimeMils: 0] // library marker davegut.tpLinkCrypto, line 63
			def cmdStr = JsonOutput.toJson(cmdBody).toString() // library marker davegut.tpLinkCrypto, line 64
			Map reqBody = [method: "securePassthrough", // library marker davegut.tpLinkCrypto, line 65
						   params: [request: aesEncrypt(cmdStr, encKey, encIv)]] // library marker davegut.tpLinkCrypto, line 66
			Map reqParams = [uri: reqData.baseUrl, // library marker davegut.tpLinkCrypto, line 67
							  body: reqBody, // library marker davegut.tpLinkCrypto, line 68
							  timeout:10,  // library marker davegut.tpLinkCrypto, line 69
							  headers: ["Cookie": cookie], // library marker davegut.tpLinkCrypto, line 70
							  contentType: "application/json", // library marker davegut.tpLinkCrypto, line 71
							  requestContentType: "application/json"] // library marker davegut.tpLinkCrypto, line 72
			asyncPost(reqParams, "parseAesLogin", reqData) // library marker davegut.tpLinkCrypto, line 73
			logDebug(logData) // library marker davegut.tpLinkCrypto, line 74
		} catch (err) { // library marker davegut.tpLinkCrypto, line 75
			logData << [respStatus: "ERROR parsing HTTP resp.data", // library marker davegut.tpLinkCrypto, line 76
						respData: resp.data, error: err] // library marker davegut.tpLinkCrypto, line 77
			logWarn(logData) // library marker davegut.tpLinkCrypto, line 78
		} // library marker davegut.tpLinkCrypto, line 79
	} else { // library marker davegut.tpLinkCrypto, line 80
		logData << [respStatus: "ERROR in HTTP response", resp: resp.properties] // library marker davegut.tpLinkCrypto, line 81
		logWarn(logData) // library marker davegut.tpLinkCrypto, line 82
	} // library marker davegut.tpLinkCrypto, line 83
} // library marker davegut.tpLinkCrypto, line 84

def parseAesLogin(resp, data) { // library marker davegut.tpLinkCrypto, line 86
	if (device) { // library marker davegut.tpLinkCrypto, line 87
		Map logData = [method: "parseAesLogin"] // library marker davegut.tpLinkCrypto, line 88
		if (resp.status == 200) { // library marker davegut.tpLinkCrypto, line 89
			if (resp.json.error_code == 0) { // library marker davegut.tpLinkCrypto, line 90
				try { // library marker davegut.tpLinkCrypto, line 91
					byte[] encKey = new JsonSlurper().parseText(encKey) // library marker davegut.tpLinkCrypto, line 92
					byte[] encIv = new JsonSlurper().parseText(encIv) // library marker davegut.tpLinkCrypto, line 93
					def clearResp = aesDecrypt(resp.json.result.response, encKey, encIv) // library marker davegut.tpLinkCrypto, line 94
					Map cmdResp = new JsonSlurper().parseText(clearResp) // library marker davegut.tpLinkCrypto, line 95
					if (cmdResp.error_code == 0) { // library marker davegut.tpLinkCrypto, line 96
						def token = cmdResp.result.token // library marker davegut.tpLinkCrypto, line 97
						logData << [respStatus: "OK", token: token] // library marker davegut.tpLinkCrypto, line 98
						device.updateSetting("token",[type:"password", value: token]) // library marker davegut.tpLinkCrypto, line 99
						logDebug(logData) // library marker davegut.tpLinkCrypto, line 100
					} else { // library marker davegut.tpLinkCrypto, line 101
						logData << [respStatus: "ERROR code in cmdResp",  // library marker davegut.tpLinkCrypto, line 102
									error_code: cmdResp.error_code, // library marker davegut.tpLinkCrypto, line 103
									check: "cryptoArray, credentials", data: cmdResp] // library marker davegut.tpLinkCrypto, line 104
						logWarn(logData) // library marker davegut.tpLinkCrypto, line 105
					} // library marker davegut.tpLinkCrypto, line 106
				} catch (err) { // library marker davegut.tpLinkCrypto, line 107
					logData << [respStatus: "ERROR parsing respJson", respJson: resp.json, // library marker davegut.tpLinkCrypto, line 108
								error: err] // library marker davegut.tpLinkCrypto, line 109
					logWarn(logData) // library marker davegut.tpLinkCrypto, line 110
				} // library marker davegut.tpLinkCrypto, line 111
			} else { // library marker davegut.tpLinkCrypto, line 112
				logData << [respStatus: "ERROR code in resp.json", errorCode: resp.json.error_code, // library marker davegut.tpLinkCrypto, line 113
							respJson: resp.json] // library marker davegut.tpLinkCrypto, line 114
				logWarn(logData) // library marker davegut.tpLinkCrypto, line 115
			} // library marker davegut.tpLinkCrypto, line 116
		} else { // library marker davegut.tpLinkCrypto, line 117
			logData << [respStatus: "ERROR in HTTP response", respStatus: resp.status, data: resp.properties] // library marker davegut.tpLinkCrypto, line 118
			logWarn(logData) // library marker davegut.tpLinkCrypto, line 119
		} // library marker davegut.tpLinkCrypto, line 120
	} else { // library marker davegut.tpLinkCrypto, line 121
		getAesToken(resp, data.data) // library marker davegut.tpLinkCrypto, line 122
	} // library marker davegut.tpLinkCrypto, line 123
} // library marker davegut.tpLinkCrypto, line 124

//	===== KLAP Handshake ===== // library marker davegut.tpLinkCrypto, line 126
def klapHandshake(baseUrl = getDataValue("baseUrl"), localHash = parent.localHash, devData = null) { // library marker davegut.tpLinkCrypto, line 127
	byte[] localSeed = new byte[16] // library marker davegut.tpLinkCrypto, line 128
	new Random().nextBytes(localSeed) // library marker davegut.tpLinkCrypto, line 129
	Map reqData = [localSeed: localSeed, baseUrl: baseUrl, localHash: localHash, devData:devData] // library marker davegut.tpLinkCrypto, line 130
	Map reqParams = [uri: "${baseUrl}/handshake1", // library marker davegut.tpLinkCrypto, line 131
					 body: localSeed, // library marker davegut.tpLinkCrypto, line 132
					 contentType: "application/octet-stream", // library marker davegut.tpLinkCrypto, line 133
					 requestContentType: "application/octet-stream", // library marker davegut.tpLinkCrypto, line 134
					 timeout:10] // library marker davegut.tpLinkCrypto, line 135
	asyncPost(reqParams, "parseKlapHandshake", reqData) // library marker davegut.tpLinkCrypto, line 136
} // library marker davegut.tpLinkCrypto, line 137

def parseKlapHandshake(resp, data) { // library marker davegut.tpLinkCrypto, line 139
	Map logData = [method: "parseKlapHandshake", data: data] // library marker davegut.tpLinkCrypto, line 140
	if (resp.status == 200 && resp.data != null) { // library marker davegut.tpLinkCrypto, line 141
		try { // library marker davegut.tpLinkCrypto, line 142
			Map reqData = [devData: data.data.devData, baseUrl: data.data.baseUrl] // library marker davegut.tpLinkCrypto, line 143
			byte[] localSeed = data.data.localSeed // library marker davegut.tpLinkCrypto, line 144
			byte[] seedData = resp.data.decodeBase64() // library marker davegut.tpLinkCrypto, line 145
			byte[] remoteSeed = seedData[0 .. 15] // library marker davegut.tpLinkCrypto, line 146
			byte[] serverHash = seedData[16 .. 47] // library marker davegut.tpLinkCrypto, line 147
			byte[] localHash = data.data.localHash.decodeBase64() // library marker davegut.tpLinkCrypto, line 148
			byte[] authHash = [localSeed, remoteSeed, localHash].flatten() // library marker davegut.tpLinkCrypto, line 149
			byte[] localAuthHash = mdEncode("SHA-256", authHash) // library marker davegut.tpLinkCrypto, line 150
			if (localAuthHash == serverHash) { // library marker davegut.tpLinkCrypto, line 151
				//	cookie // library marker davegut.tpLinkCrypto, line 152
				def cookieHeader = resp.headers["Set-Cookie"].toString() // library marker davegut.tpLinkCrypto, line 153
				def cookie = cookieHeader.substring(cookieHeader.indexOf(":") +1, cookieHeader.indexOf(";")) // library marker davegut.tpLinkCrypto, line 154
				logData << [cookie: cookie] // library marker davegut.tpLinkCrypto, line 155
				//	seqNo and encIv // library marker davegut.tpLinkCrypto, line 156
				byte[] payload = ["iv".getBytes(), localSeed, remoteSeed, localHash].flatten() // library marker davegut.tpLinkCrypto, line 157
				byte[] fullIv = mdEncode("SHA-256", payload) // library marker davegut.tpLinkCrypto, line 158
				byte[] byteSeqNo = fullIv[-4..-1] // library marker davegut.tpLinkCrypto, line 159

				int seqNo = byteArrayToInteger(byteSeqNo) // library marker davegut.tpLinkCrypto, line 161
				atomicState.seqNo = seqNo // library marker davegut.tpLinkCrypto, line 162

//				if (device) {  // library marker davegut.tpLinkCrypto, line 164
//				} // library marker davegut.tpLinkCrypto, line 165

				logData << [seqNo: seqNo, encIv: fullIv[0..11]] // library marker davegut.tpLinkCrypto, line 167
				//	encKey // library marker davegut.tpLinkCrypto, line 168
				payload = ["lsk".getBytes(), localSeed, remoteSeed, localHash].flatten() // library marker davegut.tpLinkCrypto, line 169
				byte[] encKey = mdEncode("SHA-256", payload)[0..15] // library marker davegut.tpLinkCrypto, line 170
				logData << [encKey: encKey] // library marker davegut.tpLinkCrypto, line 171
				//	encSig // library marker davegut.tpLinkCrypto, line 172
				payload = ["ldk".getBytes(), localSeed, remoteSeed, localHash].flatten() // library marker davegut.tpLinkCrypto, line 173
				byte[] encSig = mdEncode("SHA-256", payload)[0..27] // library marker davegut.tpLinkCrypto, line 174
				if (device) { // library marker davegut.tpLinkCrypto, line 175
					device.updateSetting("cookie",[type:"password", value: cookie])  // library marker davegut.tpLinkCrypto, line 176
					device.updateSetting("encKey",[type:"password", value: encKey])  // library marker davegut.tpLinkCrypto, line 177
					device.updateSetting("encIv",[type:"password", value: fullIv[0..11]])  // library marker davegut.tpLinkCrypto, line 178
					device.updateSetting("encSig",[type:"password", value: encSig])  // library marker davegut.tpLinkCrypto, line 179
				} else { // library marker davegut.tpLinkCrypto, line 180
					reqData << [cookie: cookie, seqNo: seqNo, encIv: fullIv[0..11],  // library marker davegut.tpLinkCrypto, line 181
								encSig: encSig, encKey: encKey] // library marker davegut.tpLinkCrypto, line 182
				} // library marker davegut.tpLinkCrypto, line 183
				logData << [encSig: encSig] // library marker davegut.tpLinkCrypto, line 184
				byte[] loginHash = [remoteSeed, localSeed, localHash].flatten() // library marker davegut.tpLinkCrypto, line 185
				byte[] body = mdEncode("SHA-256", loginHash) // library marker davegut.tpLinkCrypto, line 186
				Map reqParams = [uri: "${data.data.baseUrl}/handshake2", // library marker davegut.tpLinkCrypto, line 187
								 body: body, // library marker davegut.tpLinkCrypto, line 188
								 timeout:10, // library marker davegut.tpLinkCrypto, line 189
								 headers: ["Cookie": cookie], // library marker davegut.tpLinkCrypto, line 190
								 contentType: "application/octet-stream", // library marker davegut.tpLinkCrypto, line 191
								 requestContentType: "application/octet-stream"] // library marker davegut.tpLinkCrypto, line 192
				asyncPost(reqParams, "parseKlapHandshake2", reqData) // library marker davegut.tpLinkCrypto, line 193
			} else { // library marker davegut.tpLinkCrypto, line 194
				logData << [respStatus: "ERROR: locakAuthHash != serverHash", // library marker davegut.tpLinkCrypto, line 195
							localAuthHash: localAuthHash, serverHash: serverHash] // library marker davegut.tpLinkCrypto, line 196
				logWarn(logData) // library marker davegut.tpLinkCrypto, line 197
			} // library marker davegut.tpLinkCrypto, line 198
		} catch (err) { // library marker davegut.tpLinkCrypto, line 199
			logData << [respStatus: "ERROR parsing 200 response", resp: resp.properties, error: err] // library marker davegut.tpLinkCrypto, line 200
			logWarn(logData) // library marker davegut.tpLinkCrypto, line 201
		} // library marker davegut.tpLinkCrypto, line 202
	} else { // library marker davegut.tpLinkCrypto, line 203
		logData << [respStatus: "ERROR in HTTP response", resp: resp.properties] // library marker davegut.tpLinkCrypto, line 204
		logWarn(logData) // library marker davegut.tpLinkCrypto, line 205
	} // library marker davegut.tpLinkCrypto, line 206
} // library marker davegut.tpLinkCrypto, line 207

def parseKlapHandshake2(resp, data) { // library marker davegut.tpLinkCrypto, line 209
	Map logData = [method: "parseKlapHandshake2"] // library marker davegut.tpLinkCrypto, line 210
	if (resp.status == 200 && resp.data == null) { // library marker davegut.tpLinkCrypto, line 211
		logData << [respStatus: "Login OK"] // library marker davegut.tpLinkCrypto, line 212
		logDebug(logData) // library marker davegut.tpLinkCrypto, line 213
	} else { // library marker davegut.tpLinkCrypto, line 214
		logData << [respStatus: "LOGIN FAILED", reason: "ERROR in HTTP response", // library marker davegut.tpLinkCrypto, line 215
					resp: resp.properties] // library marker davegut.tpLinkCrypto, line 216
		logWarn(logData) // library marker davegut.tpLinkCrypto, line 217
	} // library marker davegut.tpLinkCrypto, line 218
	if (!device) { sendKlapDataCmd(logData, data) } // library marker davegut.tpLinkCrypto, line 219
} // library marker davegut.tpLinkCrypto, line 220

//	===== Comms Support ===== // library marker davegut.tpLinkCrypto, line 222
def getKlapParams(cmdBody) { // library marker davegut.tpLinkCrypto, line 223
	Map reqParams = [timeout: 10, headers: ["Cookie": cookie]] // library marker davegut.tpLinkCrypto, line 224
	int seqNo = state.seqNo + 1 // library marker davegut.tpLinkCrypto, line 225
	state.seqNo = seqNo // library marker davegut.tpLinkCrypto, line 226
	byte[] encKey = new JsonSlurper().parseText(encKey) // library marker davegut.tpLinkCrypto, line 227
	byte[] encIv = new JsonSlurper().parseText(encIv) // library marker davegut.tpLinkCrypto, line 228
	byte[] encSig = new JsonSlurper().parseText(encSig) // library marker davegut.tpLinkCrypto, line 229
	String cmdBodyJson = new groovy.json.JsonBuilder(cmdBody).toString() // library marker davegut.tpLinkCrypto, line 230

	Map encryptedData = klapEncrypt(cmdBodyJson.getBytes(), encKey, encIv, // library marker davegut.tpLinkCrypto, line 232
									encSig, seqNo) // library marker davegut.tpLinkCrypto, line 233
	reqParams << [uri: "${getDataValue("baseUrl")}/request?seq=${seqNo}", // library marker davegut.tpLinkCrypto, line 234
				  body: encryptedData.cipherData, // library marker davegut.tpLinkCrypto, line 235
				  contentType: "application/octet-stream", // library marker davegut.tpLinkCrypto, line 236
				  requestContentType: "application/octet-stream"] // library marker davegut.tpLinkCrypto, line 237
	return reqParams // library marker davegut.tpLinkCrypto, line 238
} // library marker davegut.tpLinkCrypto, line 239

def getAesParams(cmdBody) { // library marker davegut.tpLinkCrypto, line 241
	byte[] encKey = new JsonSlurper().parseText(encKey) // library marker davegut.tpLinkCrypto, line 242
	byte[] encIv = new JsonSlurper().parseText(encIv) // library marker davegut.tpLinkCrypto, line 243
	def cmdStr = JsonOutput.toJson(cmdBody).toString() // library marker davegut.tpLinkCrypto, line 244
	Map reqBody = [method: "securePassthrough", // library marker davegut.tpLinkCrypto, line 245
				   params: [request: aesEncrypt(cmdStr, encKey, encIv)]] // library marker davegut.tpLinkCrypto, line 246
	Map reqParams = [uri: "${getDataValue("baseUrl")}?token=${token}", // library marker davegut.tpLinkCrypto, line 247
					 body: new groovy.json.JsonBuilder(reqBody).toString(), // library marker davegut.tpLinkCrypto, line 248
					 contentType: "application/json", // library marker davegut.tpLinkCrypto, line 249
					 requestContentType: "application/json", // library marker davegut.tpLinkCrypto, line 250
					 timeout: 10, // library marker davegut.tpLinkCrypto, line 251
					 headers: ["Cookie": cookie]] // library marker davegut.tpLinkCrypto, line 252
	return reqParams // library marker davegut.tpLinkCrypto, line 253
} // library marker davegut.tpLinkCrypto, line 254

def parseKlapData(resp) { // library marker davegut.tpLinkCrypto, line 256
	Map parseData = [parseMethod: "parseKlapData"] // library marker davegut.tpLinkCrypto, line 257
	try { // library marker davegut.tpLinkCrypto, line 258
		byte[] encKey = new JsonSlurper().parseText(encKey) // library marker davegut.tpLinkCrypto, line 259
		byte[] encIv = new JsonSlurper().parseText(encIv) // library marker davegut.tpLinkCrypto, line 260
		int seqNo = state.seqNo // library marker davegut.tpLinkCrypto, line 261
		byte[] cipherResponse = resp.data.decodeBase64()[32..-1] // library marker davegut.tpLinkCrypto, line 262
		Map cmdResp =  new JsonSlurper().parseText(klapDecrypt(cipherResponse, encKey, // library marker davegut.tpLinkCrypto, line 263
														   encIv, seqNo)) // library marker davegut.tpLinkCrypto, line 264
		parseData << [status: "OK", cmdResp: cmdResp] // library marker davegut.tpLinkCrypto, line 265
		state.errorCount = 0 // library marker davegut.tpLinkCrypto, line 266
		setCommsError(false) // library marker davegut.tpLinkCrypto, line 267
		logDebug(parseData) // library marker davegut.tpLinkCrypto, line 268
	} catch (err) { // library marker davegut.tpLinkCrypto, line 269
		parseData << [status: "deviceDataParseError", error: err, dataLength: resp.data.length()] // library marker davegut.tpLinkCrypto, line 270
		logWarn(parseData) // library marker davegut.tpLinkCrypto, line 271
		handleCommsError("deviceDataParseError") // library marker davegut.tpLinkCrypto, line 272
	} // library marker davegut.tpLinkCrypto, line 273
	return parseData // library marker davegut.tpLinkCrypto, line 274
} // library marker davegut.tpLinkCrypto, line 275

def parseAesData(resp) { // library marker davegut.tpLinkCrypto, line 277
	Map parseData = [parseMethod: "parseAesData"] // library marker davegut.tpLinkCrypto, line 278
	try { // library marker davegut.tpLinkCrypto, line 279
		byte[] encKey = new JsonSlurper().parseText(encKey) // library marker davegut.tpLinkCrypto, line 280
		byte[] encIv = new JsonSlurper().parseText(encIv) // library marker davegut.tpLinkCrypto, line 281
		Map cmdResp = new JsonSlurper().parseText(aesDecrypt(resp.json.result.response, // library marker davegut.tpLinkCrypto, line 282
														 encKey, encIv)) // library marker davegut.tpLinkCrypto, line 283
		parseData << [status: "OK", cmdResp: cmdResp] // library marker davegut.tpLinkCrypto, line 284
		state.errorCount = 0 // library marker davegut.tpLinkCrypto, line 285
		setCommsError(false) // library marker davegut.tpLinkCrypto, line 286
		logDebug(parseData) // library marker davegut.tpLinkCrypto, line 287
	} catch (err) { // library marker davegut.tpLinkCrypto, line 288
		parseData << [status: "deviceDataParseError", error: err, dataLength: resp.data.length()] // library marker davegut.tpLinkCrypto, line 289
		logWarn(parseData) // library marker davegut.tpLinkCrypto, line 290
		handleCommsError("deviceDataParseError") // library marker davegut.tpLinkCrypto, line 291
	} // library marker davegut.tpLinkCrypto, line 292
	return parseData // library marker davegut.tpLinkCrypto, line 293
} // library marker davegut.tpLinkCrypto, line 294

//	===== Crypto Methods ===== // library marker davegut.tpLinkCrypto, line 296
def klapEncrypt(byte[] request, encKey, encIv, encSig, seqNo) { // library marker davegut.tpLinkCrypto, line 297
	byte[] encSeqNo = integerToByteArray(seqNo) // library marker davegut.tpLinkCrypto, line 298
	byte[] ivEnc = [encIv, encSeqNo].flatten() // library marker davegut.tpLinkCrypto, line 299
	def cipher = Cipher.getInstance("AES/CBC/PKCS5Padding") // library marker davegut.tpLinkCrypto, line 300
	SecretKeySpec key = new SecretKeySpec(encKey, "AES") // library marker davegut.tpLinkCrypto, line 301
	IvParameterSpec iv = new IvParameterSpec(ivEnc) // library marker davegut.tpLinkCrypto, line 302
	cipher.init(Cipher.ENCRYPT_MODE, key, iv) // library marker davegut.tpLinkCrypto, line 303
	byte[] cipherRequest = cipher.doFinal(request) // library marker davegut.tpLinkCrypto, line 304

	byte[] payload = [encSig, encSeqNo, cipherRequest].flatten() // library marker davegut.tpLinkCrypto, line 306
	byte[] signature = mdEncode("SHA-256", payload) // library marker davegut.tpLinkCrypto, line 307
	cipherRequest = [signature, cipherRequest].flatten() // library marker davegut.tpLinkCrypto, line 308
	return [cipherData: cipherRequest, seqNumber: seqNo] // library marker davegut.tpLinkCrypto, line 309
} // library marker davegut.tpLinkCrypto, line 310

def klapDecrypt(cipherResponse, encKey, encIv, seqNo) { // library marker davegut.tpLinkCrypto, line 312
	byte[] encSeqNo = integerToByteArray(seqNo) // library marker davegut.tpLinkCrypto, line 313
	byte[] ivEnc = [encIv, encSeqNo].flatten() // library marker davegut.tpLinkCrypto, line 314
	def cipher = Cipher.getInstance("AES/CBC/PKCS5Padding") // library marker davegut.tpLinkCrypto, line 315
    SecretKeySpec key = new SecretKeySpec(encKey, "AES") // library marker davegut.tpLinkCrypto, line 316
	IvParameterSpec iv = new IvParameterSpec(ivEnc) // library marker davegut.tpLinkCrypto, line 317
    cipher.init(Cipher.DECRYPT_MODE, key, iv) // library marker davegut.tpLinkCrypto, line 318
	byte[] byteResponse = cipher.doFinal(cipherResponse) // library marker davegut.tpLinkCrypto, line 319
	return new String(byteResponse, "UTF-8") // library marker davegut.tpLinkCrypto, line 320
} // library marker davegut.tpLinkCrypto, line 321

def aesEncrypt(request, encKey, encIv) { // library marker davegut.tpLinkCrypto, line 323
	def cipher = Cipher.getInstance("AES/CBC/PKCS5Padding") // library marker davegut.tpLinkCrypto, line 324
	SecretKeySpec key = new SecretKeySpec(encKey, "AES") // library marker davegut.tpLinkCrypto, line 325
	IvParameterSpec iv = new IvParameterSpec(encIv) // library marker davegut.tpLinkCrypto, line 326
	cipher.init(Cipher.ENCRYPT_MODE, key, iv) // library marker davegut.tpLinkCrypto, line 327
	String result = cipher.doFinal(request.getBytes("UTF-8")).encodeBase64().toString() // library marker davegut.tpLinkCrypto, line 328
	return result.replace("\r\n","") // library marker davegut.tpLinkCrypto, line 329
} // library marker davegut.tpLinkCrypto, line 330

def aesDecrypt(cipherResponse, encKey, encIv) { // library marker davegut.tpLinkCrypto, line 332
    byte[] decodedBytes = cipherResponse.decodeBase64() // library marker davegut.tpLinkCrypto, line 333
	def cipher = Cipher.getInstance("AES/CBC/PKCS5Padding") // library marker davegut.tpLinkCrypto, line 334
    SecretKeySpec key = new SecretKeySpec(encKey, "AES") // library marker davegut.tpLinkCrypto, line 335
	IvParameterSpec iv = new IvParameterSpec(encIv) // library marker davegut.tpLinkCrypto, line 336
    cipher.init(Cipher.DECRYPT_MODE, key, iv) // library marker davegut.tpLinkCrypto, line 337
	return new String(cipher.doFinal(decodedBytes), "UTF-8") // library marker davegut.tpLinkCrypto, line 338
} // library marker davegut.tpLinkCrypto, line 339

//	===== Encoding Methods ===== // library marker davegut.tpLinkCrypto, line 341
def mdEncode(hashMethod, byte[] data) { // library marker davegut.tpLinkCrypto, line 342
	MessageDigest md = MessageDigest.getInstance(hashMethod) // library marker davegut.tpLinkCrypto, line 343
	md.update(data) // library marker davegut.tpLinkCrypto, line 344
	return md.digest() // library marker davegut.tpLinkCrypto, line 345
} // library marker davegut.tpLinkCrypto, line 346

String encodeUtf8(String message) { // library marker davegut.tpLinkCrypto, line 348
	byte[] arr = message.getBytes("UTF8") // library marker davegut.tpLinkCrypto, line 349
	return new String(arr) // library marker davegut.tpLinkCrypto, line 350
} // library marker davegut.tpLinkCrypto, line 351

int byteArrayToInteger(byte[] byteArr) { // library marker davegut.tpLinkCrypto, line 353
	int arrayASInteger // library marker davegut.tpLinkCrypto, line 354
	try { // library marker davegut.tpLinkCrypto, line 355
		arrayAsInteger = ((byteArr[0] & 0xFF) << 24) + ((byteArr[1] & 0xFF) << 16) + // library marker davegut.tpLinkCrypto, line 356
			((byteArr[2] & 0xFF) << 8) + (byteArr[3] & 0xFF) // library marker davegut.tpLinkCrypto, line 357
	} catch (error) { // library marker davegut.tpLinkCrypto, line 358
		Map errLog = [byteArr: byteArr, ERROR: error] // library marker davegut.tpLinkCrypto, line 359
		logWarn("byteArrayToInteger: ${errLog}") // library marker davegut.tpLinkCrypto, line 360
	} // library marker davegut.tpLinkCrypto, line 361
	return arrayAsInteger // library marker davegut.tpLinkCrypto, line 362
} // library marker davegut.tpLinkCrypto, line 363

byte[] integerToByteArray(value) { // library marker davegut.tpLinkCrypto, line 365
	String hexValue = hubitat.helper.HexUtils.integerToHexString(value, 4) // library marker davegut.tpLinkCrypto, line 366
	byte[] byteValue = hubitat.helper.HexUtils.hexStringToByteArray(hexValue) // library marker davegut.tpLinkCrypto, line 367
	return byteValue // library marker davegut.tpLinkCrypto, line 368
} // library marker davegut.tpLinkCrypto, line 369

def getRsaKey() { // library marker davegut.tpLinkCrypto, line 371
	return [public: "MIGfMA0GCSqGSIb3DQEBAQUAA4GNADCBiQKBgQDGr/mHBK8aqx7UAS+g+TuAvE3J2DdwsqRn9MmAkjPGNon1ZlwM6nLQHfJHebdohyVqkNWaCECGXnftnlC8CM2c/RujvCrStRA0lVD+jixO9QJ9PcYTa07Z1FuEze7Q5OIa6pEoPxomrjxzVlUWLDXt901qCdn3/zRZpBdpXzVZtQIDAQAB", // library marker davegut.tpLinkCrypto, line 372
			private: "MIICeAIBADANBgkqhkiG9w0BAQEFAASCAmIwggJeAgEAAoGBAMav+YcErxqrHtQBL6D5O4C8TcnYN3CypGf0yYCSM8Y2ifVmXAzqctAd8kd5t2iHJWqQ1ZoIQIZed+2eULwIzZz9G6O8KtK1EDSVUP6OLE71An09xhNrTtnUW4TN7tDk4hrqkSg/GiauPHNWVRYsNe33TWoJ2ff/NFmkF2lfNVm1AgMBAAECgYEAocxCHmKBGe2KAEkq+SKdAxvVGO77TsobOhDMWug0Q1C8jduaUGZHsxT/7JbA9d1AagSh/XqE2Sdq8FUBF+7vSFzozBHyGkrX1iKURpQFEQM2j9JgUCucEavnxvCqDYpscyNRAgqz9jdh+BjEMcKAG7o68bOw41ZC+JyYR41xSe0CQQD1os71NcZiMVqYcBud6fTYFHZz3HBNcbzOk+RpIHyi8aF3zIqPKIAh2pO4s7vJgrMZTc2wkIe0ZnUrm0oaC//jAkEAzxIPW1mWd3+KE3gpgyX0cFkZsDmlIbWojUIbyz8NgeUglr+BczARG4ITrTV4fxkGwNI4EZxBT8vXDSIXJ8NDhwJBAIiKndx0rfg7Uw7VkqRvPqk2hrnU2aBTDw8N6rP9WQsCoi0DyCnX65Hl/KN5VXOocYIpW6NAVA8VvSAmTES6Ut0CQQCX20jD13mPfUsHaDIZafZPhiheoofFpvFLVtYHQeBoCF7T7vHCRdfl8oj3l6UcoH/hXMmdsJf9KyI1EXElyf91AkAvLfmAS2UvUnhX4qyFioitjxwWawSnf+CewN8LDbH7m5JVXJEh3hqp+aLHg1EaW4wJtkoKLCF+DeVIgbSvOLJw"] // library marker davegut.tpLinkCrypto, line 373
} // library marker davegut.tpLinkCrypto, line 374

// ~~~~~ end include (37) davegut.tpLinkCrypto ~~~~~

// ~~~~~ start include (30) davegut.Logging ~~~~~
library ( // library marker davegut.Logging, line 1
	name: "Logging", // library marker davegut.Logging, line 2
	namespace: "davegut", // library marker davegut.Logging, line 3
	author: "Dave Gutheinz", // library marker davegut.Logging, line 4
	description: "Common Logging and info gathering Methods", // library marker davegut.Logging, line 5
	category: "utilities", // library marker davegut.Logging, line 6
	documentationLink: "" // library marker davegut.Logging, line 7
) // library marker davegut.Logging, line 8

def nameSpace() { return "davegut" } // library marker davegut.Logging, line 10

def version() { return "2.3.9a" } // library marker davegut.Logging, line 12

def label() { // library marker davegut.Logging, line 14
	if (device) {  // library marker davegut.Logging, line 15
		return device.displayName + "-${version()}" // library marker davegut.Logging, line 16
	} else {  // library marker davegut.Logging, line 17
		return app.getLabel() + "-${version()}" // library marker davegut.Logging, line 18
	} // library marker davegut.Logging, line 19
} // library marker davegut.Logging, line 20

def listAttributes() { // library marker davegut.Logging, line 22
	def attrData = device.getCurrentStates() // library marker davegut.Logging, line 23
	Map attrs = [:] // library marker davegut.Logging, line 24
	attrData.each { // library marker davegut.Logging, line 25
		attrs << ["${it.name}": it.value] // library marker davegut.Logging, line 26
	} // library marker davegut.Logging, line 27
	return attrs // library marker davegut.Logging, line 28
} // library marker davegut.Logging, line 29

def setLogsOff() { // library marker davegut.Logging, line 31
	def logData = [logEnable: logEnable] // library marker davegut.Logging, line 32
	if (logEnable) { // library marker davegut.Logging, line 33
		runIn(1800, debugLogOff) // library marker davegut.Logging, line 34
		logData << [debugLogOff: "scheduled"] // library marker davegut.Logging, line 35
	} // library marker davegut.Logging, line 36
	return logData // library marker davegut.Logging, line 37
} // library marker davegut.Logging, line 38

def logTrace(msg){ log.trace "${label()}: ${msg}" } // library marker davegut.Logging, line 40

def logInfo(msg) {  // library marker davegut.Logging, line 42
	if (infoLog) { log.info "${label()}: ${msg}" } // library marker davegut.Logging, line 43
} // library marker davegut.Logging, line 44

def debugLogOff() { // library marker davegut.Logging, line 46
	if (device) { // library marker davegut.Logging, line 47
		device.updateSetting("logEnable", [type:"bool", value: false]) // library marker davegut.Logging, line 48
	} else { // library marker davegut.Logging, line 49
		app.updateSetting("logEnable", false) // library marker davegut.Logging, line 50
	} // library marker davegut.Logging, line 51
	logInfo("debugLogOff") // library marker davegut.Logging, line 52
} // library marker davegut.Logging, line 53

def logDebug(msg) { // library marker davegut.Logging, line 55
	if (logEnable) { log.debug "${label()}: ${msg}" } // library marker davegut.Logging, line 56
} // library marker davegut.Logging, line 57

def logWarn(msg) { log.warn "${label()}: ${msg}" } // library marker davegut.Logging, line 59

def logError(msg) { log.error "${label()}: ${msg}" } // library marker davegut.Logging, line 61

// ~~~~~ end include (30) davegut.Logging ~~~~~
