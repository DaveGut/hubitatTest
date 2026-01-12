/*	Multi-TP-Link Product Integration Application
	Copyright Dave Gutheinz
License:  https://github.com/DaveGut/HubitatActive/blob/master/KasaDevices/License.md
===== Link to Documentation =====
	https://github.com/DaveGut/HubitatActive/blob/master/KasaDevices/Documentation.pdf
 

 

========================================*/








 
import groovy.json.JsonBuilder
import groovy.json.JsonSlurper
import org.json.JSONObject

definition(
	name: "Test Tapo Integration",
	namespace: nameSpace(),
	author: "Dave Gutheinz",
	description: "Application to install Tapo protocol TP-Link bulbs, plugs, and switches.",
	category: "Convenience",
	iconUrl: "",
	iconX2Url: "",
	installOnOpen: true,
	singleInstance: true,
	documentationLink: "https://github.com/DaveGut/tpLink_Hubitat",
	importUrl: "https://raw.githubusercontent.com/DaveGut/tpLink_Hubitat/refs/heads/main/App/tapo_device_install.groovy"
)

preferences {
	page(name: "startPage")
	page(name: "enterCredentialsPage")
	page(name: "addDevicesPage")
	page(name: "removeDevicesPage")
}

def installed() {
	app?.updateSetting("logEnable", false)
	app?.updateSetting("infoLog", true)
	def hub = location.hubs[0]
	def hubIpArray = hub.localIP.split('\\.')
	def segments = [hubIpArray[0],hubIpArray[1],hubIpArray[2]].join(".")
	app?.updateSetting("lanSegment", [type:"string", value: segments])
	app?.updateSetting("hostLimits", [type:"string", value: "2, 254"])
	app?.updateSetting("encPassword", "INVALID")
	app?.updateSetting("encUsername", "INVALID")
	app?.updateSetting("localHash", "INVALID")
	atomicState.devices = [:]
	atomicState.unsupported = [:]
	logInfo([method: "installed", status: "Initialized settings"])
}
 
def updated() {
	Map logData = [method: "updated", status: "setting updated for new session"]
	app?.removeSetting("selectedAddDevices")
	app?.removeSetting("selectedRemoveDevices")
	app?.updateSetting("logEnable", false)
	app?.updateSetting("appSetup", false)
	app?.updateSetting("spInst", false)
	state.needCreds = false
	runIn(30, scheduleItems)
	logInfo(logData)
}
 
def scheduleItems() {
	Map logData = [method: "scheduleItems"]
	unschedule()
	runIn(570, resetTpLinkChecked)
	logData << setLogsOff()
	Map newDevices = [:]
	atomicState.devices.each {
		def isChild = getChildDevice(it.key)
		if (isChild) {
			newDevices << it
		}
	}
	atomicState.devices = newDevices
	atomicState.unsupported = [:]
	logData << [devData: "Purged non-children"]
	logDebug(logData)
}

def uninstalled() {
    getAllChildDevices().each { 
        deleteChildDevice(it.deviceNetworkId)
    }
	logInfo([method: "uninstalled", status: "Devices and App uninstalled"])
}

def initInstance() {
	Map logData = [method: "initInstance"]
	if (!state.needCreds) { state.needCreds = false }
	if (!atomicState.unsupported) { atomicState.unsupported = [:] }
	state.tpLinkChecked = false
	logData << [setSegments: setSegments()]
	if (state.appVersion != version()) {
		state.appVersion = version()
		app.removeSetting("scheduled")	//	ver 2.4.1 only
		app.removeSetting("appVer")		//	ver 2.4.1 only
		app.removeSetting("ports")		//	ver 2.4.1 only
		state.remove("portArray")		//	ver 2.4.1 only
		app.removeSetting("showFound")	//	ver 2.4.1 only
		app.removeSetting("startApp")	//	ver 2.4.1 only
		app.removeSetting("finding")	//	ver 2.4.1 only
		logData << [versionUpdates: "update to appVersion ${version()}"]
	}
	if (!state.credSpecChars) { state.credSpecChar = [:] }

	
//////////////////////////////////////
if (userName && userPassword) {
createTpLinkCreds()
}
////////////////////////////////	
	
	logInfo(logData)
	return
}

def setSegments() {
	try {
		state.segArray = lanSegment.split('\\,')
		def rangeArray = hostLimits.split('\\,')
		def array0 = rangeArray[0].toInteger()
		def array1 = array0 + 2
		if (rangeArray.size() > 1) {
			array1 = rangeArray[1].toInteger()
		}
		state.hostArray = [array0, array1]
		return "segmentsUpdated"
	} catch (e) {
		logWarn("startPage: Invalid entry for Lan Segements or Host Array Range. Resetting to default!")
		def hub = location.hubs[0]
		def hubIpArray = hub.localIP.split('\\.')
		def segments = [hubIpArray[0],hubIpArray[1],hubIpArray[2]].join(".")
		app?.updateSetting("lanSegment", [type:"string", value: segments])
		app?.updateSetting("hostLimits", [type:"string", value: "1, 254"])
		return "error updating segments"
	}
}


//////////////////////////////////////////////
def startPage() {
	logInfo([method: "startPage", status: "Starting ${app.getLabel()} Setup"])
	def action = initInstance()
	if (selectedRemoveDevices) { removeDevices() } 
	else if (selectedAddDevices) { addDevices() }
	return dynamicPage(name:"startPage",
					   uninstall: true,
					   install: true) {
		section() {
			input "spInst", "bool", title: "<b>Display Quick Instructions</b>",
				submitOnChange: true, defaultValue: true
			if (spInst) { paragraph quickStartPg() }
			Map lanParams = [LanSegments: state.segArray, hostRange: state.hostArray]
			String params = "<b>Application Setup Parameters</b>"
			params += "\n\t<b>lanDiscoveryParams</b>: ${lanParams}"
			paragraph params
			input "appSetup", "bool", title: "<b>Modify Application Setup</b> (LanDiscParams)",
				submitOnChange: true, defaultValue: false
			if (appSetup) {
				input "lanSegment", "string",
					title: "<b>Lan Segments</b> (ex: 192.168.50, 192,168.01)", submitOnChange: true
				input "hostLimits", "string",
					title: "<b>Host Address Range</b> (ex: 5, 100)", submitOnChange: true
			}
			def credDesc = "Credentials: userName: ${userName}, password set/redacted."
			if (!userName || !userPassword) {
				credDesc = "<b>Credentials not set.  Enter credentials to proceed.</b>"
				state.needCreds = true
			} else {
				state.needCreds = false
//				String specChars = testCreds()	//	tests the credentials for special characters.
//				createTpLinkCreds()
//				credDesc += "\nEncoded password and username set based on credentials."
				if (state.credSpecChars != [:]) {
					credDesc += "\nSpecial characters in credentials may cause device connection issues."
				}
			}

			href "enterCredentialsPage",
				title: "<b>Enter/Update Username and Password</b>",
				description: credDesc
			if (!state.needCreds) {
				href "addDevicesPage",
					title: "<b>Scan for devices and add</b>",
					description: "It will take 30+ seconds to find devices."
			} else {
				paragraph "<b>Credentials are required to scan for to find devices.</b>"
			}
			href "removeDevicesPage",
				title: "<b>Remove Devices</b>",
				description: "Select to remove selected Device from Hubitat."
			input "logEnable", "bool",
				   title: "<b>Debug logging</b>",
				   submitOnChange: true
		}
	}
}
///////////////////////////////////////////



def enterCredentialsPage() {
	Map credData = [:]
	return dynamicPage (name: "enterCredentialsPage", 
    					title: "Enter  Credentials",
						nextPage: startPage,
                        install: false) {
		section() {
			if (userName && userPassword) {
				createTpLinkCreds()
			}
			input "hidePassword", "bool",
				title: "<b>Hide Password</b>",
				submitOnChange: true,
				defaultValue: false
			String credText = "Password and Username are both case sensitive. "
			credText += "\n<b>Some special characters in password causes failure.</b>"
			paragraph credText
			def pwdType = "string"
			if (hidePassword) { pwdType = "password" }
			input ("userName", "string",
            		title: "Email Address", 
                    required: false,
                    submitOnChange: true)
			input ("userPassword", pwdType,
            		title: "Account Password",
                    required: false,
                    submitOnChange: true)
		}
	}
}

//	===== Add selected newdevices =====
def addDevicesPage() {
	logDebug("addDevicesPage")
	app?.removeSetting("selectedAddDevices")
	def await = findTpLinkDevices("getTpLinkLanData", 5)
	def addDevicesData = atomicState.devices
	Map uninstalledDevices = [:]
	List installedDrivers = getInstalledDrivers()
	Map foundDevices = [:]
	addDevicesData.each { 
		def isChild = getChildDevice(it.key)
		if (!isChild) {
			uninstalledDevices["${it.key}"] = "${it.value.alias}, ${it.value.type}"
			def driver = "TpLink ${it.value.type}"
			if (!installedDrivers.find{ it == driver }) {
				foundDevices << ["${it.value.alias}":  "<b>Not installed.  Needs driver ${driver}</b>"]
			} else {
				foundDevices << ["${it.value.alias}":  "<b>Not installed.</b>  Driver found."]
			}
		} else {
			foundDevices << ["${it.value.alias}":  "Installed."]
		}
	}
	foundDevices = foundDevices.sort()
	String devicesFound = "<b>Found Devices</b>"
	foundDevices.each{ devicesFound += "\n\t${it}" }
	String missingDevices = "<b>Exercise missing devices through the Tapo Phone "
	missingDevices += "App. Then select this function.</b>"
	
	return dynamicPage(name:"addDevicesPage",
					   title: "Add Devices to Hubitat",
					   nextPage: startPage,
					   install: false) {
	 	section() {
			input ("selectedAddDevices", "enum",
				   required: false,
				   multiple: true,
				   title: "<b>Devices to add</b> (${uninstalledDevices.size() ?: 0} available).\n\t" +
				   "Total Devices: ${addDevicesData.size()}",
				   description: "Use the dropdown to select devices.  Then select 'Done'.",
				   options: uninstalledDevices)
			if (atomicState.unsupported.size() > 0) {
				def unsupNote = "<b>Found Unsupported Devices</b>: ${atomicState.unsupported}"
				paragraph unsupNote
			}
			paragraph devicesFound
			href "addDevicesPage",
				title: "<b>Rescan for Additional Devices</b>",
				description: missingDevices
		}
	}
}

def getInstalledDrivers() {
	List installedDrivers = []
	Map params = [
		uri: "https://127.0.0.1:8443",
		ignoreSSLIssues: true,
		path: "/hub2/userDeviceTypes",
	  ]
	try {
		httpGet(params) { resp ->
			resp.data.each {
				if (it.namespace == nameSpace()) {
					installedDrivers << it.name
				}
			}
		}
		logDebug([method: "getInstalledDrivers", drivers: installedDrivers])
	} catch (err) {
		logWarn([method: "getInstalledDrivers", err: err,
				 message: "Unable to get installed driver list"])
	}
	return installedDrivers
}

def supportedProducts() {
	return ["SMART.TAPOBULB", "SMART.TAPOPLUG", "SMART.TAPOSWITCH", "SMART.TAPOHUB", 
			"SMART.KASAHUB", "SMART.KASAPLUG", "SMART.KASASWITCH", 
			"SMART.TAPOROBOVAC", "SMART.IPCAMERA", "SMART.TAPODOORBELL",
//			"IOT.SMARTPLUGSWITCH", "SMART.MATTERBULB", "SMART.MATTERPLUG",
	]
}

//	===== Add Devices =====
def addDevices() {
	Map logData = [method: "addDevices", selectedAddDevices: selectedAddDevices]
	def hub = location.hubs[0]
	def devicesData = atomicState.devices
	selectedAddDevices.each { dni ->
		def isChild = getChildDevice(dni)
		if (!isChild) {
			def device = devicesData.find { it.key == dni }
			addDevice(device, dni)
		}
		pauseExecution(3000)
	}
	logInfo(logData)
	app?.removeSetting("selectedAddDevices")
}

def addDevice(device, dni) {
	Map logData = [method: "addDevice", dni: dni, alias: device.value.alias]
	try {
		Map deviceData = [devIp: device.value.devIp,
						  protocol: device.value.protocol,
						  baseUrl: device.value.baseUrl,
						  type: device.value.type]
		if (device.value.ledVer) { deviceData << [ledVer: device.value.ledVer] }
		if (device.value.isEm) { deviceData << [isEm: device.value.isEm] }
		if (device.value.gradOnOff) { deviceData << [gradOnOff: device.value.gradOnOff] }
		if (device.value.ctLow) { deviceData << [ctLow: device.value.ctLow] }
		if (device.value.ctHigh) { deviceData << [ctHigh: device.value.ctHigh] }
		if (device.value.alert) { deviceData << [alert: device.value.alert] }
		if (device.value.power) { deviceData << [power: device.value.power] }
		if (device.value.isDoorbell) { deviceData << [isDoorbell: device.value.isDoorbell] }
		addChildDevice(
			nameSpace(),
			"TpLink ${device.value.type}",
			dni,
			[
				"label": device.value.alias,
				"name" : device.value.model,
				"data" : deviceData
			]
		)
		logData << [status: "added"]
		logInfo(logData)
	} catch (err) {
		logData << [status: "failedToAdd", device: device, errorMsg: err]
		logWarn(logData)
	}
	return
}

//	===== Remove Devices =====
def removeDevicesPage() {
	Map logData = [method: "removeDevicesPage"]
	Map installedDevices = [:]
	getChildDevices().each {
		installedDevices << ["${it.device.deviceNetworkId}": it.device.label]
	}
	logData << [installedDevices: installedDevices]
	logInfo(logData)
	return dynamicPage(name:"removedDevicesPage",
					   title:"<b>Remove Devices from Hubitat</b>",
					   nextPage: startPage,
					   install: false) {
		section() {
			input ("selectedRemoveDevices", "enum",
				   required: false,
				   multiple: true,
				   title: "Devices to remove (${installedDevices.size() ?: 0} available)",
				   description: "Use the dropdown to select devices.  Then select 'Done'.",
				   options: installedDevices)
		}
	}
}
def removeDevices() {
	Map logData = [method: "removeDevices", selectedRemoveDevices: selectedRemoveDevices]
	selectedRemoveDevices.each { dni ->
		deleteChildDevice(dni)
		logData << ["${dni}": [status: "deleted"]]
	}
	app?.removeSetting("selectedRemoveDevices")
	logInfo(logData)
}
 
def getDeviceData(dni) {
	Map devices = atomicState.devices
	def device = devices.find { it.key == dni }
	Map devData = device.value
	return devData
}

//	===== Common UDP Communications =====
private sendLanCmd(ip, port, cmdData, action, commsTo = 5, ignore = false) {
	def myHubAction = new hubitat.device.HubAction(
		cmdData,
		hubitat.device.Protocol.LAN,
		[type: hubitat.device.HubAction.Type.LAN_TYPE_UDPCLIENT,
		 destinationAddress: "${ip}:${port}",
		 encoding: hubitat.device.HubAction.Encoding.HEX_STRING,
		 ignoreResponse: ignore,
		 parseWarning: true,
		 timeout: commsTo,
		 callback: action])
	try {
		sendHubCommand(myHubAction)
	} catch (error) {
		logWarn("sendLanCmd: command to ${ip}:${port} failed. Error = ${error}")
	}
	return
}

def pingTest() {
	Map devices = atomicState.devices
	devices.each {device ->
		def baseUrl = device.value.baseUrl
		ping(baseUrl)
	}
}

def quickStartPg() {
	String quickSP = ""
	quickSP += "a. Tapo Phone App: Install device.\n"
	quickSP += "b. Tapo Phone App: Turn on Third Party Compatibility.\n"
	quickSP += "c. Tapo Phone App: Exercise device to be installed.\n"
	quickSP += "d. Here: Enter/Update Username/Pwd, as required.\n"
	quickSP += "e. Here: Scan for devices and add.\n"
	quickSP += "<b>Detailed information: use the ? icon above</b>.\n"
	return quickSP
}

//	Default credentials for Tapo and Kasa Devices
def getDefaultCreds() {
	Map defaultCredentials = [
		kasa: [un: "kasa@tp-link.net", pw: "kasaSetup"],
		tapo: [un: "test@tp-link.net", pw: "test"],
		blank: [un: "", pw: ""]
	]
	return defaultCredentials
}

// ~~~~~ start include (788) davegut.appTpLinkSmart ~~~~~
library ( // library marker davegut.appTpLinkSmart, line 1
	name: "appTpLinkSmart", // library marker davegut.appTpLinkSmart, line 2
	namespace: "davegut", // library marker davegut.appTpLinkSmart, line 3
	author: "Dave Gutheinz", // library marker davegut.appTpLinkSmart, line 4
	description: "Discovery library for Application support the Tapo protocol devices.", // library marker davegut.appTpLinkSmart, line 5
	category: "utilities", // library marker davegut.appTpLinkSmart, line 6
	documentationLink: "" // library marker davegut.appTpLinkSmart, line 7
) // library marker davegut.appTpLinkSmart, line 8

//	Remove use of localHash // library marker davegut.appTpLinkSmart, line 10
//	update tpLinkCapConfiguration // library marker davegut.appTpLinkSmart, line 11
//	Remove setting localHash from app // library marker davegut.appTpLinkSmart, line 12



import org.json.JSONObject // library marker davegut.appTpLinkSmart, line 16
import groovy.json.JsonOutput // library marker davegut.appTpLinkSmart, line 17
import groovy.json.JsonBuilder // library marker davegut.appTpLinkSmart, line 18
import groovy.json.JsonSlurper // library marker davegut.appTpLinkSmart, line 19

def createTpLinkCreds() { // library marker davegut.appTpLinkSmart, line 21
	Map SMARTCredData = [user: userName, password: userPassword] // library marker davegut.appTpLinkSmart, line 22
	//	Look for Special Characters in credentials // library marker davegut.appTpLinkSmart, line 23
	//	These MAY cause issues, so we do a DEBUG message PLUS state with character defs. // library marker davegut.appTpLinkSmart, line 24
	Map credSpecChars = [:] // library marker davegut.appTpLinkSmart, line 25
	//	userName // library marker davegut.appTpLinkSmart, line 26
	def pattern = /[^a-zA-Z0-9@.]/ // library marker davegut.appTpLinkSmart, line 27
	def matcher = userName =~ pattern // library marker davegut.appTpLinkSmart, line 28
	List specChars = [] // library marker davegut.appTpLinkSmart, line 29
	matcher.each { match -> // library marker davegut.appTpLinkSmart, line 30
		specChars << match[0] // library marker davegut.appTpLinkSmart, line 31
	} // library marker davegut.appTpLinkSmart, line 32
	if (specChars != []) { // library marker davegut.appTpLinkSmart, line 33
		credSpecChars << [userName: specChars] // library marker davegut.appTpLinkSmart, line 34
	} // library marker davegut.appTpLinkSmart, line 35
	//	userPassword // library marker davegut.appTpLinkSmart, line 36
	specChars = userPassword.findAll(/\W+/) // library marker davegut.appTpLinkSmart, line 37
	if (specChars != []) { // library marker davegut.appTpLinkSmart, line 38
		credSpecChars << [userPassword: specChars] // library marker davegut.appTpLinkSmart, line 39
	} // library marker davegut.appTpLinkSmart, line 40
	state.credSpecChars = credSpecChars // library marker davegut.appTpLinkSmart, line 41
	SMARTCredData << [specChars: credSpecChars] // library marker davegut.appTpLinkSmart, line 42
//	Set up credential hashes that are static for userName and userPassword	 // library marker davegut.appTpLinkSmart, line 43
	String encUsername = mdEncode("SHA-1", userName.bytes).encodeHex().encodeAsBase64().toString() // library marker davegut.appTpLinkSmart, line 44
	app?.updateSetting("encUsername", [type: "string", value: encUsername]) // library marker davegut.appTpLinkSmart, line 45
	SMARTCredData << [encUsername: encUsername] // library marker davegut.appTpLinkSmart, line 46
	String encPassword = userPassword.trim().bytes.encodeBase64().toString() // library marker davegut.appTpLinkSmart, line 47
	app?.updateSetting("encPassword", [type: "string", value: encPassword]) // library marker davegut.appTpLinkSmart, line 48
	SMARTCredData << [encPassword: encPassword] // library marker davegut.appTpLinkSmart, line 49
	//	vacAes Creds (password only) // library marker davegut.appTpLinkSmart, line 50
	String encPasswordVac = mdEncode("MD5", userPassword.trim().bytes).encodeHex().toString().toUpperCase() // library marker davegut.appTpLinkSmart, line 51
	app?.updateSetting("encPasswordVac", [type: "string", value: encPasswordVac]) // library marker davegut.appTpLinkSmart, line 52
	SMARTCredData << [encPasswordVac: encPasswordVac] // library marker davegut.appTpLinkSmart, line 53
	//	Camera Creds (password only) // library marker davegut.appTpLinkSmart, line 54
	String encPasswordCam = mdEncode("SHA-256", userPassword.trim().bytes).encodeHex().toString().toUpperCase() // library marker davegut.appTpLinkSmart, line 55
	app?.updateSetting("encPasswordCam", [type: "string", value: encPasswordCam]) // library marker davegut.appTpLinkSmart, line 56
	SMARTCredData << [encPasswordCam: encPasswordCam] // library marker davegut.appTpLinkSmart, line 57
	logDebug(SMARTCredData) // library marker davegut.appTpLinkSmart, line 58


log.trace SMARTCredData	 // library marker davegut.appTpLinkSmart, line 61


} // library marker davegut.appTpLinkSmart, line 64



