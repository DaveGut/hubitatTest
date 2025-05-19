/*	TP-Link TAPO plug, switches, lights, hub, and hub sensors.
		Copyright Dave Gutheinz
License:  https://github.com/DaveGut/HubitatActive/blob/master/KasaDevices/License.md

Supports:  Tapo Plugs and Switches, New and Matter Kasa Plugs and Switches.
=================================================================================================*/
metadata {
	definition (name: "TpLink Plug", namespace: nameSpace(), author: "Dave Gutheinz", 
				singleThreaded: true,
				importUrl: "https://raw.githubusercontent.com/DaveGut/tpLink_Hubitat/main/Drivers/tpLink_plug.groovy")
	{ }
	preferences {
		commonPreferences()
	}
}

def installed() {
	Map logData = [method: "installed", commonInstalled: commonInstalled()]
	state.eventType = "digital"
	logInfo(logData)
}

def updated() {
	Map logData = [method: "updated", commonUpdated: commonUpdated()]
	logInfo(logData)
}

def parse_get_device_info(result, data) {
	switchParse(result)
}








// ~~~~~ start include (256) davegut.tpLinkCapSwitch ~~~~~
library ( // library marker davegut.tpLinkCapSwitch, line 1
	name: "tpLinkCapSwitch", // library marker davegut.tpLinkCapSwitch, line 2
	namespace: "davegut", // library marker davegut.tpLinkCapSwitch, line 3
	author: "Compied by Dave Gutheinz", // library marker davegut.tpLinkCapSwitch, line 4
	description: "Hubitat capability Switch methods", // library marker davegut.tpLinkCapSwitch, line 5
	category: "utilities", // library marker davegut.tpLinkCapSwitch, line 6
	documentationLink: "" // library marker davegut.tpLinkCapSwitch, line 7
) // library marker davegut.tpLinkCapSwitch, line 8

capability "Switch" // library marker davegut.tpLinkCapSwitch, line 10

def on() { setPower(true) } // library marker davegut.tpLinkCapSwitch, line 12

def off() { setPower(false) } // library marker davegut.tpLinkCapSwitch, line 14

def setPower(onOff) { // library marker davegut.tpLinkCapSwitch, line 16
	state.eventType = "digital" // library marker davegut.tpLinkCapSwitch, line 17
	logDebug("setPower: [device_on: ${onOff}]") // library marker davegut.tpLinkCapSwitch, line 18
	List requests = [[ // library marker davegut.tpLinkCapSwitch, line 19
		method: "set_device_info", // library marker davegut.tpLinkCapSwitch, line 20
		params: [device_on: onOff]]] // library marker davegut.tpLinkCapSwitch, line 21
	requests << [method: "get_device_info"] // library marker davegut.tpLinkCapSwitch, line 22
	sendDevCmd(requests, device.getDeviceNetworkId(), "parseUpdates") // library marker davegut.tpLinkCapSwitch, line 23
} // library marker davegut.tpLinkCapSwitch, line 24

def switchParse(result) { // library marker davegut.tpLinkCapSwitch, line 26
	Map logData = [method: "switchParse"] // library marker davegut.tpLinkCapSwitch, line 27
	if (result.device_on != null) { // library marker davegut.tpLinkCapSwitch, line 28
		def onOff = "off" // library marker davegut.tpLinkCapSwitch, line 29
		if (result.device_on == true) { onOff = "on" } // library marker davegut.tpLinkCapSwitch, line 30
		if (device.currentValue("switch") != onOff) { // library marker davegut.tpLinkCapSwitch, line 31
			sendEvent(name: "switch", value: onOff, type: state.eventType) // library marker davegut.tpLinkCapSwitch, line 32
			if (getDataValue("isEm") == "true" && !pollInterval.contains("sec")) { // library marker davegut.tpLinkCapSwitch, line 33
				runIn(4, refresh) // library marker davegut.tpLinkCapSwitch, line 34
			} // library marker davegut.tpLinkCapSwitch, line 35
		} // library marker davegut.tpLinkCapSwitch, line 36
		state.eventType = "physical" // library marker davegut.tpLinkCapSwitch, line 37
		logData << [switch: onOff] // library marker davegut.tpLinkCapSwitch, line 38
	} // library marker davegut.tpLinkCapSwitch, line 39
	logDebug(logData) // library marker davegut.tpLinkCapSwitch, line 40
} // library marker davegut.tpLinkCapSwitch, line 41

// ~~~~~ end include (256) davegut.tpLinkCapSwitch ~~~~~

// ~~~~~ start include (255) davegut.tpLinkCapEngMon ~~~~~
library ( // library marker davegut.tpLinkCapEngMon, line 1
	name: "tpLinkCapEngMon", // library marker davegut.tpLinkCapEngMon, line 2
	namespace: "davegut", // library marker davegut.tpLinkCapEngMon, line 3
	author: "Compied by Dave Gutheinz", // library marker davegut.tpLinkCapEngMon, line 4
	description: "Hubitat Energy Monitor methods", // library marker davegut.tpLinkCapEngMon, line 5
	category: "utilities", // library marker davegut.tpLinkCapEngMon, line 6
	documentationLink: "" // library marker davegut.tpLinkCapEngMon, line 7
) // library marker davegut.tpLinkCapEngMon, line 8

capability "EnergyMeter" // library marker davegut.tpLinkCapEngMon, line 10
capability "PowerMeter" // library marker davegut.tpLinkCapEngMon, line 11
attribute "past30Energy", "number" // library marker davegut.tpLinkCapEngMon, line 12
attribute "past7Energy", "number" // library marker davegut.tpLinkCapEngMon, line 13

def emUpdated() { // library marker davegut.tpLinkCapEngMon, line 15
	Map logData = [emDataUpdate: "30 mins"] // library marker davegut.tpLinkCapEngMon, line 16
	runEvery30Minutes(getEmData) // library marker davegut.tpLinkCapEngMon, line 17
	getEmData() // library marker davegut.tpLinkCapEngMon, line 18
	return logData // library marker davegut.tpLinkCapEngMon, line 19
} // library marker davegut.tpLinkCapEngMon, line 20

def powerPoll() { // library marker davegut.tpLinkCapEngMon, line 22
	List requests = [[method: "get_current_power"]] // library marker davegut.tpLinkCapEngMon, line 23
	sendDevCmd(requests, device.getDeviceNetworkId(), "parseUpdates") // library marker davegut.tpLinkCapEngMon, line 24
} // library marker davegut.tpLinkCapEngMon, line 25

def parse_get_current_power(result, data) { // library marker davegut.tpLinkCapEngMon, line 27
	Map logData = [method: "parse_get_current_power", data: data, // library marker davegut.tpLinkCapEngMon, line 28
				   power: result.current_power] // library marker davegut.tpLinkCapEngMon, line 29
	updateAttr("power", result.current_power) // library marker davegut.tpLinkCapEngMon, line 30
	logDebug(logData) // library marker davegut.tpLinkCapEngMon, line 31
} // library marker davegut.tpLinkCapEngMon, line 32

def getEmData() { // library marker davegut.tpLinkCapEngMon, line 34
	List requests = [[method: "get_device_usage"]] // library marker davegut.tpLinkCapEngMon, line 35
	sendDevCmd(requests, device.getDeviceNetworkId(), "parseUpdates") // library marker davegut.tpLinkCapEngMon, line 36
} // library marker davegut.tpLinkCapEngMon, line 37

def parse_get_device_usage(result, data) { // library marker davegut.tpLinkCapEngMon, line 39
	Map logData = [method: "parse_get_device_usage", data: data] // library marker davegut.tpLinkCapEngMon, line 40
	def usage = result.power_usage // library marker davegut.tpLinkCapEngMon, line 41
	updateAttr("energy", usage.today) // library marker davegut.tpLinkCapEngMon, line 42
	updateAttr("past30Energy", usage.past30) // library marker davegut.tpLinkCapEngMon, line 43
	updateAttr("past7Energy", usage.past7) // library marker davegut.tpLinkCapEngMon, line 44
	logData << [energy:usage.today, past30Energy: usage.past30, past7Energy: usage.past7] // library marker davegut.tpLinkCapEngMon, line 45
	logDebug(logData) // library marker davegut.tpLinkCapEngMon, line 46
} // library marker davegut.tpLinkCapEngMon, line 47

// ~~~~~ end include (255) davegut.tpLinkCapEngMon ~~~~~

// ~~~~~ start include (260) davegut.tpLinkCommon ~~~~~
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
	List pollOptions = ["5 sec", "10 sec", "1 min", "5 min", "15 min", "30 min"] // library marker davegut.tpLinkCommon, line 15
	input ("pollInterval", "enum", title: "Refresh Interval (includes on-off polling)", // library marker davegut.tpLinkCommon, line 16
		   options: pollOptions, defaultValue: "30 min") // library marker davegut.tpLinkCommon, line 17
	if (getDataValue("hasLed") == "true") { // library marker davegut.tpLinkCommon, line 18
		input ("ledRule", "enum", title: "LED Mode (if night mode, set type and times in phone app)", // library marker davegut.tpLinkCommon, line 19
			   options: ["always", "never", "night_mode"], defaultValue: "always") // library marker davegut.tpLinkCommon, line 20
	} // library marker davegut.tpLinkCommon, line 21
	input ("syncName", "enum", title: "Update Device Names and Labels",  // library marker davegut.tpLinkCommon, line 22
		   options: ["hubMaster", "tapoAppMaster", "notSet"], defaultValue: "notSet") // library marker davegut.tpLinkCommon, line 23
	input ("rebootDev", "bool", title: "Reboot Device", defaultValue: false) // library marker davegut.tpLinkCommon, line 24
	input ("logEnable", "bool",  title: "Enable debug logging for 30 minutes", defaultValue: false) // library marker davegut.tpLinkCommon, line 25
	input ("infoLog", "bool", title: "Enable information logging",defaultValue: true) // library marker davegut.tpLinkCommon, line 26
} // library marker davegut.tpLinkCommon, line 27

def commonInstalled() { // library marker davegut.tpLinkCommon, line 29
	Map logData = [method: "commonInstalled"] // library marker davegut.tpLinkCommon, line 30
	updateAttr("commsError", "false") // library marker davegut.tpLinkCommon, line 31
	state.errorCount = 0 // library marker davegut.tpLinkCommon, line 32
	logData << [configure: configure()] // library marker davegut.tpLinkCommon, line 33
	return logData // library marker davegut.tpLinkCommon, line 34
} // library marker davegut.tpLinkCommon, line 35

def commonUpdated() { // library marker davegut.tpLinkCommon, line 37
	unschedule() // library marker davegut.tpLinkCommon, line 38
	def commsErr = device.currentValue("commsError") // library marker davegut.tpLinkCommon, line 39
	Map logData = [commsError: commsErr] // library marker davegut.tpLinkCommon, line 40
	if (rebootDev == true) { // library marker davegut.tpLinkCommon, line 41
		List requests = [[method: "device_reboot"]] // library marker davegut.tpLinkCommon, line 42
		sendDevCmd(requests, "rebootDevice", "parseUpdates")  // library marker davegut.tpLinkCommon, line 43
		logData << [rebootDevice: "device reboot being attempted"] // library marker davegut.tpLinkCommon, line 44
	} else { // library marker davegut.tpLinkCommon, line 45
		logData << [pollInterval: setPollInterval()] // library marker davegut.tpLinkCommon, line 46
		logData << [logging: setLogsOff()] // library marker davegut.tpLinkCommon, line 47
		logData << [updateDevSettings: updDevSettings()] // library marker davegut.tpLinkCommon, line 48
		if(pollInterval != "5 sec" && pollInterval != "10 sec") { // library marker davegut.tpLinkCommon, line 49
			runIn(5, refresh) // library marker davegut.tpLinkCommon, line 50
		} // library marker davegut.tpLinkCommon, line 51
		if (getDataValue("isEm") == "true") { // library marker davegut.tpLinkCommon, line 52
			runIn(7, emUpdated) // library marker davegut.tpLinkCommon, line 53
		} // library marker davegut.tpLinkCommon, line 54
	} // library marker davegut.tpLinkCommon, line 55
	return logData // library marker davegut.tpLinkCommon, line 56
} // library marker davegut.tpLinkCommon, line 57

