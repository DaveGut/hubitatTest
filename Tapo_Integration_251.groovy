/*	Multi-TP-Link Product Integration Application
	Copyright Dave Gutheinz
License:  https://github.com/DaveGut/HubitatActive/blob/master/KasaDevices/License.md
===== Link to Documentation =====
	https://github.com/DaveGut/HubitatActive/blob/master/KasaDevices/Documentation.pdf
========================================*/

//	Change: Add library tpLinkKlap1





import groovy.json.JsonBuilder
import groovy.json.JsonSlurper
import org.json.JSONObject

definition(
	name: "Tapo Integration",
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
	logInfo([method: "installed", status: "Initialized settings"])
}
def updated() {
	app?.removeSetting("selectedAddDevices")
	app?.removeSetting("selectedRemoveDevices")
	app?.updateSetting("logEnable", false)
	app?.updateSetting("appSetup", false)
	app?.updateSetting("scheduled", false)
	state.needCreds = false
	scheduleItems()
	logInfo([method: "updated", status: "setting updated for new session"])
}

def scheduleItems() {
	Map logData = [method: "scheduleItems"]
	unschedule()
	runIn(570, resetTpLinkChecked)
	app?.updateSetting("scheduled", false)
	logData << setLogsOff()
	logInfo(logData)
}

def uninstalled() {
    getAllChildDevices().each { 
        deleteChildDevice(it.deviceNetworkId)
    }
	logInfo([method: "uninstalled", status: "Devices and App uninstalled"])
}

def initInstance() {
	if (!scheduled) {
		unschedule()
		runIn(1800, scheduleItems)
		app?.updateSetting("scheduled", true)
	}
	if (!state.needCreds) { state.needCreds = false }
	state.tpLinkChecked = false
	setSegments()
	if (state.appVersion != version()) {
		state.appVersion = version()
		app.removeSetting("appVer")		//	ver 2.4.1 only
		app.removeSetting("ports")		//	ver 2.4.1 only
		state.remove("portArray")		//	ver 2.4.1 only
		app.removeSetting("showFound")	//	ver 2.4.1 only
		app.removeSetting("startApp")	//	ver 2.4.1 only
		app.removeSetting("finding")	//	ver 2.4.1 only
		atomicState.devices = [:]	//	assures update of devices.
		logInfo([method: "initInstance", status: "App data updated for appVersion ${version()}"])
	}
	if (appSetup) {
		setSegments()
		logInfo([method: "initInstance", status: "Updated App Setup Data"])
	}
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
	} catch (e) {
		logWarn("startPage: Invalid entry for Lan Segements or Host Array Range. Resetting to default!")
		def hub = location.hubs[0]
		def hubIpArray = hub.localIP.split('\\.')
		def segments = [hubIpArray[0],hubIpArray[1],hubIpArray[2]].join(".")
		app?.updateSetting("lanSegment", [type:"string", value: segments])
		app?.updateSetting("hostLimits", [type:"string", value: "1, 254"])
	}
}