def findTpLinkDevices(action, timeout = 10) { // library marker davegut.appTpLinkSmart, line 68
	Map logData = [method: "findTpLinkDevices", action: action, timeOut: timeout] // library marker davegut.appTpLinkSmart, line 69
	def start = state.hostArray.min().toInteger() // library marker davegut.appTpLinkSmart, line 70
	def finish = state.hostArray.max().toInteger() + 1 // library marker davegut.appTpLinkSmart, line 71
	logData << [hostArray: state.hostArray, pollSegments: state.segArray] // library marker davegut.appTpLinkSmart, line 72
	List deviceIPs = [] // library marker davegut.appTpLinkSmart, line 73
	state.segArray.each { // library marker davegut.appTpLinkSmart, line 74
		def pollSegment = it.trim() // library marker davegut.appTpLinkSmart, line 75
		logData << [pollSegment: pollSegment] // library marker davegut.appTpLinkSmart, line 76
		for(int i = start; i < finish; i++) { // library marker davegut.appTpLinkSmart, line 77
			deviceIPs.add("${pollSegment}.${i.toString()}") // library marker davegut.appTpLinkSmart, line 78
		} // library marker davegut.appTpLinkSmart, line 79
		def cmdData = "0200000101e51100095c11706d6f58577b22706172616d73223a7b227273615f6b6579223a222d2d2d2d2d424547494e205055424c4943204b45592d2d2d2d2d5c6e4d494942496a414e42676b71686b6947397730424151454641414f43415138414d49494243674b43415145416d684655445279687367797073467936576c4d385c6e54646154397a61586133586a3042712f4d6f484971696d586e2b736b4e48584d525a6550564134627532416257386d79744a5033445073665173795679536e355c6e6f425841674d303149674d4f46736350316258367679784d523871614b33746e466361665a4653684d79536e31752f564f2f47474f795436507459716f384e315c6e44714d77373563334b5a4952387a4c71516f744657747239543337536e50754a7051555a7055376679574b676377716e7338785a657a78734e6a6465534171765c6e3167574e75436a5356686d437931564d49514942576d616a37414c47544971596a5442376d645348562f2b614a32564467424c6d7770344c7131664c4f6a466f5c6e33737241683144744a6b537376376a624f584d51695666453873764b6877586177717661546b5658382f7a4f44592b2f64684f5374694a4e6c466556636c35585c6e4a514944415141425c6e2d2d2d2d2d454e44205055424c4943204b45592d2d2d2d2d5c6e227d7d" // library marker davegut.appTpLinkSmart, line 80
		logData << [pass1: "port 20002"] // library marker davegut.appTpLinkSmart, line 81
		atomicState.finding = true // library marker davegut.appTpLinkSmart, line 82
		sendLanCmd(deviceIPs.join(','), "20002", cmdData, action, timeout) // library marker davegut.appTpLinkSmart, line 83
		runIn(timeout + 5, udpTimeout) // library marker davegut.appTpLinkSmart, line 84
		pauseExecution(timeout * 1000) // library marker davegut.appTpLinkSmart, line 85
		def await = waitFor(30) // library marker davegut.appTpLinkSmart, line 86
		//	Port 20004 devices may not respond to first poll (battery saving).  Poll twice. // library marker davegut.appTpLinkSmart, line 87
		logData << [pass2: "port 20004"] // library marker davegut.appTpLinkSmart, line 88
		sendLanCmd(deviceIPs.join(','), "20004", cmdData, "nullParse", 1) // library marker davegut.appTpLinkSmart, line 89
		pauseExecution(2000) // library marker davegut.appTpLinkSmart, line 90
		atomicState.finding = true // library marker davegut.appTpLinkSmart, line 91
		sendLanCmd(deviceIPs.join(','), "20004", cmdData, action, timeout) // library marker davegut.appTpLinkSmart, line 92
		runIn(timeout + 5, udpTimeout) // library marker davegut.appTpLinkSmart, line 93
		pauseExecution(timeout * 1000) // library marker davegut.appTpLinkSmart, line 94
		await = waitFor(30) // library marker davegut.appTpLinkSmart, line 95
	} // library marker davegut.appTpLinkSmart, line 96
	pauseExecution(5000) // library marker davegut.appTpLinkSmart, line 97
	logInfo(logData) // library marker davegut.appTpLinkSmart, line 98
	state.tpLinkChecked = true // library marker davegut.appTpLinkSmart, line 99
	runIn(570, resetTpLinkChecked) // library marker davegut.appTpLinkSmart, line 100
	return // library marker davegut.appTpLinkSmart, line 101
} // library marker davegut.appTpLinkSmart, line 102

def udpTimeout() { // library marker davegut.appTpLinkSmart, line 104
	Map logData = [method: "udpTimeout", status: "no devices found", error: "udpTimeout"] // library marker davegut.appTpLinkSmart, line 105
	logDebug(logData) // library marker davegut.appTpLinkSmart, line 106
	atomicState.finding = false // library marker davegut.appTpLinkSmart, line 107
} // library marker davegut.appTpLinkSmart, line 108

def waitFor(secs) { // library marker davegut.appTpLinkSmart, line 110
	int i = 0 // library marker davegut.appTpLinkSmart, line 111
	for(i = 0; i < secs; i+=1) { // library marker davegut.appTpLinkSmart, line 112
		if (atomicState.finding == false) { i = secs }  // library marker davegut.appTpLinkSmart, line 113
		else { pauseExecution(1000) } // library marker davegut.appTpLinkSmart, line 114
	} // library marker davegut.appTpLinkSmart, line 115
	return // library marker davegut.appTpLinkSmart, line 116
} // library marker davegut.appTpLinkSmart, line 117

def nullParse(response) {} // library marker davegut.appTpLinkSmart, line 119

def getTpLinkLanData(response) { // library marker davegut.appTpLinkSmart, line 121
	Map logData = [method: "getTpLinkLanData",  // library marker davegut.appTpLinkSmart, line 122
				   action: "Completed LAN Discovery", // library marker davegut.appTpLinkSmart, line 123
				   smartDevicesFound: response.size()] // library marker davegut.appTpLinkSmart, line 124
	logInfo(logData) // library marker davegut.appTpLinkSmart, line 125
	unschedule("udpTimeout") // library marker davegut.appTpLinkSmart, line 126
	List discData = [] // library marker davegut.appTpLinkSmart, line 127
	if (response instanceof Map) { // library marker davegut.appTpLinkSmart, line 128
		Map devData = getDiscData(response) // library marker davegut.appTpLinkSmart, line 129
		if (devData.status == "OK") { // library marker davegut.appTpLinkSmart, line 130
			discData << devData // library marker davegut.appTpLinkSmart, line 131
		} // library marker davegut.appTpLinkSmart, line 132
	} else { // library marker davegut.appTpLinkSmart, line 133
		response.each { // library marker davegut.appTpLinkSmart, line 134
			Map devData = getDiscData(it) // library marker davegut.appTpLinkSmart, line 135
			if (devData.status == "OK" && !discData.toString().contains(devData.dni)) { // library marker davegut.appTpLinkSmart, line 136
				discData << devData // library marker davegut.appTpLinkSmart, line 137
			} // library marker davegut.appTpLinkSmart, line 138
		} // library marker davegut.appTpLinkSmart, line 139
	} // library marker davegut.appTpLinkSmart, line 140
	getAllTpLinkDeviceData(discData) // library marker davegut.appTpLinkSmart, line 141
} // library marker davegut.appTpLinkSmart, line 142

def getDiscData(response) { // library marker davegut.appTpLinkSmart, line 144
	Map devData = [:] // library marker davegut.appTpLinkSmart, line 145
	Map unsupDev = atomicState.unsupported // library marker davegut.appTpLinkSmart, line 146
	try { // library marker davegut.appTpLinkSmart, line 147
		def respData = parseLanMessage(response.description) // library marker davegut.appTpLinkSmart, line 148
		if (respData.type == "LAN_TYPE_UDPCLIENT") { // library marker davegut.appTpLinkSmart, line 149
			byte[] payloadByte = hubitat.helper.HexUtils.hexStringToByteArray(respData.payload.drop(32))  // library marker davegut.appTpLinkSmart, line 150
			String payloadString = new String(payloadByte) // library marker davegut.appTpLinkSmart, line 151
			//	Handle Jumbo packets // library marker davegut.appTpLinkSmart, line 152
			if (payloadString.length() > 1007) { // library marker davegut.appTpLinkSmart, line 153
				if (payloadString.contains(""":"H200",""")) { // library marker davegut.appTpLinkSmart, line 154
					//	H200. Keep data up to, but not including "key" // library marker davegut.appTpLinkSmart, line 155
					payloadString = payloadString.substring(0,payloadString.indexOf(""","key":""")) + "}}}" // library marker davegut.appTpLinkSmart, line 156
				} else { // library marker davegut.appTpLinkSmart, line 157
					//	Unknown cases with fingers crossed.  (Note: I could go through a lot of processing // library marker davegut.appTpLinkSmart, line 158
					//	here to determine the break point; however, I am lazy and it is a currently undiscovered case..) // library marker davegut.appTpLinkSmart, line 159
					payloadString = payloadString + """"}}}""" // library marker davegut.appTpLinkSmart, line 160
				} // library marker davegut.appTpLinkSmart, line 161
			} // library marker davegut.appTpLinkSmart, line 162
			Map payload = new JsonSlurper().parseText(payloadString).result // library marker davegut.appTpLinkSmart, line 163
			List supported = supportedProducts() // library marker davegut.appTpLinkSmart, line 164
			String devType = payload.device_type // library marker davegut.appTpLinkSmart, line 165
			String model = payload.device_model // library marker davegut.appTpLinkSmart, line 166
			String devIp = payload.ip // library marker davegut.appTpLinkSmart, line 167
			String protocol = payload.mgt_encrypt_schm.encrypt_type // library marker davegut.appTpLinkSmart, line 168
			String status = "true" // library marker davegut.appTpLinkSmart, line 169
			if (protocol == "TPAP") {  // library marker davegut.appTpLinkSmart, line 170
				status = "Protocol not supported" // library marker davegut.appTpLinkSmart, line 171
				unsupDev << ["<b>${model}</b>": "Protocol ${protocol} not supported"] // library marker davegut.appTpLinkSmart, line 172
			}	else if (model == "HS200") { // library marker davegut.appTpLinkSmart, line 173
				status = "Device model not supported" // library marker davegut.appTpLinkSmart, line 174
				unsupDev << ["<b>${model}</b>": "Model ${model} not supported"] // library marker davegut.appTpLinkSmart, line 175
			}	else if (!supported.contains(devType)) {  // library marker davegut.appTpLinkSmart, line 176
				status = "Device type not supported"  // library marker davegut.appTpLinkSmart, line 177
				unsupDev << ["<b>${model}</b>": "DevType ${devType} not supported"] // library marker davegut.appTpLinkSmart, line 178
			} // library marker davegut.appTpLinkSmart, line 179
			if (status == "true") { // library marker davegut.appTpLinkSmart, line 180
				String dni = payload.mac.replaceAll("-", "") // library marker davegut.appTpLinkSmart, line 181
				String port = payload.mgt_encrypt_schm.http_port // library marker davegut.appTpLinkSmart, line 182
				String httpStr = "http://" // library marker davegut.appTpLinkSmart, line 183
				String httpPath = "/app" // library marker davegut.appTpLinkSmart, line 184
				if (payload.mgt_encrypt_schm.is_support_https) { // library marker davegut.appTpLinkSmart, line 185
					httpStr = "https://" // library marker davegut.appTpLinkSmart, line 186
				} // library marker davegut.appTpLinkSmart, line 187
				if (devType == "SMART.IPCAMERA" || devType == "SMART.TAPODOORBELL") { // library marker davegut.appTpLinkSmart, line 188
					protocol = "camera" // library marker davegut.appTpLinkSmart, line 189
					port = "443" // library marker davegut.appTpLinkSmart, line 190
				} else if (devType == "SMART.TAPOROBOVAC" && protocol == "AES") { // library marker davegut.appTpLinkSmart, line 191
					protocol = "vacAes" // library marker davegut.appTpLinkSmart, line 192
					httpPath = "" // library marker davegut.appTpLinkSmart, line 193
				} // library marker davegut.appTpLinkSmart, line 194
				String baseUrl = httpStr + devIp + ":" + port + httpPath // library marker davegut.appTpLinkSmart, line 195
				devData << [udpPort: respData.port, type: devType, model: model, baseUrl: baseUrl,  // library marker davegut.appTpLinkSmart, line 196
							dni: dni, ip: devIp, port: port, protocol: protocol, status: "OK"] // library marker davegut.appTpLinkSmart, line 197
				if (payload.power) { devData << [power: payload.power] } // library marker davegut.appTpLinkSmart, line 198
				logDebug(devData) // library marker davegut.appTpLinkSmart, line 199
			} else { // library marker davegut.appTpLinkSmart, line 200
				Map errResp = [method: "getDiscData", payload: payload, status: status] // library marker davegut.appTpLinkSmart, line 201
				logDebug(errResp) // library marker davegut.appTpLinkSmart, line 202
			} // library marker davegut.appTpLinkSmart, line 203
		} // library marker davegut.appTpLinkSmart, line 204
	} catch (err) { // library marker davegut.appTpLinkSmart, line 205
		devData << [status: "INVALID", respData: repsData, error: err] // library marker davegut.appTpLinkSmart, line 206
		logWarn(devData) // library marker davegut.appTpLinkSmart, line 207
	} // library marker davegut.appTpLinkSmart, line 208
	atomicState.unsupported = unsupDev // library marker davegut.appTpLinkSmart, line 209
	return devData // library marker davegut.appTpLinkSmart, line 210
} // library marker davegut.appTpLinkSmart, line 211

def getAllTpLinkDeviceData(List discData) { // library marker davegut.appTpLinkSmart, line 213
	Map logData = [method: "getAllTpLinkDeviceData", discData: discData.size()] // library marker davegut.appTpLinkSmart, line 214
	discData.each { Map devData -> // library marker davegut.appTpLinkSmart, line 215
		if (devData.protocol == "KLAP") { // library marker davegut.appTpLinkSmart, line 216



//			klapHandshake(devData.baseUrl, localHash, devData) // library marker davegut.appTpLinkSmart, line 220
			klapHandshake(devData.baseUrl, devData) // library marker davegut.appTpLinkSmart, line 221



		} else if (devData.protocol == "AES") { // library marker davegut.appTpLinkSmart, line 225
			aesHandshake(devData.baseUrl, devData) // library marker davegut.appTpLinkSmart, line 226
		} else if (devData.protocol == "vacAes") { // library marker davegut.appTpLinkSmart, line 227
			vacAesHandshake(devData.baseUrl, userName, encPasswordVac, devData) // library marker davegut.appTpLinkSmart, line 228
		} else if (devData.protocol == "camera") { // library marker davegut.appTpLinkSmart, line 229
			Map hsInput = [url: devData.baseUrl, user: userName, pwd: encPasswordCam] // library marker davegut.appTpLinkSmart, line 230
			cameraHandshake(hsInput, devData) // library marker davegut.appTpLinkSmart, line 231
		} else {  // library marker davegut.appTpLinkSmart, line 232
			unknownProt(devData) // library marker davegut.appTpLinkSmart, line 233
		} // library marker davegut.appTpLinkSmart, line 234
		pauseExecution(500) // library marker davegut.appTpLinkSmart, line 235
	} // library marker davegut.appTpLinkSmart, line 236
	atomicState.finding = false // library marker davegut.appTpLinkSmart, line 237
	logDebug(logData) // library marker davegut.appTpLinkSmart, line 238
} // library marker davegut.appTpLinkSmart, line 239

def getDataCmd() { // library marker davegut.appTpLinkSmart, line 241
	List requests = [[method: "get_device_info"]] // library marker davegut.appTpLinkSmart, line 242
	requests << [method: "component_nego"] // library marker davegut.appTpLinkSmart, line 243
	Map cmdBody = [ // library marker davegut.appTpLinkSmart, line 244
		method: "multipleRequest", // library marker davegut.appTpLinkSmart, line 245
		params: [requests: requests]] // library marker davegut.appTpLinkSmart, line 246
	return cmdBody // library marker davegut.appTpLinkSmart, line 247
} // library marker davegut.appTpLinkSmart, line 248

def addToDevices(devData, cmdData) { // library marker davegut.appTpLinkSmart, line 250
	Map logData = [method: "addToDevices"] // library marker davegut.appTpLinkSmart, line 251
	String dni = devData.dni // library marker davegut.appTpLinkSmart, line 252
	def devicesData = atomicState.devices // library marker davegut.appTpLinkSmart, line 253
	devicesData.remove(dni) // library marker davegut.appTpLinkSmart, line 254
	def comps // library marker davegut.appTpLinkSmart, line 255
	def cmdResp // library marker davegut.appTpLinkSmart, line 256
	String alias // library marker davegut.appTpLinkSmart, line 257
	String tpType = devData.type // library marker davegut.appTpLinkSmart, line 258
	String model = devData.model // library marker davegut.appTpLinkSmart, line 259
	if (devData.protocol != "camera") { // library marker davegut.appTpLinkSmart, line 260
		comps = cmdData.find { it.method == "component_nego" } // library marker davegut.appTpLinkSmart, line 261
		comps = comps.result.component_list // library marker davegut.appTpLinkSmart, line 262
		cmdResp = cmdData.find { it.method == "get_device_info" } // library marker davegut.appTpLinkSmart, line 263
		cmdResp = cmdResp.result // library marker davegut.appTpLinkSmart, line 264
		byte[] plainBytes = cmdResp.nickname.decodeBase64() // library marker davegut.appTpLinkSmart, line 265
		alias = new String(plainBytes) // library marker davegut.appTpLinkSmart, line 266
		if (alias == "") { alias = model } // library marker davegut.appTpLinkSmart, line 267
	} else { // library marker davegut.appTpLinkSmart, line 268
		comps = cmdData.find { it.method == "getAppComponentList" } // library marker davegut.appTpLinkSmart, line 269
		comps = comps.result.app_component.app_component_list // library marker davegut.appTpLinkSmart, line 270
		cmdResp = cmdData.find { it.method == "getDeviceInfo" } // library marker davegut.appTpLinkSmart, line 271
		cmdResp = cmdResp.result.device_info.basic_info // library marker davegut.appTpLinkSmart, line 272
		alias = cmdResp.device_alias // library marker davegut.appTpLinkSmart, line 273
		if (alias == "") { alias = model } // library marker davegut.appTpLinkSmart, line 274
	} // library marker davegut.appTpLinkSmart, line 275
	def type = "Unknown" // library marker davegut.appTpLinkSmart, line 276
	def ctHigh // library marker davegut.appTpLinkSmart, line 277
	def ctLow // library marker davegut.appTpLinkSmart, line 278
	Map deviceData = [devIp: devData.ip, deviceType: tpType, protocol: devData.protocol, // library marker davegut.appTpLinkSmart, line 279
					  model: model, baseUrl: devData.baseUrl, alias: alias] // library marker davegut.appTpLinkSmart, line 280
	//	Determine Driver to Load // library marker davegut.appTpLinkSmart, line 281
	if (tpType.contains("PLUG") || tpType.contains("SWITCH")) { // library marker davegut.appTpLinkSmart, line 282
		type = "Plug" // library marker davegut.appTpLinkSmart, line 283
		if (comps.find { it.id == "control_child" }) { // library marker davegut.appTpLinkSmart, line 284
			type = "Parent" // library marker davegut.appTpLinkSmart, line 285
		} else if (comps.find{it.id=="dimmer"} || comps.find{it.id=="brightness"}) { // library marker davegut.appTpLinkSmart, line 286
			type = "Dimmer" // library marker davegut.appTpLinkSmart, line 287
		} // library marker davegut.appTpLinkSmart, line 288
	} else if (tpType.contains("HUB")) { // library marker davegut.appTpLinkSmart, line 289
		type = "Hub" // library marker davegut.appTpLinkSmart, line 290
	} else if (tpType.contains("BULB")) { // library marker davegut.appTpLinkSmart, line 291
		type = "Dimmer" // library marker davegut.appTpLinkSmart, line 292
		if (comps.find { it.id == "light_strip" }) { // library marker davegut.appTpLinkSmart, line 293
			type = "Lightstrip" // library marker davegut.appTpLinkSmart, line 294
		} else if (comps.find { it.id == "color" }) { // library marker davegut.appTpLinkSmart, line 295
			type = "Color Bulb" // library marker davegut.appTpLinkSmart, line 296
		} // library marker davegut.appTpLinkSmart, line 297
		if (type != "Dimmer" && comps.find { it.id == "color_temperature" } ) { // library marker davegut.appTpLinkSmart, line 298
			ctHigh = cmdResp.color_temp_range[1] // library marker davegut.appTpLinkSmart, line 299
			ctLow = cmdResp.color_temp_range[0] // library marker davegut.appTpLinkSmart, line 300
			deviceData << [ctHigh: ctHigh, ctLow: ctLow] // library marker davegut.appTpLinkSmart, line 301
		} // library marker davegut.appTpLinkSmart, line 302
	} else if (tpType.contains("ROBOVAC")) { // library marker davegut.appTpLinkSmart, line 303
		type = "Robovac" // library marker davegut.appTpLinkSmart, line 304
	} else if (tpType.contains("CAMERA")) { // library marker davegut.appTpLinkSmart, line 305
		type = "Camera" // library marker davegut.appTpLinkSmart, line 306
		if (comps.find { it.name == "ptz" } ) { // library marker davegut.appTpLinkSmart, line 307
			type = "Cam Ptz" // library marker davegut.appTpLinkSmart, line 308
		} // library marker davegut.appTpLinkSmart, line 309
	} else if (tpType.contains("DOORBELL")) { // library marker davegut.appTpLinkSmart, line 310
		type = "Camera" // library marker davegut.appTpLinkSmart, line 311
		deviceData << [isDoorbell: "true"] // library marker davegut.appTpLinkSmart, line 312
	} else if (tpType.contains("CHIME")) { // library marker davegut.appTpLinkSmart, line 313
		type = "Chime" // library marker davegut.appTpLinkSmart, line 314
	} // library marker davegut.appTpLinkSmart, line 315
	deviceData << [type: type] // library marker davegut.appTpLinkSmart, line 316
	if (comps.find {it.id == "led"} ) {  // library marker davegut.appTpLinkSmart, line 317
		String ledVer = comps.find {it.id == "led"}.ver_code // library marker davegut.appTpLinkSmart, line 318
		deviceData << [ledVer: ledVer] // library marker davegut.appTpLinkSmart, line 319
	} // library marker davegut.appTpLinkSmart, line 320
	if (comps.find {it.id == "energy_monitoring"}) { deviceData << [isEm: "true"] } // library marker davegut.appTpLinkSmart, line 321
	if (comps.find {it.id == "on_off_gradually"}) { deviceData << [gradOnOff: "true"] } // library marker davegut.appTpLinkSmart, line 322
	if (comps.find { it.name == "led"}) { // library marker davegut.appTpLinkSmart, line 323
		String ledVer = comps.find { it.name == "led" }.version // library marker davegut.appTpLinkSmart, line 324
		deviceData << [ledVer: ledVer] // library marker davegut.appTpLinkSmart, line 325
	} // library marker davegut.appTpLinkSmart, line 326
	if (comps.find {it.name == "alert"}) { deviceData << [alert: "true"] } // library marker davegut.appTpLinkSmart, line 327
	if (devData.power) { deviceData << [power: devData.power] } // library marker davegut.appTpLinkSmart, line 328
	devicesData << ["${dni}": deviceData] // library marker davegut.appTpLinkSmart, line 329
	atomicState.devices = devicesData // library marker davegut.appTpLinkSmart, line 330
	logData << ["${deviceData.alias}": deviceData, dni: dni] // library marker davegut.appTpLinkSmart, line 331
	Map InfoData = ["${deviceData.alias}": "added to device data"] // library marker davegut.appTpLinkSmart, line 332
	logInfo("${deviceData.alias}: added to device data") // library marker davegut.appTpLinkSmart, line 333
	updateChild(dni, deviceData) // library marker davegut.appTpLinkSmart, line 334
	logDebug(logData) // library marker davegut.appTpLinkSmart, line 335
} // library marker davegut.appTpLinkSmart, line 336