def finishReboot(respData) { // library marker davegut.tpLinkCommon, line 59
	Map logData = [method: "finishReboot", respData: respData] // library marker davegut.tpLinkCommon, line 60
	logData << [wait: "<b>20s for device to reconnect to LAN</b>", action: "executing deviceHandshake"] // library marker davegut.tpLinkCommon, line 61
	runIn(20, configure) // library marker davegut.tpLinkCommon, line 62
	device.updateSetting("rebootDev",[type:"bool", value: false]) // library marker davegut.tpLinkCommon, line 63
	logInfo(logData) // library marker davegut.tpLinkCommon, line 64
} // library marker davegut.tpLinkCommon, line 65

def updDevSettings() { // library marker davegut.tpLinkCommon, line 67
	List requests = [] // library marker davegut.tpLinkCommon, line 68
	if (syncName == "hubMaster") { // library marker davegut.tpLinkCommon, line 69
		String nickname = device.getLabel().bytes.encodeBase64().toString() // library marker davegut.tpLinkCommon, line 70
		requests << [method: "set_device_info", params: [nickname: nickname]] // library marker davegut.tpLinkCommon, line 71
	} // library marker davegut.tpLinkCommon, line 72
	if (ledRule) { // library marker davegut.tpLinkCommon, line 73
		requests << [method: "get_led_info"] // library marker davegut.tpLinkCommon, line 74
	} // library marker davegut.tpLinkCommon, line 75
	if (getDataValue("isEm") == "true") { // library marker davegut.tpLinkCommon, line 76
		requests << [method: "get_energy_usage"] // library marker davegut.tpLinkCommon, line 77
	} // library marker davegut.tpLinkCommon, line 78
	requests << [method: "get_device_info"] // library marker davegut.tpLinkCommon, line 79
	sendDevCmd(requests, device.getDeviceNetworkId(), "parseUpdates") // library marker davegut.tpLinkCommon, line 80
	return "Updated" // library marker davegut.tpLinkCommon, line 81
} // library marker davegut.tpLinkCommon, line 82

//	===== Capability Configuration ===== // library marker davegut.tpLinkCommon, line 84
def appConfigure(delay) { // library marker davegut.tpLinkCommon, line 85
	runIn(delay, configure) // library marker davegut.tpLinkCommon, line 86
} // library marker davegut.tpLinkCommon, line 87
def configure() { // library marker davegut.tpLinkCommon, line 88
	//	new design. // library marker davegut.tpLinkCommon, line 89
	//	a.	Ping the device for user information. // library marker davegut.tpLinkCommon, line 90
	//	b.	Poll the UDP port of the device to see if the device is this  // library marker davegut.tpLinkCommon, line 91
	//		device.  Goes to method configure2 for parsing. // library marker davegut.tpLinkCommon, line 92
	//	c.	If not this device, then run parent.tpLinkCheckForDevices to  // library marker davegut.tpLinkCommon, line 93
	//		repoll the lan and update the device baseUrl (if found). // library marker davegut.tpLinkCommon, line 94
	//		1.	If not found, notify the user of the failure and continue  // library marker davegut.tpLinkCommon, line 95
	//		using current baseUrl. // library marker davegut.tpLinkCommon, line 96
	//	d.  update device data, login to the device, and schedule // library marker davegut.tpLinkCommon, line 97
	//		periodic logins. // library marker davegut.tpLinkCommon, line 98
	def ip = getDataValue("baseUrl").replace("""http://""", "").replace(":80/app", "") // library marker davegut.tpLinkCommon, line 99
	Map logData = [method: "configure", ip: ip] // library marker davegut.tpLinkCommon, line 100
	logInfo("executing ${logData}") // library marker davegut.tpLinkCommon, line 101
	def await = ping()	//	quick check to (possibly) wake up connection. Also informs user if fails. // library marker davegut.tpLinkCommon, line 102
	def cmdData = "0200000101e51100095c11706d6f58577b22706172616d73223a7b227273615f6b6579223a222d2d2d2d2d424547494e205055424c4943204b45592d2d2d2d2d5c6e4d494942496a414e42676b71686b6947397730424151454641414f43415138414d49494243674b43415145416d684655445279687367797073467936576c4d385c6e54646154397a61586133586a3042712f4d6f484971696d586e2b736b4e48584d525a6550564134627532416257386d79744a5033445073665173795679536e355c6e6f425841674d303149674d4f46736350316258367679784d523871614b33746e466361665a4653684d79536e31752f564f2f47474f795436507459716f384e315c6e44714d77373563334b5a4952387a4c71516f744657747239543337536e50754a7051555a7055376679574b676377716e7338785a657a78734e6a6465534171765c6e3167574e75436a5356686d437931564d49514942576d616a37414c47544971596a5442376d645348562f2b614a32564467424c6d7770344c7131664c4f6a466f5c6e33737241683144744a6b537376376a624f584d51695666453873764b6877586177717661546b5658382f7a4f44592b2f64684f5374694a4e6c466556636c35585c6e4a514944415141425c6e2d2d2d2d2d454e44205055424c4943204b45592d2d2d2d2d5c6e227d7d" // library marker davegut.tpLinkCommon, line 103
	try { // library marker davegut.tpLinkCommon, line 104
		sendFindCmd(ip, "20002", cmdData, "configure2", timeout) // library marker davegut.tpLinkCommon, line 105
		logInfo(logData) // library marker davegut.tpLinkCommon, line 106
	} catch (err) { // library marker davegut.tpLinkCommon, line 107
		//	If error here, log. then run parent.tpLinkCheckForDevices // library marker davegut.tpLinkCommon, line 108
		//	followed by configure3 // library marker davegut.tpLinkCommon, line 109
		def parentChecked = parent.tpLinkCheckForDevices(5) // library marker davegut.tpLinkCommon, line 110
		logData << [status: "FAILED", error: err, parentChecked: parentChecked] // library marker davegut.tpLinkCommon, line 111
		logWarn(logData) // library marker davegut.tpLinkCommon, line 112
		configure3(parentChecked) // library marker davegut.tpLinkCommon, line 113
	} // library marker davegut.tpLinkCommon, line 114
} // library marker davegut.tpLinkCommon, line 115
def configure2(response) { // library marker davegut.tpLinkCommon, line 116
	Map logData = [method: "configure2"] // library marker davegut.tpLinkCommon, line 117
	def respData = parseLanMessage(response) // library marker davegut.tpLinkCommon, line 118
	String hubDni = device.getDeviceNetworkId() // library marker davegut.tpLinkCommon, line 119
	logData << [dni: respData.mac, hubDni: hubDni] // library marker davegut.tpLinkCommon, line 120
	def parentChecked = false // library marker davegut.tpLinkCommon, line 121
	if (respData.mac != hubDni) { // library marker davegut.tpLinkCommon, line 122
		logData << [status: "FAILED", action: "parentCheck"] // library marker davegut.tpLinkCommon, line 123
		parentChecked = parent.tpLinkCheckForDevices(5) // library marker davegut.tpLinkCommon, line 124
	} else { // library marker davegut.tpLinkCommon, line 125
		logData << [status: "OK", action: configure3] // library marker davegut.tpLinkCommon, line 126
	} // library marker davegut.tpLinkCommon, line 127
	configure3(parentChecked) // library marker davegut.tpLinkCommon, line 128
	logInfo(logData) // library marker davegut.tpLinkCommon, line 129
} // library marker davegut.tpLinkCommon, line 130
def configure3(parentChecked = false) { // library marker davegut.tpLinkCommon, line 131
	Map logData = [method: "configure3", parentChecked: parentChecked] // library marker davegut.tpLinkCommon, line 132
	logData << updateDeviceData() // library marker davegut.tpLinkCommon, line 133
	logData << deviceHandshake() // library marker davegut.tpLinkCommon, line 134
	pauseExecution(10000) // library marker davegut.tpLinkCommon, line 135
	runEvery3Hours(deviceHandshake) // library marker davegut.tpLinkCommon, line 136
	logData << [handshakeInterval: "3 Hours"] // library marker davegut.tpLinkCommon, line 137
	logData << [action: "exec updated"] // library marker davegut.tpLinkCommon, line 138
	runIn(2, updated) // library marker davegut.tpLinkCommon, line 139
	logInfo(logData) // library marker davegut.tpLinkCommon, line 140
} // library marker davegut.tpLinkCommon, line 141

def setPollInterval(pInterval = pollInterval) { // library marker davegut.tpLinkCommon, line 143
	if (pInterval.contains("sec")) { // library marker davegut.tpLinkCommon, line 144
		logWarn("<b>Poll intervals of less than 1 minute may overload the Hub</b>") // library marker davegut.tpLinkCommon, line 145
		def interval = pInterval.replace(" sec", "").toInteger() // library marker davegut.tpLinkCommon, line 146
		def start = Math.round((interval-1) * Math.random()).toInteger() // library marker davegut.tpLinkCommon, line 147
		schedule("${start}/${interval} * * * * ?", "refresh") // library marker davegut.tpLinkCommon, line 148
	} else { // library marker davegut.tpLinkCommon, line 149
		def interval= pInterval.replace(" min", "").toInteger() // library marker davegut.tpLinkCommon, line 150
		def start = Math.round(59 * Math.random()).toInteger() // library marker davegut.tpLinkCommon, line 151
		schedule("${start} */${interval} * * * ?", "refresh") // library marker davegut.tpLinkCommon, line 152
	} // library marker davegut.tpLinkCommon, line 153
	return pInterval // library marker davegut.tpLinkCommon, line 154
} // library marker davegut.tpLinkCommon, line 155