def startPage() {
	logInfo([method: "startPage", status: "Starting ${app.getLabel()} Setup"])
	def action = initInstance()
	if (selectedRemoveDevices) { removeDevices() } 
	else if (selectedAddDevices) { addDevices() }
	return dynamicPage(name:"startPage",
					   uninstall: true,
					   install: true) {
		section() {
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
				def wait = createTpLinkCreds()
				logDebug(wait)
				credDesc += "\nEncoded password and username set based on credentials."
				state.needCreds = false
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

def enterCredentialsPage() {
	Map credData = [:]
	return dynamicPage (name: "enterCredentialsPage", 
    					title: "Enter  Credentials",
						nextPage: startPage,
                        install: false) {
		section() {
			input "hidePassword", "bool",
				title: "<b>Hide Password</b>",
				submitOnChange: true,
				defaultValue: false
			paragraph "<b>Password and Username are both case sensitive.</b>"
			def pwdType = "string"
			if (hidePassword) { pwdType = "password" }
			input ("userName", "email",
            		title: "Email Address", 
                    required: false,
                    submitOnChange: false)
			input ("userPassword", pwdType,
            		title: "Account Password",
                    required: false,
                    submitOnChange: false)
		}
	}
}

//	===== Add selected newdevices =====
def addDevicesPage() {
	logDebug("addDevicesPage")
	app?.removeSetting("selectedAddDevices")
	def action = findDevices(5)
	def addDevicesData = atomicState.devices
	Map uninstalledDevices = [:]
	List installedDrivers = getInstalledDrivers()
	Map foundDevices = [:]
	def matterDev = false
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
			if (matterDev == true) {
				paragraph "<b>Caution.</b> Do not install Matter device here if already installed using the Hubitat Matter Ingegration."
			}
			paragraph devicesFound
			href "addDevicesPage",
				title: "<b>Rescan for Additional Devices</b>",
				description: "<b>Perform scan again to try to capture missing devices.</b>"
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

def findDevices(timeout) {
	Map logData = [method: "findDevices", action: "findingDevices"]
	logInfo(logData)
	def await = findTpLinkDevices("getTpLinkLanData", timeout)
	return
}

def supportedProducts() {
	return ["SMART.TAPOBULB", "SMART.TAPOPLUG", "SMART.TAPOSWITCH","SMART.KASAHUB", 
			"SMART.TAPOHUB", "SMART.KASAPLUG", "SMART.KASASWITCH", "SMART.TAPOROBOVAC",
		    "SMART.MATTERBULB", "SMART.MATTERPLUG"]
}

//	===== Add Devices =====
def addDevices() {
	Map logData = [method: "addDevices", selectedDevices: selectedDevices]
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
	Map logData = [method: "addDevice", dni: dni]
	try {
		Map deviceData = [protocol: device.value.protocol]
		deviceData << [baseUrl: device.value.baseUrl,
					   tpLinkType: device.value.deviceType,
					   type: device.value.type,
					   isEm: device.value.isEm,
					   hasLed: device.value.hasLed]
		if (device.value.ctLow != null) {
			deviceData << [ctLow: device.value.ctLow,
						   ctHigh: device.value.ctHigh]
		}
		try {
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
			logData << [status: "failedToAdd", DRIVER: "TpLink ${device.value.type}",
						errorMsg: error, mostLikelyCause: "DRIVER NOT INSTALLED"]
			logWarn(logData)
		}
	} catch (error) {
		logData << [status: "failedToAdd", device: device, errorMsg: error]
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
		def isChild = getChildDevice(dni)
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

// ~~~~~ start include (252) davegut.appTpLinkSmart ~~~~~
library ( // library marker davegut.appTpLinkSmart, line 1
	name: "appTpLinkSmart", // library marker davegut.appTpLinkSmart, line 2
	namespace: "davegut", // library marker davegut.appTpLinkSmart, line 3
	author: "Dave Gutheinz", // library marker davegut.appTpLinkSmart, line 4
	description: "Discovery library for Application support the Tapo protocol devices.", // library marker davegut.appTpLinkSmart, line 5
	category: "utilities", // library marker davegut.appTpLinkSmart, line 6
	documentationLink: "" // library marker davegut.appTpLinkSmart, line 7
) // library marker davegut.appTpLinkSmart, line 8
import org.json.JSONObject // library marker davegut.appTpLinkSmart, line 9
import groovy.json.JsonOutput // library marker davegut.appTpLinkSmart, line 10
import groovy.json.JsonBuilder // library marker davegut.appTpLinkSmart, line 11
import groovy.json.JsonSlurper // library marker davegut.appTpLinkSmart, line 12

def createTpLinkCreds() { // library marker davegut.appTpLinkSmart, line 14
	Map SMARTCredData = [u: userName, p: userPassword] // library marker davegut.appTpLinkSmart, line 15
	//	User Creds (username/password hashed) // library marker davegut.appTpLinkSmart, line 16
	String encUsername = mdEncode("SHA-1", userName.bytes).encodeHex().encodeAsBase64().toString() // library marker davegut.appTpLinkSmart, line 17
	app?.updateSetting("encUsername", [type: "string", value: encUsername]) // library marker davegut.appTpLinkSmart, line 18
	SMARTCredData << [encUsername: encUsername] // library marker davegut.appTpLinkSmart, line 19
	String encPassword = userPassword.trim().bytes.encodeBase64().toString() // library marker davegut.appTpLinkSmart, line 20
	app?.updateSetting("encPassword", [type: "string", value: encPassword]) // library marker davegut.appTpLinkSmart, line 21
	SMARTCredData << [encPassword: encPassword] // library marker davegut.appTpLinkSmart, line 22
	//	AES1 (vacAes) Creds (password only) // library marker davegut.appTpLinkSmart, line 23
	String encPasswordVac = mdEncode("MD5", userPassword.trim().bytes).encodeHex().toString().toUpperCase() // library marker davegut.appTpLinkSmart, line 24
	app?.updateSetting("encPasswordVac", [type: "string", value: encPasswordVac]) // library marker davegut.appTpLinkSmart, line 25
	SMARTCredData << [encPasswordVac: encPasswordVac] // library marker davegut.appTpLinkSmart, line 26
	//	KLAP Local Hash // library marker davegut.appTpLinkSmart, line 27
	byte[] userHashByte = mdEncode("SHA-1", encodeUtf8(userName).getBytes()) // library marker davegut.appTpLinkSmart, line 28
	byte[] passwordHashByte = mdEncode("SHA-1", encodeUtf8(userPassword.trim()).getBytes()) // library marker davegut.appTpLinkSmart, line 29
	byte[] authHashByte = [userHashByte, passwordHashByte].flatten() // library marker davegut.appTpLinkSmart, line 30
	String authHash = mdEncode("SHA-256", authHashByte).encodeBase64().toString() // library marker davegut.appTpLinkSmart, line 31
	app?.updateSetting("localHash", [type: "string", value: authHash]) // library marker davegut.appTpLinkSmart, line 32
//	app?.updateSetting("authHashKlap2", [type: "string", value: authHash]) // library marker davegut.appTpLinkSmart, line 33
	SMARTCredData << [localHash: localHash] // library marker davegut.appTpLinkSmart, line 34
	//	KLAP1 Local Hash // library marker davegut.appTpLinkSmart, line 35
	userHashByte = mdEncode("MD5", encodeUtf8(userName).getBytes()) // library marker davegut.appTpLinkSmart, line 36
	passwordHashByte = mdEncode("MD5", encodeUtf8(userPassword.trim()).getBytes()) // library marker davegut.appTpLinkSmart, line 37
	authHashByte = [userHashByte, passwordHashByte].flatten() // library marker davegut.appTpLinkSmart, line 38
	authHash = mdEncode("MD5", authHashByte).encodeBase64().toString() // library marker davegut.appTpLinkSmart, line 39
	app?.updateSetting("localHash1", [type: "string", value: authHash]) // library marker davegut.appTpLinkSmart, line 40
//	app?.updateSetting("authHashKlap1", [type: "string", value: authHash]) // library marker davegut.appTpLinkSmart, line 41
	SMARTCredData << [authHashKlap1: authHashKlap1] // library marker davegut.appTpLinkSmart, line 42
////////////////////// // library marker davegut.appTpLinkSmart, line 43
log.trace SMARTCredData // library marker davegut.appTpLinkSmart, line 44
////////////////////// // library marker davegut.appTpLinkSmart, line 45
	logDebug(SMARTCredData) // library marker davegut.appTpLinkSmart, line 46
	return [SMARTDevCreds: SMARTCredData] // library marker davegut.appTpLinkSmart, line 47
} // library marker davegut.appTpLinkSmart, line 48

def findTpLinkDevices(action, timeout = 10) { // library marker davegut.appTpLinkSmart, line 50
	Map logData = [method: "findTpLinkDevices", action: action, timeOut: timeout] // library marker davegut.appTpLinkSmart, line 51
	def start = state.hostArray.min().toInteger() // library marker davegut.appTpLinkSmart, line 52
	def finish = state.hostArray.max().toInteger() + 1 // library marker davegut.appTpLinkSmart, line 53
	logData << [hostArray: state.hostArray, pollSegments: state.segArray] // library marker davegut.appTpLinkSmart, line 54
	List deviceIPs = [] // library marker davegut.appTpLinkSmart, line 55
	state.segArray.each { // library marker davegut.appTpLinkSmart, line 56
		def pollSegment = it.trim() // library marker davegut.appTpLinkSmart, line 57
		logData << [pollSegment: pollSegment] // library marker davegut.appTpLinkSmart, line 58
           for(int i = start; i < finish; i++) { // library marker davegut.appTpLinkSmart, line 59
			deviceIPs.add("${pollSegment}.${i.toString()}") // library marker davegut.appTpLinkSmart, line 60
		} // library marker davegut.appTpLinkSmart, line 61
		def cmdData = "0200000101e51100095c11706d6f58577b22706172616d73223a7b227273615f6b6579223a222d2d2d2d2d424547494e205055424c4943204b45592d2d2d2d2d5c6e4d494942496a414e42676b71686b6947397730424151454641414f43415138414d49494243674b43415145416d684655445279687367797073467936576c4d385c6e54646154397a61586133586a3042712f4d6f484971696d586e2b736b4e48584d525a6550564134627532416257386d79744a5033445073665173795679536e355c6e6f425841674d303149674d4f46736350316258367679784d523871614b33746e466361665a4653684d79536e31752f564f2f47474f795436507459716f384e315c6e44714d77373563334b5a4952387a4c71516f744657747239543337536e50754a7051555a7055376679574b676377716e7338785a657a78734e6a6465534171765c6e3167574e75436a5356686d437931564d49514942576d616a37414c47544971596a5442376d645348562f2b614a32564467424c6d7770344c7131664c4f6a466f5c6e33737241683144744a6b537376376a624f584d51695666453873764b6877586177717661546b5658382f7a4f44592b2f64684f5374694a4e6c466556636c35585c6e4a514944415141425c6e2d2d2d2d2d454e44205055424c4943204b45592d2d2d2d2d5c6e227d7d" // library marker davegut.appTpLinkSmart, line 62
		await = sendLanCmd(deviceIPs.join(','), "20002", cmdData, action, timeout) // library marker davegut.appTpLinkSmart, line 63
		atomicState.finding = true // library marker davegut.appTpLinkSmart, line 64
		int i // library marker davegut.appTpLinkSmart, line 65
		for(i = 0; i < 60; i+=5) { // library marker davegut.appTpLinkSmart, line 66
			pauseExecution(5000) // library marker davegut.appTpLinkSmart, line 67
			if (atomicState.finding == false) { // library marker davegut.appTpLinkSmart, line 68
				logInfo("<b>FindingDevices: Finished Finding</b>") // library marker davegut.appTpLinkSmart, line 69
				pauseExecution(5000) // library marker davegut.appTpLinkSmart, line 70
				i = 61 // library marker davegut.appTpLinkSmart, line 71
				break // library marker davegut.appTpLinkSmart, line 72
			} // library marker davegut.appTpLinkSmart, line 73
			logInfo("<b>FindingDevices: ${i} seconds</b>") // library marker davegut.appTpLinkSmart, line 74
		} // library marker davegut.appTpLinkSmart, line 75
	} // library marker davegut.appTpLinkSmart, line 76
	logDebug(logData) // library marker davegut.appTpLinkSmart, line 77
	return logData // library marker davegut.appTpLinkSmart, line 78
} // library marker davegut.appTpLinkSmart, line 79

def getTpLinkLanData(response) { // library marker davegut.appTpLinkSmart, line 81
	Map logData = [method: "getTpLinkLanData",  // library marker davegut.appTpLinkSmart, line 82
				   action: "Completed LAN Discovery", // library marker davegut.appTpLinkSmart, line 83
				   smartDevicesFound: response.size()] // library marker davegut.appTpLinkSmart, line 84
	logInfo(logData) // library marker davegut.appTpLinkSmart, line 85
	List discData = [] // library marker davegut.appTpLinkSmart, line 86
	if (response instanceof Map) { // library marker davegut.appTpLinkSmart, line 87
		Map devData = getDiscData(response) // library marker davegut.appTpLinkSmart, line 88
		if (devData.status == "OK") { // library marker davegut.appTpLinkSmart, line 89
			discData << devData // library marker davegut.appTpLinkSmart, line 90
		} // library marker davegut.appTpLinkSmart, line 91
	} else { // library marker davegut.appTpLinkSmart, line 92
		response.each { // library marker davegut.appTpLinkSmart, line 93
			Map devData = getDiscData(it) // library marker davegut.appTpLinkSmart, line 94
			if (devData.status == "OK") { // library marker davegut.appTpLinkSmart, line 95
				discData << devData // library marker davegut.appTpLinkSmart, line 96
			} // library marker davegut.appTpLinkSmart, line 97
		} // library marker davegut.appTpLinkSmart, line 98
	} // library marker davegut.appTpLinkSmart, line 99
	getAllTpLinkDeviceData(discData) // library marker davegut.appTpLinkSmart, line 100
	app?.updateSetting("finding", false) // library marker davegut.appTpLinkSmart, line 101
	runIn(5, updateTpLinkDevices, [data: discData]) // library marker davegut.appTpLinkSmart, line 102
////////////////  For running test on data during development // library marker davegut.appTpLinkSmart, line 103
//runIn(5, testKlap2) // library marker davegut.appTpLinkSmart, line 104
/////////////////	 // library marker davegut.appTpLinkSmart, line 105
} // library marker davegut.appTpLinkSmart, line 106

def getDiscData(response) { // library marker davegut.appTpLinkSmart, line 108
	//	Changes:  Added KLAP1, changed vacAes to AES1.  Redid logic to use // library marker davegut.appTpLinkSmart, line 109
	//		https boolean to create baseUrl.  Use protocol level in  // library marker davegut.appTpLinkSmart, line 110
	//		deciding whether AES1/AES and KLAP1/KLAP. // library marker davegut.appTpLinkSmart, line 111
	Map devData = [method: "getDiscData"] // library marker davegut.appTpLinkSmart, line 112
	try { // library marker davegut.appTpLinkSmart, line 113
		def respData = parseLanMessage(response.description) // library marker davegut.appTpLinkSmart, line 114
		if (respData.type == "LAN_TYPE_UDPCLIENT") { // library marker davegut.appTpLinkSmart, line 115
			byte[] payloadByte = hubitat.helper.HexUtils.hexStringToByteArray(respData.payload.drop(32))  // library marker davegut.appTpLinkSmart, line 116
			String payloadString = new String(payloadByte) // library marker davegut.appTpLinkSmart, line 117
			if (payloadString.length() > 1007) { // library marker davegut.appTpLinkSmart, line 118
				payloadString = payloadString + """"}}}""" // library marker davegut.appTpLinkSmart, line 119
			} // library marker davegut.appTpLinkSmart, line 120
			Map payload = new JsonSlurper().parseText(payloadString).result // library marker davegut.appTpLinkSmart, line 121
			List supported = supportedProducts() // library marker davegut.appTpLinkSmart, line 122
			String devType = payload.device_type // library marker davegut.appTpLinkSmart, line 123
			String model = payload.device_model // library marker davegut.appTpLinkSmart, line 124
			if (supported.contains(devType)) { // library marker davegut.appTpLinkSmart, line 125
				if (!payload.mgt_encrypt_schm.encrypt_type) { // library marker davegut.appTpLinkSmart, line 126
					String mssg = "<b>The ${model} is not supported " // library marker davegut.appTpLinkSmart, line 127
					mssg += "by this integration version.</b>" // library marker davegut.appTpLinkSmart, line 128
					devData << [payload: payload, status: "INVALID", reason: "Device not supported."] // library marker davegut.appTpLinkSmart, line 129
					logWarn(mssg) // library marker davegut.appTpLinkSmart, line 130
					return devData // library marker davegut.appTpLinkSmart, line 131
				} // library marker davegut.appTpLinkSmart, line 132
				String protocol = payload.mgt_encrypt_schm.encrypt_type // library marker davegut.appTpLinkSmart, line 133
				String level = payload.mgt_encrypt_schm.lv // library marker davegut.appTpLinkSmart, line 134
				def isHttps = payload.mgt_encrypt_schm.is_support_https // library marker davegut.appTpLinkSmart, line 135
				String port = payload.mgt_encrypt_schm.http_port // library marker davegut.appTpLinkSmart, line 136
				String devIp = payload.ip // library marker davegut.appTpLinkSmart, line 137
				String dni = payload.mac.replaceAll("-", "") // library marker davegut.appTpLinkSmart, line 138

				String prot = "http://" // library marker davegut.appTpLinkSmart, line 140
				if (isHttps) { prot = "https://" } // library marker davegut.appTpLinkSmart, line 141
				String baseUrl = "${prot}${devIp}:${port}/app" // library marker davegut.appTpLinkSmart, line 142
				if (protocol == "KLAP" && level == null) { // library marker davegut.appTpLinkSmart, line 143
					protocol = "KLAP1"	//	legacy KLAP Protocol implementation // library marker davegut.appTpLinkSmart, line 144
				} else if (protocol == "AES" && level == null) { // library marker davegut.appTpLinkSmart, line 145
					protocol = "AES1"	//	legacy AES protocol, aka vacAES in this app. // library marker davegut.appTpLinkSmart, line 146
					baseUrl = "${prot}${devIp}:${port}" // library marker davegut.appTpLinkSmart, line 147
				} // library marker davegut.appTpLinkSmart, line 148
				devData << [type: devType, model: model, baseUrl: baseUrl, dni: dni,  // library marker davegut.appTpLinkSmart, line 149
							devId: payload.device_id, ip: devIp, port: port,  // library marker davegut.appTpLinkSmart, line 150
							protocol: protocol, status: "OK"] // library marker davegut.appTpLinkSmart, line 151
			} else { // library marker davegut.appTpLinkSmart, line 152
				devData << [type: devType, model: model, status: "INVALID",  // library marker davegut.appTpLinkSmart, line 153
							reason: "Device not supported.", payload: payload] // library marker davegut.appTpLinkSmart, line 154
				logWarn(devData) // library marker davegut.appTpLinkSmart, line 155
			} // library marker davegut.appTpLinkSmart, line 156
		} // library marker davegut.appTpLinkSmart, line 157
		logDebug(devData) // library marker davegut.appTpLinkSmart, line 158
	} catch (err) { // library marker davegut.appTpLinkSmart, line 159
		devData << [status: "INVALID", respData: repsData, error: err] // library marker davegut.appTpLinkSmart, line 160
		logWarn(devData) // library marker davegut.appTpLinkSmart, line 161
	} // library marker davegut.appTpLinkSmart, line 162
	return devData // library marker davegut.appTpLinkSmart, line 163
} // library marker davegut.appTpLinkSmart, line 164

def getAllTpLinkDeviceData(List discData) { // library marker davegut.appTpLinkSmart, line 166
	//	Changes:  Addes KLAP1 and changed vacAes to AES1 for distributing to handshakes. // library marker davegut.appTpLinkSmart, line 167
	Map logData = [method: "getAllTpLinkDeviceData", discData: discData.size()] // library marker davegut.appTpLinkSmart, line 168
	discData.each { Map devData -> // library marker davegut.appTpLinkSmart, line 169
		if (devData.protocol == "KLAP") { // library marker davegut.appTpLinkSmart, line 170
			klapHandshake(devData.baseUrl, localHash, devData) // library marker davegut.appTpLinkSmart, line 171
		} else if (devData.protocol == "KLAP1") { // library marker davegut.appTpLinkSmart, line 172
			klap1Handshake(devData.baseUrl, devData) // library marker davegut.appTpLinkSmart, line 173
		} else if (devData.protocol == "AES") { // library marker davegut.appTpLinkSmart, line 174
			aesHandshake(devData.baseUrl, devData) // library marker davegut.appTpLinkSmart, line 175
		} else if (devData.protocol == "AES1") { // library marker davegut.appTpLinkSmart, line 176
			aes1Handshake(devData.baseUrl, devData) // library marker davegut.appTpLinkSmart, line 177
		} else {  // library marker davegut.appTpLinkSmart, line 178
			logData << [ERROR: "Unknown Protocol", discData: discData] // library marker davegut.appTpLinkSmart, line 179
			logWarn(logData) // library marker davegut.appTpLinkSmart, line 180
		} // library marker davegut.appTpLinkSmart, line 181
		pauseExecution(1000) // library marker davegut.appTpLinkSmart, line 182
	} // library marker davegut.appTpLinkSmart, line 183
	atomicState.finding = false // library marker davegut.appTpLinkSmart, line 184
	logDebug(logData) // library marker davegut.appTpLinkSmart, line 185
} // library marker davegut.appTpLinkSmart, line 186

def getDataCmd() { // library marker davegut.appTpLinkSmart, line 188
	List requests = [[method: "get_device_info"]] // library marker davegut.appTpLinkSmart, line 189
	requests << [method: "component_nego"] // library marker davegut.appTpLinkSmart, line 190
	Map cmdBody = [ // library marker davegut.appTpLinkSmart, line 191
		method: "multipleRequest", // library marker davegut.appTpLinkSmart, line 192
		params: [requests: requests]] // library marker davegut.appTpLinkSmart, line 193
	return cmdBody // library marker davegut.appTpLinkSmart, line 194
} // library marker davegut.appTpLinkSmart, line 195

def addToDevices(devData, cmdResp) { // library marker davegut.appTpLinkSmart, line 197
	Map logData = [method: "addToDevices"] // library marker davegut.appTpLinkSmart, line 198
	String dni = devData.dni // library marker davegut.appTpLinkSmart, line 199
	def devicesData = atomicState.devices // library marker davegut.appTpLinkSmart, line 200
	def components = cmdResp.find { it.method == "component_nego" } // library marker davegut.appTpLinkSmart, line 201
	cmdResp = cmdResp.find { it.method == "get_device_info" } // library marker davegut.appTpLinkSmart, line 202
	cmdResp = cmdResp.result // library marker davegut.appTpLinkSmart, line 203
	byte[] plainBytes = cmdResp.nickname.decodeBase64() // library marker davegut.appTpLinkSmart, line 204
	def alias = new String(plainBytes) // library marker davegut.appTpLinkSmart, line 205
	if (alias == "") { alias = cmdResp.model } // library marker davegut.appTpLinkSmart, line 206
	def comps = components.result.component_list // library marker davegut.appTpLinkSmart, line 207
	String tpType = devData.type // library marker davegut.appTpLinkSmart, line 208
	def type = "Unknown" // library marker davegut.appTpLinkSmart, line 209
	def ctHigh // library marker davegut.appTpLinkSmart, line 210
	def ctLow // library marker davegut.appTpLinkSmart, line 211
	//	Creat map deviceData // library marker davegut.appTpLinkSmart, line 212
	Map deviceData = [deviceType: tpType, protocol: devData.protocol, // library marker davegut.appTpLinkSmart, line 213
				   model: devData.model, baseUrl: devData.baseUrl, alias: alias] // library marker davegut.appTpLinkSmart, line 214
	//	Determine Driver to Load // library marker davegut.appTpLinkSmart, line 215
	if (tpType.contains("PLUG") || tpType.contains("SWITCH")) { // library marker davegut.appTpLinkSmart, line 216
		type = "Plug" // library marker davegut.appTpLinkSmart, line 217
		if (comps.find { it.id == "control_child" }) { // library marker davegut.appTpLinkSmart, line 218
			type = "Parent" // library marker davegut.appTpLinkSmart, line 219
		} else if (comps.find { it.id == "dimmer" }) { // library marker davegut.appTpLinkSmart, line 220
			type = "Dimmer" // library marker davegut.appTpLinkSmart, line 221
		} // library marker davegut.appTpLinkSmart, line 222
	} else if (tpType.contains("HUB")) { // library marker davegut.appTpLinkSmart, line 223
		type = "Hub" // library marker davegut.appTpLinkSmart, line 224
	} else if (tpType.contains("BULB")) { // library marker davegut.appTpLinkSmart, line 225
		type = "Dimmer" // library marker davegut.appTpLinkSmart, line 226
		if (comps.find { it.id == "light_strip" }) { // library marker davegut.appTpLinkSmart, line 227
			type = "Lightstrip" // library marker davegut.appTpLinkSmart, line 228
		} else if (comps.find { it.id == "color" }) { // library marker davegut.appTpLinkSmart, line 229
			type = "Color Bulb" // library marker davegut.appTpLinkSmart, line 230
		} // library marker davegut.appTpLinkSmart, line 231
		//	Get color temp range for Bulb and Lightstrip // library marker davegut.appTpLinkSmart, line 232
		if (type != "Dimmer" && comps.find { it.id == "color_temperature" } ) { // library marker davegut.appTpLinkSmart, line 233
			ctHigh = cmdResp.color_temp_range[1] // library marker davegut.appTpLinkSmart, line 234
			ctLow = cmdResp.color_temp_range[0] // library marker davegut.appTpLinkSmart, line 235
			deviceData << [ctHigh: ctHigh, ctLow: ctLow] // library marker davegut.appTpLinkSmart, line 236
		} // library marker davegut.appTpLinkSmart, line 237
	} else if (tpType.contains("ROBOVAC")) { // library marker davegut.appTpLinkSmart, line 238
		type = "Robovac" // library marker davegut.appTpLinkSmart, line 239
	} // library marker davegut.appTpLinkSmart, line 240
	//	Determine device-specific data relative to device settings // library marker davegut.appTpLinkSmart, line 241
	def hasLed = "false" // library marker davegut.appTpLinkSmart, line 242
	if (comps.find { it.id == "led" } ) { hasLed = "true" } // library marker davegut.appTpLinkSmart, line 243
	def isEm = "false" // library marker davegut.appTpLinkSmart, line 244
	if (comps.find { it.id == "energy_monitoring" } ) { isEm = "true" } // library marker davegut.appTpLinkSmart, line 245
	def gradOnOff = "false" // library marker davegut.appTpLinkSmart, line 246
	if (comps.find { it.id == "on_off_gradually" } ) { gradOnOff = "true" } // library marker davegut.appTpLinkSmart, line 247
	deviceData << [type: type, hasLed: hasLed, isEm: isEm, gradOnOff: gradOnOff] // library marker davegut.appTpLinkSmart, line 248
	//	Add to devices and close out method // library marker davegut.appTpLinkSmart, line 249
	devicesData << ["${dni}": deviceData] // library marker davegut.appTpLinkSmart, line 250
	atomicState.devices = devicesData // library marker davegut.appTpLinkSmart, line 251
	logData << ["${deviceData.alias}": deviceData, dni: dni] // library marker davegut.appTpLinkSmart, line 252
	Map InfoData = ["${deviceData.alias}": "added to device data"] // library marker davegut.appTpLinkSmart, line 253
	logInfo("${deviceData.alias}: added to device data") // library marker davegut.appTpLinkSmart, line 254
	updateChild(dni,deviceData) // library marker davegut.appTpLinkSmart, line 255
	logDebug(logData) // library marker davegut.appTpLinkSmart, line 256
} // library marker davegut.appTpLinkSmart, line 257

def updateChild(dni, deviceData) { // library marker davegut.appTpLinkSmart, line 259
	def child = getChildDevice(dni) // library marker davegut.appTpLinkSmart, line 260
	if (child) { // library marker davegut.appTpLinkSmart, line 261
		child.updateChild(deviceData) // library marker davegut.appTpLinkSmart, line 262
	} // library marker davegut.appTpLinkSmart, line 263
} // library marker davegut.appTpLinkSmart, line 264

//	===== get Smart KLAP Protocol Data ===== // library marker davegut.appTpLinkSmart, line 266
def sendKlapDataCmd(handshakeData, data) { // library marker davegut.appTpLinkSmart, line 267
	if (handshakeData.respStatus != "Login OK") { // library marker davegut.appTpLinkSmart, line 268
		Map logData = [method: "sendKlapDataCmd", handshake: handshakeData] // library marker davegut.appTpLinkSmart, line 269
		logWarn(logData) // library marker davegut.appTpLinkSmart, line 270
	} else { // library marker davegut.appTpLinkSmart, line 271
		Map reqParams = [timeout: 10, headers: ["Cookie": data.data.cookie]] // library marker davegut.appTpLinkSmart, line 272
		def seqNo = data.data.seqNo + 1 // library marker davegut.appTpLinkSmart, line 273
		String cmdBodyJson = new groovy.json.JsonBuilder(getDataCmd()).toString() // library marker davegut.appTpLinkSmart, line 274
		Map encryptedData = klapEncrypt(cmdBodyJson.getBytes(), data.data.encKey,  // library marker davegut.appTpLinkSmart, line 275
										data.data.encIv, data.data.encSig, seqNo) // library marker davegut.appTpLinkSmart, line 276
		reqParams << [uri: "${data.data.baseUrl}/request?seq=${encryptedData.seqNumber}", // library marker davegut.appTpLinkSmart, line 277
					  body: encryptedData.cipherData, // library marker davegut.appTpLinkSmart, line 278
					  contentType: "application/octet-stream", // library marker davegut.appTpLinkSmart, line 279
					  requestContentType: "application/octet-stream"] // library marker davegut.appTpLinkSmart, line 280
		asynchttpPost("parseKlapResp", reqParams, [data: data.data]) // library marker davegut.appTpLinkSmart, line 281
	} // library marker davegut.appTpLinkSmart, line 282
} // library marker davegut.appTpLinkSmart, line 283

def parseKlapResp(resp, data) { // library marker davegut.appTpLinkSmart, line 285
	Map logData = [method: "parseKlapResp"] // library marker davegut.appTpLinkSmart, line 286
	if (resp.status == 200) { // library marker davegut.appTpLinkSmart, line 287
		try { // library marker davegut.appTpLinkSmart, line 288
			byte[] cipherResponse = resp.data.decodeBase64()[32..-1] // library marker davegut.appTpLinkSmart, line 289
			def clearResp = klapDecrypt(cipherResponse, data.data.encKey, // library marker davegut.appTpLinkSmart, line 290
										data.data.encIv, data.data.seqNo + 1) // library marker davegut.appTpLinkSmart, line 291
			Map cmdResp =  new JsonSlurper().parseText(clearResp) // library marker davegut.appTpLinkSmart, line 292
			logData << [status: "OK", cmdResp: cmdResp] // library marker davegut.appTpLinkSmart, line 293
			if (cmdResp.error_code == 0) { // library marker davegut.appTpLinkSmart, line 294
				addToDevices(data.data.devData, cmdResp.result.responses) // library marker davegut.appTpLinkSmart, line 295
				logDebug(logData) // library marker davegut.appTpLinkSmart, line 296
			} else { // library marker davegut.appTpLinkSmart, line 297
				logData << [status: "errorInCmdResp"] // library marker davegut.appTpLinkSmart, line 298
				logWarn(logData) // library marker davegut.appTpLinkSmart, line 299
			} // library marker davegut.appTpLinkSmart, line 300
		} catch (err) { // library marker davegut.appTpLinkSmart, line 301
			logData << [status: "deviceDataParseError", error: err, dataLength: resp.data.length()] // library marker davegut.appTpLinkSmart, line 302
			logWarn(logData) // library marker davegut.appTpLinkSmart, line 303
		} // library marker davegut.appTpLinkSmart, line 304
	} else { // library marker davegut.appTpLinkSmart, line 305
		logData << [status: "httpFailure", data: resp.properties] // library marker davegut.appTpLinkSmart, line 306
		logWarn(logData) // library marker davegut.appTpLinkSmart, line 307
	} // library marker davegut.appTpLinkSmart, line 308
} // library marker davegut.appTpLinkSmart, line 309

//	===== get Smart KLAP1 Protocol Data ===== // library marker davegut.appTpLinkSmart, line 311
def sendKlap1DataCmd(handshakeData, data) { // library marker davegut.appTpLinkSmart, line 312
	//	New section created by copying sendKlapDataCmd and modifying. // library marker davegut.appTpLinkSmart, line 313
	if (handshakeData.respStatus != "Login OK") { // library marker davegut.appTpLinkSmart, line 314
		Map logData = [method: "sendKlap1DataCmd", handshake: handshakeData] // library marker davegut.appTpLinkSmart, line 315
		logWarn(logData) // library marker davegut.appTpLinkSmart, line 316
	} else { // library marker davegut.appTpLinkSmart, line 317
		def seqNo = data.data.seqNo + 1 // library marker davegut.appTpLinkSmart, line 318
		String cmdBodyJson = new groovy.json.JsonBuilder(getDataCmd()).toString() // library marker davegut.appTpLinkSmart, line 319
		Map encryptedData = klap1Encrypt(cmdBodyJson.getBytes(), data.data.encKey,  // library marker davegut.appTpLinkSmart, line 320
										data.data.encIv, data.data.encSig, seqNo) // library marker davegut.appTpLinkSmart, line 321
		Map reqParams = [ // library marker davegut.appTpLinkSmart, line 322
			uri: "${data.data.baseUrl}/request?seq=${encryptedData.seqNumber}", // library marker davegut.appTpLinkSmart, line 323
			body: encryptedData.cipherData, // library marker davegut.appTpLinkSmart, line 324
			ignoreSSLIssues: true, // library marker davegut.appTpLinkSmart, line 325
			timeout:10, // library marker davegut.appTpLinkSmart, line 326
			headers: ["Cookie": cookie], // library marker davegut.appTpLinkSmart, line 327
			contentType: "application/octet-stream", // library marker davegut.appTpLinkSmart, line 328
			requestContentType: "application/octet-stream"] // library marker davegut.appTpLinkSmart, line 329
		asynchttpPost("parseKlap1Resp", reqParams, [data: data.data]) // library marker davegut.appTpLinkSmart, line 330
	} // library marker davegut.appTpLinkSmart, line 331
} // library marker davegut.appTpLinkSmart, line 332

def parseKlap1Resp(resp, data) { // library marker davegut.appTpLinkSmart, line 334
	//	Simple copy of parseKlapResp // library marker davegut.appTpLinkSmart, line 335
	Map logData = [method: "parseKlap1Resp"] // library marker davegut.appTpLinkSmart, line 336
	if (resp.status == 200) { // library marker davegut.appTpLinkSmart, line 337
		try { // library marker davegut.appTpLinkSmart, line 338
			byte[] cipherResponse = resp.data.decodeBase64()[32..-1] // library marker davegut.appTpLinkSmart, line 339
			def clearResp = klap1Decrypt(cipherResponse, data.data.encKey, // library marker davegut.appTpLinkSmart, line 340
										data.data.encIv, data.data.seqNo + 1) // library marker davegut.appTpLinkSmart, line 341
			Map cmdResp =  new JsonSlurper().parseText(clearResp) // library marker davegut.appTpLinkSmart, line 342
			logData << [status: "OK", cmdResp: cmdResp] // library marker davegut.appTpLinkSmart, line 343
			if (cmdResp.error_code == 0) { // library marker davegut.appTpLinkSmart, line 344
				addToDevices(data.data.devData, cmdResp.result.responses) // library marker davegut.appTpLinkSmart, line 345
				logDebug(logData) // library marker davegut.appTpLinkSmart, line 346
			} else { // library marker davegut.appTpLinkSmart, line 347
				logData << [status: "errorInCmdResp"] // library marker davegut.appTpLinkSmart, line 348
				logWarn(logData) // library marker davegut.appTpLinkSmart, line 349
			} // library marker davegut.appTpLinkSmart, line 350
		} catch (err) { // library marker davegut.appTpLinkSmart, line 351
			logData << [status: "deviceDataParseError", error: err, dataLength: resp.data.length()] // library marker davegut.appTpLinkSmart, line 352
			logWarn(logData) // library marker davegut.appTpLinkSmart, line 353
		} // library marker davegut.appTpLinkSmart, line 354
	} else { // library marker davegut.appTpLinkSmart, line 355
		logData << [status: "httpFailure", data: resp.properties] // library marker davegut.appTpLinkSmart, line 356
		logWarn(logData) // library marker davegut.appTpLinkSmart, line 357
	} // library marker davegut.appTpLinkSmart, line 358
} // library marker davegut.appTpLinkSmart, line 359

//	===== get Smart AES Protocol Data ===== // library marker davegut.appTpLinkSmart, line 361
def getAesToken(resp, data) { // library marker davegut.appTpLinkSmart, line 362
	Map logData = [method: "getAesToken"] // library marker davegut.appTpLinkSmart, line 363
	if (resp.status == 200) { // library marker davegut.appTpLinkSmart, line 364
		if (resp.json.error_code == 0) { // library marker davegut.appTpLinkSmart, line 365
			try { // library marker davegut.appTpLinkSmart, line 366
				def clearResp = aesDecrypt(resp.json.result.response, data.encKey, data.encIv) // library marker davegut.appTpLinkSmart, line 367
				Map cmdResp = new JsonSlurper().parseText(clearResp) // library marker davegut.appTpLinkSmart, line 368
				if (cmdResp.error_code == 0) { // library marker davegut.appTpLinkSmart, line 369
					def token = cmdResp.result.token // library marker davegut.appTpLinkSmart, line 370
					logData << [respStatus: "OK", token: token] // library marker davegut.appTpLinkSmart, line 371
					logDebug(logData) // library marker davegut.appTpLinkSmart, line 372
					sendAesDataCmd(token, data) // library marker davegut.appTpLinkSmart, line 373
				} else { // library marker davegut.appTpLinkSmart, line 374
					logData << [respStatus: "ERROR code in cmdResp",  // library marker davegut.appTpLinkSmart, line 375
								error_code: cmdResp.error_code, // library marker davegut.appTpLinkSmart, line 376
								check: "cryptoArray, credentials", data: cmdResp] // library marker davegut.appTpLinkSmart, line 377
					logWarn(logData) // library marker davegut.appTpLinkSmart, line 378
				} // library marker davegut.appTpLinkSmart, line 379
			} catch (err) { // library marker davegut.appTpLinkSmart, line 380
				logData << [respStatus: "ERROR parsing respJson", respJson: resp.json, // library marker davegut.appTpLinkSmart, line 381
							error: err] // library marker davegut.appTpLinkSmart, line 382
				logWarn(logData) // library marker davegut.appTpLinkSmart, line 383
			} // library marker davegut.appTpLinkSmart, line 384
		} else { // library marker davegut.appTpLinkSmart, line 385
			logData << [respStatus: "ERROR code in resp.json", errorCode: resp.json.error_code, // library marker davegut.appTpLinkSmart, line 386
						respJson: resp.json] // library marker davegut.appTpLinkSmart, line 387
			logWarn(logData) // library marker davegut.appTpLinkSmart, line 388
		} // library marker davegut.appTpLinkSmart, line 389
	} else { // library marker davegut.appTpLinkSmart, line 390
		logData << [respStatus: "ERROR in HTTP response", respStatus: resp.status, data: resp.properties] // library marker davegut.appTpLinkSmart, line 391
		logWarn(logData) // library marker davegut.appTpLinkSmart, line 392
	} // library marker davegut.appTpLinkSmart, line 393
} // library marker davegut.appTpLinkSmart, line 394

def sendAesDataCmd(token, data) { // library marker davegut.appTpLinkSmart, line 396
	def cmdStr = JsonOutput.toJson(getDataCmd()).toString() // library marker davegut.appTpLinkSmart, line 397
	Map reqBody = [method: "securePassthrough", // library marker davegut.appTpLinkSmart, line 398
				   params: [request: aesEncrypt(cmdStr, data.encKey, data.encIv)]] // library marker davegut.appTpLinkSmart, line 399
	Map reqParams = [uri: "${data.baseUrl}?token=${token}", // library marker davegut.appTpLinkSmart, line 400
					 body: new groovy.json.JsonBuilder(reqBody).toString(), // library marker davegut.appTpLinkSmart, line 401
					 contentType: "application/json", // library marker davegut.appTpLinkSmart, line 402
					 requestContentType: "application/json", // library marker davegut.appTpLinkSmart, line 403
					 timeout: 10,  // library marker davegut.appTpLinkSmart, line 404
					 headers: ["Cookie": data.cookie]] // library marker davegut.appTpLinkSmart, line 405
	asynchttpPost("parseAesResp", reqParams, [data: data]) // library marker davegut.appTpLinkSmart, line 406
} // library marker davegut.appTpLinkSmart, line 407

def parseAesResp(resp, data) { // library marker davegut.appTpLinkSmart, line 409
	Map logData = [method: "parseAesResp"] // library marker davegut.appTpLinkSmart, line 410
	if (resp.status == 200) { // library marker davegut.appTpLinkSmart, line 411
		try { // library marker davegut.appTpLinkSmart, line 412
			Map cmdResp = new JsonSlurper().parseText(aesDecrypt(resp.json.result.response, // library marker davegut.appTpLinkSmart, line 413
																 data.data.encKey, data.data.encIv)) // library marker davegut.appTpLinkSmart, line 414
			logData << [status: "OK", cmdResp: cmdResp] // library marker davegut.appTpLinkSmart, line 415
			if (cmdResp.error_code == 0) { // library marker davegut.appTpLinkSmart, line 416
				addToDevices(data.data.devData, cmdResp.result.responses) // library marker davegut.appTpLinkSmart, line 417
				logDebug(logData) // library marker davegut.appTpLinkSmart, line 418
			} else { // library marker davegut.appTpLinkSmart, line 419
				logData << [status: "errorInCmdResp"] // library marker davegut.appTpLinkSmart, line 420
				logWarn(logData) // library marker davegut.appTpLinkSmart, line 421
			} // library marker davegut.appTpLinkSmart, line 422
		} catch (err) { // library marker davegut.appTpLinkSmart, line 423
			logData << [status: "deviceDataParseError", error: err, dataLength: resp.data.length()] // library marker davegut.appTpLinkSmart, line 424
			logWarn(logData) // library marker davegut.appTpLinkSmart, line 425
		} // library marker davegut.appTpLinkSmart, line 426
	} else { // library marker davegut.appTpLinkSmart, line 427
		logData << [status: "httpFailure", data: resp.properties] // library marker davegut.appTpLinkSmart, line 428
		logWarn(logData) // library marker davegut.appTpLinkSmart, line 429
	} // library marker davegut.appTpLinkSmart, line 430
} // library marker davegut.appTpLinkSmart, line 431

//	===== get Smart AES1 Protocol Data ===== // library marker davegut.appTpLinkSmart, line 433
def aes1Handshake(baseUrl, devData) { // library marker davegut.appTpLinkSmart, line 434
	Map reqData = [baseUrl: baseUrl, devData: devData] // library marker davegut.appTpLinkSmart, line 435
	Map cmdBody = [method: "login", // library marker davegut.appTpLinkSmart, line 436
				   params: [hashed: true,  // library marker davegut.appTpLinkSmart, line 437
							password: encPasswordVac, // library marker davegut.appTpLinkSmart, line 438
							username: userName]] // library marker davegut.appTpLinkSmart, line 439
	Map reqParams = [uri: baseUrl, // library marker davegut.appTpLinkSmart, line 440
					 ignoreSSLIssues: true, // library marker davegut.appTpLinkSmart, line 441
					 body: cmdBody, // library marker davegut.appTpLinkSmart, line 442
					 contentType: "application/json", // library marker davegut.appTpLinkSmart, line 443
					 requestContentType: "application/json", // library marker davegut.appTpLinkSmart, line 444
					 timeout: 10] // library marker davegut.appTpLinkSmart, line 445
	asynchttpPost("parseAes1Login", reqParams, [data: reqData]) // library marker davegut.appTpLinkSmart, line 446
} // library marker davegut.appTpLinkSmart, line 447

def parseAes1Login(resp, data) { // library marker davegut.appTpLinkSmart, line 449
	Map logData = [method: "parseAes1Login", oldToken: token] // library marker davegut.appTpLinkSmart, line 450
	if (resp.status == 200 && resp.json != null) { // library marker davegut.appTpLinkSmart, line 451
		logData << [status: "OK"] // library marker davegut.appTpLinkSmart, line 452
		logData << [token: resp.json.result.token] // library marker davegut.appTpLinkSmart, line 453
		sendAes1DataCmd(resp.json.result.token, data) // library marker davegut.appTpLinkSmart, line 454
		logDebug(logData) // library marker davegut.appTpLinkSmart, line 455
	} else { // library marker davegut.appTpLinkSmart, line 456
		logData << [respStatus: "ERROR in HTTP response", resp: resp.properties] // library marker davegut.appTpLinkSmart, line 457
		logWarn(logData) // library marker davegut.appTpLinkSmart, line 458
	} // library marker davegut.appTpLinkSmart, line 459
} // library marker davegut.appTpLinkSmart, line 460

def sendAes1DataCmd(token, data) { // library marker davegut.appTpLinkSmart, line 462
	Map devData = data.data.devData // library marker davegut.appTpLinkSmart, line 463
	Map reqParams = [uri: "${data.data.baseUrl}/?token=${token}", // library marker davegut.appTpLinkSmart, line 464
					 body: getDataCmd(), // library marker davegut.appTpLinkSmart, line 465
					 contentType: "application/json", // library marker davegut.appTpLinkSmart, line 466
					 requestContentType: "application/json", // library marker davegut.appTpLinkSmart, line 467
					 ignoreSSLIssues: true, // library marker davegut.appTpLinkSmart, line 468
					 timeout: 10] // library marker davegut.appTpLinkSmart, line 469
	asynchttpPost("parseAes1Resp", reqParams, [data: devData]) // library marker davegut.appTpLinkSmart, line 470
} // library marker davegut.appTpLinkSmart, line 471

def parseAes1Resp(resp, devData) { // library marker davegut.appTpLinkSmart, line 473
	Map logData = [parseMethod: "parseAes1Resp"] // library marker davegut.appTpLinkSmart, line 474
	try { // library marker davegut.appTpLinkSmart, line 475
		Map cmdResp = resp.json // library marker davegut.appTpLinkSmart, line 476
		logData << [status: "OK", cmdResp: cmdResp] // library marker davegut.appTpLinkSmart, line 477
			if (cmdResp.error_code == 0) { // library marker davegut.appTpLinkSmart, line 478
				addToDevices(devData.data, cmdResp.result.responses) // library marker davegut.appTpLinkSmart, line 479
				logDebug(logData) // library marker davegut.appTpLinkSmart, line 480
			} else { // library marker davegut.appTpLinkSmart, line 481
				logData << [status: "errorInCmdResp"] // library marker davegut.appTpLinkSmart, line 482
				logWarn(logData) // library marker davegut.appTpLinkSmart, line 483
			} // library marker davegut.appTpLinkSmart, line 484
	} catch (err) { // library marker davegut.appTpLinkSmart, line 485
		logData << [status: "deviceDataParseError", error: err, dataLength: resp.data.length()] // library marker davegut.appTpLinkSmart, line 486
		logWarn(logData) // library marker davegut.appTpLinkSmart, line 487
	} // library marker davegut.appTpLinkSmart, line 488
	return parseData	 // library marker davegut.appTpLinkSmart, line 489
} // library marker davegut.appTpLinkSmart, line 490

def tpLinkCheckForDevices(timeout = 3) { // library marker davegut.appTpLinkSmart, line 492
	Map logData = [method: "tpLinkCheckForDevices"] // library marker davegut.appTpLinkSmart, line 493
	def checked = true // library marker davegut.appTpLinkSmart, line 494
	if (state.tpLinkChecked == true) { // library marker davegut.appTpLinkSmart, line 495
		checked = false // library marker davegut.appTpLinkSmart, line 496
		logData << [status: "noCheck", reason: "Completed within last 10 minutes"] // library marker davegut.appTpLinkSmart, line 497
	} else { // library marker davegut.appTpLinkSmart, line 498
		def findData = findTpLinkDevices("parseTpLinkCheck", timeout) // library marker davegut.appTpLinkSmart, line 499
		logData << [status: "checking"] // library marker davegut.appTpLinkSmart, line 500
		pauseExecution(5000) // library marker davegut.appTpLinkSmart, line 501
	} // library marker davegut.appTpLinkSmart, line 502
	logDebug(logData) // library marker davegut.appTpLinkSmart, line 503
	return checked // library marker davegut.appTpLinkSmart, line 504
} // library marker davegut.appTpLinkSmart, line 505

def resetTpLinkChecked() { state.tpLinkChecked = false } // library marker davegut.appTpLinkSmart, line 507

def parseTpLinkCheck(response) { // library marker davegut.appTpLinkSmart, line 509
	List discData = [] // library marker davegut.appTpLinkSmart, line 510
	if (response instanceof Map) { // library marker davegut.appTpLinkSmart, line 511
		Map devdata = getDiscData(response) // library marker davegut.appTpLinkSmart, line 512
		if (devData.status != "INVALID") { // library marker davegut.appTpLinkSmart, line 513
			discData << devData // library marker davegut.appTpLinkSmart, line 514
		} // library marker davegut.appTpLinkSmart, line 515
	} else { // library marker davegut.appTpLinkSmart, line 516
		response.each { // library marker davegut.appTpLinkSmart, line 517
			Map devData = getDiscData(it) // library marker davegut.appTpLinkSmart, line 518
			if (devData.status == "OK") { // library marker davegut.appTpLinkSmart, line 519
				discData << devData // library marker davegut.appTpLinkSmart, line 520
			} // library marker davegut.appTpLinkSmart, line 521
		} // library marker davegut.appTpLinkSmart, line 522
	} // library marker davegut.appTpLinkSmart, line 523
	atomicState.finding = false // library marker davegut.appTpLinkSmart, line 524
	updateTpLinkDevices(discData) // library marker davegut.appTpLinkSmart, line 525
} // library marker davegut.appTpLinkSmart, line 526

def updateTpLinkDevices(discData) { // library marker davegut.appTpLinkSmart, line 528
	Map logData = [method: "updateTpLinkDevices"] // library marker davegut.appTpLinkSmart, line 529
	state.tpLinkChecked = true // library marker davegut.appTpLinkSmart, line 530
	runIn(570, resetTpLinkChecked) // library marker davegut.appTpLinkSmart, line 531
	List children = getChildDevices() // library marker davegut.appTpLinkSmart, line 532
	children.each { childDev -> // library marker davegut.appTpLinkSmart, line 533
		Map childData = [:] // library marker davegut.appTpLinkSmart, line 534
		def dni = childDev.deviceNetworkId // library marker davegut.appTpLinkSmart, line 535
		def connected = "false" // library marker davegut.appTpLinkSmart, line 536
		Map devData = discData.find{ it.dni == dni } // library marker davegut.appTpLinkSmart, line 537
		if (childDev.getDataValue("baseUrl")) { // library marker davegut.appTpLinkSmart, line 538
			if (devData != null) { // library marker davegut.appTpLinkSmart, line 539
				if (childDev.getDataValue("baseUrl") == devData.baseUrl && // library marker davegut.appTpLinkSmart, line 540
				    childDev.getDataValue("protocol") == devData.protocol) { // library marker davegut.appTpLinkSmart, line 541
					childData << [status: "noChanges"] // library marker davegut.appTpLinkSmart, line 542
				} else { // library marker davegut.appTpLinkSmart, line 543
					childDev.updateDataValue("baseUrl", devData.baseUrl) // library marker davegut.appTpLinkSmart, line 544
					childDev.updateDataValue("protocol", devData.protocol) // library marker davegut.appTpLinkSmart, line 545
					childData << ["baseUrl": devData.baseUrl, // library marker davegut.appTpLinkSmart, line 546
								  "protocol": devData.protocol, // library marker davegut.appTpLinkSmart, line 547
								  "connected": "true"] // library marker davegut.appTpLinkSmart, line 548
				} // library marker davegut.appTpLinkSmart, line 549
			} else { // library marker davegut.appTpLinkSmart, line 550
				Map warnData = [method: "updateTpLinkDevices", device: childDev, // library marker davegut.appTpLinkSmart, line 551
								connected: "false", reason: "not Discovered By App"] // library marker davegut.appTpLinkSmart, line 552
				logWarn(warnData) // library marker davegut.appTpLinkSmart, line 553
			} // library marker davegut.appTpLinkSmart, line 554
			pauseExecution(500) // library marker davegut.appTpLinkSmart, line 555
		} // library marker davegut.appTpLinkSmart, line 556
		logData << ["${childDev}": childData] // library marker davegut.appTpLinkSmart, line 557
	} // library marker davegut.appTpLinkSmart, line 558
	logDebug(logData) // library marker davegut.appTpLinkSmart, line 559
} // library marker davegut.appTpLinkSmart, line 560

// ~~~~~ end include (252) davegut.appTpLinkSmart ~~~~~

// ~~~~~ start include (263) davegut.tpLinkTransKlap1 ~~~~~
library ( // library marker davegut.tpLinkTransKlap1, line 1
	name: "tpLinkTransKlap1", // library marker davegut.tpLinkTransKlap1, line 2
	namespace: "davegut", // library marker davegut.tpLinkTransKlap1, line 3
	author: "Compiled by Dave Gutheinz", // library marker davegut.tpLinkTransKlap1, line 4
	description: "Handshake methods for TP-Link Integration explicit to Vacuum", // library marker davegut.tpLinkTransKlap1, line 5
	category: "utilities", // library marker davegut.tpLinkTransKlap1, line 6
	documentationLink: "" // library marker davegut.tpLinkTransKlap1, line 7
) // library marker davegut.tpLinkTransKlap1, line 8
import java.security.spec.PKCS8EncodedKeySpec // library marker davegut.tpLinkTransKlap1, line 9
import javax.crypto.Cipher // library marker davegut.tpLinkTransKlap1, line 10
import java.security.KeyFactory // library marker davegut.tpLinkTransKlap1, line 11
import java.util.Random // library marker davegut.tpLinkTransKlap1, line 12
import javax.crypto.spec.SecretKeySpec // library marker davegut.tpLinkTransKlap1, line 13
import javax.crypto.spec.IvParameterSpec // library marker davegut.tpLinkTransKlap1, line 14
import java.security.MessageDigest // library marker davegut.tpLinkTransKlap1, line 15

//	===== KLAP1 Handshake ===== // library marker davegut.tpLinkTransKlap1, line 17
def klap1Handshake(baseUrl = getDataValue("baseUrl"), devData = null) { // library marker davegut.tpLinkTransKlap1, line 18
	byte[] localSeed = new byte[16] // library marker davegut.tpLinkTransKlap1, line 19
	new Random().nextBytes(localSeed) // library marker davegut.tpLinkTransKlap1, line 20
	Map reqData = [localSeed: localSeed, baseUrl: baseUrl, devData:devData] // library marker davegut.tpLinkTransKlap1, line 21
	Map reqParams = [uri: "${baseUrl}/handshake1", // library marker davegut.tpLinkTransKlap1, line 22
					 body: localSeed, // library marker davegut.tpLinkTransKlap1, line 23
					 contentType: "application/octet-stream", // library marker davegut.tpLinkTransKlap1, line 24
					 requestContentType: "application/octet-stream", // library marker davegut.tpLinkTransKlap1, line 25
					 ignoreSSLIssues: true, // library marker davegut.tpLinkTransKlap1, line 26
					 timeout:10] // library marker davegut.tpLinkTransKlap1, line 27
	asynchttpPost("parseklap1Handshake", reqParams, [data: reqData]) // library marker davegut.tpLinkTransKlap1, line 28
} // library marker davegut.tpLinkTransKlap1, line 29

def parseklap1Handshake(resp, data) { // library marker davegut.tpLinkTransKlap1, line 31
	Map logData = [method: "parseKlap1Handshake"] // library marker davegut.tpLinkTransKlap1, line 32
///////////////////////////// // library marker davegut.tpLinkTransKlap1, line 33
//Map testData = [DATA: "methodInput", data: data, resp: resp.properties] // library marker davegut.tpLinkTransKlap1, line 34
//Map testData = [DATA: "parseHandshakeInput", data: data, resp: resp.properties] // library marker davegut.tpLinkTransKlap1, line 35
//log.trace "<b>${testData}</b>" // library marker davegut.tpLinkTransKlap1, line 36
///////////////////////// // library marker davegut.tpLinkTransKlap1, line 37
	if (resp.status == 200 && resp.data != null) { // library marker davegut.tpLinkTransKlap1, line 38
		try { // library marker davegut.tpLinkTransKlap1, line 39
			Map reqData = [devData: data.data.devData, baseUrl: data.data.baseUrl] // library marker davegut.tpLinkTransKlap1, line 40
			byte[] localSeed = data.data.localSeed // library marker davegut.tpLinkTransKlap1, line 41
			byte[] seedData = resp.data.decodeBase64() // library marker davegut.tpLinkTransKlap1, line 42

		if (seedData.size() != 48) { // library marker davegut.tpLinkTransKlap1, line 44
			//	Check return length.  If not 48 bytes, Abort handshake with error message // library marker davegut.tpLinkTransKlap1, line 45
			logData << [ERROR: "invalid return from device", seedDataLen: seeData.length(), resp: resp.properties] // library marker davegut.tpLinkTransKlap1, line 46
			logWarn(logData) // library marker davegut.tpLinkTransKlap1, line 47
			return // library marker davegut.tpLinkTransKlap1, line 48
		} // library marker davegut.tpLinkTransKlap1, line 49
			byte[] remoteSeed = seedData[0 .. 15] // library marker davegut.tpLinkTransKlap1, line 50
			byte[] serverHash = seedData[16 .. 47] // library marker davegut.tpLinkTransKlap1, line 51
			//	Locally create localHash // library marker davegut.tpLinkTransKlap1, line 52
			if (device) { // library marker davegut.tpLinkTransKlap1, line 53
				userName = parent.userName // library marker davegut.tpLinkTransKlap1, line 54
				userPassword = parent.userPassword // library marker davegut.tpLinkTransKlap1, line 55
			} // library marker davegut.tpLinkTransKlap1, line 56
			byte[] unHashMd = mdEncode("SHA-1", encodeUtf8(userName).getBytes()) // library marker davegut.tpLinkTransKlap1, line 57
			byte[] pwHashMd = mdEncode("SHA-1", encodeUtf8(userPassword).getBytes()) // library marker davegut.tpLinkTransKlap1, line 58
			byte[] unPwHashByte = [unHashMd, pwHashMd].flatten() // library marker davegut.tpLinkTransKlap1, line 59
			byte[] unPwHashMd = mdEncode("SHA-256", unPwHashByte)		//	localHash // library marker davegut.tpLinkTransKlap1, line 60
			byte[] authHash = [localSeed, remoteSeed, unPwHashMd].flatten() // library marker davegut.tpLinkTransKlap1, line 61
			byte[] localAuthHash = mdEncode("SHA-256", authHash) // library marker davegut.tpLinkTransKlap1, line 62
//////////////////// // library marker davegut.tpLinkTransKlap1, line 63
//testData = [step: "handshake1", un: userName, pw: userPassword, serverAuthHash: serverAuthHash, serverHash: serverHash] // library marker davegut.tpLinkTransKlap1, line 64
//log.trace testData // library marker davegut.tpLinkTransKlap1, line 65
/////////////////// // library marker davegut.tpLinkTransKlap1, line 66

			if (localAuthHash == serverHash) { // library marker davegut.tpLinkTransKlap1, line 68
log.debug "<b>SUCCESS.  Handshake 1 Complete.  Start Handshake 2 testing</b>" // library marker davegut.tpLinkTransKlap1, line 69
//////////////////// // library marker davegut.tpLinkTransKlap1, line 70
testData = [step: "Calculate Params", localSeed: localSeed, remoteSeed: remoteSeed, authHash: authHash] // library marker davegut.tpLinkTransKlap1, line 71
log.trace testData // library marker davegut.tpLinkTransKlap1, line 72
/////////////////// // library marker davegut.tpLinkTransKlap1, line 73
				def cookieHeader = resp.headers["Set-Cookie"].toString() // library marker davegut.tpLinkTransKlap1, line 74
				def cookie = null // library marker davegut.tpLinkTransKlap1, line 75
				if (cookieHeader != null) { // library marker davegut.tpLinkTransKlap1, line 76
					cookie = cookieHeader.substring(cookieHeader.indexOf(":") +1, cookieHeader.indexOf(";")) // library marker davegut.tpLinkTransKlap1, line 77
				} // library marker davegut.tpLinkTransKlap1, line 78
				//	seqNo and encIv // library marker davegut.tpLinkTransKlap1, line 79
// // library marker davegut.tpLinkTransKlap1, line 80
				byte[] payload = ["iv".getBytes(), localSeed, remoteSeed, authHash].flatten() // library marker davegut.tpLinkTransKlap1, line 81
				byte[] fullIv = mdEncode("SHA-256", payload) // library marker davegut.tpLinkTransKlap1, line 82
				byte[] byteSeqNo = fullIv[-4..-1] // library marker davegut.tpLinkTransKlap1, line 83

				int seqNo = byteArrayToInteger(byteSeqNo) // library marker davegut.tpLinkTransKlap1, line 85
				atomicState.seqNo = seqNo // library marker davegut.tpLinkTransKlap1, line 86

				//	encKey // library marker davegut.tpLinkTransKlap1, line 88
				payload = ["lsk".getBytes(), localSeed, remoteSeed, authHash].flatten() // library marker davegut.tpLinkTransKlap1, line 89
				byte[] encKey = mdEncode("SHA-256", payload)[0..15] // library marker davegut.tpLinkTransKlap1, line 90
				//	encSig // library marker davegut.tpLinkTransKlap1, line 91
				payload = ["ldk".getBytes(), localSeed, remoteSeed, authHash].flatten() // library marker davegut.tpLinkTransKlap1, line 92
				byte[] encSig = mdEncode("SHA-256", payload)[0..27] // library marker davegut.tpLinkTransKlap1, line 93
				if (device) { // library marker davegut.tpLinkTransKlap1, line 94
					device.updateSetting("cookie",[type:"password", value: cookie])  // library marker davegut.tpLinkTransKlap1, line 95
					device.updateSetting("encKey",[type:"password", value: encKey])  // library marker davegut.tpLinkTransKlap1, line 96
					device.updateSetting("encIv",[type:"password", value: fullIv[0..11]])  // library marker davegut.tpLinkTransKlap1, line 97
					device.updateSetting("encSig",[type:"password", value: encSig])  // library marker davegut.tpLinkTransKlap1, line 98
				} else { // library marker davegut.tpLinkTransKlap1, line 99
					reqData << [cookie: cookie, seqNo: seqNo, encIv: fullIv[0..11],  // library marker davegut.tpLinkTransKlap1, line 100
								encSig: encSig, encKey: encKey] // library marker davegut.tpLinkTransKlap1, line 101
				} // library marker davegut.tpLinkTransKlap1, line 102
/* // library marker davegut.tpLinkTransKlap1, line 103
Current error is forbiden.  This is typically caused by the loginHash  // library marker davegut.tpLinkTransKlap1, line 104
being incorrect. I tested incorrect cookie - different failure.   // library marker davegut.tpLinkTransKlap1, line 105
There are two possibilities here: // library marker davegut.tpLinkTransKlap1, line 106
a.  creating body does not user remoteSeed, localSeed, authHash // library marker davegut.tpLinkTransKlap1, line 107
b.	uses incorrect encode method (currently "SHA-256") // library marker davegut.tpLinkTransKlap1, line 108
Investigation of python design source, there are two possibilities // library marker davegut.tpLinkTransKlap1, line 109
for the body, one using all three seeds (version A) and one // library marker davegut.tpLinkTransKlap1, line 110
using remoteSeed and authHash only. // library marker davegut.tpLinkTransKlap1, line 111

Last try was using all three hashes in body.  This try (version B) // library marker davegut.tpLinkTransKlap1, line 113
uses only remoteSeed and authHash. // library marker davegut.tpLinkTransKlap1, line 114

*/ // library marker davegut.tpLinkTransKlap1, line 116
//		Doesn't follow other hashes (localSeed and locahHash only)?				 // library marker davegut.tpLinkTransKlap1, line 117
//				byte[] loginHash = [remoteSeed, localSeed, authHash].flatten()	//	Version = A // library marker davegut.tpLinkTransKlap1, line 118
				byte[] loginHash = [remoteSeed, authHash].flatten()		//	Version = B // library marker davegut.tpLinkTransKlap1, line 119
				byte[] body = mdEncode("SHA-256", loginHash) // library marker davegut.tpLinkTransKlap1, line 120
				Map reqParams = [uri: "${data.data.baseUrl}/handshake2", // library marker davegut.tpLinkTransKlap1, line 121
								 body: body, // library marker davegut.tpLinkTransKlap1, line 122
								 ignoreSSLIssues: true, // library marker davegut.tpLinkTransKlap1, line 123
								 timeout:10, // library marker davegut.tpLinkTransKlap1, line 124
								 headers: ["Cookie": cookie], // library marker davegut.tpLinkTransKlap1, line 125
								 contentType: "application/octet-stream", // library marker davegut.tpLinkTransKlap1, line 126
								 requestContentType: "application/octet-stream"] // library marker davegut.tpLinkTransKlap1, line 127
/////////////////////////// // library marker davegut.tpLinkTransKlap1, line 128
logData << [step: "handshake2", version: "B", reqData: reqData, reqParams: reqParams] // library marker davegut.tpLinkTransKlap1, line 129
logTrace "<b>${logData}</b>" // library marker davegut.tpLinkTransKlap1, line 130
///////////////////////// // library marker davegut.tpLinkTransKlap1, line 131
				asynchttpPost("parseklap1Handshake2", reqParams, [data: reqData]) // library marker davegut.tpLinkTransKlap1, line 132
			} else { // library marker davegut.tpLinkTransKlap1, line 133
				logData << [respStatus: "ERROR: localAuthHash != serverHash", // library marker davegut.tpLinkTransKlap1, line 134
							action: "<b>Check credentials and try again</b>"] // library marker davegut.tpLinkTransKlap1, line 135
				logWarn(logData) // library marker davegut.tpLinkTransKlap1, line 136
			} // library marker davegut.tpLinkTransKlap1, line 137
		} catch (err) { // library marker davegut.tpLinkTransKlap1, line 138
			logData << [respStatus: "ERROR parsing 200 response", resp: resp.properties, error: err] // library marker davegut.tpLinkTransKlap1, line 139
			logData << [action: "<b>Try Configure command</b>"] // library marker davegut.tpLinkTransKlap1, line 140
			logWarn(logData) // library marker davegut.tpLinkTransKlap1, line 141
		} // library marker davegut.tpLinkTransKlap1, line 142
	} else { // library marker davegut.tpLinkTransKlap1, line 143
		logData << [respStatus: resp.status, message: resp.errorMessage] // library marker davegut.tpLinkTransKlap1, line 144
		logData << [action: "<b>Try Configure command</b>"] // library marker davegut.tpLinkTransKlap1, line 145
		logWarn(logData) // library marker davegut.tpLinkTransKlap1, line 146
	} // library marker davegut.tpLinkTransKlap1, line 147
} // library marker davegut.tpLinkTransKlap1, line 148

def parseklap1Handshake2(resp, data) { // library marker davegut.tpLinkTransKlap1, line 150
///////////////////////////// // library marker davegut.tpLinkTransKlap1, line 151
//Map testData = [DATA: "methodInput", data: data, resp: resp.properties] // library marker davegut.tpLinkTransKlap1, line 152
Map testData = [DATA: "parseHandshake2Input", data: data, resp: resp.properties] // library marker davegut.tpLinkTransKlap1, line 153
log.trace "<b>${testData}</b>" // library marker davegut.tpLinkTransKlap1, line 154
///////////////////////// // library marker davegut.tpLinkTransKlap1, line 155
//	Change: move sendKlap1DataCmd call.  Changes error from logInfo to logWarn  (change Klap also) // library marker davegut.tpLinkTransKlap1, line 156
	Map logData = [method: "parseklap1Handshake2"] // library marker davegut.tpLinkTransKlap1, line 157
	if (resp.status == 200 && resp.data == null) { // library marker davegut.tpLinkTransKlap1, line 158
		logData << [respStatus: "Login OK"] // library marker davegut.tpLinkTransKlap1, line 159
		setCommsError(200) // library marker davegut.tpLinkTransKlap1, line 160
		logDebug(logData) // library marker davegut.tpLinkTransKlap1, line 161
		if (!device) { sendklap1DataCmd(logData, data) } // library marker davegut.tpLinkTransKlap1, line 162
	} else { // library marker davegut.tpLinkTransKlap1, line 163
		logData << [respStatus: "LOGIN FAILED", reason: "ERROR in HTTP response", // library marker davegut.tpLinkTransKlap1, line 164
					resp: resp.properties] // library marker davegut.tpLinkTransKlap1, line 165
		logWarn(logData) // library marker davegut.tpLinkTransKlap1, line 166
	} // library marker davegut.tpLinkTransKlap1, line 167
} // library marker davegut.tpLinkTransKlap1, line 168

//	===== Comms Support ===== // library marker davegut.tpLinkTransKlap1, line 170
def getklap1Params(cmdBody) { // library marker davegut.tpLinkTransKlap1, line 171
	Map reqParams = [timeout: 10, headers: ["Cookie": cookie]] // library marker davegut.tpLinkTransKlap1, line 172
	int seqNo = state.seqNo + 1 // library marker davegut.tpLinkTransKlap1, line 173
	state.seqNo = seqNo // library marker davegut.tpLinkTransKlap1, line 174
	byte[] encKey = new JsonSlurper().parseText(encKey) // library marker davegut.tpLinkTransKlap1, line 175
	byte[] encIv = new JsonSlurper().parseText(encIv) // library marker davegut.tpLinkTransKlap1, line 176
	byte[] encSig = new JsonSlurper().parseText(encSig) // library marker davegut.tpLinkTransKlap1, line 177
	String cmdBodyJson = new groovy.json.JsonBuilder(cmdBody).toString() // library marker davegut.tpLinkTransKlap1, line 178

	Map encryptedData = klap1Encrypt(cmdBodyJson.getBytes(), encKey, encIv, // library marker davegut.tpLinkTransKlap1, line 180
									encSig, seqNo) // library marker davegut.tpLinkTransKlap1, line 181
	reqParams << [uri: "${getDataValue("baseUrl")}/request?seq=${seqNo}", // library marker davegut.tpLinkTransKlap1, line 182
				  ignoreSSLIssues: true, // library marker davegut.tpLinkTransKlap1, line 183
				  body: encryptedData.cipherData, // library marker davegut.tpLinkTransKlap1, line 184
				  contentType: "application/octet-stream", // library marker davegut.tpLinkTransKlap1, line 185
				  requestContentType: "application/octet-stream"] // library marker davegut.tpLinkTransKlap1, line 186
	return reqParams // library marker davegut.tpLinkTransKlap1, line 187
} // library marker davegut.tpLinkTransKlap1, line 188

def parseklap1Data(resp) { // library marker davegut.tpLinkTransKlap1, line 190
	Map parseData = [parseMethod: "parseklap1Data"] // library marker davegut.tpLinkTransKlap1, line 191
	try { // library marker davegut.tpLinkTransKlap1, line 192
		byte[] encKey = new JsonSlurper().parseText(encKey) // library marker davegut.tpLinkTransKlap1, line 193
		byte[] encIv = new JsonSlurper().parseText(encIv) // library marker davegut.tpLinkTransKlap1, line 194
		int seqNo = state.seqNo // library marker davegut.tpLinkTransKlap1, line 195
		byte[] cipherResponse = resp.data.decodeBase64()[32..-1] // library marker davegut.tpLinkTransKlap1, line 196
		Map cmdResp =  new JsonSlurper().parseText(klap1Decrypt(cipherResponse, encKey, // library marker davegut.tpLinkTransKlap1, line 197
														   encIv, seqNo)) // library marker davegut.tpLinkTransKlap1, line 198
		parseData << [cryptoStatus: "OK", cmdResp: cmdResp] // library marker davegut.tpLinkTransKlap1, line 199
	} catch (err) { // library marker davegut.tpLinkTransKlap1, line 200
		parseData << [cryptoStatus: "decryptDataError", error: err] // library marker davegut.tpLinkTransKlap1, line 201
	} // library marker davegut.tpLinkTransKlap1, line 202
	return parseData // library marker davegut.tpLinkTransKlap1, line 203
} // library marker davegut.tpLinkTransKlap1, line 204

//	===== Crypto Methods ===== // library marker davegut.tpLinkTransKlap1, line 206
def klap1Encrypt(byte[] request, encKey, encIv, encSig, seqNo) { // library marker davegut.tpLinkTransKlap1, line 207
	byte[] encSeqNo = integerToByteArray(seqNo) // library marker davegut.tpLinkTransKlap1, line 208
	byte[] ivEnc = [encIv, encSeqNo].flatten() // library marker davegut.tpLinkTransKlap1, line 209
	def cipher = Cipher.getInstance("AES/CBC/PKCS5Padding") // library marker davegut.tpLinkTransKlap1, line 210
	SecretKeySpec key = new SecretKeySpec(encKey, "AES") // library marker davegut.tpLinkTransKlap1, line 211
	IvParameterSpec iv = new IvParameterSpec(ivEnc) // library marker davegut.tpLinkTransKlap1, line 212
	cipher.init(Cipher.ENCRYPT_MODE, key, iv) // library marker davegut.tpLinkTransKlap1, line 213
	byte[] cipherRequest = cipher.doFinal(request) // library marker davegut.tpLinkTransKlap1, line 214

	byte[] payload = [encSig, encSeqNo, cipherRequest].flatten() // library marker davegut.tpLinkTransKlap1, line 216
	byte[] signature = mdEncode("SHA-256", payload) // library marker davegut.tpLinkTransKlap1, line 217
	cipherRequest = [signature, cipherRequest].flatten() // library marker davegut.tpLinkTransKlap1, line 218
	return [cipherData: cipherRequest, seqNumber: seqNo] // library marker davegut.tpLinkTransKlap1, line 219
} // library marker davegut.tpLinkTransKlap1, line 220

def klap1Decrypt(cipherResponse, encKey, encIv, seqNo) { // library marker davegut.tpLinkTransKlap1, line 222
	byte[] encSeqNo = integerToByteArray(seqNo) // library marker davegut.tpLinkTransKlap1, line 223
	byte[] ivEnc = [encIv, encSeqNo].flatten() // library marker davegut.tpLinkTransKlap1, line 224
	def cipher = Cipher.getInstance("AES/CBC/PKCS5Padding") // library marker davegut.tpLinkTransKlap1, line 225
    SecretKeySpec key = new SecretKeySpec(encKey, "AES") // library marker davegut.tpLinkTransKlap1, line 226
	IvParameterSpec iv = new IvParameterSpec(ivEnc) // library marker davegut.tpLinkTransKlap1, line 227
    cipher.init(Cipher.DECRYPT_MODE, key, iv) // library marker davegut.tpLinkTransKlap1, line 228
	byte[] byteResponse = cipher.doFinal(cipherResponse) // library marker davegut.tpLinkTransKlap1, line 229
	return new String(byteResponse, "UTF-8") // library marker davegut.tpLinkTransKlap1, line 230
} // library marker davegut.tpLinkTransKlap1, line 231

//	===== Misc routines that are general (apply to all) // library marker davegut.tpLinkTransKlap1, line 233
//	Send asyncPost - generates final reqParams and sends the data // library marker davegut.tpLinkTransKlap1, line 234
//	data is a map: [body:body, uri: uri, cookie: cookie, data: data] // library marker davegut.tpLinkTransKlap1, line 235

////////////////////////////////////////// // library marker davegut.tpLinkTransKlap1, line 237
//	FOR TEST ONLY // library marker davegut.tpLinkTransKlap1, line 238


def testKlap2() { // library marker davegut.tpLinkTransKlap1, line 241
/*[DATA:parseHandshake2Input,  // library marker davegut.tpLinkTransKlap1, line 242
 data:[data:[ // library marker davegut.tpLinkTransKlap1, line 243
	 devData:[ // library marker davegut.tpLinkTransKlap1, line 244
		 method:getDiscData, type:SMART.TAPOROBOVAC, model:RV30 Max Plus(US), baseUrl:"https://10.111.11.24:4433/app",  // library marker davegut.tpLinkTransKlap1, line 245
		 dni:B019216F355E, devId:90a4469dbdb9e0871261c37b99049f90, ip:10.111.11.24, port:4433, protocol:KLAP1, status:OK],  // library marker davegut.tpLinkTransKlap1, line 246
	 baseUrl:"https://10.111.11.24:4433/app",  // library marker davegut.tpLinkTransKlap1, line 247
	 cookie:TP_SESSIONID=18AEFF0BA8473E0E44F0482EACFED049,  // library marker davegut.tpLinkTransKlap1, line 248
	 seqNo:-1944556694,  // library marker davegut.tpLinkTransKlap1, line 249
	 encIv:[127, -49, 11, -115, 1, 29, 72, -111, 127, 40, 0, -122],  // library marker davegut.tpLinkTransKlap1, line 250
	 encSig:[29, 22, 51, 109, -76, -90, 34, -123, 122, 81, 14, 15, 76, 14, 48, -40, 98, -118, -104, -21, 50, 119, 103, -38, -39, 56, -11, 6],  // library marker davegut.tpLinkTransKlap1, line 251
	 encKey:[-124, -35, -92, 119, 39, 32, -5, -27, -82, 102, -109, -68, -63, -28, -60, 33]]],  // library marker davegut.tpLinkTransKlap1, line 252
 resp:[ // library marker davegut.tpLinkTransKlap1, line 253
	 headers:[Server:SHIP 2.0, Content-Length:0, Content-Type:text/html],  // library marker davegut.tpLinkTransKlap1, line 254
	 warningMessages:[], class:class hubitat.scheduling.AsyncResponse,  // library marker davegut.tpLinkTransKlap1, line 255
	 status:403,  // library marker davegut.tpLinkTransKlap1, line 256
	 errorMessage:Forbidden,  // library marker davegut.tpLinkTransKlap1, line 257
	 errorData:null]] // library marker davegut.tpLinkTransKlap1, line 258

[method:parseKlap1Handshake, step:handshake2, version:A,  // library marker davegut.tpLinkTransKlap1, line 260
 reqData:[ // library marker davegut.tpLinkTransKlap1, line 261
	 devData:[ // library marker davegut.tpLinkTransKlap1, line 262
		 method:getDiscData, type:SMART.TAPOROBOVAC, model:RV30 Max Plus(US), baseUrl:"https://10.111.11.24:4433/app",  // library marker davegut.tpLinkTransKlap1, line 263
		 dni:B019216F355E, devId:90a4469dbdb9e0871261c37b99049f90, ip:10.111.11.24, port:4433, protocol:KLAP1, status:OK],  // library marker davegut.tpLinkTransKlap1, line 264
	 baseUrl:"https://10.111.11.24:4433/app",  // library marker davegut.tpLinkTransKlap1, line 265
	 cookie:TP_SESSIONID=18AEFF0BA8473E0E44F0482EACFED049,  // library marker davegut.tpLinkTransKlap1, line 266
	 seqNo:-1944556694,  // library marker davegut.tpLinkTransKlap1, line 267
	 encIv:[127, -49, 11, -115, 1, 29, 72, -111, 127, 40, 0, -122],  // library marker davegut.tpLinkTransKlap1, line 268
	 encSig:[29, 22, 51, 109, -76, -90, 34, -123, 122, 81, 14, 15, 76, 14, 48, -40, 98, -118, -104, -21, 50, 119, 103, -38, -39, 56, -11, 6],  // library marker davegut.tpLinkTransKlap1, line 269
	 encKey:[-124, -35, -92, 119, 39, 32, -5, -27, -82, 102, -109, -68, -63, -28, -60, 33]],  // library marker davegut.tpLinkTransKlap1, line 270
 reqParams:[ // library marker davegut.tpLinkTransKlap1, line 271
	 uri:"https://10.111.11.24:4433/app/handshake2",  // library marker davegut.tpLinkTransKlap1, line 272
	 body:[-116, 35, 119, 69, -34, 81, 3, 19, -57, -93, 53, -100, -17, 106, 93, -54, 62, 55, -65, -9, -24, 96, 53, -25, -9, -38, 42, 102, -8, -52, 4, -124],  // library marker davegut.tpLinkTransKlap1, line 273
	 headers:[Cookie:TP_SESSIONID=18AEFF0BA8473E0E44F0482EACFED049],  // library marker davegut.tpLinkTransKlap1, line 274
	 contentType:application/octet-stream, requestContentType:application/octet-stream]] // library marker davegut.tpLinkTransKlap1, line 275
*/ // library marker davegut.tpLinkTransKlap1, line 276


/* // library marker davegut.tpLinkTransKlap1, line 279
[DATA:parseHandshakeInput,  // library marker davegut.tpLinkTransKlap1, line 280
 data:[ // library marker davegut.tpLinkTransKlap1, line 281
	 data:[ // library marker davegut.tpLinkTransKlap1, line 282
		 localSeed:[104, 76, 40, -76, 123, 112, 45, -17, -5, -122, 126, -29, -79, 35, 28, -61],  // library marker davegut.tpLinkTransKlap1, line 283
		 baseUrl:"https://10.111.11.24:4433/app",  // library marker davegut.tpLinkTransKlap1, line 284
		 devData:[ // library marker davegut.tpLinkTransKlap1, line 285
			 method:"getDiscData", type:"SMART.TAPOROBOVAC", model:"RV30 Max Plus(US)",  // library marker davegut.tpLinkTransKlap1, line 286
			 baseUrl:"https://10.111.11.24:4433/app", dni:"B019216F355E", devId:"90a4469dbdb9e0871261c37b99049f90",  // library marker davegut.tpLinkTransKlap1, line 287
			 ip:"10.111.11.24", port:"4433", protocol:"KLAP1", status:"OK"]]],  // library marker davegut.tpLinkTransKlap1, line 288
 resp:[ // library marker davegut.tpLinkTransKlap1, line 289
	 headers:[ // library marker davegut.tpLinkTransKlap1, line 290
		 Server:"SHIP 2.0",  // library marker davegut.tpLinkTransKlap1, line 291
		 "Set-Cookie":"TP_SESSIONID=BA24F3269B57017F7D30DA49F5A55570;TIMEOUT=86400",  // library marker davegut.tpLinkTransKlap1, line 292
		 "Content-Length":48, "Content-Type":"text/html"],  // library marker davegut.tpLinkTransKlap1, line 293
	 warningMessages:[],  // library marker davegut.tpLinkTransKlap1, line 294
	 class:"class hubitat.scheduling.AsyncResponse",  // library marker davegut.tpLinkTransKlap1, line 295
	 status:200,  // library marker davegut.tpLinkTransKlap1, line 296
	 data:"Cze4X+EegFAarIgEHAG4X1wF+J/qJ3CkncBCVxBUo0oZ/uQSVeq5fYCNLsxu6E2m" // library marker davegut.tpLinkTransKlap1, line 297
 ]] // library marker davegut.tpLinkTransKlap1, line 298
*/ // library marker davegut.tpLinkTransKlap1, line 299

Map data = [data:[ // library marker davegut.tpLinkTransKlap1, line 301
	localSeed:[104, 76, 40, -76, 123, 112, 45, -17, -5, -122, 126, -29, -79, 35, 28, -61],  // library marker davegut.tpLinkTransKlap1, line 302
	baseUrl:"https://192.168.50.63:4433/app", 		//	Dev test // library marker davegut.tpLinkTransKlap1, line 303
	devData:[ // library marker davegut.tpLinkTransKlap1, line 304
		method:"getDiscData", type:"SMART.TAPOROBOVAC", model:"RV30 Max Plus(US)",  // library marker davegut.tpLinkTransKlap1, line 305
		baseUrl:"https://192.168.50.63:4433/app", 		//	Dev test // library marker davegut.tpLinkTransKlap1, line 306
		dni:"B019216F355E",  // library marker davegut.tpLinkTransKlap1, line 307
		devId:"90a4469dbdb9e0871261c37b99049f90",  // library marker davegut.tpLinkTransKlap1, line 308
		ip:"192.168.50.63", 		//	Dev test // library marker davegut.tpLinkTransKlap1, line 309
		port:"4433",  // library marker davegut.tpLinkTransKlap1, line 310
		protocol:"KLAP1", status:"OK" // library marker davegut.tpLinkTransKlap1, line 311
	]]] // library marker davegut.tpLinkTransKlap1, line 312

Map resp = [ // library marker davegut.tpLinkTransKlap1, line 314
	headers:[ // library marker davegut.tpLinkTransKlap1, line 315
		Server:"SHIP 2.0",  // library marker davegut.tpLinkTransKlap1, line 316
		"Set-Cookie":"TP_SESSIONID=BA24F3269B57017F7D30DA49F5A55570;TIMEOUT=86400",  // library marker davegut.tpLinkTransKlap1, line 317
		"Content-Length":48,  // library marker davegut.tpLinkTransKlap1, line 318
		"Content-Type":"text/html" // library marker davegut.tpLinkTransKlap1, line 319
	],  // library marker davegut.tpLinkTransKlap1, line 320
	status:200,  // library marker davegut.tpLinkTransKlap1, line 321
	data:"Cze4X+EegFAarIgEHAG4X1wF+J/qJ3CkncBCVxBUo0oZ/uQSVeq5fYCNLsxu6E2m" // library marker davegut.tpLinkTransKlap1, line 322
] // library marker davegut.tpLinkTransKlap1, line 323

} // library marker davegut.tpLinkTransKlap1, line 325
///////////////////////////////	 // library marker davegut.tpLinkTransKlap1, line 326
byte[] genServerHashKlap1(un, pw, localSeed, remoteSeed) { // library marker davegut.tpLinkTransKlap1, line 327
	byte[] unHashMd = mdEncode("MD5", encodeUtf8(un).getBytes()) // library marker davegut.tpLinkTransKlap1, line 328
	byte[] pwHashMd = mdEncode("MD5", encodeUtf8(pw).getBytes()) // library marker davegut.tpLinkTransKlap1, line 329
	byte[] unPwHashByte = [unHashMd, pwHashMd].flatten() // library marker davegut.tpLinkTransKlap1, line 330
	byte[] unPwHashMd = mdEncode("MD5", unPwHashByte) // library marker davegut.tpLinkTransKlap1, line 331
	byte[] authHashByte = [localSeed, remoteSeed, unPwHashMd].flatten() // library marker davegut.tpLinkTransKlap1, line 332
	return mdEncode("SHA-256", authHashByte) // library marker davegut.tpLinkTransKlap1, line 333
} // library marker davegut.tpLinkTransKlap1, line 334
byte[] genServerHashKlap2(un, pw, localSeed, remoteSeed) { // library marker davegut.tpLinkTransKlap1, line 335
	byte[] unHashMd = mdEncode("SHA-1", encodeUtf8(un).getBytes()) // library marker davegut.tpLinkTransKlap1, line 336
	byte[] pwHashMd = mdEncode("SHA-1", encodeUtf8(pw).getBytes()) // library marker davegut.tpLinkTransKlap1, line 337
	byte[] unPwHashByte = [unHashMd, pwHashMd].flatten() // library marker davegut.tpLinkTransKlap1, line 338
	byte[] unPwHashMd = mdEncode("SHA-256", unPwHashByte) // library marker davegut.tpLinkTransKlap1, line 339
	byte[] authHashByte = [localSeed, remoteSeed, unPwHashMd].flatten() // library marker davegut.tpLinkTransKlap1, line 340
	return mdEncode("SHA-256", authHashByte) // library marker davegut.tpLinkTransKlap1, line 341
} // library marker davegut.tpLinkTransKlap1, line 342

// ~~~~~ end include (263) davegut.tpLinkTransKlap1 ~~~~~

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
	} else if (protocol == "KLAP1") { // library marker davegut.tpLinkComms, line 21
		reqParams = getKlap1Params(cmdBody) // library marker davegut.tpLinkComms, line 22
	} else if (protocol == "AES") { // library marker davegut.tpLinkComms, line 23
		reqParams = getAesParams(cmdBody) // library marker davegut.tpLinkComms, line 24
	} else if (protocol == "AES1" || protocol == "vacAes") { // library marker davegut.tpLinkComms, line 25
		reqParams = getAes1Params(cmdBody) // library marker davegut.tpLinkComms, line 26
	} // library marker davegut.tpLinkComms, line 27
	if (state.errorCount == 0) { // library marker davegut.tpLinkComms, line 28
		state.lastCommand = cmdData // library marker davegut.tpLinkComms, line 29
	} // library marker davegut.tpLinkComms, line 30
	asynchttpPost(action, reqParams, [data: reqData]) // library marker davegut.tpLinkComms, line 31
} // library marker davegut.tpLinkComms, line 32

def parseData(resp, protocol = getDataValue("protocol")) { // library marker davegut.tpLinkComms, line 34
	Map logData = [method: "parseData", status: resp.status] // library marker davegut.tpLinkComms, line 35
	def message = "OK" // library marker davegut.tpLinkComms, line 36
	if (resp.status != 200) { message = resp.errorMessage } // library marker davegut.tpLinkComms, line 37
	if (resp.status == 200) { // library marker davegut.tpLinkComms, line 38
		if (protocol == "KLAP") { // library marker davegut.tpLinkComms, line 39
			logData << parseKlapData(resp) // library marker davegut.tpLinkComms, line 40
		} else if (protocol == "AES") { // library marker davegut.tpLinkComms, line 41
			logData << parseAesData(resp) // library marker davegut.tpLinkComms, line 42
		} else if (protocol == "AES1" || protocol == "vacAes") { // library marker davegut.tpLinkComms, line 43
			logData << parseAes1Data(resp) // library marker davegut.tpLinkComms, line 44
		} // library marker davegut.tpLinkComms, line 45
	} else { // library marker davegut.tpLinkComms, line 46
		String userMessage = "unspecified" // library marker davegut.tpLinkComms, line 47
		if (resp.status == 403) { // library marker davegut.tpLinkComms, line 48
			userMessage = "<b>Try again. If error persists, check your credentials</b>" // library marker davegut.tpLinkComms, line 49
		} else if (resp.status == 408) { // library marker davegut.tpLinkComms, line 50
			userMessage = "<b>Your router connection to ${getDataValue("baseUrl")} failed.  Run Configure.</b>" // library marker davegut.tpLinkComms, line 51
		} else { // library marker davegut.tpLinkComms, line 52
			userMessage = "<b>Unhandled error Lan return</b>" // library marker davegut.tpLinkComms, line 53
		} // library marker davegut.tpLinkComms, line 54
		logData << [respMessage: message, userMessage: userMessage] // library marker davegut.tpLinkComms, line 55
		logDebug(logData) // library marker davegut.tpLinkComms, line 56
	} // library marker davegut.tpLinkComms, line 57
	handleCommsError(resp.status, message) // library marker davegut.tpLinkComms, line 58
	return logData // library marker davegut.tpLinkComms, line 59
} // library marker davegut.tpLinkComms, line 60

//	===== Communications Error Handling ===== // library marker davegut.tpLinkComms, line 62
def handleCommsError(status, msg = "") { // library marker davegut.tpLinkComms, line 63
	//	Retransmit all comms error except Switch and Level related (Hub retries for these). // library marker davegut.tpLinkComms, line 64
	//	This is determined by state.digital // library marker davegut.tpLinkComms, line 65
	if (status == 200) { // library marker davegut.tpLinkComms, line 66
		setCommsError(status, "OK") // library marker davegut.tpLinkComms, line 67
	} else { // library marker davegut.tpLinkComms, line 68
		Map logData = [method: "handleCommsError", status: code, msg: msg] // library marker davegut.tpLinkComms, line 69
		def count = state.errorCount + 1 // library marker davegut.tpLinkComms, line 70
		logData << [count: count, status: status, msg: msg] // library marker davegut.tpLinkComms, line 71
		switch(count) { // library marker davegut.tpLinkComms, line 72
			case 1: // library marker davegut.tpLinkComms, line 73
			case 2: // library marker davegut.tpLinkComms, line 74
				//	errors 1 and 2, retry immediately // library marker davegut.tpLinkComms, line 75
				runIn(2, delayedPassThrough) // library marker davegut.tpLinkComms, line 76
				break // library marker davegut.tpLinkComms, line 77
			case 3: // library marker davegut.tpLinkComms, line 78
				//	error 3, login or scan find device on the lan // library marker davegut.tpLinkComms, line 79
				//	then retry // library marker davegut.tpLinkComms, line 80
				if (status == 403) { // library marker davegut.tpLinkComms, line 81
					logData << [action: "attemptLogin"] // library marker davegut.tpLinkComms, line 82
					deviceHandshake() // library marker davegut.tpLinkComms, line 83
					runIn(4, delayedPassThrough) // library marker davegut.tpLinkComms, line 84
				} else { // library marker davegut.tpLinkComms, line 85
					logData << [action: "Find on LAN then login"] // library marker davegut.tpLinkComms, line 86
					configure() // library marker davegut.tpLinkComms, line 87
					runIn(10, delayedPassThrough) // library marker davegut.tpLinkComms, line 88
				} // library marker davegut.tpLinkComms, line 89
				break // library marker davegut.tpLinkComms, line 90
			case 4: // library marker davegut.tpLinkComms, line 91
				runIn(2, delayedPassThrough) // library marker davegut.tpLinkComms, line 92
				break // library marker davegut.tpLinkComms, line 93
			default: // library marker davegut.tpLinkComms, line 94
				//	Set comms error first time errros are 5 or more. // library marker davegut.tpLinkComms, line 95
				logData << [action: "SetCommsErrorTrue"] // library marker davegut.tpLinkComms, line 96
				setCommsError(status, msg, 5) // library marker davegut.tpLinkComms, line 97
		} // library marker davegut.tpLinkComms, line 98
		state.errorCount = count // library marker davegut.tpLinkComms, line 99
		logInfo(logData) // library marker davegut.tpLinkComms, line 100
	} // library marker davegut.tpLinkComms, line 101
} // library marker davegut.tpLinkComms, line 102

def delayedPassThrough() { // library marker davegut.tpLinkComms, line 104
	//	Do a single packet ping to check LAN connectivity.  This does // library marker davegut.tpLinkComms, line 105
	//	not stop the sending of the retry message. // library marker davegut.tpLinkComms, line 106
	def await = ping(getDataValue("baseUrl"), 1) // library marker davegut.tpLinkComms, line 107
	def cmdData = new JSONObject(state.lastCommand) // library marker davegut.tpLinkComms, line 108
	def cmdBody = parseJson(cmdData.cmdBody.toString()) // library marker davegut.tpLinkComms, line 109
	asyncSend(cmdBody, cmdData.reqData, cmdData.action) // library marker davegut.tpLinkComms, line 110
} // library marker davegut.tpLinkComms, line 111

def ping(baseUrl = getDataValue("baseUrl"), count = 1) { // library marker davegut.tpLinkComms, line 113
	def ip = baseUrl.replace("""http://""", "").replace(":80/app", "").replace(":4433", "") // library marker davegut.tpLinkComms, line 114
	ip = ip.replace("""https://""", "").replace(":4433", "") // library marker davegut.tpLinkComms, line 115
	hubitat.helper.NetworkUtils.PingData pingData = hubitat.helper.NetworkUtils.ping(ip, count) // library marker davegut.tpLinkComms, line 116
	Map pingReturn = [method: "ping", ip: ip] // library marker davegut.tpLinkComms, line 117
	if (pingData.packetsReceived == count) { // library marker davegut.tpLinkComms, line 118
		pingReturn << [pingStatus: "success"] // library marker davegut.tpLinkComms, line 119
		logDebug(pingReturn) // library marker davegut.tpLinkComms, line 120
	} else { // library marker davegut.tpLinkComms, line 121
		pingReturn << [pingData: pingData, pingStatus: "<b>FAILED</b>.  There may be issues with your LAN."] // library marker davegut.tpLinkComms, line 122
		logWarn(pingReturn) // library marker davegut.tpLinkComms, line 123
	} // library marker davegut.tpLinkComms, line 124
	return pingReturn // library marker davegut.tpLinkComms, line 125
} // library marker davegut.tpLinkComms, line 126

def setCommsError(status, msg = "OK", count = state.commsError) { // library marker davegut.tpLinkComms, line 128
	Map logData = [method: "setCommsError", status: status, errorMsg: msg, count: count] // library marker davegut.tpLinkComms, line 129
	if (device && status == 200) { // library marker davegut.tpLinkComms, line 130
		state.errorCount = 0 // library marker davegut.tpLinkComms, line 131
		if (device.currentValue("commsError") == "true") { // library marker davegut.tpLinkComms, line 132
			updateAttr("commsError", "false") // library marker davegut.tpLinkComms, line 133
			setPollInterval() // library marker davegut.tpLinkComms, line 134
			unschedule("errorDeviceHandshake") // library marker davegut.tpLinkComms, line 135
			logInfo(logData) // library marker davegut.tpLinkComms, line 136
		} // library marker davegut.tpLinkComms, line 137
	} else if (device) { // library marker davegut.tpLinkComms, line 138
		if (device.currentValue("commsError") == "false" && count > 4) { // library marker davegut.tpLinkComms, line 139
			updateAttr("commsError", "true") // library marker davegut.tpLinkComms, line 140
			setPollInterval("30 min") // library marker davegut.tpLinkComms, line 141
			runEvery10Minutes(errorConfigure) // library marker davegut.tpLinkComms, line 142
			logData << [pollInterval: "30 Min", errorDeviceHandshake: "ever 10 min"] // library marker davegut.tpLinkComms, line 143
			logWarn(logData) // library marker davegut.tpLinkComms, line 144
			if (status == 403) { // library marker davegut.tpLinkComms, line 145
				logWarn(logInErrorAction()) // library marker davegut.tpLinkComms, line 146
			} else { // library marker davegut.tpLinkComms, line 147
				logWarn(lanErrorAction()) // library marker davegut.tpLinkComms, line 148
			} // library marker davegut.tpLinkComms, line 149
		} else { // library marker davegut.tpLinkComms, line 150
			logData << [error: "Unspecified Error"] // library marker davegut.tpLinkComms, line 151
			logWarn(logData) // library marker davegut.tpLinkComms, line 152
		} // library marker davegut.tpLinkComms, line 153
	} // library marker davegut.tpLinkComms, line 154
} // library marker davegut.tpLinkComms, line 155

def lanErrorAction() { // library marker davegut.tpLinkComms, line 157
	def action = "Likely cause of this error is YOUR LAN device configuration: " // library marker davegut.tpLinkComms, line 158
	action += "a. VERIFY your device is on the DHCP list in your router, " // library marker davegut.tpLinkComms, line 159
	action += "b. VERIFY your device is in the active device list in your router, and " // library marker davegut.tpLinkComms, line 160
	action += "c. TRY controlling your device from the TAPO phone app." // library marker davegut.tpLinkComms, line 161
	return action // library marker davegut.tpLinkComms, line 162
} // library marker davegut.tpLinkComms, line 163

def logInErrorAction() { // library marker davegut.tpLinkComms, line 165
	def action = "Likely cause is your login credentials are incorrect or the login has expired. " // library marker davegut.tpLinkComms, line 166
	action += "a. RUN command Configure. b. If error persists, check your credentials in the App" // library marker davegut.tpLinkComms, line 167
	return action // library marker davegut.tpLinkComms, line 168
} // library marker davegut.tpLinkComms, line 169

def errorConfigure() { // library marker davegut.tpLinkComms, line 171
	logDebug([method: "errorConfigure"]) // library marker davegut.tpLinkComms, line 172
	configure() // library marker davegut.tpLinkComms, line 173
} // library marker davegut.tpLinkComms, line 174

//	===== Common UDP Communications for checking if device at IP is device in Hubitat ===== // library marker davegut.tpLinkComms, line 176
private sendFindCmd(ip, port, cmdData, action, commsTo = 5, ignore = false) { // library marker davegut.tpLinkComms, line 177
	def myHubAction = new hubitat.device.HubAction( // library marker davegut.tpLinkComms, line 178
		cmdData, // library marker davegut.tpLinkComms, line 179
		hubitat.device.Protocol.LAN, // library marker davegut.tpLinkComms, line 180
		[type: hubitat.device.HubAction.Type.LAN_TYPE_UDPCLIENT, // library marker davegut.tpLinkComms, line 181
		 destinationAddress: "${ip}:${port}", // library marker davegut.tpLinkComms, line 182
		 encoding: hubitat.device.HubAction.Encoding.HEX_STRING, // library marker davegut.tpLinkComms, line 183
		 ignoreResponse: ignore, // library marker davegut.tpLinkComms, line 184
		 parseWarning: true, // library marker davegut.tpLinkComms, line 185
		 timeout: commsTo, // library marker davegut.tpLinkComms, line 186
		 callback: action]) // library marker davegut.tpLinkComms, line 187
	try { // library marker davegut.tpLinkComms, line 188
		sendHubCommand(myHubAction) // library marker davegut.tpLinkComms, line 189
	} catch (error) { // library marker davegut.tpLinkComms, line 190
		logWarn("sendLanCmd: command to ${ip}:${port} failed. Error = ${error}") // library marker davegut.tpLinkComms, line 191
	} // library marker davegut.tpLinkComms, line 192
	return // library marker davegut.tpLinkComms, line 193
} // library marker davegut.tpLinkComms, line 194

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

//	Modify to add libraries tpLinkTransKlap, tpLinkTransAes, tpLinkTransAES1 and tpLinkTransKLAP2??? // library marker davegut.tpLinkCrypto, line 17
//	===== AES Handshake and Login ===== // library marker davegut.tpLinkCrypto, line 18
def aesHandshake(baseUrl = getDataValue("baseUrl"), devData = null) { // library marker davegut.tpLinkCrypto, line 19
	Map reqData = [baseUrl: baseUrl, devData: devData] // library marker davegut.tpLinkCrypto, line 20
	Map rsaKey = getRsaKey() // library marker davegut.tpLinkCrypto, line 21
	def pubPem = "-----BEGIN PUBLIC KEY-----\n${rsaKey.public}-----END PUBLIC KEY-----\n" // library marker davegut.tpLinkCrypto, line 22
	Map cmdBody = [ method: "handshake", params: [ key: pubPem]] // library marker davegut.tpLinkCrypto, line 23
	Map reqParams = [uri: baseUrl, // library marker davegut.tpLinkCrypto, line 24
					 body: new groovy.json.JsonBuilder(cmdBody).toString(), // library marker davegut.tpLinkCrypto, line 25
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
							  timeout:10,  // library marker davegut.tpLinkCrypto, line 70
							  headers: ["Cookie": cookie], // library marker davegut.tpLinkCrypto, line 71
							  contentType: "application/json", // library marker davegut.tpLinkCrypto, line 72
							  requestContentType: "application/json"] // library marker davegut.tpLinkCrypto, line 73
			asynchttpPost("parseAesLogin", reqParams, [data: reqData]) // library marker davegut.tpLinkCrypto, line 74
			logDebug(logData) // library marker davegut.tpLinkCrypto, line 75
		} catch (err) { // library marker davegut.tpLinkCrypto, line 76
			logData << [respStatus: "ERROR parsing HTTP resp.data", // library marker davegut.tpLinkCrypto, line 77
						respData: resp.data, error: err] // library marker davegut.tpLinkCrypto, line 78
			logWarn(logData) // library marker davegut.tpLinkCrypto, line 79
		} // library marker davegut.tpLinkCrypto, line 80
	} else { // library marker davegut.tpLinkCrypto, line 81
		logData << [respStatus: "ERROR in HTTP response", resp: resp.properties] // library marker davegut.tpLinkCrypto, line 82
		logWarn(logData) // library marker davegut.tpLinkCrypto, line 83
	} // library marker davegut.tpLinkCrypto, line 84
} // library marker davegut.tpLinkCrypto, line 85

def parseAesLogin(resp, data) { // library marker davegut.tpLinkCrypto, line 87
	if (device) { // library marker davegut.tpLinkCrypto, line 88
		Map logData = [method: "parseAesLogin"] // library marker davegut.tpLinkCrypto, line 89
		if (resp.status == 200) { // library marker davegut.tpLinkCrypto, line 90
			if (resp.json.error_code == 0) { // library marker davegut.tpLinkCrypto, line 91
				try { // library marker davegut.tpLinkCrypto, line 92
					byte[] encKey = new JsonSlurper().parseText(encKey) // library marker davegut.tpLinkCrypto, line 93
					byte[] encIv = new JsonSlurper().parseText(encIv) // library marker davegut.tpLinkCrypto, line 94
					def clearResp = aesDecrypt(resp.json.result.response, encKey, encIv) // library marker davegut.tpLinkCrypto, line 95
					Map cmdResp = new JsonSlurper().parseText(clearResp) // library marker davegut.tpLinkCrypto, line 96
					if (cmdResp.error_code == 0) { // library marker davegut.tpLinkCrypto, line 97
						def token = cmdResp.result.token // library marker davegut.tpLinkCrypto, line 98
						logData << [respStatus: "OK", token: token] // library marker davegut.tpLinkCrypto, line 99
						device.updateSetting("token",[type:"password", value: token]) // library marker davegut.tpLinkCrypto, line 100
						setCommsError(200) // library marker davegut.tpLinkCrypto, line 101
						logDebug(logData) // library marker davegut.tpLinkCrypto, line 102
					} else { // library marker davegut.tpLinkCrypto, line 103
						logData << [respStatus: "ERROR code in cmdResp",  // library marker davegut.tpLinkCrypto, line 104
									error_code: cmdResp.error_code, // library marker davegut.tpLinkCrypto, line 105
									check: "cryptoArray, credentials", data: cmdResp] // library marker davegut.tpLinkCrypto, line 106
						logInfo(logData) // library marker davegut.tpLinkCrypto, line 107
					} // library marker davegut.tpLinkCrypto, line 108
				} catch (err) { // library marker davegut.tpLinkCrypto, line 109
					logData << [respStatus: "ERROR parsing respJson", respJson: resp.json, // library marker davegut.tpLinkCrypto, line 110
								error: err] // library marker davegut.tpLinkCrypto, line 111
					logInfo(logData) // library marker davegut.tpLinkCrypto, line 112
				} // library marker davegut.tpLinkCrypto, line 113
			} else { // library marker davegut.tpLinkCrypto, line 114
				logData << [respStatus: "ERROR code in resp.json", errorCode: resp.json.error_code, // library marker davegut.tpLinkCrypto, line 115
							respJson: resp.json] // library marker davegut.tpLinkCrypto, line 116
				logInfo(logData) // library marker davegut.tpLinkCrypto, line 117
			} // library marker davegut.tpLinkCrypto, line 118
		} else { // library marker davegut.tpLinkCrypto, line 119
			logData << [respStatus: "ERROR in HTTP response", respStatus: resp.status, data: resp.properties] // library marker davegut.tpLinkCrypto, line 120
			logInfo(logData) // library marker davegut.tpLinkCrypto, line 121
		} // library marker davegut.tpLinkCrypto, line 122
	} else { // library marker davegut.tpLinkCrypto, line 123
		//	Code used in application only. // library marker davegut.tpLinkCrypto, line 124
		getAesToken(resp, data.data) // library marker davegut.tpLinkCrypto, line 125
	} // library marker davegut.tpLinkCrypto, line 126
} // library marker davegut.tpLinkCrypto, line 127

//	===== KLAP Handshake ===== // library marker davegut.tpLinkCrypto, line 129
def klapHandshake(baseUrl = getDataValue("baseUrl"), localHash = parent.localHash, devData = null) { // library marker davegut.tpLinkCrypto, line 130
	byte[] localSeed = new byte[16] // library marker davegut.tpLinkCrypto, line 131
	new Random().nextBytes(localSeed) // library marker davegut.tpLinkCrypto, line 132
	Map reqData = [localSeed: localSeed, baseUrl: baseUrl, localHash: localHash, devData:devData] // library marker davegut.tpLinkCrypto, line 133
	Map reqParams = [uri: "${baseUrl}/handshake1", // library marker davegut.tpLinkCrypto, line 134
					 body: localSeed, // library marker davegut.tpLinkCrypto, line 135
					 contentType: "application/octet-stream", // library marker davegut.tpLinkCrypto, line 136
					 requestContentType: "application/octet-stream", // library marker davegut.tpLinkCrypto, line 137
					 timeout:10] // library marker davegut.tpLinkCrypto, line 138
	asynchttpPost("parseKlapHandshake", reqParams, [data: reqData]) // library marker davegut.tpLinkCrypto, line 139
} // library marker davegut.tpLinkCrypto, line 140

def parseKlapHandshake(resp, data) { // library marker davegut.tpLinkCrypto, line 142
	Map logData = [method: "parseKlapHandshake"] // library marker davegut.tpLinkCrypto, line 143
	if (resp.status == 200 && resp.data != null) { // library marker davegut.tpLinkCrypto, line 144
		try { // library marker davegut.tpLinkCrypto, line 145
			Map reqData = [devData: data.data.devData, baseUrl: data.data.baseUrl] // library marker davegut.tpLinkCrypto, line 146
			byte[] localSeed = data.data.localSeed // library marker davegut.tpLinkCrypto, line 147
			byte[] seedData = resp.data.decodeBase64() // library marker davegut.tpLinkCrypto, line 148
			byte[] remoteSeed = seedData[0 .. 15] // library marker davegut.tpLinkCrypto, line 149
			byte[] serverHash = seedData[16 .. 47] // library marker davegut.tpLinkCrypto, line 150
			byte[] localHash = data.data.localHash.decodeBase64() // library marker davegut.tpLinkCrypto, line 151
			byte[] authHash = [localSeed, remoteSeed, localHash].flatten() // library marker davegut.tpLinkCrypto, line 152
			byte[] localAuthHash = mdEncode("SHA-256", authHash) // library marker davegut.tpLinkCrypto, line 153
			if (localAuthHash == serverHash) { // library marker davegut.tpLinkCrypto, line 154
				//	cookie // library marker davegut.tpLinkCrypto, line 155
				def cookieHeader = resp.headers["Set-Cookie"].toString() // library marker davegut.tpLinkCrypto, line 156
				def cookie = cookieHeader.substring(cookieHeader.indexOf(":") +1, cookieHeader.indexOf(";")) // library marker davegut.tpLinkCrypto, line 157
				//	seqNo and encIv // library marker davegut.tpLinkCrypto, line 158
				byte[] payload = ["iv".getBytes(), localSeed, remoteSeed, localHash].flatten() // library marker davegut.tpLinkCrypto, line 159
				byte[] fullIv = mdEncode("SHA-256", payload) // library marker davegut.tpLinkCrypto, line 160
				byte[] byteSeqNo = fullIv[-4..-1] // library marker davegut.tpLinkCrypto, line 161

				int seqNo = byteArrayToInteger(byteSeqNo) // library marker davegut.tpLinkCrypto, line 163
				atomicState.seqNo = seqNo // library marker davegut.tpLinkCrypto, line 164

				//	encKey // library marker davegut.tpLinkCrypto, line 166
				payload = ["lsk".getBytes(), localSeed, remoteSeed, localHash].flatten() // library marker davegut.tpLinkCrypto, line 167
				byte[] encKey = mdEncode("SHA-256", payload)[0..15] // library marker davegut.tpLinkCrypto, line 168
				//	encSig // library marker davegut.tpLinkCrypto, line 169
				payload = ["ldk".getBytes(), localSeed, remoteSeed, localHash].flatten() // library marker davegut.tpLinkCrypto, line 170
				byte[] encSig = mdEncode("SHA-256", payload)[0..27] // library marker davegut.tpLinkCrypto, line 171
				if (device) { // library marker davegut.tpLinkCrypto, line 172
					device.updateSetting("cookie",[type:"password", value: cookie])  // library marker davegut.tpLinkCrypto, line 173
					device.updateSetting("encKey",[type:"password", value: encKey])  // library marker davegut.tpLinkCrypto, line 174
					device.updateSetting("encIv",[type:"password", value: fullIv[0..11]])  // library marker davegut.tpLinkCrypto, line 175
					device.updateSetting("encSig",[type:"password", value: encSig])  // library marker davegut.tpLinkCrypto, line 176
				} else { // library marker davegut.tpLinkCrypto, line 177
					reqData << [cookie: cookie, seqNo: seqNo, encIv: fullIv[0..11],  // library marker davegut.tpLinkCrypto, line 178
								encSig: encSig, encKey: encKey] // library marker davegut.tpLinkCrypto, line 179
				} // library marker davegut.tpLinkCrypto, line 180
				byte[] loginHash = [remoteSeed, localSeed, localHash].flatten() // library marker davegut.tpLinkCrypto, line 181
				byte[] body = mdEncode("SHA-256", loginHash) // library marker davegut.tpLinkCrypto, line 182
				Map reqParams = [uri: "${data.data.baseUrl}/handshake2", // library marker davegut.tpLinkCrypto, line 183
								 body: body, // library marker davegut.tpLinkCrypto, line 184
								 timeout:10, // library marker davegut.tpLinkCrypto, line 185
								 ignoreSSLIssues: true, // library marker davegut.tpLinkCrypto, line 186
								 headers: ["Cookie": cookie], // library marker davegut.tpLinkCrypto, line 187
								 contentType: "application/octet-stream", // library marker davegut.tpLinkCrypto, line 188
								 requestContentType: "application/octet-stream"] // library marker davegut.tpLinkCrypto, line 189
				asynchttpPost("parseKlapHandshake2", reqParams, [data: reqData]) // library marker davegut.tpLinkCrypto, line 190
			} else { // library marker davegut.tpLinkCrypto, line 191
				logData << [respStatus: "ERROR: localAuthHash != serverHash", // library marker davegut.tpLinkCrypto, line 192
							action: "<b>Check credentials and try again</b>"] // library marker davegut.tpLinkCrypto, line 193
				logWarn(logData) // library marker davegut.tpLinkCrypto, line 194
			} // library marker davegut.tpLinkCrypto, line 195
		} catch (err) { // library marker davegut.tpLinkCrypto, line 196
			logData << [respStatus: "ERROR parsing 200 response", resp: resp.properties, error: err] // library marker davegut.tpLinkCrypto, line 197
			logData << [action: "<b>Try Configure command</b>"] // library marker davegut.tpLinkCrypto, line 198
			logWarn(logData) // library marker davegut.tpLinkCrypto, line 199
		} // library marker davegut.tpLinkCrypto, line 200
	} else { // library marker davegut.tpLinkCrypto, line 201
		logData << [respStatus: resp.status, message: resp.errorMessage] // library marker davegut.tpLinkCrypto, line 202
		logData << [action: "<b>Try Configure command</b>"] // library marker davegut.tpLinkCrypto, line 203
		logWarn(logData) // library marker davegut.tpLinkCrypto, line 204
	} // library marker davegut.tpLinkCrypto, line 205
} // library marker davegut.tpLinkCrypto, line 206

def parseKlapHandshake2(resp, data) { // library marker davegut.tpLinkCrypto, line 208
	Map logData = [method: "parseKlapHandshake2"] // library marker davegut.tpLinkCrypto, line 209
	if (resp.status == 200 && resp.data == null) { // library marker davegut.tpLinkCrypto, line 210
		logData << [respStatus: "Login OK"] // library marker davegut.tpLinkCrypto, line 211
		setCommsError(200) // library marker davegut.tpLinkCrypto, line 212
		logDebug(logData) // library marker davegut.tpLinkCrypto, line 213
	} else { // library marker davegut.tpLinkCrypto, line 214
		logData << [respStatus: "LOGIN FAILED", reason: "ERROR in HTTP response", // library marker davegut.tpLinkCrypto, line 215
					resp: resp.properties] // library marker davegut.tpLinkCrypto, line 216
		logInfo(logData) // library marker davegut.tpLinkCrypto, line 217
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
		parseData << [cryptoStatus: "OK", cmdResp: cmdResp] // library marker davegut.tpLinkCrypto, line 265
	} catch (err) { // library marker davegut.tpLinkCrypto, line 266
		parseData << [cryptoStatus: "decryptDataError", error: err] // library marker davegut.tpLinkCrypto, line 267
	} // library marker davegut.tpLinkCrypto, line 268
	return parseData // library marker davegut.tpLinkCrypto, line 269
} // library marker davegut.tpLinkCrypto, line 270

def parseAesData(resp) { // library marker davegut.tpLinkCrypto, line 272
	Map parseData = [parseMethod: "parseAesData"] // library marker davegut.tpLinkCrypto, line 273
	try { // library marker davegut.tpLinkCrypto, line 274
		byte[] encKey = new JsonSlurper().parseText(encKey) // library marker davegut.tpLinkCrypto, line 275
		byte[] encIv = new JsonSlurper().parseText(encIv) // library marker davegut.tpLinkCrypto, line 276
		Map cmdResp = new JsonSlurper().parseText(aesDecrypt(resp.json.result.response, // library marker davegut.tpLinkCrypto, line 277
														 encKey, encIv)) // library marker davegut.tpLinkCrypto, line 278
		parseData << [cryptoStatus: "OK", cmdResp: cmdResp] // library marker davegut.tpLinkCrypto, line 279
	} catch (err) { // library marker davegut.tpLinkCrypto, line 280
		parseData << [cryptoStatus: "decryptDataError", error: err, dataLength: resp.data.length()] // library marker davegut.tpLinkCrypto, line 281
	} // library marker davegut.tpLinkCrypto, line 282
	return parseData // library marker davegut.tpLinkCrypto, line 283
} // library marker davegut.tpLinkCrypto, line 284

//	===== Crypto Methods ===== // library marker davegut.tpLinkCrypto, line 286
def klapEncrypt(byte[] request, encKey, encIv, encSig, seqNo) { // library marker davegut.tpLinkCrypto, line 287
	byte[] encSeqNo = integerToByteArray(seqNo) // library marker davegut.tpLinkCrypto, line 288
	byte[] ivEnc = [encIv, encSeqNo].flatten() // library marker davegut.tpLinkCrypto, line 289
	def cipher = Cipher.getInstance("AES/CBC/PKCS5Padding") // library marker davegut.tpLinkCrypto, line 290
	SecretKeySpec key = new SecretKeySpec(encKey, "AES") // library marker davegut.tpLinkCrypto, line 291
	IvParameterSpec iv = new IvParameterSpec(ivEnc) // library marker davegut.tpLinkCrypto, line 292
	cipher.init(Cipher.ENCRYPT_MODE, key, iv) // library marker davegut.tpLinkCrypto, line 293
	byte[] cipherRequest = cipher.doFinal(request) // library marker davegut.tpLinkCrypto, line 294

	byte[] payload = [encSig, encSeqNo, cipherRequest].flatten() // library marker davegut.tpLinkCrypto, line 296
	byte[] signature = mdEncode("SHA-256", payload) // library marker davegut.tpLinkCrypto, line 297
	cipherRequest = [signature, cipherRequest].flatten() // library marker davegut.tpLinkCrypto, line 298
	return [cipherData: cipherRequest, seqNumber: seqNo] // library marker davegut.tpLinkCrypto, line 299
} // library marker davegut.tpLinkCrypto, line 300

def klapDecrypt(cipherResponse, encKey, encIv, seqNo) { // library marker davegut.tpLinkCrypto, line 302
	byte[] encSeqNo = integerToByteArray(seqNo) // library marker davegut.tpLinkCrypto, line 303
	byte[] ivEnc = [encIv, encSeqNo].flatten() // library marker davegut.tpLinkCrypto, line 304
	def cipher = Cipher.getInstance("AES/CBC/PKCS5Padding") // library marker davegut.tpLinkCrypto, line 305
    SecretKeySpec key = new SecretKeySpec(encKey, "AES") // library marker davegut.tpLinkCrypto, line 306
	IvParameterSpec iv = new IvParameterSpec(ivEnc) // library marker davegut.tpLinkCrypto, line 307
    cipher.init(Cipher.DECRYPT_MODE, key, iv) // library marker davegut.tpLinkCrypto, line 308
	byte[] byteResponse = cipher.doFinal(cipherResponse) // library marker davegut.tpLinkCrypto, line 309
	return new String(byteResponse, "UTF-8") // library marker davegut.tpLinkCrypto, line 310
} // library marker davegut.tpLinkCrypto, line 311

def aesEncrypt(request, encKey, encIv) { // library marker davegut.tpLinkCrypto, line 313
	def cipher = Cipher.getInstance("AES/CBC/PKCS5Padding") // library marker davegut.tpLinkCrypto, line 314
	SecretKeySpec key = new SecretKeySpec(encKey, "AES") // library marker davegut.tpLinkCrypto, line 315
	IvParameterSpec iv = new IvParameterSpec(encIv) // library marker davegut.tpLinkCrypto, line 316
	cipher.init(Cipher.ENCRYPT_MODE, key, iv) // library marker davegut.tpLinkCrypto, line 317
	String result = cipher.doFinal(request.getBytes("UTF-8")).encodeBase64().toString() // library marker davegut.tpLinkCrypto, line 318
	return result.replace("\r\n","") // library marker davegut.tpLinkCrypto, line 319
} // library marker davegut.tpLinkCrypto, line 320

def aesDecrypt(cipherResponse, encKey, encIv) { // library marker davegut.tpLinkCrypto, line 322
    byte[] decodedBytes = cipherResponse.decodeBase64() // library marker davegut.tpLinkCrypto, line 323
	def cipher = Cipher.getInstance("AES/CBC/PKCS5Padding") // library marker davegut.tpLinkCrypto, line 324
    SecretKeySpec key = new SecretKeySpec(encKey, "AES") // library marker davegut.tpLinkCrypto, line 325
	IvParameterSpec iv = new IvParameterSpec(encIv) // library marker davegut.tpLinkCrypto, line 326
    cipher.init(Cipher.DECRYPT_MODE, key, iv) // library marker davegut.tpLinkCrypto, line 327
	return new String(cipher.doFinal(decodedBytes), "UTF-8") // library marker davegut.tpLinkCrypto, line 328
} // library marker davegut.tpLinkCrypto, line 329

//	===== Encoding Methods ===== // library marker davegut.tpLinkCrypto, line 331
def mdEncode(hashMethod, byte[] data) { // library marker davegut.tpLinkCrypto, line 332
	MessageDigest md = MessageDigest.getInstance(hashMethod) // library marker davegut.tpLinkCrypto, line 333
	md.update(data) // library marker davegut.tpLinkCrypto, line 334
	return md.digest() // library marker davegut.tpLinkCrypto, line 335
} // library marker davegut.tpLinkCrypto, line 336

String encodeUtf8(String message) { // library marker davegut.tpLinkCrypto, line 338
	byte[] arr = message.getBytes("UTF8") // library marker davegut.tpLinkCrypto, line 339
	return new String(arr) // library marker davegut.tpLinkCrypto, line 340
} // library marker davegut.tpLinkCrypto, line 341

int byteArrayToInteger(byte[] byteArr) { // library marker davegut.tpLinkCrypto, line 343
	int arrayASInteger // library marker davegut.tpLinkCrypto, line 344
	try { // library marker davegut.tpLinkCrypto, line 345
		arrayAsInteger = ((byteArr[0] & 0xFF) << 24) + ((byteArr[1] & 0xFF) << 16) + // library marker davegut.tpLinkCrypto, line 346
			((byteArr[2] & 0xFF) << 8) + (byteArr[3] & 0xFF) // library marker davegut.tpLinkCrypto, line 347
	} catch (error) { // library marker davegut.tpLinkCrypto, line 348
		Map errLog = [byteArr: byteArr, ERROR: error] // library marker davegut.tpLinkCrypto, line 349
		logWarn("byteArrayToInteger: ${errLog}") // library marker davegut.tpLinkCrypto, line 350
	} // library marker davegut.tpLinkCrypto, line 351
	return arrayAsInteger // library marker davegut.tpLinkCrypto, line 352
} // library marker davegut.tpLinkCrypto, line 353

byte[] integerToByteArray(value) { // library marker davegut.tpLinkCrypto, line 355
	String hexValue = hubitat.helper.HexUtils.integerToHexString(value, 4) // library marker davegut.tpLinkCrypto, line 356
	byte[] byteValue = hubitat.helper.HexUtils.hexStringToByteArray(hexValue) // library marker davegut.tpLinkCrypto, line 357
	return byteValue // library marker davegut.tpLinkCrypto, line 358
} // library marker davegut.tpLinkCrypto, line 359

def getRsaKey() { // library marker davegut.tpLinkCrypto, line 361
	return [public: "MIGfMA0GCSqGSIb3DQEBAQUAA4GNADCBiQKBgQDGr/mHBK8aqx7UAS+g+TuAvE3J2DdwsqRn9MmAkjPGNon1ZlwM6nLQHfJHebdohyVqkNWaCECGXnftnlC8CM2c/RujvCrStRA0lVD+jixO9QJ9PcYTa07Z1FuEze7Q5OIa6pEoPxomrjxzVlUWLDXt901qCdn3/zRZpBdpXzVZtQIDAQAB", // library marker davegut.tpLinkCrypto, line 362
			private: "MIICeAIBADANBgkqhkiG9w0BAQEFAASCAmIwggJeAgEAAoGBAMav+YcErxqrHtQBL6D5O4C8TcnYN3CypGf0yYCSM8Y2ifVmXAzqctAd8kd5t2iHJWqQ1ZoIQIZed+2eULwIzZz9G6O8KtK1EDSVUP6OLE71An09xhNrTtnUW4TN7tDk4hrqkSg/GiauPHNWVRYsNe33TWoJ2ff/NFmkF2lfNVm1AgMBAAECgYEAocxCHmKBGe2KAEkq+SKdAxvVGO77TsobOhDMWug0Q1C8jduaUGZHsxT/7JbA9d1AagSh/XqE2Sdq8FUBF+7vSFzozBHyGkrX1iKURpQFEQM2j9JgUCucEavnxvCqDYpscyNRAgqz9jdh+BjEMcKAG7o68bOw41ZC+JyYR41xSe0CQQD1os71NcZiMVqYcBud6fTYFHZz3HBNcbzOk+RpIHyi8aF3zIqPKIAh2pO4s7vJgrMZTc2wkIe0ZnUrm0oaC//jAkEAzxIPW1mWd3+KE3gpgyX0cFkZsDmlIbWojUIbyz8NgeUglr+BczARG4ITrTV4fxkGwNI4EZxBT8vXDSIXJ8NDhwJBAIiKndx0rfg7Uw7VkqRvPqk2hrnU2aBTDw8N6rP9WQsCoi0DyCnX65Hl/KN5VXOocYIpW6NAVA8VvSAmTES6Ut0CQQCX20jD13mPfUsHaDIZafZPhiheoofFpvFLVtYHQeBoCF7T7vHCRdfl8oj3l6UcoH/hXMmdsJf9KyI1EXElyf91AkAvLfmAS2UvUnhX4qyFioitjxwWawSnf+CewN8LDbH7m5JVXJEh3hqp+aLHg1EaW4wJtkoKLCF+DeVIgbSvOLJw"] // library marker davegut.tpLinkCrypto, line 363
} // library marker davegut.tpLinkCrypto, line 364

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