def updateChild(dni, deviceData) { // library marker davegut.appTpLinkSmart, line 338
	def child = getChildDevice(dni) // library marker davegut.appTpLinkSmart, line 339
	if (child) { // library marker davegut.appTpLinkSmart, line 340
		child.updateChild(deviceData) // library marker davegut.appTpLinkSmart, line 341
	} // library marker davegut.appTpLinkSmart, line 342
} // library marker davegut.appTpLinkSmart, line 343

//	===== get Smart KLAP Protocol Data ===== // library marker davegut.appTpLinkSmart, line 345
def sendKlapDataCmd(handshakeData, data) { // library marker davegut.appTpLinkSmart, line 346
	if (handshakeData.respStatus != "Login OK") { // library marker davegut.appTpLinkSmart, line 347
		Map logData = [method: "sendKlapDataCmd", handshake: handshakeData, data: data] // library marker davegut.appTpLinkSmart, line 348
		logWarn(logData) // library marker davegut.appTpLinkSmart, line 349
	} else { // library marker davegut.appTpLinkSmart, line 350
		Map reqParams = [timeout: 10, headers: ["Cookie": data.data.cookie]] // library marker davegut.appTpLinkSmart, line 351
		def seqNo = data.data.seqNo + 1 // library marker davegut.appTpLinkSmart, line 352
		String cmdBodyJson = new groovy.json.JsonBuilder(getDataCmd()).toString() // library marker davegut.appTpLinkSmart, line 353
		Map encryptedData = klapEncrypt(cmdBodyJson.getBytes(), data.data.encKey,  // library marker davegut.appTpLinkSmart, line 354
										data.data.encIv, data.data.encSig, seqNo) // library marker davegut.appTpLinkSmart, line 355
		reqParams << [ // library marker davegut.appTpLinkSmart, line 356
			uri: "${data.data.devData.baseUrl}/request?seq=${encryptedData.seqNumber}", // library marker davegut.appTpLinkSmart, line 357
			body: encryptedData.cipherData, // library marker davegut.appTpLinkSmart, line 358
			ignoreSSLIssues: true, // library marker davegut.appTpLinkSmart, line 359
			timeout:10, // library marker davegut.appTpLinkSmart, line 360
			contentType: "application/octet-stream", // library marker davegut.appTpLinkSmart, line 361
			requestContentType: "application/octet-stream"] // library marker davegut.appTpLinkSmart, line 362
		if (data.data.devData.model == "P100") { // library marker davegut.appTpLinkSmart, line 363
			log.info "Pausing for the TAPO P100 close connection condition" // library marker davegut.appTpLinkSmart, line 364
			pauseExecution(2000) // library marker davegut.appTpLinkSmart, line 365
		} // library marker davegut.appTpLinkSmart, line 366
		asynchttpPost("parseKlapResp", reqParams, [data: data.data]) // library marker davegut.appTpLinkSmart, line 367
	} // library marker davegut.appTpLinkSmart, line 368
} // library marker davegut.appTpLinkSmart, line 369

def parseKlapResp(resp, respData) { // library marker davegut.appTpLinkSmart, line 371
	Map data = respData.data // library marker davegut.appTpLinkSmart, line 372
	Map logData = [method: "parseKlapResp", ip: data.devData.ip, model: data.devData.model] // library marker davegut.appTpLinkSmart, line 373
	if (resp.status == 200) { // library marker davegut.appTpLinkSmart, line 374
		try { // library marker davegut.appTpLinkSmart, line 375
			byte[] cipherResponse = resp.data.decodeBase64()[32..-1] // library marker davegut.appTpLinkSmart, line 376
			def clearResp = klapDecrypt(cipherResponse, data.encKey, // library marker davegut.appTpLinkSmart, line 377
										data.encIv, data.seqNo + 1) // library marker davegut.appTpLinkSmart, line 378
			Map cmdResp =  new JsonSlurper().parseText(clearResp) // library marker davegut.appTpLinkSmart, line 379
			logData << [status: "OK"] // library marker davegut.appTpLinkSmart, line 380
			if (cmdResp.error_code == 0) { // library marker davegut.appTpLinkSmart, line 381
				addToDevices(data.devData, cmdResp.result.responses) // library marker davegut.appTpLinkSmart, line 382
				logDebug(logData) // library marker davegut.appTpLinkSmart, line 383
			} else { // library marker davegut.appTpLinkSmart, line 384
				logData << [status: "errorInCmdResp", cmdResp: cmdResp] // library marker davegut.appTpLinkSmart, line 385
				logWarn(logData) // library marker davegut.appTpLinkSmart, line 386
			} // library marker davegut.appTpLinkSmart, line 387
		} catch (err) { // library marker davegut.appTpLinkSmart, line 388
			logData << [status: "deviceDataParseError", error: err, dataLength: resp.data.length()] // library marker davegut.appTpLinkSmart, line 389
			logWarn(logData) // library marker davegut.appTpLinkSmart, line 390
		} // library marker davegut.appTpLinkSmart, line 391
	} else { // library marker davegut.appTpLinkSmart, line 392
		logData << [status: "httpFailure", data: resp.properties] // library marker davegut.appTpLinkSmart, line 393
		logWarn(logData) // library marker davegut.appTpLinkSmart, line 394
	} // library marker davegut.appTpLinkSmart, line 395
} // library marker davegut.appTpLinkSmart, line 396

//	===== get Smart Camera Protocol Data ===== // library marker davegut.appTpLinkSmart, line 398
def sendCameraDataCmd(devData, camCmdIn) { // library marker davegut.appTpLinkSmart, line 399
	List requests = [[method:"getDeviceInfo", params:[device_info:[name:["basic_info"]]]], // library marker davegut.appTpLinkSmart, line 400
					 [method:"getAppComponentList", params:[app_component:[name:"app_component_list"]]]] // library marker davegut.appTpLinkSmart, line 401
	Map cmdBody = [method: "multipleRequest", params: [requests: requests]] // library marker davegut.appTpLinkSmart, line 402
	def cmdStr = JsonOutput.toJson(cmdBody) // library marker davegut.appTpLinkSmart, line 403
	Map reqBody = [method: "securePassthrough", // library marker davegut.appTpLinkSmart, line 404
				   params: [request: aesEncrypt(cmdStr, camCmdIn.lsk, camCmdIn.ivb)]] // library marker davegut.appTpLinkSmart, line 405
	String cmdData = new groovy.json.JsonBuilder(reqBody).toString() // library marker davegut.appTpLinkSmart, line 406
	String initTagHex = camCmdIn.encPwd + camCmdIn.cnonce // library marker davegut.appTpLinkSmart, line 407
	String initTag = mdEncode("SHA-256", initTagHex.getBytes()).encodeHex().toString().toUpperCase() // library marker davegut.appTpLinkSmart, line 408
	String tagString = initTag + cmdData + camCmdIn.seqNo // library marker davegut.appTpLinkSmart, line 409
	String tag =  mdEncode("SHA-256", tagString.getBytes()).encodeHex().toString().toUpperCase() // library marker davegut.appTpLinkSmart, line 410
	Map heads = getCamHeaders() // library marker davegut.appTpLinkSmart, line 411
	heads << ["Tapo_tag": tag, Seq: camCmdIn.seqNo] // library marker davegut.appTpLinkSmart, line 412
	Map reqParams = [uri: camCmdIn.apiUrl, // library marker davegut.appTpLinkSmart, line 413
					 body: cmdData, // library marker davegut.appTpLinkSmart, line 414
					 contentType: "application/json", // library marker davegut.appTpLinkSmart, line 415
					 requestContentType: "application/json", // library marker davegut.appTpLinkSmart, line 416
					 timeout: 10, // library marker davegut.appTpLinkSmart, line 417
					 ignoreSSLIssues: true, // library marker davegut.appTpLinkSmart, line 418
					 headers: heads // library marker davegut.appTpLinkSmart, line 419
					] // library marker davegut.appTpLinkSmart, line 420
	asynchttpPost("parseCameraResp", reqParams, [data: [devData: devData, camCmdIn: camCmdIn]]) // library marker davegut.appTpLinkSmart, line 421
} // library marker davegut.appTpLinkSmart, line 422

def parseCameraResp(resp, data) { // library marker davegut.appTpLinkSmart, line 424
	Map logData = [method: "parseCameraResp", ip: data.data.devData.ip] // library marker davegut.appTpLinkSmart, line 425
	if (resp.json.error_code == 0) { // library marker davegut.appTpLinkSmart, line 426
		resp = resp.json // library marker davegut.appTpLinkSmart, line 427
		try { // library marker davegut.appTpLinkSmart, line 428
			def clearResp = aesDecrypt(resp.result.response, data.data.camCmdIn.lsk,  // library marker davegut.appTpLinkSmart, line 429
									   data.data.camCmdIn.ivb) // library marker davegut.appTpLinkSmart, line 430
			Map cmdResp =  new JsonSlurper().parseText(clearResp) // library marker davegut.appTpLinkSmart, line 431
			logData << [status: "OK"] // library marker davegut.appTpLinkSmart, line 432
			if (cmdResp.error_code == 0) { // library marker davegut.appTpLinkSmart, line 433
				addToDevices(data.data.devData, cmdResp.result.responses) // library marker davegut.appTpLinkSmart, line 434
                logDebug(logData) // library marker davegut.appTpLinkSmart, line 435
			} else { // library marker davegut.appTpLinkSmart, line 436
				logData << [status: "errorInCmdResp", cmdResp: cmdResp] // library marker davegut.appTpLinkSmart, line 437
				logWarn(logData) // library marker davegut.appTpLinkSmart, line 438
			} // library marker davegut.appTpLinkSmart, line 439
		} catch (err) { // library marker davegut.appTpLinkSmart, line 440
			logData << [status: "decryptDataError", error: err] // library marker davegut.appTpLinkSmart, line 441
			logWarn(logData) // library marker davegut.appTpLinkSmart, line 442
		} // library marker davegut.appTpLinkSmart, line 443
	} else { // library marker davegut.appTpLinkSmart, line 444
		logData << [status: "rerurnDataErrorCode", resp: resp] // library marker davegut.appTpLinkSmart, line 445
		logWarn(logData) // library marker davegut.appTpLinkSmart, line 446
	} // library marker davegut.appTpLinkSmart, line 447
} // library marker davegut.appTpLinkSmart, line 448

//	===== get Smart AES Protocol Data ===== // library marker davegut.appTpLinkSmart, line 450
def getAesToken(resp, data) { // library marker davegut.appTpLinkSmart, line 451
	Map logData = [method: "getAesToken"] // library marker davegut.appTpLinkSmart, line 452
	if (resp.status == 200) { // library marker davegut.appTpLinkSmart, line 453
		if (resp.json.error_code == 0) { // library marker davegut.appTpLinkSmart, line 454
			try { // library marker davegut.appTpLinkSmart, line 455
				def clearResp = aesDecrypt(resp.json.result.response, data.encKey, data.encIv) // library marker davegut.appTpLinkSmart, line 456
				Map cmdResp = new JsonSlurper().parseText(clearResp) // library marker davegut.appTpLinkSmart, line 457
				if (cmdResp.error_code == 0) { // library marker davegut.appTpLinkSmart, line 458
					def token = cmdResp.result.token // library marker davegut.appTpLinkSmart, line 459
					logData << [respStatus: "OK", token: token] // library marker davegut.appTpLinkSmart, line 460
					logDebug(logData) // library marker davegut.appTpLinkSmart, line 461
					sendAesDataCmd(token, data) // library marker davegut.appTpLinkSmart, line 462
				} else { // library marker davegut.appTpLinkSmart, line 463
					logData << [respStatus: "ERROR code in cmdResp",  // library marker davegut.appTpLinkSmart, line 464
								error_code: cmdResp.error_code, // library marker davegut.appTpLinkSmart, line 465
								check: "cryptoArray, credentials", data: cmdResp] // library marker davegut.appTpLinkSmart, line 466
					logWarn(logData) // library marker davegut.appTpLinkSmart, line 467
				} // library marker davegut.appTpLinkSmart, line 468
			} catch (err) { // library marker davegut.appTpLinkSmart, line 469
				logData << [respStatus: "ERROR parsing respJson", respJson: resp.json, // library marker davegut.appTpLinkSmart, line 470
							error: err] // library marker davegut.appTpLinkSmart, line 471
				logWarn(logData) // library marker davegut.appTpLinkSmart, line 472
			} // library marker davegut.appTpLinkSmart, line 473
		} else { // library marker davegut.appTpLinkSmart, line 474
			logData << [respStatus: "ERROR code in resp.json", errorCode: resp.json.error_code, // library marker davegut.appTpLinkSmart, line 475
						respJson: resp.json] // library marker davegut.appTpLinkSmart, line 476
			logWarn(logData) // library marker davegut.appTpLinkSmart, line 477
		} // library marker davegut.appTpLinkSmart, line 478
	} else { // library marker davegut.appTpLinkSmart, line 479
		logData << [respStatus: "ERROR in HTTP response", respStatus: resp.status, data: resp.properties] // library marker davegut.appTpLinkSmart, line 480
		logWarn(logData) // library marker davegut.appTpLinkSmart, line 481
	} // library marker davegut.appTpLinkSmart, line 482
} // library marker davegut.appTpLinkSmart, line 483

def sendAesDataCmd(token, data) { // library marker davegut.appTpLinkSmart, line 485
	def cmdStr = JsonOutput.toJson(getDataCmd()).toString() // library marker davegut.appTpLinkSmart, line 486
	Map reqBody = [method: "securePassthrough", // library marker davegut.appTpLinkSmart, line 487
				   params: [request: aesEncrypt(cmdStr, data.encKey, data.encIv)]] // library marker davegut.appTpLinkSmart, line 488
	Map reqParams = [uri: "${data.baseUrl}?token=${token}", // library marker davegut.appTpLinkSmart, line 489
					 body: new groovy.json.JsonBuilder(reqBody).toString(), // library marker davegut.appTpLinkSmart, line 490
					 contentType: "application/json", // library marker davegut.appTpLinkSmart, line 491
					 requestContentType: "application/json", // library marker davegut.appTpLinkSmart, line 492
					 timeout: 10, // library marker davegut.appTpLinkSmart, line 493
					 headers: ["Cookie": data.cookie]] // library marker davegut.appTpLinkSmart, line 494
	if (data.devData.model == "P100") { // library marker davegut.appTpLinkSmart, line 495
		pauseExecution(2000) // library marker davegut.appTpLinkSmart, line 496
	} // library marker davegut.appTpLinkSmart, line 497
	asynchttpPost("parseAesResp", reqParams, [data: data]) // library marker davegut.appTpLinkSmart, line 498
} // library marker davegut.appTpLinkSmart, line 499

def parseAesResp(resp, data) { // library marker davegut.appTpLinkSmart, line 501
	Map logData = [method: "parseAesResp"] // library marker davegut.appTpLinkSmart, line 502
	if (resp.status == 200) { // library marker davegut.appTpLinkSmart, line 503
		try { // library marker davegut.appTpLinkSmart, line 504
			Map cmdResp = new JsonSlurper().parseText(aesDecrypt(resp.json.result.response, // library marker davegut.appTpLinkSmart, line 505
																 data.data.encKey, data.data.encIv)) // library marker davegut.appTpLinkSmart, line 506
			logData << [status: "OK", cmdResp: cmdResp] // library marker davegut.appTpLinkSmart, line 507
			if (cmdResp.error_code == 0) { // library marker davegut.appTpLinkSmart, line 508
				addToDevices(data.data.devData, cmdResp.result.responses) // library marker davegut.appTpLinkSmart, line 509
				logDebug(logData) // library marker davegut.appTpLinkSmart, line 510
			} else { // library marker davegut.appTpLinkSmart, line 511
				logData << [status: "errorInCmdResp"] // library marker davegut.appTpLinkSmart, line 512
				logWarn(logData) // library marker davegut.appTpLinkSmart, line 513
			} // library marker davegut.appTpLinkSmart, line 514
		} catch (err) { // library marker davegut.appTpLinkSmart, line 515
			logData << [status: "deviceDataParseError", error: err, dataLength: resp.data.length()] // library marker davegut.appTpLinkSmart, line 516
			logWarn(logData) // library marker davegut.appTpLinkSmart, line 517
		} // library marker davegut.appTpLinkSmart, line 518
	} else { // library marker davegut.appTpLinkSmart, line 519
		logData << [status: "httpFailure", data: resp.properties] // library marker davegut.appTpLinkSmart, line 520
		logWarn(logData) // library marker davegut.appTpLinkSmart, line 521
	} // library marker davegut.appTpLinkSmart, line 522
} // library marker davegut.appTpLinkSmart, line 523

//	===== get Smart vacAes Protocol Data ===== // library marker davegut.appTpLinkSmart, line 525
def sendVacAesDataCmd(token, data) { // library marker davegut.appTpLinkSmart, line 526
	Map devData = data.data.devData // library marker davegut.appTpLinkSmart, line 527
	Map reqParams = getVacAesParams(getDataCmd(), "${data.data.baseUrl}/?token=${token}") // library marker davegut.appTpLinkSmart, line 528
	asynchttpPost("parseVacAesResp", reqParams, [data: devData]) // library marker davegut.appTpLinkSmart, line 529
} // library marker davegut.appTpLinkSmart, line 530

def parseVacAesResp(resp, devData) { // library marker davegut.appTpLinkSmart, line 532
	Map logData = [parseMethod: "parseVacAesResp"] // library marker davegut.appTpLinkSmart, line 533
	try { // library marker davegut.appTpLinkSmart, line 534
		Map cmdResp = resp.json // library marker davegut.appTpLinkSmart, line 535
		logData << [status: "OK", cmdResp: cmdResp] // library marker davegut.appTpLinkSmart, line 536
			if (cmdResp.error_code == 0) { // library marker davegut.appTpLinkSmart, line 537
				addToDevices(devData.data, cmdResp.result.responses) // library marker davegut.appTpLinkSmart, line 538
				logDebug(logData) // library marker davegut.appTpLinkSmart, line 539
			} else { // library marker davegut.appTpLinkSmart, line 540
				logData << [status: "errorInCmdResp"] // library marker davegut.appTpLinkSmart, line 541
				logWarn(logData) // library marker davegut.appTpLinkSmart, line 542
			} // library marker davegut.appTpLinkSmart, line 543
	} catch (err) { // library marker davegut.appTpLinkSmart, line 544
		logData << [status: "deviceDataParseError", error: err, dataLength: resp.data.length()] // library marker davegut.appTpLinkSmart, line 545
		logWarn(logData) // library marker davegut.appTpLinkSmart, line 546
	} // library marker davegut.appTpLinkSmart, line 547
	return parseData // library marker davegut.appTpLinkSmart, line 548
} // library marker davegut.appTpLinkSmart, line 549