//	===== Data Distribution (and parse) ===== // library marker davegut.tpLinkCommon, line 157
def parseUpdates(resp, data = null) { // library marker davegut.tpLinkCommon, line 158
	Map logData = [method: "parseUpdates", data: data] // library marker davegut.tpLinkCommon, line 159
	def respData = parseData(resp) // library marker davegut.tpLinkCommon, line 160
	if (resp.status == 200 && respData.cryptoStatus == "OK") { // library marker davegut.tpLinkCommon, line 161
		def cmdResp = respData.cmdResp.result.responses // library marker davegut.tpLinkCommon, line 162
		if (respData.cmdResp.result.responses != null) { // library marker davegut.tpLinkCommon, line 163
			respData.cmdResp.result.responses.each { // library marker davegut.tpLinkCommon, line 164
				if (it.error_code == 0) { // library marker davegut.tpLinkCommon, line 165
					distGetData(it, data) // library marker davegut.tpLinkCommon, line 166
				} else { // library marker davegut.tpLinkCommon, line 167
					logData << ["${it.method}": [status: "cmdFailed", data: it]] // library marker davegut.tpLinkCommon, line 168
					logDebug(logData) // library marker davegut.tpLinkCommon, line 169
				} // library marker davegut.tpLinkCommon, line 170
			} // library marker davegut.tpLinkCommon, line 171
		} // library marker davegut.tpLinkCommon, line 172
		if (respData.cmdResp.result.responseData != null) { // library marker davegut.tpLinkCommon, line 173
			respData.cmdResp.result.responseData.result.responses.each { // library marker davegut.tpLinkCommon, line 174
				if (it.error_code == 0) { // library marker davegut.tpLinkCommon, line 175
					distChildGetData(it, data) // library marker davegut.tpLinkCommon, line 176
				} else { // library marker davegut.tpLinkCommon, line 177
					logData << ["${it.method}": [status: "cmdFailed", data: it]] // library marker davegut.tpLinkCommon, line 178
					logDebug(logData) // library marker davegut.tpLinkCommon, line 179
				} // library marker davegut.tpLinkCommon, line 180
			} // library marker davegut.tpLinkCommon, line 181
		} // library marker davegut.tpLinkCommon, line 182
	} else { // library marker davegut.tpLinkCommon, line 183
		logData << [errorMsg: "Misc Error"] // library marker davegut.tpLinkCommon, line 184
		logDebug(logData) // library marker davegut.tpLinkCommon, line 185
	} // library marker davegut.tpLinkCommon, line 186
} // library marker davegut.tpLinkCommon, line 187

def distGetData(devResp, data) { // library marker davegut.tpLinkCommon, line 189
	switch(devResp.method) { // library marker davegut.tpLinkCommon, line 190
		case "get_device_info": // library marker davegut.tpLinkCommon, line 191
			parse_get_device_info(devResp.result, data) // library marker davegut.tpLinkCommon, line 192
			parseNameUpdate(devResp.result) // library marker davegut.tpLinkCommon, line 193
			break // library marker davegut.tpLinkCommon, line 194
		case "get_current_power": // library marker davegut.tpLinkCommon, line 195
			parse_get_current_power(devResp.result, data) // library marker davegut.tpLinkCommon, line 196
			break // library marker davegut.tpLinkCommon, line 197
		case "get_device_usage": // library marker davegut.tpLinkCommon, line 198
			parse_get_device_usage(devResp.result, data) // library marker davegut.tpLinkCommon, line 199
			break // library marker davegut.tpLinkCommon, line 200
		case "get_child_device_list": // library marker davegut.tpLinkCommon, line 201
			parse_get_child_device_list(devResp.result, data) // library marker davegut.tpLinkCommon, line 202
			break // library marker davegut.tpLinkCommon, line 203
		case "get_alarm_configure": // library marker davegut.tpLinkCommon, line 204
			parse_get_alarm_configure(devResp.result, data) // library marker davegut.tpLinkCommon, line 205
			break // library marker davegut.tpLinkCommon, line 206
		case "get_led_info": // library marker davegut.tpLinkCommon, line 207
			parse_get_led_info(devResp.result, data) // library marker davegut.tpLinkCommon, line 208
			break // library marker davegut.tpLinkCommon, line 209
		case "device_reboot": // library marker davegut.tpLinkCommon, line 210
			finishReboot(devResp) // library marker davegut.tpLinkCommon, line 211
			break // library marker davegut.tpLinkCommon, line 212
		case "get_battery_info": // library marker davegut.tpLinkCommon, line 213
			parse_get_battery_info(devResp.result) // library marker davegut.tpLinkCommon, line 214
			break // library marker davegut.tpLinkCommon, line 215
		case "getCleanNumber": // library marker davegut.tpLinkCommon, line 216
			parse_getCleanNumber(devResp.result) // library marker davegut.tpLinkCommon, line 217
			break // library marker davegut.tpLinkCommon, line 218
		case "getSwitchClean": // library marker davegut.tpLinkCommon, line 219
			parse_getSwitchClean(devResp) // library marker davegut.tpLinkCommon, line 220
			break // library marker davegut.tpLinkCommon, line 221
		case "getMopState": // library marker davegut.tpLinkCommon, line 222
			parse_getMopState(devResp) // library marker davegut.tpLinkCommon, line 223
			break // library marker davegut.tpLinkCommon, line 224
		case "getSwitchCharge": // library marker davegut.tpLinkCommon, line 225
			updateAttr("docking", devResp.switch_charge) // library marker davegut.tpLinkCommon, line 226
			break // library marker davegut.tpLinkCommon, line 227
		case "getVacStatus": // library marker davegut.tpLinkCommon, line 228
			parse_getVacStatus(devResp.result) // library marker davegut.tpLinkCommon, line 229
			break // library marker davegut.tpLinkCommon, line 230
		default: // library marker davegut.tpLinkCommon, line 231
			if (!devResp.method.contains("set_")) { // library marker davegut.tpLinkCommon, line 232
				Map logData = [method: "distGetData", data: data, // library marker davegut.tpLinkCommon, line 233
							   devMethod: devResp.method, status: "unprocessed"] // library marker davegut.tpLinkCommon, line 234
				logDebug(logData) // library marker davegut.tpLinkCommon, line 235
			} // library marker davegut.tpLinkCommon, line 236
	} // library marker davegut.tpLinkCommon, line 237
} // library marker davegut.tpLinkCommon, line 238

def parse_get_led_info(result, data) { // library marker davegut.tpLinkCommon, line 240
	Map logData = [method: "parse_get_led_info", data: data] // library marker davegut.tpLinkCommon, line 241
	if (ledRule != result.led_rule) { // library marker davegut.tpLinkCommon, line 242
		Map request = [ // library marker davegut.tpLinkCommon, line 243
			method: "set_led_info", // library marker davegut.tpLinkCommon, line 244
			params: [ // library marker davegut.tpLinkCommon, line 245
				led_rule: ledRule, // library marker davegut.tpLinkCommon, line 246
				night_mode: [ // library marker davegut.tpLinkCommon, line 247
					night_mode_type: result.night_mode.night_mode_type, // library marker davegut.tpLinkCommon, line 248
					sunrise_offset: result.night_mode.sunrise_offset,  // library marker davegut.tpLinkCommon, line 249
					sunset_offset:result.night_mode.sunset_offset, // library marker davegut.tpLinkCommon, line 250
					start_time: result.night_mode.start_time, // library marker davegut.tpLinkCommon, line 251
					end_time: result.night_mode.end_time // library marker davegut.tpLinkCommon, line 252
				]]] // library marker davegut.tpLinkCommon, line 253
		asyncSend(request, "delayedUpdates", "parseUpdates") // library marker davegut.tpLinkCommon, line 254
		device.updateSetting("ledRule", [type:"enum", value: ledRule]) // library marker davegut.tpLinkCommon, line 255
		logData << [status: "updatingLedRule"] // library marker davegut.tpLinkCommon, line 256
	} // library marker davegut.tpLinkCommon, line 257
	logData << [ledRule: ledRule] // library marker davegut.tpLinkCommon, line 258
	logDebug(logData) // library marker davegut.tpLinkCommon, line 259
} // library marker davegut.tpLinkCommon, line 260

def parseNameUpdate(result) { // library marker davegut.tpLinkCommon, line 262
	if (syncName != "notSet") { // library marker davegut.tpLinkCommon, line 263
		Map logData = [method: "parseNameUpdate"] // library marker davegut.tpLinkCommon, line 264
		byte[] plainBytes = result.nickname.decodeBase64() // library marker davegut.tpLinkCommon, line 265
		def newLabel = new String(plainBytes) // library marker davegut.tpLinkCommon, line 266
		device.setLabel(newLabel) // library marker davegut.tpLinkCommon, line 267
		device.updateSetting("syncName",[type:"enum", value: "notSet"]) // library marker davegut.tpLinkCommon, line 268
		logData << [label: newLabel] // library marker davegut.tpLinkCommon, line 269
		logDebug(logData) // library marker davegut.tpLinkCommon, line 270
	} // library marker davegut.tpLinkCommon, line 271
} // library marker davegut.tpLinkCommon, line 272

//	===== Capability Refresh ===== // library marker davegut.tpLinkCommon, line 274
def refresh() { // library marker davegut.tpLinkCommon, line 275
	def type = getDataValue("type") // library marker davegut.tpLinkCommon, line 276
	List requests = [[method: "get_device_info"]] // library marker davegut.tpLinkCommon, line 277
	if (type == "Hub" || type == "Parent") { // library marker davegut.tpLinkCommon, line 278
		requests << [method:"get_child_device_list"] // library marker davegut.tpLinkCommon, line 279
	} // library marker davegut.tpLinkCommon, line 280
	if (getDataValue("isEm") == "true") { // library marker davegut.tpLinkCommon, line 281
		requests << [method: "get_current_power"] // library marker davegut.tpLinkCommon, line 282
	} // library marker davegut.tpLinkCommon, line 283
	if (getDataValue("protocol") == "vacAes") { // library marker davegut.tpLinkCommon, line 284
		vacRefresh() // library marker davegut.tpLinkCommon, line 285
	} // library marker davegut.tpLinkCommon, line 286
	sendDevCmd(requests, device.getDeviceNetworkId(), "parseUpdates") // library marker davegut.tpLinkCommon, line 287
} // library marker davegut.tpLinkCommon, line 288

//	===== Version Compatibility ===== // library marker davegut.tpLinkCommon, line 290
def plugEmRefresh() { refresh() } // library marker davegut.tpLinkCommon, line 291
def parentRefresh() { refresh() } // library marker davegut.tpLinkCommon, line 292
def minRefresh() { refresh() } // library marker davegut.tpLinkCommon, line 293

def sendDevCmd(requests, data, action) { // library marker davegut.tpLinkCommon, line 295
	Map cmdBody = [ // library marker davegut.tpLinkCommon, line 296
		method: "multipleRequest", // library marker davegut.tpLinkCommon, line 297
		params: [requests: requests]] // library marker davegut.tpLinkCommon, line 298
	asyncSend(cmdBody, data, action) // library marker davegut.tpLinkCommon, line 299
} // library marker davegut.tpLinkCommon, line 300

def nullParse(resp, data) { } // library marker davegut.tpLinkCommon, line 302

//	===== Check/Update device data ===== // library marker davegut.tpLinkCommon, line 304
def updateDeviceData() { // library marker davegut.tpLinkCommon, line 305
	def devData = parent.getDeviceData(device.getDeviceNetworkId()) // library marker davegut.tpLinkCommon, line 306
	updateChild(devData) // library marker davegut.tpLinkCommon, line 307
	return [updateDeviceData: "updating with app data"] // library marker davegut.tpLinkCommon, line 308
} // library marker davegut.tpLinkCommon, line 309

def updateChild(devData) { // library marker davegut.tpLinkCommon, line 311
	def currVersion = getDataValue("version") // library marker davegut.tpLinkCommon, line 312
	Map logData = [method: "updateChild"] // library marker davegut.tpLinkCommon, line 313
	if (devData != null) { // library marker davegut.tpLinkCommon, line 314
		updateDataValue("baseUrl", devData.baseUrl) // library marker davegut.tpLinkCommon, line 315
		updateDataValue("protocol", devData.protocol) // library marker davegut.tpLinkCommon, line 316
		logData << [baseUrl: devData.baseUrl, protocol: devData.protocol] // library marker davegut.tpLinkCommon, line 317
		if (currVeresion != version()) { // library marker davegut.tpLinkCommon, line 318
			updateDataValue("isEm", devData.isEm) // library marker davegut.tpLinkCommon, line 319
			updateDataValue("hasLed", devData.hasLed) // library marker davegut.tpLinkCommon, line 320
			updateDataValue("version", version()) // library marker davegut.tpLinkCommon, line 321
		} // library marker davegut.tpLinkCommon, line 322
		logData << [isEm: devData.isEm, hasLed: devData.hasLed,  // library marker davegut.tpLinkCommon, line 323
					currVersion: currVersion, newVersion: version()] // library marker davegut.tpLinkCommon, line 324
	} else { // library marker davegut.tpLinkCommon, line 325
		logData << [Note: "DEVICE DATA IS NULL"] // library marker davegut.tpLinkCommon, line 326
	} // library marker davegut.tpLinkCommon, line 327
	logInfo(logData) // library marker davegut.tpLinkCommon, line 328
} // library marker davegut.tpLinkCommon, line 329