//	===== Support device data update request ===== // library marker davegut.appTpLinkSmart, line 551
def tpLinkCheckForDevices(timeout = 5) { // library marker davegut.appTpLinkSmart, line 552
	Map logData = [method: "tpLinkCheckForDevices"] // library marker davegut.appTpLinkSmart, line 553
	def checked = true // library marker davegut.appTpLinkSmart, line 554
	if (state.tpLinkChecked == true) { // library marker davegut.appTpLinkSmart, line 555
		checked = false // library marker davegut.appTpLinkSmart, line 556
		logData << [status: "noCheck", reason: "Completed within last 10 minutes"] // library marker davegut.appTpLinkSmart, line 557
	} else { // library marker davegut.appTpLinkSmart, line 558
		def findData = findTpLinkDevices("parseTpLinkCheck", timeout) // library marker davegut.appTpLinkSmart, line 559
		logData << [status: "checking"] // library marker davegut.appTpLinkSmart, line 560
		pauseExecution((timeout+2)*1000) // library marker davegut.appTpLinkSmart, line 561
	} // library marker davegut.appTpLinkSmart, line 562
	logDebug(logData) // library marker davegut.appTpLinkSmart, line 563
	return checked // library marker davegut.appTpLinkSmart, line 564
} // library marker davegut.appTpLinkSmart, line 565

def resetTpLinkChecked() { state.tpLinkChecked = false } // library marker davegut.appTpLinkSmart, line 567

def parseTpLinkCheck(response) { // library marker davegut.appTpLinkSmart, line 569
	List discData = [] // library marker davegut.appTpLinkSmart, line 570
	if (response instanceof Map) { // library marker davegut.appTpLinkSmart, line 571
		Map devdata = getDiscData(response) // library marker davegut.appTpLinkSmart, line 572
		if (devData.status != "INVALID") { // library marker davegut.appTpLinkSmart, line 573
			discData << devData // library marker davegut.appTpLinkSmart, line 574
		} // library marker davegut.appTpLinkSmart, line 575
	} else { // library marker davegut.appTpLinkSmart, line 576
		response.each { // library marker davegut.appTpLinkSmart, line 577
			Map devData = getDiscData(it) // library marker davegut.appTpLinkSmart, line 578
			if (devData.status == "OK") { // library marker davegut.appTpLinkSmart, line 579
				discData << devData // library marker davegut.appTpLinkSmart, line 580
			} // library marker davegut.appTpLinkSmart, line 581
		} // library marker davegut.appTpLinkSmart, line 582
	} // library marker davegut.appTpLinkSmart, line 583
	updateTpLinkDevices(discData) // library marker davegut.appTpLinkSmart, line 584
} // library marker davegut.appTpLinkSmart, line 585

def updateTpLinkDevices(discData) { // library marker davegut.appTpLinkSmart, line 587
	Map logData = [method: "updateTpLinkDevices"] // library marker davegut.appTpLinkSmart, line 588
	state.tpLinkChecked = true // library marker davegut.appTpLinkSmart, line 589
	runIn(570, resetTpLinkChecked) // library marker davegut.appTpLinkSmart, line 590
	List children = getChildDevices() // library marker davegut.appTpLinkSmart, line 591
	children.each { childDev -> // library marker davegut.appTpLinkSmart, line 592
		Map childData = [:] // library marker davegut.appTpLinkSmart, line 593
		def dni = childDev.deviceNetworkId // library marker davegut.appTpLinkSmart, line 594
		def connected = "false" // library marker davegut.appTpLinkSmart, line 595
		Map devData = discData.find{ it.dni == dni } // library marker davegut.appTpLinkSmart, line 596
		if (childDev.getDataValue("baseUrl")) { // library marker davegut.appTpLinkSmart, line 597
			if (devData != null) { // library marker davegut.appTpLinkSmart, line 598
				if (childDev.getDataValue("baseUrl") == devData.baseUrl && // library marker davegut.appTpLinkSmart, line 599
				    childDev.getDataValue("protocol") == devData.protocol) { // library marker davegut.appTpLinkSmart, line 600
					childData << [status: "noChanges"] // library marker davegut.appTpLinkSmart, line 601
				} else { // library marker davegut.appTpLinkSmart, line 602
					childDev.updateDataValue("baseUrl", devData.baseUrl) // library marker davegut.appTpLinkSmart, line 603
					childDev.updateDataValue("protocol", devData.protocol) // library marker davegut.appTpLinkSmart, line 604
					childData << ["baseUrl": devData.baseUrl, // library marker davegut.appTpLinkSmart, line 605
								  "protocol": devData.protocol, // library marker davegut.appTpLinkSmart, line 606
								  "connected": "true"] // library marker davegut.appTpLinkSmart, line 607
				} // library marker davegut.appTpLinkSmart, line 608
			} // library marker davegut.appTpLinkSmart, line 609
			pauseExecution(500) // library marker davegut.appTpLinkSmart, line 610
		} // library marker davegut.appTpLinkSmart, line 611
		logData << ["${childDev}": childData] // library marker davegut.appTpLinkSmart, line 612
	} // library marker davegut.appTpLinkSmart, line 613
	logDebug(logData) // library marker davegut.appTpLinkSmart, line 614
} // library marker davegut.appTpLinkSmart, line 615

// ~~~~~ end include (788) davegut.appTpLinkSmart ~~~~~

// ~~~~~ start include (799) davegut.tpLinkComms ~~~~~
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

//	===== Communications Methods ===== // library marker davegut.tpLinkComms, line 14
def asyncSend(cmdBody, reqData, action) { // library marker davegut.tpLinkComms, line 15
	Map cmdData = [cmdBody: cmdBody, reqData: reqData, action: action] // library marker davegut.tpLinkComms, line 16
	def protocol = getDataValue("protocol") // library marker davegut.tpLinkComms, line 17
	Map reqParams = [:] // library marker davegut.tpLinkComms, line 18
	if (protocol == "KLAP") { // library marker davegut.tpLinkComms, line 19
		reqParams = getKlapParams(cmdBody) // library marker davegut.tpLinkComms, line 20
	} else if (protocol == "camera") { // library marker davegut.tpLinkComms, line 21
		reqParams = getCameraParams(cmdBody, reqData) // library marker davegut.tpLinkComms, line 22
	} else if (protocol == "AES") { // library marker davegut.tpLinkComms, line 23
		reqParams = getAesParams(cmdBody) // library marker davegut.tpLinkComms, line 24
	} else if (protocol == "vacAes") { // library marker davegut.tpLinkComms, line 25
		reqParams = getVacAesParams(cmdBody, "${getDataValue("baseUrl")}/?token=${token}") // library marker davegut.tpLinkComms, line 26
	} // library marker davegut.tpLinkComms, line 27
	if (reqParams != [:]) { // library marker davegut.tpLinkComms, line 28
		if (state.errorCount == 0) { state.lastCommand = cmdData } // library marker davegut.tpLinkComms, line 29
		asynchttpPost(action, reqParams, [data: reqData]) // library marker davegut.tpLinkComms, line 30
		logDebug([method: "asyncSend", reqData: reqData]) // library marker davegut.tpLinkComms, line 31
	} else { // library marker davegut.tpLinkComms, line 32
		unknownProt(reqData) // library marker davegut.tpLinkComms, line 33
	} // library marker davegut.tpLinkComms, line 34
} // library marker davegut.tpLinkComms, line 35

def unknownProt(reqData) { // library marker davegut.tpLinkComms, line 37
	Map warnData = ["<b>UnknownProtocol</b>": [data: reqData, // library marker davegut.tpLinkComms, line 38
				    msg: "Device will not install or if installed will not work"]] // library marker davegut.tpLinkComms, line 39
	logWarn(warnData) // library marker davegut.tpLinkComms, line 40
} // library marker davegut.tpLinkComms, line 41

def parseData(resp, protocol = getDataValue("protocol"), data = null) { // library marker davegut.tpLinkComms, line 43
	Map logData = [method: "parseData", status: resp.status, protocol: protocol, // library marker davegut.tpLinkComms, line 44
				   sourceMethod: data.data] // library marker davegut.tpLinkComms, line 45
	def message = "OK" // library marker davegut.tpLinkComms, line 46
	if (resp.status == 200) { // library marker davegut.tpLinkComms, line 47
		if (protocol == "KLAP") { // library marker davegut.tpLinkComms, line 48
			logData << parseKlapData(resp, data) // library marker davegut.tpLinkComms, line 49
		} else if (protocol == "AES") { // library marker davegut.tpLinkComms, line 50
			logData << parseAesData(resp, data) // library marker davegut.tpLinkComms, line 51
		} else if (protocol == "vacAes") { // library marker davegut.tpLinkComms, line 52
			logData << parseVacAesData(resp, data) // library marker davegut.tpLinkComms, line 53
		} else if (protocol == "camera") { // library marker davegut.tpLinkComms, line 54
			logData << parseCameraData(resp, data) // library marker davegut.tpLinkComms, line 55
		} // library marker davegut.tpLinkComms, line 56
	} else { // library marker davegut.tpLinkComms, line 57
		message = resp.errorMessage // library marker davegut.tpLinkComms, line 58
		String userMessage = "unspecified" // library marker davegut.tpLinkComms, line 59
		if (resp.status == 403) { // library marker davegut.tpLinkComms, line 60
			userMessage = "<b>Try again. If error persists, check your credentials</b>" // library marker davegut.tpLinkComms, line 61
		} else if (resp.status == 408) { // library marker davegut.tpLinkComms, line 62
			userMessage = "<b>Your router connection to ${getDataValue("baseUrl")} failed.  Run Configure.</b>" // library marker davegut.tpLinkComms, line 63
		} else { // library marker davegut.tpLinkComms, line 64
			userMessage = "<b>Unhandled error Lan return</b>" // library marker davegut.tpLinkComms, line 65
		} // library marker davegut.tpLinkComms, line 66
		logData << [respMessage: message, userMessage: userMessage] // library marker davegut.tpLinkComms, line 67
		logDebug(logData) // library marker davegut.tpLinkComms, line 68
	} // library marker davegut.tpLinkComms, line 69
	handleCommsError(resp.status, message) // library marker davegut.tpLinkComms, line 70
	return logData // library marker davegut.tpLinkComms, line 71
} // library marker davegut.tpLinkComms, line 72

private sendFindCmd(ip, port, cmdData, action, commsTo = 5, ignore = false) { // library marker davegut.tpLinkComms, line 74
	def myHubAction = new hubitat.device.HubAction( // library marker davegut.tpLinkComms, line 75
		cmdData, // library marker davegut.tpLinkComms, line 76
		hubitat.device.Protocol.LAN, // library marker davegut.tpLinkComms, line 77
		[type: hubitat.device.HubAction.Type.LAN_TYPE_UDPCLIENT, // library marker davegut.tpLinkComms, line 78
		 destinationAddress: "${ip}:${port}", // library marker davegut.tpLinkComms, line 79
		 encoding: hubitat.device.HubAction.Encoding.HEX_STRING, // library marker davegut.tpLinkComms, line 80
		 ignoreResponse: ignore, // library marker davegut.tpLinkComms, line 81
		 parseWarning: true, // library marker davegut.tpLinkComms, line 82
		 timeout: commsTo, // library marker davegut.tpLinkComms, line 83
		 callback: action]) // library marker davegut.tpLinkComms, line 84
	try { // library marker davegut.tpLinkComms, line 85
		sendHubCommand(myHubAction) // library marker davegut.tpLinkComms, line 86
	} catch (error) { // library marker davegut.tpLinkComms, line 87
		logWarn("sendLanCmd: command to ${ip}:${port} failed. Error = ${error}") // library marker davegut.tpLinkComms, line 88
	} // library marker davegut.tpLinkComms, line 89
	return // library marker davegut.tpLinkComms, line 90
} // library marker davegut.tpLinkComms, line 91

//	Unknown Protocol method // library marker davegut.tpLinkComms, line 93
//	===== Communications Error Handling ===== // library marker davegut.tpLinkComms, line 94
def handleCommsError(status, msg = "") { // library marker davegut.tpLinkComms, line 95
	//	Retransmit all comms error except Switch and Level related (Hub retries for these). // library marker davegut.tpLinkComms, line 96
	//	This is determined by state.digital // library marker davegut.tpLinkComms, line 97
	if (status == 200) { // library marker davegut.tpLinkComms, line 98
		setCommsError(status, "OK") // library marker davegut.tpLinkComms, line 99
	} else { // library marker davegut.tpLinkComms, line 100
		Map logData = [method: "handleCommsError", status: code, msg: msg] // library marker davegut.tpLinkComms, line 101
		def count = state.errorCount + 1 // library marker davegut.tpLinkComms, line 102
		logData << [count: count, status: status, msg: msg] // library marker davegut.tpLinkComms, line 103
		switch(count) { // library marker davegut.tpLinkComms, line 104
			case 1: // library marker davegut.tpLinkComms, line 105
			case 2: // library marker davegut.tpLinkComms, line 106
				//	errors 1 and 2, retry immediately // library marker davegut.tpLinkComms, line 107
				runIn(1, delayedPassThrough) // library marker davegut.tpLinkComms, line 108
				break // library marker davegut.tpLinkComms, line 109
			case 3: // library marker davegut.tpLinkComms, line 110
				//	error 3, login or scan find device on the lan // library marker davegut.tpLinkComms, line 111
				//	then retry // library marker davegut.tpLinkComms, line 112
				if (status == 403) { // library marker davegut.tpLinkComms, line 113
					logData << [action: "attemptLogin"] // library marker davegut.tpLinkComms, line 114
//	await device handshake result???? // library marker davegut.tpLinkComms, line 115
					deviceHandshake() // library marker davegut.tpLinkComms, line 116
					runIn(4, delayedPassThrough) // library marker davegut.tpLinkComms, line 117
				} else { // library marker davegut.tpLinkComms, line 118
					logData << [action: "Find on LAN then login"] // library marker davegut.tpLinkComms, line 119
					configure() // library marker davegut.tpLinkComms, line 120
//	await configure result????? // library marker davegut.tpLinkComms, line 121
					runIn(10, delayedPassThrough) // library marker davegut.tpLinkComms, line 122
				} // library marker davegut.tpLinkComms, line 123
				break // library marker davegut.tpLinkComms, line 124
			case 4: // library marker davegut.tpLinkComms, line 125
				runIn(1, delayedPassThrough) // library marker davegut.tpLinkComms, line 126
				break // library marker davegut.tpLinkComms, line 127
			default: // library marker davegut.tpLinkComms, line 128
				//	Set comms error first time errros are 5 or more. // library marker davegut.tpLinkComms, line 129
				logData << [action: "SetCommsErrorTrue"] // library marker davegut.tpLinkComms, line 130
				setCommsError(status, msg, 5) // library marker davegut.tpLinkComms, line 131
		} // library marker davegut.tpLinkComms, line 132
		state.errorCount = count // library marker davegut.tpLinkComms, line 133
		logInfo(logData) // library marker davegut.tpLinkComms, line 134
	} // library marker davegut.tpLinkComms, line 135
} // library marker davegut.tpLinkComms, line 136

def delayedPassThrough() { // library marker davegut.tpLinkComms, line 138
	def cmdData = new JSONObject(state.lastCommand) // library marker davegut.tpLinkComms, line 139
	def cmdBody = parseJson(cmdData.cmdBody.toString()) // library marker davegut.tpLinkComms, line 140
	asyncSend(cmdBody, cmdData.reqData, cmdData.action) // library marker davegut.tpLinkComms, line 141
} // library marker davegut.tpLinkComms, line 142

def setCommsError(status, msg = "OK", count = state.commsError) { // library marker davegut.tpLinkComms, line 144
	Map logData = [method: "setCommsError", status: status, errorMsg: msg, count: count] // library marker davegut.tpLinkComms, line 145
	if (device && status == 200) { // library marker davegut.tpLinkComms, line 146
		state.errorCount = 0 // library marker davegut.tpLinkComms, line 147
		if (device.currentValue("commsError") == "true") { // library marker davegut.tpLinkComms, line 148
			sendEvent(name: "commsError", value: "false") // library marker davegut.tpLinkComms, line 149
			setPollInterval() // library marker davegut.tpLinkComms, line 150
			unschedule("errorConfigure") // library marker davegut.tpLinkComms, line 151
			logInfo(logData) // library marker davegut.tpLinkComms, line 152
		} // library marker davegut.tpLinkComms, line 153
	} else if (device) { // library marker davegut.tpLinkComms, line 154
		if (device.currentValue("commsError") == "false" && count > 4) { // library marker davegut.tpLinkComms, line 155
			updateAttr("commsError", "true") // library marker davegut.tpLinkComms, line 156
			setPollInterval("30 min") // library marker davegut.tpLinkComms, line 157
			runEvery10Minutes(errorConfigure) // library marker davegut.tpLinkComms, line 158
			logData << [pollInterval: "30 Min", errorConfigure: "ever 10 min"] // library marker davegut.tpLinkComms, line 159
			logWarn(logData) // library marker davegut.tpLinkComms, line 160
			if (status == 403) { // library marker davegut.tpLinkComms, line 161
				logWarn(logInErrorAction()) // library marker davegut.tpLinkComms, line 162
			} else { // library marker davegut.tpLinkComms, line 163
				logWarn(lanErrorAction()) // library marker davegut.tpLinkComms, line 164
			} // library marker davegut.tpLinkComms, line 165
		} else { // library marker davegut.tpLinkComms, line 166
			logData << [error: "Unspecified Error"] // library marker davegut.tpLinkComms, line 167
			logWarn(logData) // library marker davegut.tpLinkComms, line 168
		} // library marker davegut.tpLinkComms, line 169
	} // library marker davegut.tpLinkComms, line 170
} // library marker davegut.tpLinkComms, line 171

def errorConfigure() { // library marker davegut.tpLinkComms, line 173
	logDebug([method: "errorConfigure"]) // library marker davegut.tpLinkComms, line 174
	if (device.currentValue("commsError") == "true") { // library marker davegut.tpLinkComms, line 175
		configure() // library marker davegut.tpLinkComms, line 176
	} else { // library marker davegut.tpLinkComms, line 177
		unschedule("errorConfigure") // library marker davegut.tpLinkComms, line 178
	} // library marker davegut.tpLinkComms, line 179
} // library marker davegut.tpLinkComms, line 180

def lanErrorAction() { // library marker davegut.tpLinkComms, line 182
	def action = "Likely cause of this error is YOUR LAN device configuration: " // library marker davegut.tpLinkComms, line 183
	action += "a. VERIFY your device is on the DHCP list in your router, " // library marker davegut.tpLinkComms, line 184
	action += "b. VERIFY your device is in the active device list in your router, and " // library marker davegut.tpLinkComms, line 185
	action += "c. TRY controlling your device from the TAPO phone app." // library marker davegut.tpLinkComms, line 186
	return action // library marker davegut.tpLinkComms, line 187
} // library marker davegut.tpLinkComms, line 188

def logInErrorAction() { // library marker davegut.tpLinkComms, line 190
	def action = "Likely cause is your login credentials are incorrect or the login has expired. " // library marker davegut.tpLinkComms, line 191
	action += "a. RUN command Configure. b. If error persists, check your credentials in the App" // library marker davegut.tpLinkComms, line 192
	return action // library marker davegut.tpLinkComms, line 193
} // library marker davegut.tpLinkComms, line 194

// ~~~~~ end include (799) davegut.tpLinkComms ~~~~~

// ~~~~~ start include (800) davegut.tpLinkCrypto ~~~~~
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

//	===== Crypto Methods ===== // library marker davegut.tpLinkCrypto, line 17
def klapEncrypt(byte[] request, encKey, encIv, encSig, seqNo) { // library marker davegut.tpLinkCrypto, line 18
	byte[] encSeqNo = integerToByteArray(seqNo) // library marker davegut.tpLinkCrypto, line 19
	byte[] ivEnc = [encIv, encSeqNo].flatten() // library marker davegut.tpLinkCrypto, line 20
	def cipher = Cipher.getInstance("AES/CBC/PKCS5Padding") // library marker davegut.tpLinkCrypto, line 21
	SecretKeySpec key = new SecretKeySpec(encKey, "AES") // library marker davegut.tpLinkCrypto, line 22
	IvParameterSpec iv = new IvParameterSpec(ivEnc) // library marker davegut.tpLinkCrypto, line 23
	cipher.init(Cipher.ENCRYPT_MODE, key, iv) // library marker davegut.tpLinkCrypto, line 24
	byte[] cipherRequest = cipher.doFinal(request) // library marker davegut.tpLinkCrypto, line 25

	byte[] payload = [encSig, encSeqNo, cipherRequest].flatten() // library marker davegut.tpLinkCrypto, line 27
	byte[] signature = mdEncode("SHA-256", payload) // library marker davegut.tpLinkCrypto, line 28
	cipherRequest = [signature, cipherRequest].flatten() // library marker davegut.tpLinkCrypto, line 29
	return [cipherData: cipherRequest, seqNumber: seqNo] // library marker davegut.tpLinkCrypto, line 30
} // library marker davegut.tpLinkCrypto, line 31