//	===== Device Handshake ===== // library marker davegut.tpLinkCommon, line 331
def deviceHandshake() { // library marker davegut.tpLinkCommon, line 332
	//	Do a three packet ping to check LAN connectivity.  This does // library marker davegut.tpLinkCommon, line 333
	//	not stop the sending of the handshake message. // library marker davegut.tpLinkCommon, line 334
	def await = ping() // library marker davegut.tpLinkCommon, line 335
	//	On handshake, will log into device and then attempt a command // library marker davegut.tpLinkCommon, line 336
	//	that validates the complete crypto path (get_device_info - no parse). // library marker davegut.tpLinkCommon, line 337
	//	When comms error is set for 403 or 408 reasons, this procedure is // library marker davegut.tpLinkCommon, line 338
	//	scheduled for every 10 minutes to check if the condition has alleviated. // library marker davegut.tpLinkCommon, line 339
	def protocol = getDataValue("protocol") // library marker davegut.tpLinkCommon, line 340
	Map logData = [method: "deviceHandshake", protocol: protocol] // library marker davegut.tpLinkCommon, line 341
	if (protocol == "KLAP") { // library marker davegut.tpLinkCommon, line 342
		klapHandshake() // library marker davegut.tpLinkCommon, line 343
	} else if (protocol == "AES") { // library marker davegut.tpLinkCommon, line 344
		aesHandshake() // library marker davegut.tpLinkCommon, line 345
	} else if (protocol == "vacAes") { // library marker davegut.tpLinkCommon, line 346
		vacHandshake() // library marker davegut.tpLinkCommon, line 347
	} else { // library marker davegut.tpLinkCommon, line 348
		logData << [ERROR: "Protocol not supported"] // library marker davegut.tpLinkCommon, line 349
		logWarn(logData) // library marker davegut.tpLinkCommon, line 350
	} // library marker davegut.tpLinkCommon, line 351
	logDebug(logData) // library marker davegut.tpLinkCommon, line 352
	runIn(5, commsTest) // library marker davegut.tpLinkCommon, line 353
	return logData // library marker davegut.tpLinkCommon, line 354
} // library marker davegut.tpLinkCommon, line 355

def commsTest() { // library marker davegut.tpLinkCommon, line 357
	List requests = [[method: "get_device_info"]] // library marker davegut.tpLinkCommon, line 358
	sendDevCmd(requests, device.getDeviceNetworkId(), "parseCommsTest") // library marker davegut.tpLinkCommon, line 359
} // library marker davegut.tpLinkCommon, line 360
def parseCommsTest(resp, data = null) { // library marker davegut.tpLinkCommon, line 361
	Map logData = [method: "parseCommsTest"] // library marker davegut.tpLinkCommon, line 362
	Map respData = parseData(resp) // library marker davegut.tpLinkCommon, line 363
	def message = "OK" // library marker davegut.tpLinkCommon, line 364
	if (resp.status == 200 && respData.cryptoStatus == "OK") { // library marker davegut.tpLinkCommon, line 365
		logData << [testStatus: "success", userMessage: "Comms Path (lan/crypto module) OK"] // library marker davegut.tpLinkCommon, line 366
		logInfo(logData) // library marker davegut.tpLinkCommon, line 367
	} else if (respData.cryptoStatus != "OK") { // library marker davegut.tpLinkCommon, line 368
		logData << [testStatus: "FAILED - Crypto", // library marker davegut.tpLinkCommon, line 369
					userMessage: "Decrypting failed.  Run Configure."] // library marker davegut.tpLinkCommon, line 370
		logWarn(logData) // library marker davegut.tpLinkCommon, line 371
	} else if (resp.status != 200) { // library marker davegut.tpLinkCommon, line 372
		logData << [testStatus: "FAILED - noRoute", respMessage: message, // library marker davegut.tpLinkCommon, line 373
					userMessage: "Your router connection to ${getDataValue("baseUrl")} failed.  Run Configure."] // library marker davegut.tpLinkCommon, line 374
		logWarn(logData) // library marker davegut.tpLinkCommon, line 375
	} else { // library marker davegut.tpLinkCommon, line 376
		logData << [testStatus: "FAILED - unknown cause"] // library marker davegut.tpLinkCommon, line 377
	} // library marker davegut.tpLinkCommon, line 378
} // library marker davegut.tpLinkCommon, line 379

// ~~~~~ end include (260) davegut.tpLinkCommon ~~~~~

// ~~~~~ start include (261) davegut.tpLinkComms ~~~~~
library ( // library marker davegut.tpLinkComms, line 1
	name: "tpLinkComms", // library marker davegut.tpLinkComms, line 2
	namespace: "davegut", // library marker davegut.tpLinkComms, line 3
	author: "Compiled by Dave Gutheinz", // library marker davegut.tpLinkComms, line 4
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
	def protocol = getDataValue("protocol") // library marker davegut.tpLinkComms, line 17
	Map reqParams = [:] // library marker davegut.tpLinkComms, line 18
	if (protocol == "KLAP") { // library marker davegut.tpLinkComms, line 19
		reqParams = getKlapParams(cmdBody) // library marker davegut.tpLinkComms, line 20
	} else if (protocol == "AES") { // library marker davegut.tpLinkComms, line 21
		reqParams = getAesParams(cmdBody) // library marker davegut.tpLinkComms, line 22
	} else if (protocol == "vacAes") { // library marker davegut.tpLinkComms, line 23
		reqParams = getVacAesParams(cmdBody) // library marker davegut.tpLinkComms, line 24
	} // library marker davegut.tpLinkComms, line 25
	if (state.errorCount == 0) { // library marker davegut.tpLinkComms, line 26
		state.lastCommand = cmdData // library marker davegut.tpLinkComms, line 27
	} // library marker davegut.tpLinkComms, line 28
	asynchttpPost(action, reqParams, [data: reqData]) // library marker davegut.tpLinkComms, line 29
} // library marker davegut.tpLinkComms, line 30

def parseData(resp, protocol = getDataValue("protocol")) { // library marker davegut.tpLinkComms, line 32
	Map logData = [method: "parseData", status: resp.status] // library marker davegut.tpLinkComms, line 33
	def message = "OK" // library marker davegut.tpLinkComms, line 34
	if (resp.status != 200) { message = resp.errorMessage } // library marker davegut.tpLinkComms, line 35
	if (resp.status == 200) { // library marker davegut.tpLinkComms, line 36
		if (protocol == "KLAP") { // library marker davegut.tpLinkComms, line 37
			logData << parseKlapData(resp) // library marker davegut.tpLinkComms, line 38
		} else if (protocol == "AES") { // library marker davegut.tpLinkComms, line 39
			logData << parseAesData(resp) // library marker davegut.tpLinkComms, line 40
		} else if (protocol == "vacAes") { // library marker davegut.tpLinkComms, line 41
			logData << parseVacAesData(resp) // library marker davegut.tpLinkComms, line 42
		} // library marker davegut.tpLinkComms, line 43
	} else { // library marker davegut.tpLinkComms, line 44
		String userMessage = "unspecified" // library marker davegut.tpLinkComms, line 45
		if (resp.status == 403) { // library marker davegut.tpLinkComms, line 46
			userMessage = "<b>Try again. If error persists, check your credentials</b>" // library marker davegut.tpLinkComms, line 47
		} else if (resp.status == 408) { // library marker davegut.tpLinkComms, line 48
			userMessage = "<b>Your router connection to ${getDataValue("baseUrl")} failed.  Run Configure.</b>" // library marker davegut.tpLinkComms, line 49
		} else { // library marker davegut.tpLinkComms, line 50
			userMessage = "<b>Unhandled error Lan return</b>" // library marker davegut.tpLinkComms, line 51
		} // library marker davegut.tpLinkComms, line 52
		logData << [respMessage: message, userMessage: userMessage] // library marker davegut.tpLinkComms, line 53
		logDebug(logData) // library marker davegut.tpLinkComms, line 54
	} // library marker davegut.tpLinkComms, line 55
	handleCommsError(resp.status, message) // library marker davegut.tpLinkComms, line 56
	return logData // library marker davegut.tpLinkComms, line 57
} // library marker davegut.tpLinkComms, line 58

//	===== Communications Error Handling ===== // library marker davegut.tpLinkComms, line 60
def handleCommsError(status, msg = "") { // library marker davegut.tpLinkComms, line 61
	//	Retransmit all comms error except Switch and Level related (Hub retries for these). // library marker davegut.tpLinkComms, line 62
	//	This is determined by state.digital // library marker davegut.tpLinkComms, line 63
	if (status == 200) { // library marker davegut.tpLinkComms, line 64
		setCommsError(status, "OK") // library marker davegut.tpLinkComms, line 65
	} else { // library marker davegut.tpLinkComms, line 66
		Map logData = [method: "handleCommsError", status: code, msg: msg] // library marker davegut.tpLinkComms, line 67
		def count = state.errorCount + 1 // library marker davegut.tpLinkComms, line 68
		logData << [count: count, status: status, msg: msg] // library marker davegut.tpLinkComms, line 69
		switch(count) { // library marker davegut.tpLinkComms, line 70
			case 1: // library marker davegut.tpLinkComms, line 71
			case 2: // library marker davegut.tpLinkComms, line 72
				//	errors 1 and 2, retry immediately // library marker davegut.tpLinkComms, line 73
				runIn(2, delayedPassThrough) // library marker davegut.tpLinkComms, line 74
				break // library marker davegut.tpLinkComms, line 75
			case 3: // library marker davegut.tpLinkComms, line 76
				//	error 3, login or scan find device on the lan // library marker davegut.tpLinkComms, line 77
				//	then retry // library marker davegut.tpLinkComms, line 78
				if (status == 403) { // library marker davegut.tpLinkComms, line 79
					logData << [action: "attemptLogin"] // library marker davegut.tpLinkComms, line 80
					deviceHandshake() // library marker davegut.tpLinkComms, line 81
					runIn(4, delayedPassThrough) // library marker davegut.tpLinkComms, line 82
				} else { // library marker davegut.tpLinkComms, line 83
					logData << [action: "Find on LAN then login"] // library marker davegut.tpLinkComms, line 84
					configure() // library marker davegut.tpLinkComms, line 85
					runIn(10, delayedPassThrough) // library marker davegut.tpLinkComms, line 86
				} // library marker davegut.tpLinkComms, line 87
				break // library marker davegut.tpLinkComms, line 88
			case 4: // library marker davegut.tpLinkComms, line 89
				runIn(2, delayedPassThrough) // library marker davegut.tpLinkComms, line 90
				break // library marker davegut.tpLinkComms, line 91
			default: // library marker davegut.tpLinkComms, line 92
				//	Set comms error first time errros are 5 or more. // library marker davegut.tpLinkComms, line 93
				logData << [action: "SetCommsErrorTrue"] // library marker davegut.tpLinkComms, line 94
				setCommsError(status, msg, 5) // library marker davegut.tpLinkComms, line 95
		} // library marker davegut.tpLinkComms, line 96
		state.errorCount = count // library marker davegut.tpLinkComms, line 97
		logInfo(logData) // library marker davegut.tpLinkComms, line 98
	} // library marker davegut.tpLinkComms, line 99
} // library marker davegut.tpLinkComms, line 100

def delayedPassThrough() { // library marker davegut.tpLinkComms, line 102
	//	Do a single packet ping to check LAN connectivity.  This does // library marker davegut.tpLinkComms, line 103
	//	not stop the sending of the retry message. // library marker davegut.tpLinkComms, line 104
	def await = ping(getDataValue("baseUrl"), 1) // library marker davegut.tpLinkComms, line 105
	def cmdData = new JSONObject(state.lastCommand) // library marker davegut.tpLinkComms, line 106
	def cmdBody = parseJson(cmdData.cmdBody.toString()) // library marker davegut.tpLinkComms, line 107
	asyncSend(cmdBody, cmdData.reqData, cmdData.action) // library marker davegut.tpLinkComms, line 108
} // library marker davegut.tpLinkComms, line 109

def ping(baseUrl = getDataValue("baseUrl"), count = 1) { // library marker davegut.tpLinkComms, line 111
	def ip = baseUrl.replace("""http://""", "").replace(":80/app", "").replace(":4433", "") // library marker davegut.tpLinkComms, line 112
	ip = ip.replace("""https://""", "").replace(":4433", "") // library marker davegut.tpLinkComms, line 113
	hubitat.helper.NetworkUtils.PingData pingData = hubitat.helper.NetworkUtils.ping(ip, count) // library marker davegut.tpLinkComms, line 114
	Map pingReturn = [method: "ping", ip: ip] // library marker davegut.tpLinkComms, line 115
	if (pingData.packetsReceived == count) { // library marker davegut.tpLinkComms, line 116
		pingReturn << [pingStatus: "success"] // library marker davegut.tpLinkComms, line 117
		logDebug(pingReturn) // library marker davegut.tpLinkComms, line 118
	} else { // library marker davegut.tpLinkComms, line 119
		pingReturn << [pingData: pingData, pingStatus: "<b>FAILED</b>.  There may be issues with your LAN."] // library marker davegut.tpLinkComms, line 120
		logWarn(pingReturn) // library marker davegut.tpLinkComms, line 121
	} // library marker davegut.tpLinkComms, line 122
	return pingReturn // library marker davegut.tpLinkComms, line 123
} // library marker davegut.tpLinkComms, line 124

def setCommsError(status, msg = "OK", count = state.commsError) { // library marker davegut.tpLinkComms, line 126
	Map logData = [method: "setCommsError", status: status, errorMsg: msg, count: count] // library marker davegut.tpLinkComms, line 127
	if (device && status == 200) { // library marker davegut.tpLinkComms, line 128
		state.errorCount = 0 // library marker davegut.tpLinkComms, line 129
		if (device.currentValue("commsError") == "true") { // library marker davegut.tpLinkComms, line 130
			updateAttr("commsError", "false") // library marker davegut.tpLinkComms, line 131
			setPollInterval() // library marker davegut.tpLinkComms, line 132
			unschedule("errorDeviceHandshake") // library marker davegut.tpLinkComms, line 133
			logInfo(logData) // library marker davegut.tpLinkComms, line 134
		} // library marker davegut.tpLinkComms, line 135
	} else if (device) { // library marker davegut.tpLinkComms, line 136
		if (device.currentValue("commsError") == "false" && count > 4) { // library marker davegut.tpLinkComms, line 137
			updateAttr("commsError", "true") // library marker davegut.tpLinkComms, line 138
			setPollInterval("30 min") // library marker davegut.tpLinkComms, line 139
			runEvery10Minutes(errorConfigure) // library marker davegut.tpLinkComms, line 140
			logData << [pollInterval: "30 Min", errorDeviceHandshake: "ever 10 min"] // library marker davegut.tpLinkComms, line 141
			logWarn(logData) // library marker davegut.tpLinkComms, line 142
			if (status == 403) { // library marker davegut.tpLinkComms, line 143
				logWarn(logInErrorAction()) // library marker davegut.tpLinkComms, line 144
			} else { // library marker davegut.tpLinkComms, line 145
				logWarn(lanErrorAction()) // library marker davegut.tpLinkComms, line 146
			} // library marker davegut.tpLinkComms, line 147
		} else { // library marker davegut.tpLinkComms, line 148
			logData << [error: "Unspecified Error"] // library marker davegut.tpLinkComms, line 149
			logWarn(logData) // library marker davegut.tpLinkComms, line 150
		} // library marker davegut.tpLinkComms, line 151
	} // library marker davegut.tpLinkComms, line 152
} // library marker davegut.tpLinkComms, line 153

def lanErrorAction() { // library marker davegut.tpLinkComms, line 155
	def action = "Likely cause of this error is YOUR LAN device configuration: " // library marker davegut.tpLinkComms, line 156
	action += "a. VERIFY your device is on the DHCP list in your router, " // library marker davegut.tpLinkComms, line 157
	action += "b. VERIFY your device is in the active device list in your router, and " // library marker davegut.tpLinkComms, line 158
	action += "c. TRY controlling your device from the TAPO phone app." // library marker davegut.tpLinkComms, line 159
	return action // library marker davegut.tpLinkComms, line 160
} // library marker davegut.tpLinkComms, line 161

def logInErrorAction() { // library marker davegut.tpLinkComms, line 163
	def action = "Likely cause is your login credentials are incorrect or the login has expired. " // library marker davegut.tpLinkComms, line 164
	action += "a. RUN command Configure. b. If error persists, check your credentials in the App" // library marker davegut.tpLinkComms, line 165
	return action // library marker davegut.tpLinkComms, line 166
} // library marker davegut.tpLinkComms, line 167

def errorConfigure() { // library marker davegut.tpLinkComms, line 169
	logDebug([method: "errorConfigure"]) // library marker davegut.tpLinkComms, line 170
	configure() // library marker davegut.tpLinkComms, line 171
} // library marker davegut.tpLinkComms, line 172

//	===== Common UDP Communications for checking if device at IP is device in Hubitat ===== // library marker davegut.tpLinkComms, line 174
private sendFindCmd(ip, port, cmdData, action, commsTo = 5, ignore = false) { // library marker davegut.tpLinkComms, line 175
	def myHubAction = new hubitat.device.HubAction( // library marker davegut.tpLinkComms, line 176
		cmdData, // library marker davegut.tpLinkComms, line 177
		hubitat.device.Protocol.LAN, // library marker davegut.tpLinkComms, line 178
		[type: hubitat.device.HubAction.Type.LAN_TYPE_UDPCLIENT, // library marker davegut.tpLinkComms, line 179
		 destinationAddress: "${ip}:${port}", // library marker davegut.tpLinkComms, line 180
		 encoding: hubitat.device.HubAction.Encoding.HEX_STRING, // library marker davegut.tpLinkComms, line 181
		 ignoreResponse: ignore, // library marker davegut.tpLinkComms, line 182
		 parseWarning: true, // library marker davegut.tpLinkComms, line 183
		 timeout: commsTo, // library marker davegut.tpLinkComms, line 184
		 callback: action]) // library marker davegut.tpLinkComms, line 185
	try { // library marker davegut.tpLinkComms, line 186
		sendHubCommand(myHubAction) // library marker davegut.tpLinkComms, line 187
	} catch (error) { // library marker davegut.tpLinkComms, line 188
		logWarn("sendLanCmd: command to ${ip}:${port} failed. Error = ${error}") // library marker davegut.tpLinkComms, line 189
	} // library marker davegut.tpLinkComms, line 190
	return // library marker davegut.tpLinkComms, line 191
} // library marker davegut.tpLinkComms, line 192

// ~~~~~ end include (261) davegut.tpLinkComms ~~~~~

// ~~~~~ start include (262) davegut.tpLinkCrypto ~~~~~
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
					 ignoreSSLIssues: true, // library marker davegut.tpLinkCrypto, line 25
					 requestContentType: "application/json", // library marker davegut.tpLinkCrypto, line 26
					 timeout: 10] // library marker davegut.tpLinkCrypto, line 27
	asynchttpPost("parseAesHandshake", reqParams, [data: reqData]) // library marker davegut.tpLinkCrypto, line 28
} // library marker davegut.tpLinkCrypto, line 29

def parseAesHandshake(resp, data){ // library marker davegut.tpLinkCrypto, line 31
	Map logData = [method: "parseAesHandshake"] // library marker davegut.tpLinkCrypto, line 32
	if (resp.status == 200 && resp.data != null) { // library marker davegut.tpLinkCrypto, line 33
		try { // library marker davegut.tpLinkCrypto, line 34
			Map reqData = [devData: data.data.devData, baseUrl: data.data.baseUrl] // library marker davegut.tpLinkCrypto, line 35
			Map cmdResp =  new JsonSlurper().parseText(resp.data) // library marker davegut.tpLinkCrypto, line 36
			//	cookie // library marker davegut.tpLinkCrypto, line 37
			def cookieHeader = resp.headers["Set-Cookie"].toString() // library marker davegut.tpLinkCrypto, line 38
			def cookie = cookieHeader.substring(cookieHeader.indexOf(":") +1, cookieHeader.indexOf(";")) // library marker davegut.tpLinkCrypto, line 39
			//	keys // library marker davegut.tpLinkCrypto, line 40
			byte[] privateKeyBytes = getRsaKey().private.decodeBase64() // library marker davegut.tpLinkCrypto, line 41
			byte[] deviceKeyBytes = cmdResp.result.key.getBytes("UTF-8").decodeBase64() // library marker davegut.tpLinkCrypto, line 42
    		Cipher instance = Cipher.getInstance("RSA/ECB/PKCS1Padding") // library marker davegut.tpLinkCrypto, line 43
			instance.init(2, KeyFactory.getInstance("RSA").generatePrivate(new PKCS8EncodedKeySpec(privateKeyBytes))) // library marker davegut.tpLinkCrypto, line 44
			byte[] cryptoArray = instance.doFinal(deviceKeyBytes) // library marker davegut.tpLinkCrypto, line 45
			byte[] encKey = cryptoArray[0..15] // library marker davegut.tpLinkCrypto, line 46
			byte[] encIv = cryptoArray[16..31] // library marker davegut.tpLinkCrypto, line 47
			logData << [respStatus: "Cookies/Keys Updated", cookie: cookie, // library marker davegut.tpLinkCrypto, line 48
						encKey: encKey, encIv: encIv] // library marker davegut.tpLinkCrypto, line 49
			String password = encPassword // library marker davegut.tpLinkCrypto, line 50
			String username = encUsername // library marker davegut.tpLinkCrypto, line 51
			if (device) { // library marker davegut.tpLinkCrypto, line 52
				password = parent.encPassword // library marker davegut.tpLinkCrypto, line 53
				username = parent.encUsername // library marker davegut.tpLinkCrypto, line 54
				device.updateSetting("cookie",[type:"password", value: cookie]) // library marker davegut.tpLinkCrypto, line 55
				device.updateSetting("encKey",[type:"password", value: encKey]) // library marker davegut.tpLinkCrypto, line 56
				device.updateSetting("encIv",[type:"password", value: encIv]) // library marker davegut.tpLinkCrypto, line 57
			} else { // library marker davegut.tpLinkCrypto, line 58
				reqData << [cookie: cookie, encIv: encIv, encKey: encKey] // library marker davegut.tpLinkCrypto, line 59
			} // library marker davegut.tpLinkCrypto, line 60
			Map cmdBody = [method: "login_device", // library marker davegut.tpLinkCrypto, line 61
						   params: [password: password, // library marker davegut.tpLinkCrypto, line 62
									username: username], // library marker davegut.tpLinkCrypto, line 63
						   requestTimeMils: 0] // library marker davegut.tpLinkCrypto, line 64
			def cmdStr = JsonOutput.toJson(cmdBody).toString() // library marker davegut.tpLinkCrypto, line 65
			Map reqBody = [method: "securePassthrough", // library marker davegut.tpLinkCrypto, line 66
						   params: [request: aesEncrypt(cmdStr, encKey, encIv)]] // library marker davegut.tpLinkCrypto, line 67
			Map reqParams = [uri: reqData.baseUrl, // library marker davegut.tpLinkCrypto, line 68
							  body: reqBody, // library marker davegut.tpLinkCrypto, line 69
							 ignoreSSLIssues: true, // library marker davegut.tpLinkCrypto, line 70
							  timeout:10,  // library marker davegut.tpLinkCrypto, line 71
							  headers: ["Cookie": cookie], // library marker davegut.tpLinkCrypto, line 72
							  contentType: "application/json", // library marker davegut.tpLinkCrypto, line 73
							  requestContentType: "application/json"] // library marker davegut.tpLinkCrypto, line 74
			asynchttpPost("parseAesLogin", reqParams, [data: reqData]) // library marker davegut.tpLinkCrypto, line 75
			logDebug(logData) // library marker davegut.tpLinkCrypto, line 76
		} catch (err) { // library marker davegut.tpLinkCrypto, line 77
			logData << [respStatus: "ERROR parsing HTTP resp.data", // library marker davegut.tpLinkCrypto, line 78
						respData: resp.data, error: err] // library marker davegut.tpLinkCrypto, line 79
			logWarn(logData) // library marker davegut.tpLinkCrypto, line 80
		} // library marker davegut.tpLinkCrypto, line 81
	} else { // library marker davegut.tpLinkCrypto, line 82
		logData << [respStatus: "ERROR in HTTP response", resp: resp.properties] // library marker davegut.tpLinkCrypto, line 83
		logWarn(logData) // library marker davegut.tpLinkCrypto, line 84
	} // library marker davegut.tpLinkCrypto, line 85
} // library marker davegut.tpLinkCrypto, line 86