def klapDecrypt(cipherResponse, encKey, encIv, seqNo) { // library marker davegut.tpLinkCrypto, line 33
	byte[] encSeqNo = integerToByteArray(seqNo) // library marker davegut.tpLinkCrypto, line 34
	byte[] ivEnc = [encIv, encSeqNo].flatten() // library marker davegut.tpLinkCrypto, line 35
	def cipher = Cipher.getInstance("AES/CBC/PKCS5Padding") // library marker davegut.tpLinkCrypto, line 36
    SecretKeySpec key = new SecretKeySpec(encKey, "AES") // library marker davegut.tpLinkCrypto, line 37
	IvParameterSpec iv = new IvParameterSpec(ivEnc) // library marker davegut.tpLinkCrypto, line 38
    cipher.init(Cipher.DECRYPT_MODE, key, iv) // library marker davegut.tpLinkCrypto, line 39
	byte[] byteResponse = cipher.doFinal(cipherResponse) // library marker davegut.tpLinkCrypto, line 40
	return new String(byteResponse, "UTF-8") // library marker davegut.tpLinkCrypto, line 41
} // library marker davegut.tpLinkCrypto, line 42

def aesEncrypt(request, encKey, encIv) { // library marker davegut.tpLinkCrypto, line 44
	def cipher = Cipher.getInstance("AES/CBC/PKCS5Padding") // library marker davegut.tpLinkCrypto, line 45
	SecretKeySpec key = new SecretKeySpec(encKey, "AES") // library marker davegut.tpLinkCrypto, line 46
	IvParameterSpec iv = new IvParameterSpec(encIv) // library marker davegut.tpLinkCrypto, line 47
	cipher.init(Cipher.ENCRYPT_MODE, key, iv) // library marker davegut.tpLinkCrypto, line 48
	String result = cipher.doFinal(request.getBytes("UTF-8")).encodeBase64().toString() // library marker davegut.tpLinkCrypto, line 49
	return result.replace("\r\n","") // library marker davegut.tpLinkCrypto, line 50
} // library marker davegut.tpLinkCrypto, line 51

def aesDecrypt(cipherResponse, encKey, encIv) { // library marker davegut.tpLinkCrypto, line 53
    byte[] decodedBytes = cipherResponse.decodeBase64() // library marker davegut.tpLinkCrypto, line 54
	def cipher = Cipher.getInstance("AES/CBC/PKCS5Padding") // library marker davegut.tpLinkCrypto, line 55
    SecretKeySpec key = new SecretKeySpec(encKey, "AES") // library marker davegut.tpLinkCrypto, line 56
	IvParameterSpec iv = new IvParameterSpec(encIv) // library marker davegut.tpLinkCrypto, line 57
    cipher.init(Cipher.DECRYPT_MODE, key, iv) // library marker davegut.tpLinkCrypto, line 58
	return new String(cipher.doFinal(decodedBytes), "UTF-8") // library marker davegut.tpLinkCrypto, line 59
} // library marker davegut.tpLinkCrypto, line 60

//	===== Encoding Methods ===== // library marker davegut.tpLinkCrypto, line 62
def mdEncode(hashMethod, byte[] data) { // library marker davegut.tpLinkCrypto, line 63
	MessageDigest md = MessageDigest.getInstance(hashMethod) // library marker davegut.tpLinkCrypto, line 64
	md.update(data) // library marker davegut.tpLinkCrypto, line 65
	return md.digest() // library marker davegut.tpLinkCrypto, line 66
} // library marker davegut.tpLinkCrypto, line 67

String encodeUtf8(String message) { // library marker davegut.tpLinkCrypto, line 69
	byte[] arr = message.getBytes("UTF8") // library marker davegut.tpLinkCrypto, line 70
	return new String(arr) // library marker davegut.tpLinkCrypto, line 71
} // library marker davegut.tpLinkCrypto, line 72

int byteArrayToInteger(byte[] byteArr) { // library marker davegut.tpLinkCrypto, line 74
	int arrayASInteger // library marker davegut.tpLinkCrypto, line 75
	try { // library marker davegut.tpLinkCrypto, line 76
		arrayAsInteger = ((byteArr[0] & 0xFF) << 24) + ((byteArr[1] & 0xFF) << 16) + // library marker davegut.tpLinkCrypto, line 77
			((byteArr[2] & 0xFF) << 8) + (byteArr[3] & 0xFF) // library marker davegut.tpLinkCrypto, line 78
	} catch (error) { // library marker davegut.tpLinkCrypto, line 79
		Map errLog = [byteArr: byteArr, ERROR: error] // library marker davegut.tpLinkCrypto, line 80
		logWarn("byteArrayToInteger: ${errLog}") // library marker davegut.tpLinkCrypto, line 81
	} // library marker davegut.tpLinkCrypto, line 82
	return arrayAsInteger // library marker davegut.tpLinkCrypto, line 83
} // library marker davegut.tpLinkCrypto, line 84

byte[] integerToByteArray(value) { // library marker davegut.tpLinkCrypto, line 86
	String hexValue = hubitat.helper.HexUtils.integerToHexString(value, 4) // library marker davegut.tpLinkCrypto, line 87
	byte[] byteValue = hubitat.helper.HexUtils.hexStringToByteArray(hexValue) // library marker davegut.tpLinkCrypto, line 88
	return byteValue // library marker davegut.tpLinkCrypto, line 89
} // library marker davegut.tpLinkCrypto, line 90

def getSeed(size) { // library marker davegut.tpLinkCrypto, line 92
	byte[] temp = new byte[size] // library marker davegut.tpLinkCrypto, line 93
	new Random().nextBytes(temp) // library marker davegut.tpLinkCrypto, line 94
	return temp // library marker davegut.tpLinkCrypto, line 95
} // library marker davegut.tpLinkCrypto, line 96

// ~~~~~ end include (800) davegut.tpLinkCrypto ~~~~~

// ~~~~~ start include (802) davegut.tpLinkTransAes ~~~~~
library ( // library marker davegut.tpLinkTransAes, line 1
	name: "tpLinkTransAes", // library marker davegut.tpLinkTransAes, line 2
	namespace: "davegut", // library marker davegut.tpLinkTransAes, line 3
	author: "Compiled by Dave Gutheinz", // library marker davegut.tpLinkTransAes, line 4
	description: "Handshake methods for TP-Link Integration", // library marker davegut.tpLinkTransAes, line 5
	category: "utilities", // library marker davegut.tpLinkTransAes, line 6
	documentationLink: "" // library marker davegut.tpLinkTransAes, line 7
) // library marker davegut.tpLinkTransAes, line 8

def aesHandshake(baseUrl = getDataValue("baseUrl"), devData = null) { // library marker davegut.tpLinkTransAes, line 10
	Map reqData = [baseUrl: baseUrl, devData: devData] // library marker davegut.tpLinkTransAes, line 11
	Map rsaKey = getRsaKey() // library marker davegut.tpLinkTransAes, line 12
	def pubPem = "-----BEGIN PUBLIC KEY-----\n${rsaKey.public}-----END PUBLIC KEY-----\n" // library marker davegut.tpLinkTransAes, line 13
	Map cmdBody = [ method: "handshake", params: [ key: pubPem]] // library marker davegut.tpLinkTransAes, line 14
	Map reqParams = [uri: baseUrl, // library marker davegut.tpLinkTransAes, line 15
					 body: new groovy.json.JsonBuilder(cmdBody).toString(), // library marker davegut.tpLinkTransAes, line 16
					 requestContentType: "application/json", // library marker davegut.tpLinkTransAes, line 17
					 timeout: 10] // library marker davegut.tpLinkTransAes, line 18
	asynchttpPost("parseAesHandshake", reqParams, [data: reqData]) // library marker davegut.tpLinkTransAes, line 19
} // library marker davegut.tpLinkTransAes, line 20

def parseAesHandshake(resp, data){ // library marker davegut.tpLinkTransAes, line 22
	Map logData = [method: "parseAesHandshake"] // library marker davegut.tpLinkTransAes, line 23
	if (resp.status == 200 && resp.data != null) { // library marker davegut.tpLinkTransAes, line 24
		try { // library marker davegut.tpLinkTransAes, line 25
			Map reqData = [devData: data.data.devData, baseUrl: data.data.baseUrl] // library marker davegut.tpLinkTransAes, line 26
			Map cmdResp =  new JsonSlurper().parseText(resp.data) // library marker davegut.tpLinkTransAes, line 27
			//	cookie // library marker davegut.tpLinkTransAes, line 28
			def cookieHeader = resp.headers["Set-Cookie"].toString() // library marker davegut.tpLinkTransAes, line 29
			def cookie = cookieHeader.substring(cookieHeader.indexOf(":") +1, cookieHeader.indexOf(";")) // library marker davegut.tpLinkTransAes, line 30
			//	keys // library marker davegut.tpLinkTransAes, line 31
			byte[] privateKeyBytes = getRsaKey().private.decodeBase64() // library marker davegut.tpLinkTransAes, line 32
			byte[] deviceKeyBytes = cmdResp.result.key.getBytes("UTF-8").decodeBase64() // library marker davegut.tpLinkTransAes, line 33
    		Cipher instance = Cipher.getInstance("RSA/ECB/PKCS1Padding") // library marker davegut.tpLinkTransAes, line 34
			instance.init(2, KeyFactory.getInstance("RSA").generatePrivate(new PKCS8EncodedKeySpec(privateKeyBytes))) // library marker davegut.tpLinkTransAes, line 35
			byte[] cryptoArray = instance.doFinal(deviceKeyBytes) // library marker davegut.tpLinkTransAes, line 36
			byte[] encKey = cryptoArray[0..15] // library marker davegut.tpLinkTransAes, line 37
			byte[] encIv = cryptoArray[16..31] // library marker davegut.tpLinkTransAes, line 38
			logData << [respStatus: "Cookies/Keys Updated", cookie: cookie, // library marker davegut.tpLinkTransAes, line 39
						encKey: encKey, encIv: encIv] // library marker davegut.tpLinkTransAes, line 40
			String password = encPassword // library marker davegut.tpLinkTransAes, line 41
			String username = encUsername // library marker davegut.tpLinkTransAes, line 42
			if (device) { // library marker davegut.tpLinkTransAes, line 43
				password = parent.encPassword // library marker davegut.tpLinkTransAes, line 44
				username = parent.encUsername // library marker davegut.tpLinkTransAes, line 45
				device.updateSetting("cookie",[type:"password", value: cookie]) // library marker davegut.tpLinkTransAes, line 46
				device.updateSetting("encKey",[type:"password", value: encKey]) // library marker davegut.tpLinkTransAes, line 47
				device.updateSetting("encIv",[type:"password", value: encIv]) // library marker davegut.tpLinkTransAes, line 48
			} else { // library marker davegut.tpLinkTransAes, line 49
				reqData << [cookie: cookie, encIv: encIv, encKey: encKey] // library marker davegut.tpLinkTransAes, line 50
			} // library marker davegut.tpLinkTransAes, line 51
			Map cmdBody = [method: "login_device", // library marker davegut.tpLinkTransAes, line 52
						   params: [password: password, // library marker davegut.tpLinkTransAes, line 53
									username: username], // library marker davegut.tpLinkTransAes, line 54
						   requestTimeMils: 0] // library marker davegut.tpLinkTransAes, line 55
			def cmdStr = JsonOutput.toJson(cmdBody).toString() // library marker davegut.tpLinkTransAes, line 56
			Map reqBody = [method: "securePassthrough", // library marker davegut.tpLinkTransAes, line 57
						   params: [request: aesEncrypt(cmdStr, encKey, encIv)]] // library marker davegut.tpLinkTransAes, line 58
			Map reqParams = [uri: reqData.baseUrl, // library marker davegut.tpLinkTransAes, line 59
							  body: reqBody, // library marker davegut.tpLinkTransAes, line 60
							  timeout:10,  // library marker davegut.tpLinkTransAes, line 61
							  headers: ["Cookie": cookie], // library marker davegut.tpLinkTransAes, line 62
							  contentType: "application/json", // library marker davegut.tpLinkTransAes, line 63
							  requestContentType: "application/json"] // library marker davegut.tpLinkTransAes, line 64
			asynchttpPost("parseAesLogin", reqParams, [data: reqData]) // library marker davegut.tpLinkTransAes, line 65
			logDebug(logData) // library marker davegut.tpLinkTransAes, line 66
		} catch (err) { // library marker davegut.tpLinkTransAes, line 67
			logData << [respStatus: "ERROR parsing HTTP resp.data", // library marker davegut.tpLinkTransAes, line 68
						respData: resp.data, error: err] // library marker davegut.tpLinkTransAes, line 69
			logWarn(logData) // library marker davegut.tpLinkTransAes, line 70
		} // library marker davegut.tpLinkTransAes, line 71
	} else { // library marker davegut.tpLinkTransAes, line 72
		logData << [respStatus: "ERROR in HTTP response", resp: resp.properties] // library marker davegut.tpLinkTransAes, line 73
		logWarn(logData) // library marker davegut.tpLinkTransAes, line 74
	} // library marker davegut.tpLinkTransAes, line 75
} // library marker davegut.tpLinkTransAes, line 76

def parseAesLogin(resp, data) { // library marker davegut.tpLinkTransAes, line 78
	if (device) { // library marker davegut.tpLinkTransAes, line 79
		Map logData = [method: "parseAesLogin"] // library marker davegut.tpLinkTransAes, line 80
		if (resp.status == 200) { // library marker davegut.tpLinkTransAes, line 81
			if (resp.json.error_code == 0) { // library marker davegut.tpLinkTransAes, line 82
				try { // library marker davegut.tpLinkTransAes, line 83
					byte[] encKey = new JsonSlurper().parseText(encKey) // library marker davegut.tpLinkTransAes, line 84
					byte[] encIv = new JsonSlurper().parseText(encIv) // library marker davegut.tpLinkTransAes, line 85
					def clearResp = aesDecrypt(resp.json.result.response, encKey, encIv) // library marker davegut.tpLinkTransAes, line 86
					Map cmdResp = new JsonSlurper().parseText(clearResp) // library marker davegut.tpLinkTransAes, line 87
					if (cmdResp.error_code == 0) { // library marker davegut.tpLinkTransAes, line 88
						def token = cmdResp.result.token // library marker davegut.tpLinkTransAes, line 89
						logData << [respStatus: "OK", token: token] // library marker davegut.tpLinkTransAes, line 90
						device.updateSetting("token",[type:"password", value: token]) // library marker davegut.tpLinkTransAes, line 91
						setCommsError(200) // library marker davegut.tpLinkTransAes, line 92
						logDebug(logData) // library marker davegut.tpLinkTransAes, line 93
					} else { // library marker davegut.tpLinkTransAes, line 94
						logData << [respStatus: "ERROR code in cmdResp",  // library marker davegut.tpLinkTransAes, line 95
									error_code: cmdResp.error_code, // library marker davegut.tpLinkTransAes, line 96
									check: "cryptoArray, credentials", data: cmdResp] // library marker davegut.tpLinkTransAes, line 97
						logInfo(logData) // library marker davegut.tpLinkTransAes, line 98
					} // library marker davegut.tpLinkTransAes, line 99
				} catch (err) { // library marker davegut.tpLinkTransAes, line 100
					logData << [respStatus: "ERROR parsing respJson", respJson: resp.json, // library marker davegut.tpLinkTransAes, line 101
								error: err] // library marker davegut.tpLinkTransAes, line 102
					logInfo(logData) // library marker davegut.tpLinkTransAes, line 103
				} // library marker davegut.tpLinkTransAes, line 104
			} else { // library marker davegut.tpLinkTransAes, line 105
				logData << [respStatus: "ERROR code in resp.json", errorCode: resp.json.error_code, // library marker davegut.tpLinkTransAes, line 106
							respJson: resp.json] // library marker davegut.tpLinkTransAes, line 107
				logInfo(logData) // library marker davegut.tpLinkTransAes, line 108
			} // library marker davegut.tpLinkTransAes, line 109
		} else { // library marker davegut.tpLinkTransAes, line 110
			logData << [respStatus: "ERROR in HTTP response", respStatus: resp.status, data: resp.properties] // library marker davegut.tpLinkTransAes, line 111
			logInfo(logData) // library marker davegut.tpLinkTransAes, line 112
		} // library marker davegut.tpLinkTransAes, line 113
	} else { // library marker davegut.tpLinkTransAes, line 114
		//	Code used in application only. // library marker davegut.tpLinkTransAes, line 115
		getAesToken(resp, data.data) // library marker davegut.tpLinkTransAes, line 116
	} // library marker davegut.tpLinkTransAes, line 117
} // library marker davegut.tpLinkTransAes, line 118

def getAesParams(cmdBody) { // library marker davegut.tpLinkTransAes, line 120
	byte[] encKey = new JsonSlurper().parseText(encKey) // library marker davegut.tpLinkTransAes, line 121
	byte[] encIv = new JsonSlurper().parseText(encIv) // library marker davegut.tpLinkTransAes, line 122
	def cmdStr = JsonOutput.toJson(cmdBody).toString() // library marker davegut.tpLinkTransAes, line 123
	Map reqBody = [method: "securePassthrough", // library marker davegut.tpLinkTransAes, line 124
				   params: [request: aesEncrypt(cmdStr, encKey, encIv)]] // library marker davegut.tpLinkTransAes, line 125
	Map reqParams = [uri: "${getDataValue("baseUrl")}?token=${token}", // library marker davegut.tpLinkTransAes, line 126
					 body: new groovy.json.JsonBuilder(reqBody).toString(), // library marker davegut.tpLinkTransAes, line 127
					 contentType: "application/json", // library marker davegut.tpLinkTransAes, line 128
					 requestContentType: "application/json", // library marker davegut.tpLinkTransAes, line 129
					 timeout: 10, // library marker davegut.tpLinkTransAes, line 130
					 ignoreSSLIssues: true, // library marker davegut.tpLinkTransAes, line 131
					 headers: ["Cookie": cookie]] // library marker davegut.tpLinkTransAes, line 132
	return reqParams // library marker davegut.tpLinkTransAes, line 133
} // library marker davegut.tpLinkTransAes, line 134

def parseAesData(resp, data) { // library marker davegut.tpLinkTransAes, line 136
	Map parseData = [parseMethod: "parseAesData", sourceMethod: data.data] // library marker davegut.tpLinkTransAes, line 137
	try { // library marker davegut.tpLinkTransAes, line 138
		byte[] encKey = new JsonSlurper().parseText(encKey) // library marker davegut.tpLinkTransAes, line 139
		byte[] encIv = new JsonSlurper().parseText(encIv) // library marker davegut.tpLinkTransAes, line 140
		Map cmdResp = new JsonSlurper().parseText(aesDecrypt(resp.json.result.response, // library marker davegut.tpLinkTransAes, line 141
														 encKey, encIv)) // library marker davegut.tpLinkTransAes, line 142
		parseData << [cryptoStatus: "OK", cmdResp: cmdResp] // library marker davegut.tpLinkTransAes, line 143
	} catch (err) { // library marker davegut.tpLinkTransAes, line 144
		parseData << [cryptoStatus: "decryptDataError", error: err, dataLength: resp.data.length()] // library marker davegut.tpLinkTransAes, line 145
	} // library marker davegut.tpLinkTransAes, line 146
	return parseData // library marker davegut.tpLinkTransAes, line 147
} // library marker davegut.tpLinkTransAes, line 148

def getRsaKey() { // library marker davegut.tpLinkTransAes, line 150
	return [public: "MIGfMA0GCSqGSIb3DQEBAQUAA4GNADCBiQKBgQDGr/mHBK8aqx7UAS+g+TuAvE3J2DdwsqRn9MmAkjPGNon1ZlwM6nLQHfJHebdohyVqkNWaCECGXnftnlC8CM2c/RujvCrStRA0lVD+jixO9QJ9PcYTa07Z1FuEze7Q5OIa6pEoPxomrjxzVlUWLDXt901qCdn3/zRZpBdpXzVZtQIDAQAB", // library marker davegut.tpLinkTransAes, line 151
			private: "MIICeAIBADANBgkqhkiG9w0BAQEFAASCAmIwggJeAgEAAoGBAMav+YcErxqrHtQBL6D5O4C8TcnYN3CypGf0yYCSM8Y2ifVmXAzqctAd8kd5t2iHJWqQ1ZoIQIZed+2eULwIzZz9G6O8KtK1EDSVUP6OLE71An09xhNrTtnUW4TN7tDk4hrqkSg/GiauPHNWVRYsNe33TWoJ2ff/NFmkF2lfNVm1AgMBAAECgYEAocxCHmKBGe2KAEkq+SKdAxvVGO77TsobOhDMWug0Q1C8jduaUGZHsxT/7JbA9d1AagSh/XqE2Sdq8FUBF+7vSFzozBHyGkrX1iKURpQFEQM2j9JgUCucEavnxvCqDYpscyNRAgqz9jdh+BjEMcKAG7o68bOw41ZC+JyYR41xSe0CQQD1os71NcZiMVqYcBud6fTYFHZz3HBNcbzOk+RpIHyi8aF3zIqPKIAh2pO4s7vJgrMZTc2wkIe0ZnUrm0oaC//jAkEAzxIPW1mWd3+KE3gpgyX0cFkZsDmlIbWojUIbyz8NgeUglr+BczARG4ITrTV4fxkGwNI4EZxBT8vXDSIXJ8NDhwJBAIiKndx0rfg7Uw7VkqRvPqk2hrnU2aBTDw8N6rP9WQsCoi0DyCnX65Hl/KN5VXOocYIpW6NAVA8VvSAmTES6Ut0CQQCX20jD13mPfUsHaDIZafZPhiheoofFpvFLVtYHQeBoCF7T7vHCRdfl8oj3l6UcoH/hXMmdsJf9KyI1EXElyf91AkAvLfmAS2UvUnhX4qyFioitjxwWawSnf+CewN8LDbH7m5JVXJEh3hqp+aLHg1EaW4wJtkoKLCF+DeVIgbSvOLJw"] // library marker davegut.tpLinkTransAes, line 152
} // library marker davegut.tpLinkTransAes, line 153