def parseAesLogin(resp, data) { // library marker davegut.tpLinkCrypto, line 88
	if (device) { // library marker davegut.tpLinkCrypto, line 89
		Map logData = [method: "parseAesLogin"] // library marker davegut.tpLinkCrypto, line 90
		if (resp.status == 200) { // library marker davegut.tpLinkCrypto, line 91
			if (resp.json.error_code == 0) { // library marker davegut.tpLinkCrypto, line 92
				try { // library marker davegut.tpLinkCrypto, line 93
					byte[] encKey = new JsonSlurper().parseText(encKey) // library marker davegut.tpLinkCrypto, line 94
					byte[] encIv = new JsonSlurper().parseText(encIv) // library marker davegut.tpLinkCrypto, line 95
					def clearResp = aesDecrypt(resp.json.result.response, encKey, encIv) // library marker davegut.tpLinkCrypto, line 96
					Map cmdResp = new JsonSlurper().parseText(clearResp) // library marker davegut.tpLinkCrypto, line 97
					if (cmdResp.error_code == 0) { // library marker davegut.tpLinkCrypto, line 98
						def token = cmdResp.result.token // library marker davegut.tpLinkCrypto, line 99
						logData << [respStatus: "OK", token: token] // library marker davegut.tpLinkCrypto, line 100
						device.updateSetting("token",[type:"password", value: token]) // library marker davegut.tpLinkCrypto, line 101
						setCommsError(200) // library marker davegut.tpLinkCrypto, line 102
						logDebug(logData) // library marker davegut.tpLinkCrypto, line 103
					} else { // library marker davegut.tpLinkCrypto, line 104
						logData << [respStatus: "ERROR code in cmdResp",  // library marker davegut.tpLinkCrypto, line 105
									error_code: cmdResp.error_code, // library marker davegut.tpLinkCrypto, line 106
									check: "cryptoArray, credentials", data: cmdResp] // library marker davegut.tpLinkCrypto, line 107
						logInfo(logData) // library marker davegut.tpLinkCrypto, line 108
					} // library marker davegut.tpLinkCrypto, line 109
				} catch (err) { // library marker davegut.tpLinkCrypto, line 110
					logData << [respStatus: "ERROR parsing respJson", respJson: resp.json, // library marker davegut.tpLinkCrypto, line 111
								error: err] // library marker davegut.tpLinkCrypto, line 112
					logInfo(logData) // library marker davegut.tpLinkCrypto, line 113
				} // library marker davegut.tpLinkCrypto, line 114
			} else { // library marker davegut.tpLinkCrypto, line 115
				logData << [respStatus: "ERROR code in resp.json", errorCode: resp.json.error_code, // library marker davegut.tpLinkCrypto, line 116
							respJson: resp.json] // library marker davegut.tpLinkCrypto, line 117
				logInfo(logData) // library marker davegut.tpLinkCrypto, line 118
			} // library marker davegut.tpLinkCrypto, line 119
		} else { // library marker davegut.tpLinkCrypto, line 120
			logData << [respStatus: "ERROR in HTTP response", respStatus: resp.status, data: resp.properties] // library marker davegut.tpLinkCrypto, line 121
			logInfo(logData) // library marker davegut.tpLinkCrypto, line 122
		} // library marker davegut.tpLinkCrypto, line 123
	} else { // library marker davegut.tpLinkCrypto, line 124
		//	Code used in application only. // library marker davegut.tpLinkCrypto, line 125
		getAesToken(resp, data.data) // library marker davegut.tpLinkCrypto, line 126
	} // library marker davegut.tpLinkCrypto, line 127
} // library marker davegut.tpLinkCrypto, line 128

//	===== KLAP Handshake ===== // library marker davegut.tpLinkCrypto, line 130
def klapHandshake(baseUrl = getDataValue("baseUrl"), localHash = parent.localHash, devData = null) { // library marker davegut.tpLinkCrypto, line 131
	byte[] localSeed = new byte[16] // library marker davegut.tpLinkCrypto, line 132
	new Random().nextBytes(localSeed) // library marker davegut.tpLinkCrypto, line 133
	Map reqData = [localSeed: localSeed, baseUrl: baseUrl, localHash: localHash, devData:devData] // library marker davegut.tpLinkCrypto, line 134
	Map reqParams = [uri: "${baseUrl}/handshake1", // library marker davegut.tpLinkCrypto, line 135
					 body: localSeed, // library marker davegut.tpLinkCrypto, line 136
					 contentType: "application/octet-stream", // library marker davegut.tpLinkCrypto, line 137
					 requestContentType: "application/octet-stream", // library marker davegut.tpLinkCrypto, line 138
					 ignoreSSLIssues: true, // library marker davegut.tpLinkCrypto, line 139
					 timeout:10] // library marker davegut.tpLinkCrypto, line 140
	asynchttpPost("parseKlapHandshake", reqParams, [data: reqData]) // library marker davegut.tpLinkCrypto, line 141
} // library marker davegut.tpLinkCrypto, line 142

def parseKlapHandshake(resp, data) { // library marker davegut.tpLinkCrypto, line 144
	Map logData = [method: "parseKlapHandshake"] // library marker davegut.tpLinkCrypto, line 145
	if (resp.status == 200 && resp.data != null) { // library marker davegut.tpLinkCrypto, line 146
		try { // library marker davegut.tpLinkCrypto, line 147
			Map reqData = [devData: data.data.devData, baseUrl: data.data.baseUrl] // library marker davegut.tpLinkCrypto, line 148
			byte[] localSeed = data.data.localSeed // library marker davegut.tpLinkCrypto, line 149
			byte[] seedData = resp.data.decodeBase64() // library marker davegut.tpLinkCrypto, line 150
			byte[] remoteSeed = seedData[0 .. 15] // library marker davegut.tpLinkCrypto, line 151
			byte[] serverHash = seedData[16 .. 47] // library marker davegut.tpLinkCrypto, line 152
			byte[] localHash = data.data.localHash.decodeBase64() // library marker davegut.tpLinkCrypto, line 153
			byte[] authHash = [localSeed, remoteSeed, localHash].flatten() // library marker davegut.tpLinkCrypto, line 154
			byte[] localAuthHash = mdEncode("SHA-256", authHash) // library marker davegut.tpLinkCrypto, line 155
			if (localAuthHash == serverHash) { // library marker davegut.tpLinkCrypto, line 156
				//	cookie // library marker davegut.tpLinkCrypto, line 157
				def cookieHeader = resp.headers["Set-Cookie"].toString() // library marker davegut.tpLinkCrypto, line 158
				def cookie = cookieHeader.substring(cookieHeader.indexOf(":") +1, cookieHeader.indexOf(";")) // library marker davegut.tpLinkCrypto, line 159
				//	seqNo and encIv // library marker davegut.tpLinkCrypto, line 160
				byte[] payload = ["iv".getBytes(), localSeed, remoteSeed, localHash].flatten() // library marker davegut.tpLinkCrypto, line 161
				byte[] fullIv = mdEncode("SHA-256", payload) // library marker davegut.tpLinkCrypto, line 162
				byte[] byteSeqNo = fullIv[-4..-1] // library marker davegut.tpLinkCrypto, line 163

				int seqNo = byteArrayToInteger(byteSeqNo) // library marker davegut.tpLinkCrypto, line 165
				atomicState.seqNo = seqNo // library marker davegut.tpLinkCrypto, line 166

				//	encKey // library marker davegut.tpLinkCrypto, line 168
				payload = ["lsk".getBytes(), localSeed, remoteSeed, localHash].flatten() // library marker davegut.tpLinkCrypto, line 169
				byte[] encKey = mdEncode("SHA-256", payload)[0..15] // library marker davegut.tpLinkCrypto, line 170
				//	encSig // library marker davegut.tpLinkCrypto, line 171
				payload = ["ldk".getBytes(), localSeed, remoteSeed, localHash].flatten() // library marker davegut.tpLinkCrypto, line 172
				byte[] encSig = mdEncode("SHA-256", payload)[0..27] // library marker davegut.tpLinkCrypto, line 173
				if (device) { // library marker davegut.tpLinkCrypto, line 174
					device.updateSetting("cookie",[type:"password", value: cookie])  // library marker davegut.tpLinkCrypto, line 175
					device.updateSetting("encKey",[type:"password", value: encKey])  // library marker davegut.tpLinkCrypto, line 176
					device.updateSetting("encIv",[type:"password", value: fullIv[0..11]])  // library marker davegut.tpLinkCrypto, line 177
					device.updateSetting("encSig",[type:"password", value: encSig])  // library marker davegut.tpLinkCrypto, line 178
				} else { // library marker davegut.tpLinkCrypto, line 179
					reqData << [cookie: cookie, seqNo: seqNo, encIv: fullIv[0..11],  // library marker davegut.tpLinkCrypto, line 180
								encSig: encSig, encKey: encKey] // library marker davegut.tpLinkCrypto, line 181
				} // library marker davegut.tpLinkCrypto, line 182
				byte[] loginHash = [remoteSeed, localSeed, localHash].flatten() // library marker davegut.tpLinkCrypto, line 183
				byte[] body = mdEncode("SHA-256", loginHash) // library marker davegut.tpLinkCrypto, line 184
				Map reqParams = [uri: "${data.data.baseUrl}/handshake2", // library marker davegut.tpLinkCrypto, line 185
								 body: body, // library marker davegut.tpLinkCrypto, line 186
								 ignoreSSLIssues: true, // library marker davegut.tpLinkCrypto, line 187
								 timeout:10, // library marker davegut.tpLinkCrypto, line 188
								 headers: ["Cookie": cookie], // library marker davegut.tpLinkCrypto, line 189
								 contentType: "application/octet-stream", // library marker davegut.tpLinkCrypto, line 190
								 requestContentType: "application/octet-stream"] // library marker davegut.tpLinkCrypto, line 191
				asynchttpPost("parseKlapHandshake2", reqParams, [data: reqData]) // library marker davegut.tpLinkCrypto, line 192
			} else { // library marker davegut.tpLinkCrypto, line 193
				logData << [respStatus: "ERROR: localAuthHash != serverHash", // library marker davegut.tpLinkCrypto, line 194
							action: "<b>Check credentials and try again</b>"] // library marker davegut.tpLinkCrypto, line 195
				logWarn(logData) // library marker davegut.tpLinkCrypto, line 196
			} // library marker davegut.tpLinkCrypto, line 197
		} catch (err) { // library marker davegut.tpLinkCrypto, line 198
			logData << [respStatus: "ERROR parsing 200 response", resp: resp.properties, error: err] // library marker davegut.tpLinkCrypto, line 199
			logData << [action: "<b>Try Configure command</b>"] // library marker davegut.tpLinkCrypto, line 200
			logWarn(logData) // library marker davegut.tpLinkCrypto, line 201
		} // library marker davegut.tpLinkCrypto, line 202
	} else { // library marker davegut.tpLinkCrypto, line 203
		logData << [respStatus: resp.status, message: resp.errorMessage] // library marker davegut.tpLinkCrypto, line 204
		logData << [action: "<b>Try Configure command</b>"] // library marker davegut.tpLinkCrypto, line 205
		logWarn(logData) // library marker davegut.tpLinkCrypto, line 206
	} // library marker davegut.tpLinkCrypto, line 207
} // library marker davegut.tpLinkCrypto, line 208

def parseKlapHandshake2(resp, data) { // library marker davegut.tpLinkCrypto, line 210
	Map logData = [method: "parseKlapHandshake2"] // library marker davegut.tpLinkCrypto, line 211
	if (resp.status == 200 && resp.data == null) { // library marker davegut.tpLinkCrypto, line 212
		logData << [respStatus: "Login OK"] // library marker davegut.tpLinkCrypto, line 213
		setCommsError(200) // library marker davegut.tpLinkCrypto, line 214
		logDebug(logData) // library marker davegut.tpLinkCrypto, line 215
	} else { // library marker davegut.tpLinkCrypto, line 216
		logData << [respStatus: "LOGIN FAILED", reason: "ERROR in HTTP response", // library marker davegut.tpLinkCrypto, line 217
					resp: resp.properties] // library marker davegut.tpLinkCrypto, line 218
		logInfo(logData) // library marker davegut.tpLinkCrypto, line 219
	} // library marker davegut.tpLinkCrypto, line 220
	if (!device) { sendKlapDataCmd(logData, data) } // library marker davegut.tpLinkCrypto, line 221
} // library marker davegut.tpLinkCrypto, line 222

//	===== Comms Support ===== // library marker davegut.tpLinkCrypto, line 224
def getKlapParams(cmdBody) { // library marker davegut.tpLinkCrypto, line 225
	Map reqParams = [timeout: 10, headers: ["Cookie": cookie]] // library marker davegut.tpLinkCrypto, line 226
	int seqNo = state.seqNo + 1 // library marker davegut.tpLinkCrypto, line 227
	state.seqNo = seqNo // library marker davegut.tpLinkCrypto, line 228
	byte[] encKey = new JsonSlurper().parseText(encKey) // library marker davegut.tpLinkCrypto, line 229
	byte[] encIv = new JsonSlurper().parseText(encIv) // library marker davegut.tpLinkCrypto, line 230
	byte[] encSig = new JsonSlurper().parseText(encSig) // library marker davegut.tpLinkCrypto, line 231
	String cmdBodyJson = new groovy.json.JsonBuilder(cmdBody).toString() // library marker davegut.tpLinkCrypto, line 232

	Map encryptedData = klapEncrypt(cmdBodyJson.getBytes(), encKey, encIv, // library marker davegut.tpLinkCrypto, line 234
									encSig, seqNo) // library marker davegut.tpLinkCrypto, line 235
	reqParams << [uri: "${getDataValue("baseUrl")}/request?seq=${seqNo}", // library marker davegut.tpLinkCrypto, line 236
				  ignoreSSLIssues: true, // library marker davegut.tpLinkCrypto, line 237
				  body: encryptedData.cipherData, // library marker davegut.tpLinkCrypto, line 238
				  contentType: "application/octet-stream", // library marker davegut.tpLinkCrypto, line 239
				  requestContentType: "application/octet-stream"] // library marker davegut.tpLinkCrypto, line 240
	return reqParams // library marker davegut.tpLinkCrypto, line 241
} // library marker davegut.tpLinkCrypto, line 242

def getAesParams(cmdBody) { // library marker davegut.tpLinkCrypto, line 244
	byte[] encKey = new JsonSlurper().parseText(encKey) // library marker davegut.tpLinkCrypto, line 245
	byte[] encIv = new JsonSlurper().parseText(encIv) // library marker davegut.tpLinkCrypto, line 246
	def cmdStr = JsonOutput.toJson(cmdBody).toString() // library marker davegut.tpLinkCrypto, line 247
	Map reqBody = [method: "securePassthrough", // library marker davegut.tpLinkCrypto, line 248
				   params: [request: aesEncrypt(cmdStr, encKey, encIv)]] // library marker davegut.tpLinkCrypto, line 249
	Map reqParams = [uri: "${getDataValue("baseUrl")}?token=${token}", // library marker davegut.tpLinkCrypto, line 250
					 body: new groovy.json.JsonBuilder(reqBody).toString(), // library marker davegut.tpLinkCrypto, line 251
					 ignoreSSLIssues: true, // library marker davegut.tpLinkCrypto, line 252
					 contentType: "application/json", // library marker davegut.tpLinkCrypto, line 253
					 requestContentType: "application/json", // library marker davegut.tpLinkCrypto, line 254
					 timeout: 10, // library marker davegut.tpLinkCrypto, line 255
					 headers: ["Cookie": cookie]] // library marker davegut.tpLinkCrypto, line 256
	return reqParams // library marker davegut.tpLinkCrypto, line 257
} // library marker davegut.tpLinkCrypto, line 258

def parseKlapData(resp) { // library marker davegut.tpLinkCrypto, line 260
	Map parseData = [parseMethod: "parseKlapData"] // library marker davegut.tpLinkCrypto, line 261
	try { // library marker davegut.tpLinkCrypto, line 262
		byte[] encKey = new JsonSlurper().parseText(encKey) // library marker davegut.tpLinkCrypto, line 263
		byte[] encIv = new JsonSlurper().parseText(encIv) // library marker davegut.tpLinkCrypto, line 264
		int seqNo = state.seqNo // library marker davegut.tpLinkCrypto, line 265
		byte[] cipherResponse = resp.data.decodeBase64()[32..-1] // library marker davegut.tpLinkCrypto, line 266
		Map cmdResp =  new JsonSlurper().parseText(klapDecrypt(cipherResponse, encKey, // library marker davegut.tpLinkCrypto, line 267
														   encIv, seqNo)) // library marker davegut.tpLinkCrypto, line 268
		parseData << [cryptoStatus: "OK", cmdResp: cmdResp] // library marker davegut.tpLinkCrypto, line 269
	} catch (err) { // library marker davegut.tpLinkCrypto, line 270
		parseData << [cryptoStatus: "decryptDataError", error: err] // library marker davegut.tpLinkCrypto, line 271
	} // library marker davegut.tpLinkCrypto, line 272
	return parseData // library marker davegut.tpLinkCrypto, line 273
} // library marker davegut.tpLinkCrypto, line 274

def parseAesData(resp) { // library marker davegut.tpLinkCrypto, line 276
	Map parseData = [parseMethod: "parseAesData"] // library marker davegut.tpLinkCrypto, line 277
	try { // library marker davegut.tpLinkCrypto, line 278
		byte[] encKey = new JsonSlurper().parseText(encKey) // library marker davegut.tpLinkCrypto, line 279
		byte[] encIv = new JsonSlurper().parseText(encIv) // library marker davegut.tpLinkCrypto, line 280
		Map cmdResp = new JsonSlurper().parseText(aesDecrypt(resp.json.result.response, // library marker davegut.tpLinkCrypto, line 281
														 encKey, encIv)) // library marker davegut.tpLinkCrypto, line 282
		parseData << [cryptoStatus: "OK", cmdResp: cmdResp] // library marker davegut.tpLinkCrypto, line 283
	} catch (err) { // library marker davegut.tpLinkCrypto, line 284
		parseData << [cryptoStatus: "decryptDataError", error: err, dataLength: resp.data.length()] // library marker davegut.tpLinkCrypto, line 285
	} // library marker davegut.tpLinkCrypto, line 286
	return parseData // library marker davegut.tpLinkCrypto, line 287
} // library marker davegut.tpLinkCrypto, line 288

//	===== Crypto Methods ===== // library marker davegut.tpLinkCrypto, line 290
def klapEncrypt(byte[] request, encKey, encIv, encSig, seqNo) { // library marker davegut.tpLinkCrypto, line 291
	byte[] encSeqNo = integerToByteArray(seqNo) // library marker davegut.tpLinkCrypto, line 292
	byte[] ivEnc = [encIv, encSeqNo].flatten() // library marker davegut.tpLinkCrypto, line 293
	def cipher = Cipher.getInstance("AES/CBC/PKCS5Padding") // library marker davegut.tpLinkCrypto, line 294
	SecretKeySpec key = new SecretKeySpec(encKey, "AES") // library marker davegut.tpLinkCrypto, line 295
	IvParameterSpec iv = new IvParameterSpec(ivEnc) // library marker davegut.tpLinkCrypto, line 296
	cipher.init(Cipher.ENCRYPT_MODE, key, iv) // library marker davegut.tpLinkCrypto, line 297
	byte[] cipherRequest = cipher.doFinal(request) // library marker davegut.tpLinkCrypto, line 298

	byte[] payload = [encSig, encSeqNo, cipherRequest].flatten() // library marker davegut.tpLinkCrypto, line 300
	byte[] signature = mdEncode("SHA-256", payload) // library marker davegut.tpLinkCrypto, line 301
	cipherRequest = [signature, cipherRequest].flatten() // library marker davegut.tpLinkCrypto, line 302
	return [cipherData: cipherRequest, seqNumber: seqNo] // library marker davegut.tpLinkCrypto, line 303
} // library marker davegut.tpLinkCrypto, line 304