// ~~~~~ end include (802) davegut.tpLinkTransAes ~~~~~

// ~~~~~ start include (791) davegut.tpLinkCamTransport ~~~~~
library ( // library marker davegut.tpLinkCamTransport, line 1
	name: "tpLinkCamTransport", // library marker davegut.tpLinkCamTransport, line 2
	namespace: "davegut", // library marker davegut.tpLinkCamTransport, line 3
	author: "Compiled by Dave Gutheinz", // library marker davegut.tpLinkCamTransport, line 4
	description: "TP-Link Camera Protocol Implementation", // library marker davegut.tpLinkCamTransport, line 5
	category: "utilities", // library marker davegut.tpLinkCamTransport, line 6
	documentationLink: "" // library marker davegut.tpLinkCamTransport, line 7
) // library marker davegut.tpLinkCamTransport, line 8
import java.util.Random // library marker davegut.tpLinkCamTransport, line 9
import java.security.MessageDigest // library marker davegut.tpLinkCamTransport, line 10
import java.security.spec.PKCS8EncodedKeySpec // library marker davegut.tpLinkCamTransport, line 11
import javax.crypto.Cipher // library marker davegut.tpLinkCamTransport, line 12
import java.security.KeyFactory // library marker davegut.tpLinkCamTransport, line 13
import javax.crypto.spec.SecretKeySpec // library marker davegut.tpLinkCamTransport, line 14
import javax.crypto.spec.IvParameterSpec // library marker davegut.tpLinkCamTransport, line 15

def cameraHandshake(hsInput, devData = [:]) { // library marker davegut.tpLinkCamTransport, line 17
//log.debug devData // library marker davegut.tpLinkCamTransport, line 18
	Map logData = [url: hsInput.url] // library marker davegut.tpLinkCamTransport, line 19
	if (devData != [:]) { // library marker davegut.tpLinkCamTransport, line 20
		logData << [model: devData.model, ip: devData.ip] // library marker davegut.tpLinkCamTransport, line 21
	}			 // library marker davegut.tpLinkCamTransport, line 22
	def warn = true // library marker davegut.tpLinkCamTransport, line 23
//	Step 1.	Determine secureUser (userName or "admin"). // library marker davegut.tpLinkCamTransport, line 24
	def isSecure = state.isSecure // library marker davegut.tpLinkCamTransport, line 25
	if (isSecure != true) { // library marker davegut.tpLinkCamTransport, line 26
		Map secResp = checkSecure(hsInput) // library marker davegut.tpLinkCamTransport, line 27
		isSecure = secResp.secure // library marker davegut.tpLinkCamTransport, line 28
//		isSecure = checkSecure(hsInput) // library marker davegut.tpLinkCamTransport, line 29
		state.isSecure = isSecure // library marker davegut.tpLinkCamTransport, line 30
		logData << [checkSecure: secResp] // library marker davegut.tpLinkCamTransport, line 31
	} // library marker davegut.tpLinkCamTransport, line 32
	if (isSecure == true) { // library marker davegut.tpLinkCamTransport, line 33
//	Step 2.	Confirm Device. // library marker davegut.tpLinkCamTransport, line 34
		Map confData = confirmDevice(hsInput) // library marker davegut.tpLinkCamTransport, line 35
		logData << [confirmDevice: confData] // library marker davegut.tpLinkCamTransport, line 36
		if (confData.confirmed == true) { // library marker davegut.tpLinkCamTransport, line 37
//	Step 3.	Get token data. // library marker davegut.tpLinkCamTransport, line 38
			Map tokenData = getToken(hsInput, confData) // library marker davegut.tpLinkCamTransport, line 39
			logData << [tokenData: tokenData] // library marker davegut.tpLinkCamTransport, line 40
			if (tokenData.status == "OK") { // library marker davegut.tpLinkCamTransport, line 41
//	Step 4. next action. // library marker davegut.tpLinkCamTransport, line 42
				if (app) { // library marker davegut.tpLinkCamTransport, line 43
					camCmdIn = [lsk: tokenData.lsk, ivb: tokenData.ivb,  // library marker davegut.tpLinkCamTransport, line 44
								seqNo: tokenData.seqNo, apiUrl: tokenData.apiUrl, // library marker davegut.tpLinkCamTransport, line 45
								encPwd: hsInput.pwd, cnonce: confData.cnonce] // library marker davegut.tpLinkCamTransport, line 46
					sendCameraDataCmd(devData, camCmdIn) // library marker davegut.tpLinkCamTransport, line 47
				} else if (device) { // library marker davegut.tpLinkCamTransport, line 48
					device.updateSetting("nonce", [type:"password", value: confData.nonce]) // library marker davegut.tpLinkCamTransport, line 49
					device.updateSetting("cnonce", [type:"password", value: confData.cnonce]) // library marker davegut.tpLinkCamTransport, line 50
					device.updateSetting("lsk",[type:"password", value: tokenData.lsk]) // library marker davegut.tpLinkCamTransport, line 51
					device.updateSetting("ivb",[type:"password", value: tokenData.ivb]) // library marker davegut.tpLinkCamTransport, line 52
					device.updateSetting("encPwd",[type:"password", value: hsInput.encPwd]) // library marker davegut.tpLinkCamTransport, line 53
					device.updateSetting("apiUrl",[type:"password", value: tokenData.apiUrl]) // library marker davegut.tpLinkCamTransport, line 54
					state.seqNo = tokenData.seqNo // library marker davegut.tpLinkCamTransport, line 55
				} // library marker davegut.tpLinkCamTransport, line 56
				warn = false // library marker davegut.tpLinkCamTransport, line 57
			} // library marker davegut.tpLinkCamTransport, line 58
		} // library marker davegut.tpLinkCamTransport, line 59
	} // library marker davegut.tpLinkCamTransport, line 60
	if (warn == true) { // library marker davegut.tpLinkCamTransport, line 61
		logWarn([cameraHandshake: logData]) // library marker davegut.tpLinkCamTransport, line 62
	} // library marker davegut.tpLinkCamTransport, line 63
	else { // library marker davegut.tpLinkCamTransport, line 64
		logDebug([cameraHandshake: logData]) // library marker davegut.tpLinkCamTransport, line 65
	} // library marker davegut.tpLinkCamTransport, line 66
	return logData // library marker davegut.tpLinkCamTransport, line 67
} // library marker davegut.tpLinkCamTransport, line 68

def checkSecure(hsInput) { // library marker davegut.tpLinkCamTransport, line 70
	Map secResp = [:] // library marker davegut.tpLinkCamTransport, line 71
	secure = false // library marker davegut.tpLinkCamTransport, line 72
	Map cmdBody = [method: "login", params: [encrypt_type: "3",  username: hsInput.user]] // library marker davegut.tpLinkCamTransport, line 73
	Map respData = postSync(cmdBody, hsInput.url) // library marker davegut.tpLinkCamTransport, line 74
	if (respData.error_code == -40413 && respData.result && respData.result.data // library marker davegut.tpLinkCamTransport, line 75
		&& respData.result.data.encrypt_type.contains("3")) { // library marker davegut.tpLinkCamTransport, line 76
		secure = true // library marker davegut.tpLinkCamTransport, line 77
	} else { // library marker davegut.tpLinkCamTransport, line 78
		secResp << [invalidUser: getNote("checkUser")] // library marker davegut.tpLinkCamTransport, line 79
	} // library marker davegut.tpLinkCamTransport, line 80
	secResp << [secure: secure] // library marker davegut.tpLinkCamTransport, line 81
	return secResp // library marker davegut.tpLinkCamTransport, line 82
} // library marker davegut.tpLinkCamTransport, line 83

def confirmDevice(hsInput) { // library marker davegut.tpLinkCamTransport, line 85
	String cnonce = getSeed(8).encodeHex().toString().toUpperCase() // library marker davegut.tpLinkCamTransport, line 86
	Map cmdBody = [method: "login", // library marker davegut.tpLinkCamTransport, line 87
				   params: [cnonce: cnonce, encrypt_type: "3",  username: hsInput.user]] // library marker davegut.tpLinkCamTransport, line 88
	Map respData = postSync(cmdBody, hsInput.url) // library marker davegut.tpLinkCamTransport, line 89
	def confirmed = false // library marker davegut.tpLinkCamTransport, line 90
	Map confData = [:] // library marker davegut.tpLinkCamTransport, line 91
	if (respData.result) { // library marker davegut.tpLinkCamTransport, line 92
		Map results = respData.result.data // library marker davegut.tpLinkCamTransport, line 93
		if (respData.error_code == -40413 && results.code == -40401 &&  // library marker davegut.tpLinkCamTransport, line 94
			results.encrypt_type.toString().contains("3")) { // library marker davegut.tpLinkCamTransport, line 95
			String noncesPwdHash = cnonce + hsInput.pwd + results.nonce // library marker davegut.tpLinkCamTransport, line 96
			String testHash = mdEncode("SHA-256", noncesPwdHash.getBytes()).encodeHex().toString().toUpperCase() // library marker davegut.tpLinkCamTransport, line 97
			String checkData = testHash + results.nonce + cnonce // library marker davegut.tpLinkCamTransport, line 98
			if (checkData == results.device_confirm) { // library marker davegut.tpLinkCamTransport, line 99
				confData << [nonce: results.nonce, cnonce: cnonce] // library marker davegut.tpLinkCamTransport, line 100
				confirmed = true // library marker davegut.tpLinkCamTransport, line 101
			} else { // library marker davegut.tpLinkCamTransport, line 102
				confData << [error: "checkData and deviceData mismatch"] // library marker davegut.tpLinkCamTransport, line 103
			} // library marker davegut.tpLinkCamTransport, line 104
		} else { // library marker davegut.tpLinkCamTransport, line 105
			confData << [error_code: respData.error_code, code: results.code, // library marker davegut.tpLinkCamTransport, line 106
						 encrypt_type: results.encrypt_type, error: "invalid data to continue"] // library marker davegut.tpLinkCamTransport, line 107
		} // library marker davegut.tpLinkCamTransport, line 108
	} else { // library marker davegut.tpLinkCamTransport, line 109
		confData << [respData: respData, error: "no respData.results in return."] // library marker davegut.tpLinkCamTransport, line 110
	} // library marker davegut.tpLinkCamTransport, line 111
	confData << [confirmed: confirmed] // library marker davegut.tpLinkCamTransport, line 112
	if (confirmed == false) { // library marker davegut.tpLinkCamTransport, line 113
		confData << [checkPwd: getNote("checkPwd"), thirdParty: getNote("thirdParty")] // library marker davegut.tpLinkCamTransport, line 114
	} // library marker davegut.tpLinkCamTransport, line 115
return confData // library marker davegut.tpLinkCamTransport, line 116
} // library marker davegut.tpLinkCamTransport, line 117

def getToken(hsInput, confData) { // library marker davegut.tpLinkCamTransport, line 119
	Map tokenData = [:] // library marker davegut.tpLinkCamTransport, line 120
	String digestPwdHex = hsInput.pwd + confData.cnonce + confData.nonce // library marker davegut.tpLinkCamTransport, line 121
	String digestPwd = mdEncode("SHA-256", digestPwdHex.getBytes()).encodeHex().toString().toUpperCase() // library marker davegut.tpLinkCamTransport, line 122
	String fullDigestPwdHex = digestPwd + confData.cnonce + confData.nonce // library marker davegut.tpLinkCamTransport, line 123
	String fullDigestPwd = new String(fullDigestPwdHex.getBytes(), "UTF-8") // library marker davegut.tpLinkCamTransport, line 124
	Map cmdBody = [ // library marker davegut.tpLinkCamTransport, line 125
		method: "login", // library marker davegut.tpLinkCamTransport, line 126
		params: [cnonce: confData.cnonce,  // library marker davegut.tpLinkCamTransport, line 127
				 encrypt_type: "3", // library marker davegut.tpLinkCamTransport, line 128
				 digest_passwd: fullDigestPwd, // library marker davegut.tpLinkCamTransport, line 129
				 username: hsInput.user // library marker davegut.tpLinkCamTransport, line 130
			]] // library marker davegut.tpLinkCamTransport, line 131
	Map respData = postSync(cmdBody, hsInput.url) // library marker davegut.tpLinkCamTransport, line 132
	Map logData = [errorCode: respData.error_code] // library marker davegut.tpLinkCamTransport, line 133
	if (respData.error_code == 0) { // library marker davegut.tpLinkCamTransport, line 134
		Map result = respData.result // library marker davegut.tpLinkCamTransport, line 135
		if (result != null) { // library marker davegut.tpLinkCamTransport, line 136
			if (result.start_seq != null) { // library marker davegut.tpLinkCamTransport, line 137
				if (result.user_group == "root") { // library marker davegut.tpLinkCamTransport, line 138
					byte[] lsk = genEncryptToken("lsk", hsInput.pwd, confData.nonce, confData.cnonce) // library marker davegut.tpLinkCamTransport, line 139
					byte[] ivb = genEncryptToken("ivb", hsInput.pwd, confData.nonce, confData.cnonce) // library marker davegut.tpLinkCamTransport, line 140
					String apiUrl = "${hsInput.url}/stok=${result.stok}/ds" // library marker davegut.tpLinkCamTransport, line 141
					tokenData << [seqNo: result.start_seq, lsk: lsk, ivb: ivb, // library marker davegut.tpLinkCamTransport, line 142
								  apiUrl: apiUrl, status: "OK"] // library marker davegut.tpLinkCamTransport, line 143
				} else { // library marker davegut.tpLinkCamTransport, line 144
					tokenData << [status: "invalidUserGroup"] // library marker davegut.tpLinkCamTransport, line 145
				} // library marker davegut.tpLinkCamTransport, line 146
			} else { // library marker davegut.tpLinkCamTransport, line 147
				tokenData << [status: "nullStartSeq"] // library marker davegut.tpLinkCamTransport, line 148
			} // library marker davegut.tpLinkCamTransport, line 149
		} else { // library marker davegut.tpLinkCamTransport, line 150
			tokenData << [status: "nullDataFrom Device", respData: respData] // library marker davegut.tpLinkCamTransport, line 151
		} // library marker davegut.tpLinkCamTransport, line 152
	} else { // library marker davegut.tpLinkCamTransport, line 153
		tokenData << [status: "credentialError"] // library marker davegut.tpLinkCamTransport, line 154
	} // library marker davegut.tpLinkCamTransport, line 155
	if (tokenData.status != "OK") { // library marker davegut.tpLinkCamTransport, line 156
		tokenData << [respData: respData, tokenErr: getNote("tokenErr")] // library marker davegut.tpLinkCamTransport, line 157
//		logData << [tokenData: tokenData, respData: respData] // library marker davegut.tpLinkCamTransport, line 158
//		logWarn([getToken: logData]) // library marker davegut.tpLinkCamTransport, line 159
	} // library marker davegut.tpLinkCamTransport, line 160
	return tokenData // library marker davegut.tpLinkCamTransport, line 161
} // library marker davegut.tpLinkCamTransport, line 162

def genEncryptToken(tokenType, pwd, nonce, cnonce) { // library marker davegut.tpLinkCamTransport, line 164
	String hashedKeyHex = cnonce + pwd + nonce // library marker davegut.tpLinkCamTransport, line 165
	String hashedKey = mdEncode("SHA-256", hashedKeyHex.getBytes()).encodeHex().toString().toUpperCase() // library marker davegut.tpLinkCamTransport, line 166
	String tokenHex = tokenType + cnonce + nonce + hashedKey // library marker davegut.tpLinkCamTransport, line 167
	byte[] tokenFull = mdEncode("SHA-256", tokenHex.getBytes()) // library marker davegut.tpLinkCamTransport, line 168
	return tokenFull[0..15] // library marker davegut.tpLinkCamTransport, line 169
} // library marker davegut.tpLinkCamTransport, line 170

def getNote(noteId) { // library marker davegut.tpLinkCamTransport, line 172
	String note = "Undefined note." // library marker davegut.tpLinkCamTransport, line 173
	if (noteId == "checkPwd") { // library marker davegut.tpLinkCamTransport, line 174
		note = "<b>Check password. Must not have spaces. Certain special characters also cause failure.</b>" // library marker davegut.tpLinkCamTransport, line 175
	} else if (noteId == "thirdParty") { // library marker davegut.tpLinkCamTransport, line 176
		note = "<b>Check Tapo app setting Third-Party Services for on.  If on toggle off then on.</b>" // library marker davegut.tpLinkCamTransport, line 177
	} else if (noteId == "checkUser") { // library marker davegut.tpLinkCamTransport, line 178
		note = "<b>Check username. No spaces. Alternate username admin may also work.</b>" // library marker davegut.tpLinkCamTransport, line 179
	} else if (noteId == "tokenErr") { // library marker davegut.tpLinkCamTransport, line 180
		note = "<b>Try again in 10 minutes. If error persists, contact developer.</b>" // library marker davegut.tpLinkCamTransport, line 181
	} // library marker davegut.tpLinkCamTransport, line 182
	return note // library marker davegut.tpLinkCamTransport, line 183
} // library marker davegut.tpLinkCamTransport, line 184

def shortHandshake() { // library marker davegut.tpLinkCamTransport, line 186
	String pwd = parent.encPasswordCam // library marker davegut.tpLinkCamTransport, line 187
	String url = getDataValue("baseUrl") // library marker davegut.tpLinkCamTransport, line 188
	Map logData = [:] // library marker davegut.tpLinkCamTransport, line 189
	String digestPwdHex = pwd + cnonce + nonce // library marker davegut.tpLinkCamTransport, line 190
	String digestPwd = mdEncode("SHA-256", digestPwdHex.getBytes()).encodeHex().toString().toUpperCase() // library marker davegut.tpLinkCamTransport, line 191
	String fullDigestPwdHex = digestPwd + cnonce + nonce // library marker davegut.tpLinkCamTransport, line 192
	String fullDigestPwd = new String(fullDigestPwdHex.getBytes(), "UTF-8") // library marker davegut.tpLinkCamTransport, line 193
	Map cmdBody = [ // library marker davegut.tpLinkCamTransport, line 194
		method: "login", // library marker davegut.tpLinkCamTransport, line 195
		params: [cnonce: cnonce,  // library marker davegut.tpLinkCamTransport, line 196
				 encrypt_type: "3", // library marker davegut.tpLinkCamTransport, line 197
				 digest_passwd: fullDigestPwd, // library marker davegut.tpLinkCamTransport, line 198
				 username: parent.userName // library marker davegut.tpLinkCamTransport, line 199
			]] // library marker davegut.tpLinkCamTransport, line 200
	Map respData = postSync(cmdBody, url) // library marker davegut.tpLinkCamTransport, line 201

	logData << [errorCode: respData.error_code] // library marker davegut.tpLinkCamTransport, line 203
	String tokenStatus = "OK" // library marker davegut.tpLinkCamTransport, line 204
	if (respData.error_code == 0) { // library marker davegut.tpLinkCamTransport, line 205
		Map result = respData.result // library marker davegut.tpLinkCamTransport, line 206
		if (result != null) { // library marker davegut.tpLinkCamTransport, line 207
			if (result.start_seq != null) { // library marker davegut.tpLinkCamTransport, line 208
				if (result.user_group == "root") { // library marker davegut.tpLinkCamTransport, line 209
					byte[] lsk = genEncryptToken("lsk", pwd, nonce, cnonce) // library marker davegut.tpLinkCamTransport, line 210
					byte[] ivb = genEncryptToken("ivb", pwd, nonce, cnonce) // library marker davegut.tpLinkCamTransport, line 211
					String apiUrl = "${url}/stok=${result.stok}/ds" // library marker davegut.tpLinkCamTransport, line 212
logData << [seqNo: result.start_seq, lsk: lsk, ivb: ivb, apiUrl: apiUrl] // library marker davegut.tpLinkCamTransport, line 213
					device.updateSetting("lsk",[type:"password", value: lsk]) // library marker davegut.tpLinkCamTransport, line 214
					device.updateSetting("ivb",[type:"password", value: ivb]) // library marker davegut.tpLinkCamTransport, line 215
					device.updateSetting("apiUrl",[type:"password", value: apiUrl]) // library marker davegut.tpLinkCamTransport, line 216
					state.seqNo = result.start_seq // library marker davegut.tpLinkCamTransport, line 217
				} else { // library marker davegut.tpLinkCamTransport, line 218
					tokenStatus = "invalidUserGroup" // library marker davegut.tpLinkCamTransport, line 219
				} // library marker davegut.tpLinkCamTransport, line 220
			} else { // library marker davegut.tpLinkCamTransport, line 221
				tokenStatus = "nullStartSeq" // library marker davegut.tpLinkCamTransport, line 222
			} // library marker davegut.tpLinkCamTransport, line 223
		} else { // library marker davegut.tpLinkCamTransport, line 224
			tokenStatus = "nullDataFrom Device" // library marker davegut.tpLinkCamTransport, line 225
		} // library marker davegut.tpLinkCamTransport, line 226
	} else { // library marker davegut.tpLinkCamTransport, line 227
		tokenStatus ="credentialError" // library marker davegut.tpLinkCamTransport, line 228
	} // library marker davegut.tpLinkCamTransport, line 229
	if (tokenStatus != "OK") { // library marker davegut.tpLinkCamTransport, line 230
		Map hsInput = [url: url, user: parent.userName, pwd: pwd] // library marker davegut.tpLinkCamTransport, line 231
		logData << [respData: respData, cameraHandshake: cameraHandshake(hsInput)] // library marker davegut.tpLinkCamTransport, line 232
		logWarn([shortHandshake: logData]) // library marker davegut.tpLinkCamTransport, line 233
	} // library marker davegut.tpLinkCamTransport, line 234
	return logData // library marker davegut.tpLinkCamTransport, line 235
} // library marker davegut.tpLinkCamTransport, line 236