def klapDecrypt(cipherResponse, encKey, encIv, seqNo) { // library marker davegut.tpLinkCrypto, line 306
	byte[] encSeqNo = integerToByteArray(seqNo) // library marker davegut.tpLinkCrypto, line 307
	byte[] ivEnc = [encIv, encSeqNo].flatten() // library marker davegut.tpLinkCrypto, line 308
	def cipher = Cipher.getInstance("AES/CBC/PKCS5Padding") // library marker davegut.tpLinkCrypto, line 309
    SecretKeySpec key = new SecretKeySpec(encKey, "AES") // library marker davegut.tpLinkCrypto, line 310
	IvParameterSpec iv = new IvParameterSpec(ivEnc) // library marker davegut.tpLinkCrypto, line 311
    cipher.init(Cipher.DECRYPT_MODE, key, iv) // library marker davegut.tpLinkCrypto, line 312
	byte[] byteResponse = cipher.doFinal(cipherResponse) // library marker davegut.tpLinkCrypto, line 313
	return new String(byteResponse, "UTF-8") // library marker davegut.tpLinkCrypto, line 314
} // library marker davegut.tpLinkCrypto, line 315

def aesEncrypt(request, encKey, encIv) { // library marker davegut.tpLinkCrypto, line 317
	def cipher = Cipher.getInstance("AES/CBC/PKCS5Padding") // library marker davegut.tpLinkCrypto, line 318
	SecretKeySpec key = new SecretKeySpec(encKey, "AES") // library marker davegut.tpLinkCrypto, line 319
	IvParameterSpec iv = new IvParameterSpec(encIv) // library marker davegut.tpLinkCrypto, line 320
	cipher.init(Cipher.ENCRYPT_MODE, key, iv) // library marker davegut.tpLinkCrypto, line 321
	String result = cipher.doFinal(request.getBytes("UTF-8")).encodeBase64().toString() // library marker davegut.tpLinkCrypto, line 322
	return result.replace("\r\n","") // library marker davegut.tpLinkCrypto, line 323
} // library marker davegut.tpLinkCrypto, line 324

def aesDecrypt(cipherResponse, encKey, encIv) { // library marker davegut.tpLinkCrypto, line 326
    byte[] decodedBytes = cipherResponse.decodeBase64() // library marker davegut.tpLinkCrypto, line 327
	def cipher = Cipher.getInstance("AES/CBC/PKCS5Padding") // library marker davegut.tpLinkCrypto, line 328
    SecretKeySpec key = new SecretKeySpec(encKey, "AES") // library marker davegut.tpLinkCrypto, line 329
	IvParameterSpec iv = new IvParameterSpec(encIv) // library marker davegut.tpLinkCrypto, line 330
    cipher.init(Cipher.DECRYPT_MODE, key, iv) // library marker davegut.tpLinkCrypto, line 331
	return new String(cipher.doFinal(decodedBytes), "UTF-8") // library marker davegut.tpLinkCrypto, line 332
} // library marker davegut.tpLinkCrypto, line 333

//	===== Encoding Methods ===== // library marker davegut.tpLinkCrypto, line 335
def mdEncode(hashMethod, byte[] data) { // library marker davegut.tpLinkCrypto, line 336
	MessageDigest md = MessageDigest.getInstance(hashMethod) // library marker davegut.tpLinkCrypto, line 337
	md.update(data) // library marker davegut.tpLinkCrypto, line 338
	return md.digest() // library marker davegut.tpLinkCrypto, line 339
} // library marker davegut.tpLinkCrypto, line 340

String encodeUtf8(String message) { // library marker davegut.tpLinkCrypto, line 342
	byte[] arr = message.getBytes("UTF8") // library marker davegut.tpLinkCrypto, line 343
	return new String(arr) // library marker davegut.tpLinkCrypto, line 344
} // library marker davegut.tpLinkCrypto, line 345

int byteArrayToInteger(byte[] byteArr) { // library marker davegut.tpLinkCrypto, line 347
	int arrayASInteger // library marker davegut.tpLinkCrypto, line 348
	try { // library marker davegut.tpLinkCrypto, line 349
		arrayAsInteger = ((byteArr[0] & 0xFF) << 24) + ((byteArr[1] & 0xFF) << 16) + // library marker davegut.tpLinkCrypto, line 350
			((byteArr[2] & 0xFF) << 8) + (byteArr[3] & 0xFF) // library marker davegut.tpLinkCrypto, line 351
	} catch (error) { // library marker davegut.tpLinkCrypto, line 352
		Map errLog = [byteArr: byteArr, ERROR: error] // library marker davegut.tpLinkCrypto, line 353
		logWarn("byteArrayToInteger: ${errLog}") // library marker davegut.tpLinkCrypto, line 354
	} // library marker davegut.tpLinkCrypto, line 355
	return arrayAsInteger // library marker davegut.tpLinkCrypto, line 356
} // library marker davegut.tpLinkCrypto, line 357

byte[] integerToByteArray(value) { // library marker davegut.tpLinkCrypto, line 359
	String hexValue = hubitat.helper.HexUtils.integerToHexString(value, 4) // library marker davegut.tpLinkCrypto, line 360
	byte[] byteValue = hubitat.helper.HexUtils.hexStringToByteArray(hexValue) // library marker davegut.tpLinkCrypto, line 361
	return byteValue // library marker davegut.tpLinkCrypto, line 362
} // library marker davegut.tpLinkCrypto, line 363

def getRsaKey() { // library marker davegut.tpLinkCrypto, line 365
	return [public: "MIGfMA0GCSqGSIb3DQEBAQUAA4GNADCBiQKBgQDGr/mHBK8aqx7UAS+g+TuAvE3J2DdwsqRn9MmAkjPGNon1ZlwM6nLQHfJHebdohyVqkNWaCECGXnftnlC8CM2c/RujvCrStRA0lVD+jixO9QJ9PcYTa07Z1FuEze7Q5OIa6pEoPxomrjxzVlUWLDXt901qCdn3/zRZpBdpXzVZtQIDAQAB", // library marker davegut.tpLinkCrypto, line 366
			private: "MIICeAIBADANBgkqhkiG9w0BAQEFAASCAmIwggJeAgEAAoGBAMav+YcErxqrHtQBL6D5O4C8TcnYN3CypGf0yYCSM8Y2ifVmXAzqctAd8kd5t2iHJWqQ1ZoIQIZed+2eULwIzZz9G6O8KtK1EDSVUP6OLE71An09xhNrTtnUW4TN7tDk4hrqkSg/GiauPHNWVRYsNe33TWoJ2ff/NFmkF2lfNVm1AgMBAAECgYEAocxCHmKBGe2KAEkq+SKdAxvVGO77TsobOhDMWug0Q1C8jduaUGZHsxT/7JbA9d1AagSh/XqE2Sdq8FUBF+7vSFzozBHyGkrX1iKURpQFEQM2j9JgUCucEavnxvCqDYpscyNRAgqz9jdh+BjEMcKAG7o68bOw41ZC+JyYR41xSe0CQQD1os71NcZiMVqYcBud6fTYFHZz3HBNcbzOk+RpIHyi8aF3zIqPKIAh2pO4s7vJgrMZTc2wkIe0ZnUrm0oaC//jAkEAzxIPW1mWd3+KE3gpgyX0cFkZsDmlIbWojUIbyz8NgeUglr+BczARG4ITrTV4fxkGwNI4EZxBT8vXDSIXJ8NDhwJBAIiKndx0rfg7Uw7VkqRvPqk2hrnU2aBTDw8N6rP9WQsCoi0DyCnX65Hl/KN5VXOocYIpW6NAVA8VvSAmTES6Ut0CQQCX20jD13mPfUsHaDIZafZPhiheoofFpvFLVtYHQeBoCF7T7vHCRdfl8oj3l6UcoH/hXMmdsJf9KyI1EXElyf91AkAvLfmAS2UvUnhX4qyFioitjxwWawSnf+CewN8LDbH7m5JVXJEh3hqp+aLHg1EaW4wJtkoKLCF+DeVIgbSvOLJw"] // library marker davegut.tpLinkCrypto, line 367
} // library marker davegut.tpLinkCrypto, line 368

// ~~~~~ end include (262) davegut.tpLinkCrypto ~~~~~

// ~~~~~ start include (253) davegut.Logging ~~~~~
library ( // library marker davegut.Logging, line 1
	name: "Logging", // library marker davegut.Logging, line 2
	namespace: "davegut", // library marker davegut.Logging, line 3
	author: "Dave Gutheinz", // library marker davegut.Logging, line 4
	description: "Common Logging and info gathering Methods", // library marker davegut.Logging, line 5
	category: "utilities", // library marker davegut.Logging, line 6
	documentationLink: "" // library marker davegut.Logging, line 7
) // library marker davegut.Logging, line 8

def nameSpace() { return "davegut" } // library marker davegut.Logging, line 10

def version() { return "2.4.1a" } // library marker davegut.Logging, line 12

def label() { // library marker davegut.Logging, line 14
	if (device) {  // library marker davegut.Logging, line 15
		return device.displayName + "-${version()}" // library marker davegut.Logging, line 16
	} else {  // library marker davegut.Logging, line 17
		return app.getLabel() + "-${version()}" // library marker davegut.Logging, line 18
	} // library marker davegut.Logging, line 19
} // library marker davegut.Logging, line 20

def updateAttr(attr, value) { // library marker davegut.Logging, line 22
	if (device.currentValue(attr) != value) { // library marker davegut.Logging, line 23
		sendEvent(name: attr, value: value) // library marker davegut.Logging, line 24
	} // library marker davegut.Logging, line 25
} // library marker davegut.Logging, line 26

def listAttributes() { // library marker davegut.Logging, line 28
	def attrData = device.getCurrentStates() // library marker davegut.Logging, line 29
	Map attrs = [:] // library marker davegut.Logging, line 30
	attrData.each { // library marker davegut.Logging, line 31
		attrs << ["${it.name}": it.value] // library marker davegut.Logging, line 32
	} // library marker davegut.Logging, line 33
	return attrs // library marker davegut.Logging, line 34
} // library marker davegut.Logging, line 35

def setLogsOff() { // library marker davegut.Logging, line 37
	def logData = [logEnable: logEnable] // library marker davegut.Logging, line 38
	if (logEnable) { // library marker davegut.Logging, line 39
		runIn(1800, debugLogOff) // library marker davegut.Logging, line 40
		logData << [debugLogOff: "scheduled"] // library marker davegut.Logging, line 41
	} // library marker davegut.Logging, line 42
	return logData // library marker davegut.Logging, line 43
} // library marker davegut.Logging, line 44

def logTrace(msg){ log.trace "${label()}: ${msg}" } // library marker davegut.Logging, line 46

def logInfo(msg) {  // library marker davegut.Logging, line 48
	if (infoLog) { log.info "${label()}: ${msg}" } // library marker davegut.Logging, line 49
} // library marker davegut.Logging, line 50

def debugLogOff() { // library marker davegut.Logging, line 52
	if (device) { // library marker davegut.Logging, line 53
		device.updateSetting("logEnable", [type:"bool", value: false]) // library marker davegut.Logging, line 54
	} else { // library marker davegut.Logging, line 55
		app.updateSetting("logEnable", false) // library marker davegut.Logging, line 56
	} // library marker davegut.Logging, line 57
	logInfo("debugLogOff") // library marker davegut.Logging, line 58
} // library marker davegut.Logging, line 59

def logDebug(msg) { // library marker davegut.Logging, line 61
	if (logEnable) { log.debug "${label()}: ${msg}" } // library marker davegut.Logging, line 62
} // library marker davegut.Logging, line 63

def logWarn(msg) { log.warn "${label()}: ${msg}" } // library marker davegut.Logging, line 65

def logError(msg) { log.error "${label()}: ${msg}" } // library marker davegut.Logging, line 67

// ~~~~~ end include (253) davegut.Logging ~~~~~