//	===== Sync Communications ===== // library marker davegut.tpLinkCamTransport, line 238
def getCamHeaders() { // library marker davegut.tpLinkCamTransport, line 239
	Map headers = [ // library marker davegut.tpLinkCamTransport, line 240
		"Accept": "application/json", // library marker davegut.tpLinkCamTransport, line 241
		"Accept-Encoding": "gzip, deflate", // library marker davegut.tpLinkCamTransport, line 242
		"User-Agent": "Tapo CameraClient Android", // library marker davegut.tpLinkCamTransport, line 243
		"Connection": "close", // library marker davegut.tpLinkCamTransport, line 244
		"requestByApp": "true", // library marker davegut.tpLinkCamTransport, line 245
		"Content-Type": "application/json; charset=UTF-8" // library marker davegut.tpLinkCamTransport, line 246
		] // library marker davegut.tpLinkCamTransport, line 247
	return headers // library marker davegut.tpLinkCamTransport, line 248
} // library marker davegut.tpLinkCamTransport, line 249

def postSync(cmdBody, baseUrl) { // library marker davegut.tpLinkCamTransport, line 251
	Map respData = [:] // library marker davegut.tpLinkCamTransport, line 252
	Map heads = getCamHeaders() // library marker davegut.tpLinkCamTransport, line 253
	Map httpParams = [uri: baseUrl, // library marker davegut.tpLinkCamTransport, line 254
					 body: JsonOutput.toJson(cmdBody), // library marker davegut.tpLinkCamTransport, line 255
					 contentType: "application/json", // library marker davegut.tpLinkCamTransport, line 256
					 requestContentType: "application/json", // library marker davegut.tpLinkCamTransport, line 257
					 timeout: 10, // library marker davegut.tpLinkCamTransport, line 258
					 ignoreSSLIssues: true, // library marker davegut.tpLinkCamTransport, line 259
					 headers: heads // library marker davegut.tpLinkCamTransport, line 260
					 ] // library marker davegut.tpLinkCamTransport, line 261
	try { // library marker davegut.tpLinkCamTransport, line 262
		httpPostJson(httpParams) { resp -> // library marker davegut.tpLinkCamTransport, line 263
			if (resp.status == 200) { // library marker davegut.tpLinkCamTransport, line 264
				respData << resp.data // library marker davegut.tpLinkCamTransport, line 265
			} else { // library marker davegut.tpLinkCamTransport, line 266
				respData << [status: resp.status, errorData: resp.properties, // library marker davegut.tpLinkCamTransport, line 267
							 action: "<b>Check IP Address</b>"] // library marker davegut.tpLinkCamTransport, line 268
				logWarn(respData) // library marker davegut.tpLinkCamTransport, line 269
			} // library marker davegut.tpLinkCamTransport, line 270
		} // library marker davegut.tpLinkCamTransport, line 271
	} catch (err) { // library marker davegut.tpLinkCamTransport, line 272
		respData << [status: "httpPostJson error", error: err] // library marker davegut.tpLinkCamTransport, line 273
		logWarn(respData) // library marker davegut.tpLinkCamTransport, line 274
	} // library marker davegut.tpLinkCamTransport, line 275
	return respData // library marker davegut.tpLinkCamTransport, line 276
} // library marker davegut.tpLinkCamTransport, line 277

def getCameraParams(cmdBody, reqData) { // library marker davegut.tpLinkCamTransport, line 279
	byte[] encKey = new JsonSlurper().parseText(lsk) // library marker davegut.tpLinkCamTransport, line 280
	byte[] encIv = new JsonSlurper().parseText(ivb) // library marker davegut.tpLinkCamTransport, line 281
	def cmdStr = JsonOutput.toJson(cmdBody) // library marker davegut.tpLinkCamTransport, line 282
	Map reqBody = [method: "securePassthrough", // library marker davegut.tpLinkCamTransport, line 283
				   params: [request: aesEncrypt(cmdStr, encKey, encIv)]] // library marker davegut.tpLinkCamTransport, line 284
	String cmdData = new groovy.json.JsonBuilder(reqBody).toString() // library marker davegut.tpLinkCamTransport, line 285
	Integer seqNumber = state.seqNo // library marker davegut.tpLinkCamTransport, line 286
	String initTagHex = parent.encPasswordCam + cnonce // library marker davegut.tpLinkCamTransport, line 287
	String initTag = mdEncode("SHA-256", initTagHex.getBytes()).encodeHex().toString().toUpperCase() // library marker davegut.tpLinkCamTransport, line 288
	String tagString = initTag + cmdData + seqNumber // library marker davegut.tpLinkCamTransport, line 289
	String tag =  mdEncode("SHA-256", tagString.getBytes()).encodeHex().toString().toUpperCase() // library marker davegut.tpLinkCamTransport, line 290
	Map heads = getCamHeaders() // library marker davegut.tpLinkCamTransport, line 291
	heads << ["Tapo_tag": tag, Seq: seqNumber] // library marker davegut.tpLinkCamTransport, line 292
	Map reqParams = [uri: apiUrl, // library marker davegut.tpLinkCamTransport, line 293
					 body: cmdData, // library marker davegut.tpLinkCamTransport, line 294
					 contentType: "application/json", // library marker davegut.tpLinkCamTransport, line 295
					 requestContentType: "application/json", // library marker davegut.tpLinkCamTransport, line 296
					 timeout: 15, // library marker davegut.tpLinkCamTransport, line 297
					 ignoreSSLIssues: true, // library marker davegut.tpLinkCamTransport, line 298
					 headers: heads // library marker davegut.tpLinkCamTransport, line 299
					] // library marker davegut.tpLinkCamTransport, line 300
	return reqParams // library marker davegut.tpLinkCamTransport, line 301
} // library marker davegut.tpLinkCamTransport, line 302

def parseCameraData(resp, data) { // library marker davegut.tpLinkCamTransport, line 304
	Map parseData = [sourceMethod: data.data, jsonErrCode: resp.json.error_code] // library marker davegut.tpLinkCamTransport, line 305
	state.seqNo += 1 // library marker davegut.tpLinkCamTransport, line 306
	if (resp.json.error_code == 0) { // library marker davegut.tpLinkCamTransport, line 307
		try { // library marker davegut.tpLinkCamTransport, line 308
			byte[] encKey = new JsonSlurper().parseText(lsk) // library marker davegut.tpLinkCamTransport, line 309
			byte[] encIv = new JsonSlurper().parseText(ivb) // library marker davegut.tpLinkCamTransport, line 310
			Map cmdResp = new JsonSlurper().parseText(aesDecrypt(resp.json.result.response, // library marker davegut.tpLinkCamTransport, line 311
															 	encKey, encIv)) // library marker davegut.tpLinkCamTransport, line 312
			parseData << [parseStatus: "OK", cmdResp: cmdResp] // library marker davegut.tpLinkCamTransport, line 313
			state.protoError = false // library marker davegut.tpLinkCamTransport, line 314
		} catch (err) { // library marker davegut.tpLinkCamTransport, line 315
			parseData << [parseStatus: "Decrypt Error", error: err] // library marker davegut.tpLinkCamTransport, line 316
       } // library marker davegut.tpLinkCamTransport, line 317
	} else { // library marker davegut.tpLinkCamTransport, line 318
		parseData << [parseStatus: "Protocol Error"] // library marker davegut.tpLinkCamTransport, line 319
	} // library marker davegut.tpLinkCamTransport, line 320
	if (parseData.parseStatus != "OK") { // library marker davegut.tpLinkCamTransport, line 321
		if (state.protoError == false) { // library marker davegut.tpLinkCamTransport, line 322
			parseData << [nextMeth: "resolveProtocolError"] // library marker davegut.tpLinkCamTransport, line 323
			resolveProtocolError() // library marker davegut.tpLinkCamTransport, line 324
		} // library marker davegut.tpLinkCamTransport, line 325
	} // library marker davegut.tpLinkCamTransport, line 326
	return parseData // library marker davegut.tpLinkCamTransport, line 327
} // library marker davegut.tpLinkCamTransport, line 328

//	Run deviceHandshake then retry command // library marker davegut.tpLinkCamTransport, line 330
def resolveProtocolError() { // library marker davegut.tpLinkCamTransport, line 331
	Map logData = [method: "resolveProtocolError", lastCmd: state.lastCommand] // library marker davegut.tpLinkCamTransport, line 332
	state.protoError = true // library marker davegut.tpLinkCamTransport, line 333
	deviceHandshake() // library marker davegut.tpLinkCamTransport, line 334
	runIn(4, delayedPassThrough) // library marker davegut.tpLinkCamTransport, line 335
	logDebug(logData) // library marker davegut.tpLinkCamTransport, line 336
} // library marker davegut.tpLinkCamTransport, line 337


// ~~~~~ end include (791) davegut.tpLinkCamTransport ~~~~~

// ~~~~~ start include (803) davegut.tpLinkTransKlap ~~~~~
library ( // library marker davegut.tpLinkTransKlap, line 1
	name: "tpLinkTransKlap", // library marker davegut.tpLinkTransKlap, line 2
	namespace: "davegut", // library marker davegut.tpLinkTransKlap, line 3
	author: "Compiled by Dave Gutheinz", // library marker davegut.tpLinkTransKlap, line 4
	description: "Handshake methods for TP-Link Integration", // library marker davegut.tpLinkTransKlap, line 5
	category: "utilities", // library marker davegut.tpLinkTransKlap, line 6
	documentationLink: "" // library marker davegut.tpLinkTransKlap, line 7
) // library marker davegut.tpLinkTransKlap, line 8

def klapHandshake(baseUrl, devData = null) { // library marker davegut.tpLinkTransKlap, line 10
	byte[] localSeed = getSeed(16) // library marker davegut.tpLinkTransKlap, line 11
	Map reqData = [localSeed: localSeed, baseUrl: baseUrl, devData:devData] // library marker davegut.tpLinkTransKlap, line 12
	Map reqParams = [uri: "${baseUrl}/handshake1", // library marker davegut.tpLinkTransKlap, line 13
					 body: localSeed, // library marker davegut.tpLinkTransKlap, line 14
					 contentType: "application/octet-stream", // library marker davegut.tpLinkTransKlap, line 15
					 requestContentType: "application/octet-stream", // library marker davegut.tpLinkTransKlap, line 16
					 timeout:10, // library marker davegut.tpLinkTransKlap, line 17
					 ignoreSSLIssues: true] // library marker davegut.tpLinkTransKlap, line 18
	asynchttpPost("parseKlapHandshake", reqParams, [data: reqData]) // library marker davegut.tpLinkTransKlap, line 19
} // library marker davegut.tpLinkTransKlap, line 20



//////////////////////////////////////////// // library marker davegut.tpLinkTransKlap, line 24
def xxxxxklapHandshake(baseUrl, localHash, devData = null) { // library marker davegut.tpLinkTransKlap, line 25
	byte[] localSeed = getSeed(16) // library marker davegut.tpLinkTransKlap, line 26
	Map reqData = [localSeed: localSeed, baseUrl: baseUrl, localHash: localHash, devData:devData] // library marker davegut.tpLinkTransKlap, line 27
	Map reqParams = [uri: "${baseUrl}/handshake1", // library marker davegut.tpLinkTransKlap, line 28
					 body: localSeed, // library marker davegut.tpLinkTransKlap, line 29
					 contentType: "application/octet-stream", // library marker davegut.tpLinkTransKlap, line 30
					 requestContentType: "application/octet-stream", // library marker davegut.tpLinkTransKlap, line 31
					 timeout:10, // library marker davegut.tpLinkTransKlap, line 32
					 ignoreSSLIssues: true] // library marker davegut.tpLinkTransKlap, line 33
	asynchttpPost("parseKlapHandshake", reqParams, [data: reqData]) // library marker davegut.tpLinkTransKlap, line 34
} // library marker davegut.tpLinkTransKlap, line 35
//////////////////////////////////// // library marker davegut.tpLinkTransKlap, line 36



def parseKlapHandshake(resp, data) { // library marker davegut.tpLinkTransKlap, line 40
	Map reqData = [devData: data.data.devData] // library marker davegut.tpLinkTransKlap, line 41
	hs1Success = false // library marker davegut.tpLinkTransKlap, line 42
	String credType = "unknown" // library marker davegut.tpLinkTransKlap, line 43
	if (resp.status == 200 && resp.data != null) { // library marker davegut.tpLinkTransKlap, line 44
		byte[] localSeed = data.data.localSeed // library marker davegut.tpLinkTransKlap, line 45
		byte[] seedData = resp.data.decodeBase64() // library marker davegut.tpLinkTransKlap, line 46
		byte[] remoteSeed = seedData[0 .. 15] // library marker davegut.tpLinkTransKlap, line 47
		byte[] serverHash = seedData[16 .. 47] // library marker davegut.tpLinkTransKlap, line 48

//create check loop // library marker davegut.tpLinkTransKlap, line 50
//	Check userHash // library marker davegut.tpLinkTransKlap, line 51
		if (device) { // library marker davegut.tpLinkTransKlap, line 52
			userName = parent.userName // library marker davegut.tpLinkTransKlap, line 53
			userPassword = parent.userPassword // library marker davegut.tpLinkTransKlap, line 54
		} // library marker davegut.tpLinkTransKlap, line 55
		byte[] localHash = genAuthHash(userName, userPassword) // library marker davegut.tpLinkTransKlap, line 56
		byte[] authHash = [localSeed, remoteSeed, localHash].flatten() // library marker davegut.tpLinkTransKlap, line 57
		byte[] localAuthHash = mdEncode("SHA-256", authHash) // library marker davegut.tpLinkTransKlap, line 58

//	On rare occasions, the device will use the hard-coded default credentials. // library marker davegut.tpLinkTransKlap, line 60
//	check localAuthHash = serverHash, if fails start checking down a if loop stream // library marker davegut.tpLinkTransKlap, line 61
//	untils we try the other two options (kasa/tapo). // library marker davegut.tpLinkTransKlap, line 62
		if (localAuthHash == serverHash) { // library marker davegut.tpLinkTransKlap, line 63
			credType = "user" // library marker davegut.tpLinkTransKlap, line 64
		} else { // library marker davegut.tpLinkTransKlap, line 65
			//	Try Tapo default credentials (2nd most likely // library marker davegut.tpLinkTransKlap, line 66
			Map defCreds = getDefaultCreds() // library marker davegut.tpLinkTransKlap, line 67
			localHash = genAuthHash(defCreds.tapo.un, defCreds.tapo.pw) // library marker davegut.tpLinkTransKlap, line 68
			authHash = [localSeed, remoteSeed, localHash].flatten() // library marker davegut.tpLinkTransKlap, line 69
			localAuthHash = mdEncode("SHA-256", authHash) // library marker davegut.tpLinkTransKlap, line 70
log.trace "serverHash = $serverHash" // library marker davegut.tpLinkTransKlap, line 71
log.trace "TAPO Def Creds: localAuthHash = $localAuthHash" // library marker davegut.tpLinkTransKlap, line 72
			if (localAuthHash == serverHash) { // library marker davegut.tpLinkTransKlap, line 73
				credType = "tapo" // library marker davegut.tpLinkTransKlap, line 74
			} else { // library marker davegut.tpLinkTransKlap, line 75
			//	Try Kasa default credentials (2nd most likely // library marker davegut.tpLinkTransKlap, line 76
//	TEST ONLY.  REMOVE BEFORE FLIGHT // library marker davegut.tpLinkTransKlap, line 77
				localHash = genAuthHash(defCreds.kasa.un, defCreds.kasa.pw) // library marker davegut.tpLinkTransKlap, line 78
				authHash = [localSeed, remoteSeed, localHash].flatten() // library marker davegut.tpLinkTransKlap, line 79
				localAuthHash = mdEncode("SHA-256", authHash) // library marker davegut.tpLinkTransKlap, line 80
log.trace "KASA Def Creds: localAuthHash = $localAuthHash" // library marker davegut.tpLinkTransKlap, line 81
				if (localAuthHash == serverHash) { // library marker davegut.tpLinkTransKlap, line 82
					credType = "kasa" // library marker davegut.tpLinkTransKlap, line 83
				} else { // library marker davegut.tpLinkTransKlap, line 84
			//	Try blank credentials (very unlikely) // library marker davegut.tpLinkTransKlap, line 85
					localHash = genAuthHash("", "") // library marker davegut.tpLinkTransKlap, line 86
					authHash = [localSeed, remoteSeed, localHash].flatten() // library marker davegut.tpLinkTransKlap, line 87
					localAuthHash = mdEncode("SHA-256", authHash) // library marker davegut.tpLinkTransKlap, line 88
log.trace "BLANK Creds: localAuthHash = $localAuthHash" // library marker davegut.tpLinkTransKlap, line 89
					if (localAuthHash == serverHash) { // library marker davegut.tpLinkTransKlap, line 90
						credType = "blank" // library marker davegut.tpLinkTransKlap, line 91
					} // library marker davegut.tpLinkTransKlap, line 92
				} // library marker davegut.tpLinkTransKlap, line 93
			} // library marker davegut.tpLinkTransKlap, line 94
		} // library marker davegut.tpLinkTransKlap, line 95
		reqData << [credType: credType] // library marker davegut.tpLinkTransKlap, line 96

		if (credType != "unknown") { // library marker davegut.tpLinkTransKlap, line 98
			//	cookie // library marker davegut.tpLinkTransKlap, line 99
			def cookieHeader = resp.headers["Set-Cookie"].toString() // library marker davegut.tpLinkTransKlap, line 100
			def cookie = cookieHeader.substring(cookieHeader.indexOf(":") +1, cookieHeader.indexOf(";")) // library marker davegut.tpLinkTransKlap, line 101
			//	seqNo and encIv // library marker davegut.tpLinkTransKlap, line 102
			byte[] payload = ["iv".getBytes(), localSeed, remoteSeed, localHash].flatten() // library marker davegut.tpLinkTransKlap, line 103
			byte[] fullIv = mdEncode("SHA-256", payload) // library marker davegut.tpLinkTransKlap, line 104
			byte[] byteSeqNo = fullIv[-4..-1] // library marker davegut.tpLinkTransKlap, line 105
			int seqNo = byteArrayToInteger(byteSeqNo) // library marker davegut.tpLinkTransKlap, line 106
			if (device) { // library marker davegut.tpLinkTransKlap, line 107
				state.seqNo = seqNo // library marker davegut.tpLinkTransKlap, line 108
			} else { // library marker davegut.tpLinkTransKlap, line 109
				atomicState.seqNo = seqNo // library marker davegut.tpLinkTransKlap, line 110
			} // library marker davegut.tpLinkTransKlap, line 111
			//	encKey // library marker davegut.tpLinkTransKlap, line 112
			payload = ["lsk".getBytes(), localSeed, remoteSeed, localHash].flatten() // library marker davegut.tpLinkTransKlap, line 113
			byte[] encKey = mdEncode("SHA-256", payload)[0..15] // library marker davegut.tpLinkTransKlap, line 114
			//	encSig // library marker davegut.tpLinkTransKlap, line 115
			payload = ["ldk".getBytes(), localSeed, remoteSeed, localHash].flatten() // library marker davegut.tpLinkTransKlap, line 116
			byte[] encSig = mdEncode("SHA-256", payload)[0..27] // library marker davegut.tpLinkTransKlap, line 117
			hs1Success = true // library marker davegut.tpLinkTransKlap, line 118
			logDebug([parseKlapHandshake: reqData]) // library marker davegut.tpLinkTransKlap, line 119
			if (device) { // library marker davegut.tpLinkTransKlap, line 120
				device.updateSetting("cookie",[type:"password", value: cookie]) // library marker davegut.tpLinkTransKlap, line 121
				device.updateSetting("encKey",[type:"password", value: encKey]) // library marker davegut.tpLinkTransKlap, line 122
				device.updateSetting("encIv",[type:"password", value: fullIv[0..11]]) // library marker davegut.tpLinkTransKlap, line 123
				device.updateSetting("encSig",[type:"password", value: encSig]) // library marker davegut.tpLinkTransKlap, line 124
			} else { // library marker davegut.tpLinkTransKlap, line 125
				reqData << [cookie: cookie, seqNo: seqNo, encIv: fullIv[0..11], // library marker davegut.tpLinkTransKlap, line 126
							encSig: encSig, encKey: encKey] // library marker davegut.tpLinkTransKlap, line 127
			} // library marker davegut.tpLinkTransKlap, line 128
			byte[] loginHash = [remoteSeed, localSeed, localHash].flatten() // library marker davegut.tpLinkTransKlap, line 129
			byte[] body = mdEncode("SHA-256", loginHash) // library marker davegut.tpLinkTransKlap, line 130
			Map reqParams = [uri: "${data.data.baseUrl}/handshake2", // library marker davegut.tpLinkTransKlap, line 131
							 body: body, // library marker davegut.tpLinkTransKlap, line 132
							 timeout:10, // library marker davegut.tpLinkTransKlap, line 133
							 ignoreSSLIssues: true, // library marker davegut.tpLinkTransKlap, line 134
							 headers: ["Cookie": cookie], // library marker davegut.tpLinkTransKlap, line 135
							 contentType: "application/octet-stream", // library marker davegut.tpLinkTransKlap, line 136
							 requestContentType: "application/octet-stream"] // library marker davegut.tpLinkTransKlap, line 137
			try { // library marker davegut.tpLinkTransKlap, line 138
				asynchttpPost("parseKlapHandshake2", reqParams, [data: reqData]) // library marker davegut.tpLinkTransKlap, line 139
			} catch (err) { // library marker davegut.tpLinkTransKlap, line 140
				reqData << [respStatus: "ERROR parsing 200 response", resp: resp.properties, error: err, // library marker davegut.tpLinkTransKlap, line 141
							action: "<b>Try Configure command</b>"] // library marker davegut.tpLinkTransKlap, line 142
			} // library marker davegut.tpLinkTransKlap, line 143
		} else { // library marker davegut.tpLinkTransKlap, line 144
log.trace "TEST RESULT: FAILED" // library marker davegut.tpLinkTransKlap, line 145
			reqData << [respStatus: "ERROR: localAuthHash != serverHash", // library marker davegut.tpLinkTransKlap, line 146
						action: "<b>Check credentials and try again</b>"] // library marker davegut.tpLinkTransKlap, line 147
		} // library marker davegut.tpLinkTransKlap, line 148
	} else { // library marker davegut.tpLinkTransKlap, line 149
		reqData << [respStatus: resp.status, message: resp.errorMessage, // library marker davegut.tpLinkTransKlap, line 150
					action: "<b>Try Configure command</b>"] // library marker davegut.tpLinkTransKlap, line 151
	} // library marker davegut.tpLinkTransKlap, line 152
	reqData << [hs1Success: hs1Success] // library marker davegut.tpLinkTransKlap, line 153
	if (hs1Success == false) {  // library marker davegut.tpLinkTransKlap, line 154
		logWarn([parseKlapHandshake: reqData]) // library marker davegut.tpLinkTransKlap, line 155
	} // library marker davegut.tpLinkTransKlap, line 156
} // library marker davegut.tpLinkTransKlap, line 157

//////////////////////////////////////////////////// // library marker davegut.tpLinkTransKlap, line 159
def xxxxxxxparseKlapHandshake(resp, data) { // library marker davegut.tpLinkTransKlap, line 160
	Map reqData = [devData: data.data.devData] // library marker davegut.tpLinkTransKlap, line 161
	hs1Success = false // library marker davegut.tpLinkTransKlap, line 162
	if (resp.status == 200 && resp.data != null) { // library marker davegut.tpLinkTransKlap, line 163
		try { // library marker davegut.tpLinkTransKlap, line 164
			byte[] localSeed = data.data.localSeed // library marker davegut.tpLinkTransKlap, line 165
			byte[] seedData = resp.data.decodeBase64() // library marker davegut.tpLinkTransKlap, line 166
			byte[] remoteSeed = seedData[0 .. 15] // library marker davegut.tpLinkTransKlap, line 167
			byte[] serverHash = seedData[16 .. 47] // library marker davegut.tpLinkTransKlap, line 168
			byte[] localHash = data.data.localHash.decodeBase64() // library marker davegut.tpLinkTransKlap, line 169
			byte[] authHash = [localSeed, remoteSeed, localHash].flatten() // library marker davegut.tpLinkTransKlap, line 170
			byte[] localAuthHash = mdEncode("SHA-256", authHash) // library marker davegut.tpLinkTransKlap, line 171
			if (localAuthHash == serverHash) { // library marker davegut.tpLinkTransKlap, line 172
				//	cookie // library marker davegut.tpLinkTransKlap, line 173
				def cookieHeader = resp.headers["Set-Cookie"].toString() // library marker davegut.tpLinkTransKlap, line 174
				def cookie = cookieHeader.substring(cookieHeader.indexOf(":") +1, cookieHeader.indexOf(";")) // library marker davegut.tpLinkTransKlap, line 175
				//	seqNo and encIv // library marker davegut.tpLinkTransKlap, line 176
				byte[] payload = ["iv".getBytes(), localSeed, remoteSeed, localHash].flatten() // library marker davegut.tpLinkTransKlap, line 177
				byte[] fullIv = mdEncode("SHA-256", payload) // library marker davegut.tpLinkTransKlap, line 178
				byte[] byteSeqNo = fullIv[-4..-1] // library marker davegut.tpLinkTransKlap, line 179
				int seqNo = byteArrayToInteger(byteSeqNo) // library marker davegut.tpLinkTransKlap, line 180
				if (device) { // library marker davegut.tpLinkTransKlap, line 181
					state.seqNo = seqNo // library marker davegut.tpLinkTransKlap, line 182
				} else { // library marker davegut.tpLinkTransKlap, line 183
					atomicState.seqNo = seqNo // library marker davegut.tpLinkTransKlap, line 184
				} // library marker davegut.tpLinkTransKlap, line 185
				//	encKey // library marker davegut.tpLinkTransKlap, line 186
				payload = ["lsk".getBytes(), localSeed, remoteSeed, localHash].flatten() // library marker davegut.tpLinkTransKlap, line 187
				byte[] encKey = mdEncode("SHA-256", payload)[0..15] // library marker davegut.tpLinkTransKlap, line 188
				//	encSig // library marker davegut.tpLinkTransKlap, line 189
				payload = ["ldk".getBytes(), localSeed, remoteSeed, localHash].flatten() // library marker davegut.tpLinkTransKlap, line 190
				byte[] encSig = mdEncode("SHA-256", payload)[0..27] // library marker davegut.tpLinkTransKlap, line 191
				hs1Success = true // library marker davegut.tpLinkTransKlap, line 192
				logDebug([parseKlapHandshake: reqData]) // library marker davegut.tpLinkTransKlap, line 193
				if (device) { // library marker davegut.tpLinkTransKlap, line 194
					device.updateSetting("cookie",[type:"password", value: cookie])  // library marker davegut.tpLinkTransKlap, line 195
					device.updateSetting("encKey",[type:"password", value: encKey])  // library marker davegut.tpLinkTransKlap, line 196
					device.updateSetting("encIv",[type:"password", value: fullIv[0..11]])  // library marker davegut.tpLinkTransKlap, line 197
					device.updateSetting("encSig",[type:"password", value: encSig])  // library marker davegut.tpLinkTransKlap, line 198
				} else { // library marker davegut.tpLinkTransKlap, line 199
					reqData << [cookie: cookie, seqNo: seqNo, encIv: fullIv[0..11],  // library marker davegut.tpLinkTransKlap, line 200
								encSig: encSig, encKey: encKey] // library marker davegut.tpLinkTransKlap, line 201
				} // library marker davegut.tpLinkTransKlap, line 202
				byte[] loginHash = [remoteSeed, localSeed, localHash].flatten() // library marker davegut.tpLinkTransKlap, line 203
				byte[] body = mdEncode("SHA-256", loginHash) // library marker davegut.tpLinkTransKlap, line 204
				Map reqParams = [uri: "${data.data.baseUrl}/handshake2", // library marker davegut.tpLinkTransKlap, line 205
								 body: body, // library marker davegut.tpLinkTransKlap, line 206
								 timeout:10, // library marker davegut.tpLinkTransKlap, line 207
								 ignoreSSLIssues: true, // library marker davegut.tpLinkTransKlap, line 208
								 headers: ["Cookie": cookie], // library marker davegut.tpLinkTransKlap, line 209
								 contentType: "application/octet-stream", // library marker davegut.tpLinkTransKlap, line 210
								 requestContentType: "application/octet-stream"] // library marker davegut.tpLinkTransKlap, line 211
				asynchttpPost("parseKlapHandshake2", reqParams, [data: reqData]) // library marker davegut.tpLinkTransKlap, line 212
			} else { // library marker davegut.tpLinkTransKlap, line 213
				reqData << [respStatus: "ERROR: localAuthHash != serverHash", // library marker davegut.tpLinkTransKlap, line 214
							action: "<b>Check credentials and try again</b>"] // library marker davegut.tpLinkTransKlap, line 215
			} // library marker davegut.tpLinkTransKlap, line 216
		} catch (err) { // library marker davegut.tpLinkTransKlap, line 217
			reqData << [respStatus: "ERROR parsing 200 response", resp: resp.properties, error: err, // library marker davegut.tpLinkTransKlap, line 218
						action: "<b>Try Configure command</b>"] // library marker davegut.tpLinkTransKlap, line 219
		} // library marker davegut.tpLinkTransKlap, line 220
	} else { // library marker davegut.tpLinkTransKlap, line 221
		reqData << [respStatus: resp.status, message: resp.errorMessage, // library marker davegut.tpLinkTransKlap, line 222
					action: "<b>Try Configure command</b>"] // library marker davegut.tpLinkTransKlap, line 223
	} // library marker davegut.tpLinkTransKlap, line 224
	reqData << [hs1Success: hs1Success] // library marker davegut.tpLinkTransKlap, line 225
	if (hs1Success == false) {  // library marker davegut.tpLinkTransKlap, line 226
		logWarn([parseKlapHandshake: reqData]) // library marker davegut.tpLinkTransKlap, line 227
	} // library marker davegut.tpLinkTransKlap, line 228
} // library marker davegut.tpLinkTransKlap, line 229

def parseKlapHandshake2(resp, data) { // library marker davegut.tpLinkTransKlap, line 231
	Map logData = [method: "parseKlapHandshake2"] // library marker davegut.tpLinkTransKlap, line 232
	if (resp.status == 200 && resp.data == null) { // library marker davegut.tpLinkTransKlap, line 233
		logData << [respStatus: "Login OK"] // library marker davegut.tpLinkTransKlap, line 234
		setCommsError(200) // library marker davegut.tpLinkTransKlap, line 235
		logDebug(logData) // library marker davegut.tpLinkTransKlap, line 236
	} else { // library marker davegut.tpLinkTransKlap, line 237
		logData << [respStatus: "LOGIN FAILED", reason: "ERROR in HTTP response", // library marker davegut.tpLinkTransKlap, line 238
					resp: resp.properties] // library marker davegut.tpLinkTransKlap, line 239
		logWarn(logData) // library marker davegut.tpLinkTransKlap, line 240
	} // library marker davegut.tpLinkTransKlap, line 241
	if (!device) { sendKlapDataCmd(logData, data) } // library marker davegut.tpLinkTransKlap, line 242
} // library marker davegut.tpLinkTransKlap, line 243

def getKlapParams(cmdBody) { // library marker davegut.tpLinkTransKlap, line 245
	int seqNo = state.seqNo + 1 // library marker davegut.tpLinkTransKlap, line 246
	state.seqNo = seqNo // library marker davegut.tpLinkTransKlap, line 247
	byte[] encKey = new JsonSlurper().parseText(encKey) // library marker davegut.tpLinkTransKlap, line 248
	byte[] encIv = new JsonSlurper().parseText(encIv) // library marker davegut.tpLinkTransKlap, line 249
	byte[] encSig = new JsonSlurper().parseText(encSig) // library marker davegut.tpLinkTransKlap, line 250
	String cmdBodyJson = new groovy.json.JsonBuilder(cmdBody).toString() // library marker davegut.tpLinkTransKlap, line 251

	Map encryptedData = klapEncrypt(cmdBodyJson.getBytes(), encKey, encIv, // library marker davegut.tpLinkTransKlap, line 253
									encSig, seqNo) // library marker davegut.tpLinkTransKlap, line 254
	Map reqParams = [ // library marker davegut.tpLinkTransKlap, line 255
		uri: "${getDataValue("baseUrl")}/request?seq=${seqNo}", // library marker davegut.tpLinkTransKlap, line 256
		body: encryptedData.cipherData, // library marker davegut.tpLinkTransKlap, line 257
		headers: ["Cookie": cookie], // library marker davegut.tpLinkTransKlap, line 258
		contentType: "application/octet-stream", // library marker davegut.tpLinkTransKlap, line 259
		requestContentType: "application/octet-stream", // library marker davegut.tpLinkTransKlap, line 260
		timeout: 10, // library marker davegut.tpLinkTransKlap, line 261
		ignoreSSLIssues: true] // library marker davegut.tpLinkTransKlap, line 262
	return reqParams // library marker davegut.tpLinkTransKlap, line 263
} // library marker davegut.tpLinkTransKlap, line 264

def parseKlapData(resp, data) { // library marker davegut.tpLinkTransKlap, line 266
	Map parseData = [Method: "parseKlapData", sourceMethod: data.data] // library marker davegut.tpLinkTransKlap, line 267
	try { // library marker davegut.tpLinkTransKlap, line 268
		byte[] encKey = new JsonSlurper().parseText(encKey) // library marker davegut.tpLinkTransKlap, line 269
		byte[] encIv = new JsonSlurper().parseText(encIv) // library marker davegut.tpLinkTransKlap, line 270
		int seqNo = state.seqNo // library marker davegut.tpLinkTransKlap, line 271
		byte[] cipherResponse = resp.data.decodeBase64()[32..-1] // library marker davegut.tpLinkTransKlap, line 272
		Map cmdResp =  new JsonSlurper().parseText(klapDecrypt(cipherResponse, encKey, // library marker davegut.tpLinkTransKlap, line 273
														   encIv, seqNo)) // library marker davegut.tpLinkTransKlap, line 274
		parseData << [cryptoStatus: "OK", cmdResp: cmdResp] // library marker davegut.tpLinkTransKlap, line 275
	} catch (err) { // library marker davegut.tpLinkTransKlap, line 276
		parseData << [cryptoStatus: "decryptDataError", error: err] // library marker davegut.tpLinkTransKlap, line 277
	} // library marker davegut.tpLinkTransKlap, line 278
	return parseData // library marker davegut.tpLinkTransKlap, line 279
} // library marker davegut.tpLinkTransKlap, line 280

//	See change (error or not) // library marker davegut.tpLinkTransKlap, line 282
def genAuthHash(user, password) { // library marker davegut.tpLinkTransKlap, line 283
	byte[] userHashByte = mdEncode("SHA-1", encodeUtf8(user).getBytes()) // library marker davegut.tpLinkTransKlap, line 284
	byte[] passwordHashByte = mdEncode("SHA-1", encodeUtf8(password.trim()).getBytes()) // library marker davegut.tpLinkTransKlap, line 285
	byte[] authHashByte = [userHashByte, passwordHashByte].flatten() // library marker davegut.tpLinkTransKlap, line 286
//	String authHash = mdEncode("SHA-256", authHashByte).encodeBase64().toString() // library marker davegut.tpLinkTransKlap, line 287
	byte[] authHash = mdEncode("SHA-256", authHashByte) // library marker davegut.tpLinkTransKlap, line 288
	return authHash // library marker davegut.tpLinkTransKlap, line 289
} // library marker davegut.tpLinkTransKlap, line 290

// ~~~~~ end include (803) davegut.tpLinkTransKlap ~~~~~

// ~~~~~ start include (804) davegut.tpLinkTransVacAes ~~~~~
library ( // library marker davegut.tpLinkTransVacAes, line 1
	name: "tpLinkTransVacAes", // library marker davegut.tpLinkTransVacAes, line 2
	namespace: "davegut", // library marker davegut.tpLinkTransVacAes, line 3
	author: "Compiled by Dave Gutheinz", // library marker davegut.tpLinkTransVacAes, line 4
	description: "Handshake methods for TP-Link Integration", // library marker davegut.tpLinkTransVacAes, line 5
	category: "utilities", // library marker davegut.tpLinkTransVacAes, line 6
	documentationLink: "" // library marker davegut.tpLinkTransVacAes, line 7
) // library marker davegut.tpLinkTransVacAes, line 8

//	===== Login ===== // library marker davegut.tpLinkTransVacAes, line 10
def vacAesHandshake(baseUrl, userName, encPasswordVac, devData = null) {  // library marker davegut.tpLinkTransVacAes, line 11
	Map reqData = [baseUrl: baseUrl, devData: devData] // library marker davegut.tpLinkTransVacAes, line 12
	if (device) { // library marker davegut.tpLinkTransVacAes, line 13
		userName = parent.userName // library marker davegut.tpLinkTransVacAes, line 14
		encPasswordVac = parent.encPasswordVac // library marker davegut.tpLinkTransVacAes, line 15
	} // library marker davegut.tpLinkTransVacAes, line 16
	Map cmdBody = [method: "login", // library marker davegut.tpLinkTransVacAes, line 17
				   params: [hashed: true,  // library marker davegut.tpLinkTransVacAes, line 18
							password: encPasswordVac, // library marker davegut.tpLinkTransVacAes, line 19
							username: userName]] // library marker davegut.tpLinkTransVacAes, line 20
	Map reqParams = getVacAesParams(cmdBody, baseUrl) // library marker davegut.tpLinkTransVacAes, line 21
	asynchttpPost("parseVacAesLogin", reqParams, [data: reqData]) // library marker davegut.tpLinkTransVacAes, line 22
} // library marker davegut.tpLinkTransVacAes, line 23

def parseVacAesLogin(resp, data) { // library marker davegut.tpLinkTransVacAes, line 25
	Map logData = [method: "parseVacAesLogin", oldToken: token] // library marker davegut.tpLinkTransVacAes, line 26
	if (resp.status == 200 && resp.json != null) { // library marker davegut.tpLinkTransVacAes, line 27
		def newToken = resp.json.result.token // library marker davegut.tpLinkTransVacAes, line 28
		logData << [status: "OK", token: newToken] // library marker davegut.tpLinkTransVacAes, line 29
		if (device) { // library marker davegut.tpLinkTransVacAes, line 30
			device.updateSetting("token", [type: "string", value: newToken]) // library marker davegut.tpLinkTransVacAes, line 31
			setCommsError(200) // library marker davegut.tpLinkTransVacAes, line 32
		} else { // library marker davegut.tpLinkTransVacAes, line 33
			sendVacAesDataCmd(newToken, data) // library marker davegut.tpLinkTransVacAes, line 34
		}			 // library marker davegut.tpLinkTransVacAes, line 35
		logDebug(logData) // library marker davegut.tpLinkTransVacAes, line 36
	} else { // library marker davegut.tpLinkTransVacAes, line 37
		logData << [respStatus: "ERROR in HTTP response", resp: resp.properties] // library marker davegut.tpLinkTransVacAes, line 38
		logWarn(logData) // library marker davegut.tpLinkTransVacAes, line 39
	} // library marker davegut.tpLinkTransVacAes, line 40
} // library marker davegut.tpLinkTransVacAes, line 41

def getVacAesParams(cmdBody, url) { // library marker davegut.tpLinkTransVacAes, line 43
	Map reqParams = [uri: url, // library marker davegut.tpLinkTransVacAes, line 44
					 body: cmdBody, // library marker davegut.tpLinkTransVacAes, line 45
					 contentType: "application/json", // library marker davegut.tpLinkTransVacAes, line 46
					 requestContentType: "application/json", // library marker davegut.tpLinkTransVacAes, line 47
					 ignoreSSLIssues: true, // library marker davegut.tpLinkTransVacAes, line 48
					 timeout: 10] // library marker davegut.tpLinkTransVacAes, line 49
	return reqParams	 // library marker davegut.tpLinkTransVacAes, line 50
} // library marker davegut.tpLinkTransVacAes, line 51

def parseVacAesData(resp, data) { // library marker davegut.tpLinkTransVacAes, line 53
	Map parseData = [parseMethod: "parseVacAesData", sourceMethod: data.data] // library marker davegut.tpLinkTransVacAes, line 54
	try { // library marker davegut.tpLinkTransVacAes, line 55
		parseData << [cryptoStatus: "OK", cmdResp: resp.json] // library marker davegut.tpLinkTransVacAes, line 56
		logDebug(parseData) // library marker davegut.tpLinkTransVacAes, line 57
	} catch (err) { // library marker davegut.tpLinkTransVacAes, line 58
		parseData << [cryptoStatus: "deviceDataParseError", error: err, dataLength: resp.data.length()] // library marker davegut.tpLinkTransVacAes, line 59
		logWarn(parseData) // library marker davegut.tpLinkTransVacAes, line 60
		handleCommsError() // library marker davegut.tpLinkTransVacAes, line 61
	} // library marker davegut.tpLinkTransVacAes, line 62
	return parseData // library marker davegut.tpLinkTransVacAes, line 63
} // library marker davegut.tpLinkTransVacAes, line 64

// ~~~~~ end include (804) davegut.tpLinkTransVacAes ~~~~~

// ~~~~~ start include (789) davegut.Logging ~~~~~
library ( // library marker davegut.Logging, line 1
	name: "Logging", // library marker davegut.Logging, line 2
	namespace: "davegut", // library marker davegut.Logging, line 3
	author: "Dave Gutheinz", // library marker davegut.Logging, line 4
	description: "Common Logging and info gathering Methods", // library marker davegut.Logging, line 5
	category: "utilities", // library marker davegut.Logging, line 6
	documentationLink: "" // library marker davegut.Logging, line 7
) // library marker davegut.Logging, line 8

def nameSpace() { return "davegut" } // library marker davegut.Logging, line 10

def version() { return "2.4.2a" } // library marker davegut.Logging, line 12

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
	def logData = [infoLog: infoLog, logEnable: logEnable] // library marker davegut.Logging, line 38
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

// ~~~~~ end include (789) davegut.Logging ~~~~~
