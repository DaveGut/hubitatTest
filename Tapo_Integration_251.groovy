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
/*	app.removeSetting("encPasswordVac") // library marker davegut.appTpLinkSmart, line 15
	app.removeSetting("encPasswordVac") // library marker davegut.appTpLinkSmart, line 16
	app.removeSetting("encPasswordAes1") // library marker davegut.appTpLinkSmart, line 17
	app.removeSetting("localHash") // library marker davegut.appTpLinkSmart, line 18
	app.removeSetting("localHash1") // library marker davegut.appTpLinkSmart, line 19
return*/ // library marker davegut.appTpLinkSmart, line 20
	Map SMARTCredData = [:] // library marker davegut.appTpLinkSmart, line 21
	//	AES and KLAP Creds (username/password) // library marker davegut.appTpLinkSmart, line 22
	String encUsername = mdEncode("SHA-1", userName.bytes).encodeHex().encodeAsBase64().toString() // library marker davegut.appTpLinkSmart, line 23
	app?.updateSetting("encUsername", [type: "string", value: encUsername]) // library marker davegut.appTpLinkSmart, line 24
	SMARTCredData << [encUsername: encUsername] // library marker davegut.appTpLinkSmart, line 25
	String encPassword = userPassword.bytes.encodeBase64().toString() // library marker davegut.appTpLinkSmart, line 26
	app?.updateSetting("encPassword", [type: "string", value: encPassword]) // library marker davegut.appTpLinkSmart, line 27
	SMARTCredData << [encPassword: encPassword] // library marker davegut.appTpLinkSmart, line 28

	//	AES1 (vacAes) Creds (password only) // library marker davegut.appTpLinkSmart, line 30
	String encPasswordVac = mdEncode("MD5", userPassword.bytes).encodeHex().toString().toUpperCase() // library marker davegut.appTpLinkSmart, line 31
	app?.updateSetting("encPasswordVac", [type: "string", value: encPasswordVac]) // library marker davegut.appTpLinkSmart, line 32
	app?.updateSetting("encPasswordAes1", [type: "string", value: encPasswordVac]) // library marker davegut.appTpLinkSmart, line 33
	SMARTCredData << [encPasswordVac: encPasswordVac] // library marker davegut.appTpLinkSmart, line 34
	//	KLAP Hashes // library marker davegut.appTpLinkSmart, line 35
	def userHash = mdEncode("SHA-1", encodeUtf8(userName).getBytes()) // library marker davegut.appTpLinkSmart, line 36
	def passwordHash = mdEncode("SHA-1", encodeUtf8(userPassword).getBytes()) // library marker davegut.appTpLinkSmart, line 37
	byte[] LocalHashByte = [userHash, passwordHash].flatten() // library marker davegut.appTpLinkSmart, line 38
	String localHash = mdEncode("SHA-256", LocalHashByte).encodeBase64().toString() // library marker davegut.appTpLinkSmart, line 39
	app?.updateSetting("localHash", [type: "string", value: localHash]) // library marker davegut.appTpLinkSmart, line 40
	SMARTCredData << [localHash: localHash] // library marker davegut.appTpLinkSmart, line 41
	//	KLAP1 Hashes // library marker davegut.appTpLinkSmart, line 42
	userHash = mdEncode("MD5", encodeUtf8(userName).getBytes()) // library marker davegut.appTpLinkSmart, line 43
	passwordHash = mdEncode("MD5", encodeUtf8(userPassword).getBytes()) // library marker davegut.appTpLinkSmart, line 44
	LocalHashByte = [userHash, passwordHash].flatten() // library marker davegut.appTpLinkSmart, line 45
	String localHash1 = mdEncode("MD5", LocalHashByte).encodeBase64().toString() // library marker davegut.appTpLinkSmart, line 46
	app?.updateSetting("localHash1", [type: "string", value: localHash1]) // library marker davegut.appTpLinkSmart, line 47
	SMARTCredData << [localHash1: localHash1] // library marker davegut.appTpLinkSmart, line 48
logTrace(SMARTCredData) // library marker davegut.appTpLinkSmart, line 49
	return [SMARTDevCreds: SMARTCredData] // library marker davegut.appTpLinkSmart, line 50
} // library marker davegut.appTpLinkSmart, line 51

def findTpLinkDevices(action, timeout = 10) { // library marker davegut.appTpLinkSmart, line 53
	Map logData = [method: "findTpLinkDevices", action: action, timeOut: timeout] // library marker davegut.appTpLinkSmart, line 54
	def start = state.hostArray.min().toInteger() // library marker davegut.appTpLinkSmart, line 55
	def finish = state.hostArray.max().toInteger() + 1 // library marker davegut.appTpLinkSmart, line 56
	logData << [hostArray: state.hostArray, pollSegments: state.segArray] // library marker davegut.appTpLinkSmart, line 57
	List deviceIPs = [] // library marker davegut.appTpLinkSmart, line 58
	state.segArray.each { // library marker davegut.appTpLinkSmart, line 59
		def pollSegment = it.trim() // library marker davegut.appTpLinkSmart, line 60
		logData << [pollSegment: pollSegment] // library marker davegut.appTpLinkSmart, line 61
           for(int i = start; i < finish; i++) { // library marker davegut.appTpLinkSmart, line 62
			deviceIPs.add("${pollSegment}.${i.toString()}") // library marker davegut.appTpLinkSmart, line 63
		} // library marker davegut.appTpLinkSmart, line 64
		def cmdData = "0200000101e51100095c11706d6f58577b22706172616d73223a7b227273615f6b6579223a222d2d2d2d2d424547494e205055424c4943204b45592d2d2d2d2d5c6e4d494942496a414e42676b71686b6947397730424151454641414f43415138414d49494243674b43415145416d684655445279687367797073467936576c4d385c6e54646154397a61586133586a3042712f4d6f484971696d586e2b736b4e48584d525a6550564134627532416257386d79744a5033445073665173795679536e355c6e6f425841674d303149674d4f46736350316258367679784d523871614b33746e466361665a4653684d79536e31752f564f2f47474f795436507459716f384e315c6e44714d77373563334b5a4952387a4c71516f744657747239543337536e50754a7051555a7055376679574b676377716e7338785a657a78734e6a6465534171765c6e3167574e75436a5356686d437931564d49514942576d616a37414c47544971596a5442376d645348562f2b614a32564467424c6d7770344c7131664c4f6a466f5c6e33737241683144744a6b537376376a624f584d51695666453873764b6877586177717661546b5658382f7a4f44592b2f64684f5374694a4e6c466556636c35585c6e4a514944415141425c6e2d2d2d2d2d454e44205055424c4943204b45592d2d2d2d2d5c6e227d7d" // library marker davegut.appTpLinkSmart, line 65
		await = sendLanCmd(deviceIPs.join(','), "20002", cmdData, action, timeout) // library marker davegut.appTpLinkSmart, line 66
		atomicState.finding = true // library marker davegut.appTpLinkSmart, line 67
		int i // library marker davegut.appTpLinkSmart, line 68
		for(i = 0; i < 60; i+=5) { // library marker davegut.appTpLinkSmart, line 69
			pauseExecution(5000) // library marker davegut.appTpLinkSmart, line 70
			if (atomicState.finding == false) { // library marker davegut.appTpLinkSmart, line 71
				logInfo("<b>FindingDevices: Finished Finding</b>") // library marker davegut.appTpLinkSmart, line 72
				pauseExecution(5000) // library marker davegut.appTpLinkSmart, line 73
				i = 61 // library marker davegut.appTpLinkSmart, line 74
				break // library marker davegut.appTpLinkSmart, line 75
			} // library marker davegut.appTpLinkSmart, line 76
			logInfo("<b>FindingDevices: ${i} seconds</b>") // library marker davegut.appTpLinkSmart, line 77
		} // library marker davegut.appTpLinkSmart, line 78
	} // library marker davegut.appTpLinkSmart, line 79
	logDebug(logData) // library marker davegut.appTpLinkSmart, line 80
	return logData // library marker davegut.appTpLinkSmart, line 81
} // library marker davegut.appTpLinkSmart, line 82

def getTpLinkLanData(response) { // library marker davegut.appTpLinkSmart, line 84
	Map logData = [method: "getTpLinkLanData",  // library marker davegut.appTpLinkSmart, line 85
				   action: "Completed LAN Discovery", // library marker davegut.appTpLinkSmart, line 86
				   smartDevicesFound: response.size()] // library marker davegut.appTpLinkSmart, line 87
	logInfo(logData) // library marker davegut.appTpLinkSmart, line 88
	List discData = [] // library marker davegut.appTpLinkSmart, line 89
	if (response instanceof Map) { // library marker davegut.appTpLinkSmart, line 90
		Map devData = getDiscData(response) // library marker davegut.appTpLinkSmart, line 91
		if (devData.status == "OK") { // library marker davegut.appTpLinkSmart, line 92
			discData << devData // library marker davegut.appTpLinkSmart, line 93
		} // library marker davegut.appTpLinkSmart, line 94
	} else { // library marker davegut.appTpLinkSmart, line 95
		response.each { // library marker davegut.appTpLinkSmart, line 96
			Map devData = getDiscData(it) // library marker davegut.appTpLinkSmart, line 97
			if (devData.status == "OK") { // library marker davegut.appTpLinkSmart, line 98
				discData << devData // library marker davegut.appTpLinkSmart, line 99
			} // library marker davegut.appTpLinkSmart, line 100
		} // library marker davegut.appTpLinkSmart, line 101
	} // library marker davegut.appTpLinkSmart, line 102


///////////////////	 // library marker davegut.appTpLinkSmart, line 105
/*Map devData = [method:"getDiscData", type:"SMART.TAPOROBOVAC",  // library marker davegut.appTpLinkSmart, line 106
//			   model:"RV30 Max(US)", baseUrl:"https://192.168.50.63:4433/app",  // library marker davegut.appTpLinkSmart, line 107
			   model:"RV30 Max(US)", baseUrl:"https://192.168.50.63:4433",  // library marker davegut.appTpLinkSmart, line 108
			   dni:"AC15A22DA940", devId:"a6005cf4b0fee5fa8e068e0d598f9cff",  // library marker davegut.appTpLinkSmart, line 109
			   ip:"192.168.50.63", port:"4433", protocol:"KLAP1", status:"OK"] // library marker davegut.appTpLinkSmart, line 110
discData << devData			    // library marker davegut.appTpLinkSmart, line 111
log.trace discData*/ // library marker davegut.appTpLinkSmart, line 112
///////////////////	 // library marker davegut.appTpLinkSmart, line 113


	getAllTpLinkDeviceData(discData) // library marker davegut.appTpLinkSmart, line 116
	app?.updateSetting("finding", false) // library marker davegut.appTpLinkSmart, line 117
	runIn(5, updateTpLinkDevices, [data: discData]) // library marker davegut.appTpLinkSmart, line 118
} // library marker davegut.appTpLinkSmart, line 119

def getDiscData(response) { // library marker davegut.appTpLinkSmart, line 121
	Map devData = [method: "getDiscData"] // library marker davegut.appTpLinkSmart, line 122
	try { // library marker davegut.appTpLinkSmart, line 123
		def respData = parseLanMessage(response.description) // library marker davegut.appTpLinkSmart, line 124
		if (respData.type == "LAN_TYPE_UDPCLIENT") { // library marker davegut.appTpLinkSmart, line 125
			byte[] payloadByte = hubitat.helper.HexUtils.hexStringToByteArray(respData.payload.drop(32))  // library marker davegut.appTpLinkSmart, line 126
			String payloadString = new String(payloadByte) // library marker davegut.appTpLinkSmart, line 127
			if (payloadString.length() > 1007) { // library marker davegut.appTpLinkSmart, line 128
				payloadString = payloadString + """"}}}""" // library marker davegut.appTpLinkSmart, line 129
			} // library marker davegut.appTpLinkSmart, line 130
			Map payload = new JsonSlurper().parseText(payloadString).result // library marker davegut.appTpLinkSmart, line 131
			List supported = supportedProducts() // library marker davegut.appTpLinkSmart, line 132
			String devType = payload.device_type // library marker davegut.appTpLinkSmart, line 133
			String model = payload.device_model // library marker davegut.appTpLinkSmart, line 134
			if (supported.contains(devType)) { // library marker davegut.appTpLinkSmart, line 135
				if (!payload.mgt_encrypt_schm.encrypt_type) { // library marker davegut.appTpLinkSmart, line 136
					String mssg = "<b>The ${model} is not supported " // library marker davegut.appTpLinkSmart, line 137
					mssg += "by this integration version.</b>" // library marker davegut.appTpLinkSmart, line 138
					devData << [payload: payload, status: "INVALID", reason: "Device not supported."] // library marker davegut.appTpLinkSmart, line 139
					logWarn(mssg) // library marker davegut.appTpLinkSmart, line 140
					return devData // library marker davegut.appTpLinkSmart, line 141
				} // library marker davegut.appTpLinkSmart, line 142
				String protocol = payload.mgt_encrypt_schm.encrypt_type // library marker davegut.appTpLinkSmart, line 143
				String level = payload.mgt_encrypt_schm.lv // library marker davegut.appTpLinkSmart, line 144
				def isHttps = payload.mgt_encrypt_schm.is_support_https // library marker davegut.appTpLinkSmart, line 145
				String port = payload.mgt_encrypt_schm.http_port // library marker davegut.appTpLinkSmart, line 146
				String devIp = payload.ip // library marker davegut.appTpLinkSmart, line 147
				String dni = payload.mac.replaceAll("-", "") // library marker davegut.appTpLinkSmart, line 148

				String prot = "http://" // library marker davegut.appTpLinkSmart, line 150
				if (isHttps) { prot = "https://" } // library marker davegut.appTpLinkSmart, line 151
				String baseUrl = "${prot}${devIp}:${port}/app" // library marker davegut.appTpLinkSmart, line 152
				if (protocol == "KLAP" && level == null) { // library marker davegut.appTpLinkSmart, line 153
					protocol = "KLAP1"	//	legacy KLAP Protocol implementation // library marker davegut.appTpLinkSmart, line 154
				} else if (protocol == "AES" && level == null) { // library marker davegut.appTpLinkSmart, line 155
					protocol = "AES1"	//	legacy AES protocol, aka vacAES in this app. // library marker davegut.appTpLinkSmart, line 156
					baseUrl = "${prot}${devIp}:${port}" // library marker davegut.appTpLinkSmart, line 157
				} // library marker davegut.appTpLinkSmart, line 158
				devData << [type: devType, model: model, baseUrl: baseUrl, dni: dni,  // library marker davegut.appTpLinkSmart, line 159
							devId: payload.device_id, ip: devIp, port: port,  // library marker davegut.appTpLinkSmart, line 160
							protocol: protocol, status: "OK"] // library marker davegut.appTpLinkSmart, line 161
			} else { // library marker davegut.appTpLinkSmart, line 162
				devData << [type: devType, model: model, status: "INVALID",  // library marker davegut.appTpLinkSmart, line 163
							reason: "Device not supported.", payload: payload] // library marker davegut.appTpLinkSmart, line 164
				logWarn(devData) // library marker davegut.appTpLinkSmart, line 165
			} // library marker davegut.appTpLinkSmart, line 166
		} // library marker davegut.appTpLinkSmart, line 167
		logDebug(devData) // library marker davegut.appTpLinkSmart, line 168
	} catch (err) { // library marker davegut.appTpLinkSmart, line 169
		devData << [status: "INVALID", respData: repsData, error: err] // library marker davegut.appTpLinkSmart, line 170
		logWarn(devData) // library marker davegut.appTpLinkSmart, line 171
	} // library marker davegut.appTpLinkSmart, line 172
	return devData // library marker davegut.appTpLinkSmart, line 173
} // library marker davegut.appTpLinkSmart, line 174

def getAllTpLinkDeviceData(List discData) { // library marker davegut.appTpLinkSmart, line 176
	Map logData = [method: "getAllTpLinkDeviceData", discData: discData.size()] // library marker davegut.appTpLinkSmart, line 177
	discData.each { Map devData -> // library marker davegut.appTpLinkSmart, line 178
		if (devData.protocol == "KLAP") { // library marker davegut.appTpLinkSmart, line 179
			klapHandshake(devData.baseUrl, localHash, devData) // library marker davegut.appTpLinkSmart, line 180
		} else if (devData.protocol == "KLAP1") { // library marker davegut.appTpLinkSmart, line 181
			klap1Handshake(devData.baseUrl, localHash1, devData) // library marker davegut.appTpLinkSmart, line 182
		} else if (devData.protocol == "AES") { // library marker davegut.appTpLinkSmart, line 183
			aesHandshake(devData.baseUrl, devData) // library marker davegut.appTpLinkSmart, line 184
		} else if (devData.protocol == "AES1") { // library marker davegut.appTpLinkSmart, line 185
			aes1Handshake(devData.baseUrl, devData) // library marker davegut.appTpLinkSmart, line 186
		} else {  // library marker davegut.appTpLinkSmart, line 187
			logData << [ERROR: "Unknown Protocol", discData: discData] // library marker davegut.appTpLinkSmart, line 188
			logWarn(logData) // library marker davegut.appTpLinkSmart, line 189
		} // library marker davegut.appTpLinkSmart, line 190
		pauseExecution(1000) // library marker davegut.appTpLinkSmart, line 191
	} // library marker davegut.appTpLinkSmart, line 192
	atomicState.finding = false // library marker davegut.appTpLinkSmart, line 193
	logDebug(logData) // library marker davegut.appTpLinkSmart, line 194
} // library marker davegut.appTpLinkSmart, line 195

def getDataCmd() { // library marker davegut.appTpLinkSmart, line 197
	List requests = [[method: "get_device_info"]] // library marker davegut.appTpLinkSmart, line 198
	requests << [method: "component_nego"] // library marker davegut.appTpLinkSmart, line 199
	Map cmdBody = [ // library marker davegut.appTpLinkSmart, line 200
		method: "multipleRequest", // library marker davegut.appTpLinkSmart, line 201
		params: [requests: requests]] // library marker davegut.appTpLinkSmart, line 202
	return cmdBody // library marker davegut.appTpLinkSmart, line 203
} // library marker davegut.appTpLinkSmart, line 204

def addToDevices(devData, cmdResp) { // library marker davegut.appTpLinkSmart, line 206
	Map logData = [method: "addToDevices"] // library marker davegut.appTpLinkSmart, line 207
	String dni = devData.dni // library marker davegut.appTpLinkSmart, line 208
	def devicesData = atomicState.devices // library marker davegut.appTpLinkSmart, line 209
	def components = cmdResp.find { it.method == "component_nego" } // library marker davegut.appTpLinkSmart, line 210
	cmdResp = cmdResp.find { it.method == "get_device_info" } // library marker davegut.appTpLinkSmart, line 211
	cmdResp = cmdResp.result // library marker davegut.appTpLinkSmart, line 212
	byte[] plainBytes = cmdResp.nickname.decodeBase64() // library marker davegut.appTpLinkSmart, line 213
	def alias = new String(plainBytes) // library marker davegut.appTpLinkSmart, line 214
	if (alias == "") { alias = cmdResp.model } // library marker davegut.appTpLinkSmart, line 215
	def comps = components.result.component_list // library marker davegut.appTpLinkSmart, line 216
	String tpType = devData.type // library marker davegut.appTpLinkSmart, line 217
	def type = "Unknown" // library marker davegut.appTpLinkSmart, line 218
	def ctHigh // library marker davegut.appTpLinkSmart, line 219
	def ctLow // library marker davegut.appTpLinkSmart, line 220
	//	Creat map deviceData // library marker davegut.appTpLinkSmart, line 221
	Map deviceData = [deviceType: tpType, protocol: devData.protocol, // library marker davegut.appTpLinkSmart, line 222
				   model: devData.model, baseUrl: devData.baseUrl, alias: alias] // library marker davegut.appTpLinkSmart, line 223
	//	Determine Driver to Load // library marker davegut.appTpLinkSmart, line 224
	if (tpType.contains("PLUG") || tpType.contains("SWITCH")) { // library marker davegut.appTpLinkSmart, line 225
		type = "Plug" // library marker davegut.appTpLinkSmart, line 226
		if (comps.find { it.id == "control_child" }) { // library marker davegut.appTpLinkSmart, line 227
			type = "Parent" // library marker davegut.appTpLinkSmart, line 228
		} else if (comps.find { it.id == "dimmer" }) { // library marker davegut.appTpLinkSmart, line 229
			type = "Dimmer" // library marker davegut.appTpLinkSmart, line 230
		} // library marker davegut.appTpLinkSmart, line 231
	} else if (tpType.contains("HUB")) { // library marker davegut.appTpLinkSmart, line 232
		type = "Hub" // library marker davegut.appTpLinkSmart, line 233
	} else if (tpType.contains("BULB")) { // library marker davegut.appTpLinkSmart, line 234
		type = "Dimmer" // library marker davegut.appTpLinkSmart, line 235
		if (comps.find { it.id == "light_strip" }) { // library marker davegut.appTpLinkSmart, line 236
			type = "Lightstrip" // library marker davegut.appTpLinkSmart, line 237
		} else if (comps.find { it.id == "color" }) { // library marker davegut.appTpLinkSmart, line 238
			type = "Color Bulb" // library marker davegut.appTpLinkSmart, line 239
		} // library marker davegut.appTpLinkSmart, line 240
		//	Get color temp range for Bulb and Lightstrip // library marker davegut.appTpLinkSmart, line 241
		if (type != "Dimmer" && comps.find { it.id == "color_temperature" } ) { // library marker davegut.appTpLinkSmart, line 242
			ctHigh = cmdResp.color_temp_range[1] // library marker davegut.appTpLinkSmart, line 243
			ctLow = cmdResp.color_temp_range[0] // library marker davegut.appTpLinkSmart, line 244
			deviceData << [ctHigh: ctHigh, ctLow: ctLow] // library marker davegut.appTpLinkSmart, line 245
		} // library marker davegut.appTpLinkSmart, line 246
	} else if (tpType.contains("ROBOVAC")) { // library marker davegut.appTpLinkSmart, line 247
		type = "Robovac" // library marker davegut.appTpLinkSmart, line 248
	} // library marker davegut.appTpLinkSmart, line 249
	//	Determine device-specific data relative to device settings // library marker davegut.appTpLinkSmart, line 250
	def hasLed = "false" // library marker davegut.appTpLinkSmart, line 251
	if (comps.find { it.id == "led" } ) { hasLed = "true" } // library marker davegut.appTpLinkSmart, line 252
	def isEm = "false" // library marker davegut.appTpLinkSmart, line 253
	if (comps.find { it.id == "energy_monitoring" } ) { isEm = "true" } // library marker davegut.appTpLinkSmart, line 254
	def gradOnOff = "false" // library marker davegut.appTpLinkSmart, line 255
	if (comps.find { it.id == "on_off_gradually" } ) { gradOnOff = "true" } // library marker davegut.appTpLinkSmart, line 256
	deviceData << [type: type, hasLed: hasLed, isEm: isEm, gradOnOff: gradOnOff] // library marker davegut.appTpLinkSmart, line 257
	//	Add to devices and close out method // library marker davegut.appTpLinkSmart, line 258
	devicesData << ["${dni}": deviceData] // library marker davegut.appTpLinkSmart, line 259
	atomicState.devices = devicesData // library marker davegut.appTpLinkSmart, line 260
	logData << ["${deviceData.alias}": deviceData, dni: dni] // library marker davegut.appTpLinkSmart, line 261
	Map InfoData = ["${deviceData.alias}": "added to device data"] // library marker davegut.appTpLinkSmart, line 262
	logInfo("${deviceData.alias}: added to device data") // library marker davegut.appTpLinkSmart, line 263
	updateChild(dni,deviceData) // library marker davegut.appTpLinkSmart, line 264
	logDebug(logData) // library marker davegut.appTpLinkSmart, line 265
} // library marker davegut.appTpLinkSmart, line 266

def updateChild(dni, deviceData) { // library marker davegut.appTpLinkSmart, line 268
	def child = getChildDevice(dni) // library marker davegut.appTpLinkSmart, line 269
	if (child) { // library marker davegut.appTpLinkSmart, line 270
		child.updateChild(deviceData) // library marker davegut.appTpLinkSmart, line 271
	} // library marker davegut.appTpLinkSmart, line 272
} // library marker davegut.appTpLinkSmart, line 273

//	===== get Smart KLAP Protocol Data ===== // library marker davegut.appTpLinkSmart, line 275
def sendKlapDataCmd(handshakeData, data) { // library marker davegut.appTpLinkSmart, line 276
	if (handshakeData.respStatus != "Login OK") { // library marker davegut.appTpLinkSmart, line 277
		Map logData = [method: "sendKlapDataCmd", handshake: handshakeData] // library marker davegut.appTpLinkSmart, line 278
		logWarn(logData) // library marker davegut.appTpLinkSmart, line 279
	} else { // library marker davegut.appTpLinkSmart, line 280
		Map reqParams = [timeout: 10, headers: ["Cookie": data.data.cookie]] // library marker davegut.appTpLinkSmart, line 281
		def seqNo = data.data.seqNo + 1 // library marker davegut.appTpLinkSmart, line 282
		String cmdBodyJson = new groovy.json.JsonBuilder(getDataCmd()).toString() // library marker davegut.appTpLinkSmart, line 283
		Map encryptedData = klapEncrypt(cmdBodyJson.getBytes(), data.data.encKey,  // library marker davegut.appTpLinkSmart, line 284
										data.data.encIv, data.data.encSig, seqNo) // library marker davegut.appTpLinkSmart, line 285
		reqParams << [uri: "${data.data.baseUrl}/request?seq=${encryptedData.seqNumber}", // library marker davegut.appTpLinkSmart, line 286
					  body: encryptedData.cipherData, // library marker davegut.appTpLinkSmart, line 287
					  contentType: "application/octet-stream", // library marker davegut.appTpLinkSmart, line 288
					  requestContentType: "application/octet-stream"] // library marker davegut.appTpLinkSmart, line 289
		asynchttpPost("parseKlapResp", reqParams, [data: data.data]) // library marker davegut.appTpLinkSmart, line 290
	} // library marker davegut.appTpLinkSmart, line 291
} // library marker davegut.appTpLinkSmart, line 292

def parseKlapResp(resp, data) { // library marker davegut.appTpLinkSmart, line 294
	Map logData = [method: "parseKlapResp"] // library marker davegut.appTpLinkSmart, line 295
	if (resp.status == 200) { // library marker davegut.appTpLinkSmart, line 296
		try { // library marker davegut.appTpLinkSmart, line 297
			byte[] cipherResponse = resp.data.decodeBase64()[32..-1] // library marker davegut.appTpLinkSmart, line 298
			def clearResp = klapDecrypt(cipherResponse, data.data.encKey, // library marker davegut.appTpLinkSmart, line 299
										data.data.encIv, data.data.seqNo + 1) // library marker davegut.appTpLinkSmart, line 300
			Map cmdResp =  new JsonSlurper().parseText(clearResp) // library marker davegut.appTpLinkSmart, line 301
			logData << [status: "OK", cmdResp: cmdResp] // library marker davegut.appTpLinkSmart, line 302
			if (cmdResp.error_code == 0) { // library marker davegut.appTpLinkSmart, line 303
				addToDevices(data.data.devData, cmdResp.result.responses) // library marker davegut.appTpLinkSmart, line 304
				logDebug(logData) // library marker davegut.appTpLinkSmart, line 305
			} else { // library marker davegut.appTpLinkSmart, line 306
				logData << [status: "errorInCmdResp"] // library marker davegut.appTpLinkSmart, line 307
				logWarn(logData) // library marker davegut.appTpLinkSmart, line 308
			} // library marker davegut.appTpLinkSmart, line 309
		} catch (err) { // library marker davegut.appTpLinkSmart, line 310
			logData << [status: "deviceDataParseError", error: err, dataLength: resp.data.length()] // library marker davegut.appTpLinkSmart, line 311
			logWarn(logData) // library marker davegut.appTpLinkSmart, line 312
		} // library marker davegut.appTpLinkSmart, line 313
	} else { // library marker davegut.appTpLinkSmart, line 314
		logData << [status: "httpFailure", data: resp.properties] // library marker davegut.appTpLinkSmart, line 315
		logWarn(logData) // library marker davegut.appTpLinkSmart, line 316
	} // library marker davegut.appTpLinkSmart, line 317
} // library marker davegut.appTpLinkSmart, line 318

//	===== get Smart KLAP1 Protocol Data ===== // library marker davegut.appTpLinkSmart, line 320
def sendKlap1DataCmd(handshakeData, data) { // library marker davegut.appTpLinkSmart, line 321
	if (handshakeData.respStatus != "Login OK") { // library marker davegut.appTpLinkSmart, line 322
		Map logData = [method: "sendKlap1DataCmd", handshake: handshakeData] // library marker davegut.appTpLinkSmart, line 323
		logWarn(logData) // library marker davegut.appTpLinkSmart, line 324
	} else { // library marker davegut.appTpLinkSmart, line 325
		def seqNo = data.data.seqNo + 1 // library marker davegut.appTpLinkSmart, line 326
		String cmdBodyJson = new groovy.json.JsonBuilder(getDataCmd()).toString() // library marker davegut.appTpLinkSmart, line 327
		Map encryptedData = klap1Encrypt(cmdBodyJson.getBytes(), data.data.encKey,  // library marker davegut.appTpLinkSmart, line 328
										data.data.encIv, data.data.encSig, seqNo) // library marker davegut.appTpLinkSmart, line 329
		Map reqParams = [ // library marker davegut.appTpLinkSmart, line 330
			uri: "${data.data.baseUrl}/request?seq=${encryptedData.seqNumber}", // library marker davegut.appTpLinkSmart, line 331
			body: encryptedData.cipherData, // library marker davegut.appTpLinkSmart, line 332
			ignoreSSLIssues: true, // library marker davegut.appTpLinkSmart, line 333
			timeout:10, // library marker davegut.appTpLinkSmart, line 334
////////////////////// // library marker davegut.appTpLinkSmart, line 335
//			headers: ["Cookie": cookie], // library marker davegut.appTpLinkSmart, line 336
////////////////////// // library marker davegut.appTpLinkSmart, line 337
			contentType: "application/octet-stream", // library marker davegut.appTpLinkSmart, line 338
			requestContentType: "application/octet-stream"] // library marker davegut.appTpLinkSmart, line 339
///////////////// // library marker davegut.appTpLinkSmart, line 340
				if (cookie != null ) { reqParams << [headers: ["Cookie": cookie]] } // library marker davegut.appTpLinkSmart, line 341
///////////////// // library marker davegut.appTpLinkSmart, line 342
		asynchttpPost("parseKlap1Resp", reqParams, [data: data.data]) // library marker davegut.appTpLinkSmart, line 343
	} // library marker davegut.appTpLinkSmart, line 344
} // library marker davegut.appTpLinkSmart, line 345

def parseKlap1Resp(resp, data) { // library marker davegut.appTpLinkSmart, line 347
	Map logData = [method: "parseKlap1Resp"] // library marker davegut.appTpLinkSmart, line 348
	if (resp.status == 200) { // library marker davegut.appTpLinkSmart, line 349
		try { // library marker davegut.appTpLinkSmart, line 350
			byte[] cipherResponse = resp.data.decodeBase64()[32..-1] // library marker davegut.appTpLinkSmart, line 351
			def clearResp = klap1Decrypt(cipherResponse, data.data.encKey, // library marker davegut.appTpLinkSmart, line 352
										data.data.encIv, data.data.seqNo + 1) // library marker davegut.appTpLinkSmart, line 353
			Map cmdResp =  new JsonSlurper().parseText(clearResp) // library marker davegut.appTpLinkSmart, line 354
			logData << [status: "OK", cmdResp: cmdResp] // library marker davegut.appTpLinkSmart, line 355
			if (cmdResp.error_code == 0) { // library marker davegut.appTpLinkSmart, line 356
				addToDevices(data.data.devData, cmdResp.result.responses) // library marker davegut.appTpLinkSmart, line 357
				logDebug(logData) // library marker davegut.appTpLinkSmart, line 358
			} else { // library marker davegut.appTpLinkSmart, line 359
				logData << [status: "errorInCmdResp"] // library marker davegut.appTpLinkSmart, line 360
				logWarn(logData) // library marker davegut.appTpLinkSmart, line 361
			} // library marker davegut.appTpLinkSmart, line 362
		} catch (err) { // library marker davegut.appTpLinkSmart, line 363
			logData << [status: "deviceDataParseError", error: err, dataLength: resp.data.length()] // library marker davegut.appTpLinkSmart, line 364
			logWarn(logData) // library marker davegut.appTpLinkSmart, line 365
		} // library marker davegut.appTpLinkSmart, line 366
	} else { // library marker davegut.appTpLinkSmart, line 367
		logData << [status: "httpFailure", data: resp.properties] // library marker davegut.appTpLinkSmart, line 368
		logWarn(logData) // library marker davegut.appTpLinkSmart, line 369
	} // library marker davegut.appTpLinkSmart, line 370
} // library marker davegut.appTpLinkSmart, line 371

//	===== get Smart AES Protocol Data ===== // library marker davegut.appTpLinkSmart, line 373
def getAesToken(resp, data) { // library marker davegut.appTpLinkSmart, line 374
	Map logData = [method: "getAesToken"] // library marker davegut.appTpLinkSmart, line 375
	if (resp.status == 200) { // library marker davegut.appTpLinkSmart, line 376
		if (resp.json.error_code == 0) { // library marker davegut.appTpLinkSmart, line 377
			try { // library marker davegut.appTpLinkSmart, line 378
				def clearResp = aesDecrypt(resp.json.result.response, data.encKey, data.encIv) // library marker davegut.appTpLinkSmart, line 379
				Map cmdResp = new JsonSlurper().parseText(clearResp) // library marker davegut.appTpLinkSmart, line 380
				if (cmdResp.error_code == 0) { // library marker davegut.appTpLinkSmart, line 381
					def token = cmdResp.result.token // library marker davegut.appTpLinkSmart, line 382
					logData << [respStatus: "OK", token: token] // library marker davegut.appTpLinkSmart, line 383
					logDebug(logData) // library marker davegut.appTpLinkSmart, line 384
					sendAesDataCmd(token, data) // library marker davegut.appTpLinkSmart, line 385
				} else { // library marker davegut.appTpLinkSmart, line 386
					logData << [respStatus: "ERROR code in cmdResp",  // library marker davegut.appTpLinkSmart, line 387
								error_code: cmdResp.error_code, // library marker davegut.appTpLinkSmart, line 388
								check: "cryptoArray, credentials", data: cmdResp] // library marker davegut.appTpLinkSmart, line 389
					logWarn(logData) // library marker davegut.appTpLinkSmart, line 390
				} // library marker davegut.appTpLinkSmart, line 391
			} catch (err) { // library marker davegut.appTpLinkSmart, line 392
				logData << [respStatus: "ERROR parsing respJson", respJson: resp.json, // library marker davegut.appTpLinkSmart, line 393
							error: err] // library marker davegut.appTpLinkSmart, line 394
				logWarn(logData) // library marker davegut.appTpLinkSmart, line 395
			} // library marker davegut.appTpLinkSmart, line 396
		} else { // library marker davegut.appTpLinkSmart, line 397
			logData << [respStatus: "ERROR code in resp.json", errorCode: resp.json.error_code, // library marker davegut.appTpLinkSmart, line 398
						respJson: resp.json] // library marker davegut.appTpLinkSmart, line 399
			logWarn(logData) // library marker davegut.appTpLinkSmart, line 400
		} // library marker davegut.appTpLinkSmart, line 401
	} else { // library marker davegut.appTpLinkSmart, line 402
		logData << [respStatus: "ERROR in HTTP response", respStatus: resp.status, data: resp.properties] // library marker davegut.appTpLinkSmart, line 403
		logWarn(logData) // library marker davegut.appTpLinkSmart, line 404
	} // library marker davegut.appTpLinkSmart, line 405
} // library marker davegut.appTpLinkSmart, line 406

def sendAesDataCmd(token, data) { // library marker davegut.appTpLinkSmart, line 408
	def cmdStr = JsonOutput.toJson(getDataCmd()).toString() // library marker davegut.appTpLinkSmart, line 409
	Map reqBody = [method: "securePassthrough", // library marker davegut.appTpLinkSmart, line 410
				   params: [request: aesEncrypt(cmdStr, data.encKey, data.encIv)]] // library marker davegut.appTpLinkSmart, line 411
	Map reqParams = [uri: "${data.baseUrl}?token=${token}", // library marker davegut.appTpLinkSmart, line 412
					 body: new groovy.json.JsonBuilder(reqBody).toString(), // library marker davegut.appTpLinkSmart, line 413
					 contentType: "application/json", // library marker davegut.appTpLinkSmart, line 414
					 requestContentType: "application/json", // library marker davegut.appTpLinkSmart, line 415
					 timeout: 10,  // library marker davegut.appTpLinkSmart, line 416
					 headers: ["Cookie": data.cookie]] // library marker davegut.appTpLinkSmart, line 417
	asynchttpPost("parseAesResp", reqParams, [data: data]) // library marker davegut.appTpLinkSmart, line 418
} // library marker davegut.appTpLinkSmart, line 419

def parseAesResp(resp, data) { // library marker davegut.appTpLinkSmart, line 421
	Map logData = [method: "parseAesResp"] // library marker davegut.appTpLinkSmart, line 422
	if (resp.status == 200) { // library marker davegut.appTpLinkSmart, line 423
		try { // library marker davegut.appTpLinkSmart, line 424
			Map cmdResp = new JsonSlurper().parseText(aesDecrypt(resp.json.result.response, // library marker davegut.appTpLinkSmart, line 425
																 data.data.encKey, data.data.encIv)) // library marker davegut.appTpLinkSmart, line 426
			logData << [status: "OK", cmdResp: cmdResp] // library marker davegut.appTpLinkSmart, line 427
			if (cmdResp.error_code == 0) { // library marker davegut.appTpLinkSmart, line 428
				addToDevices(data.data.devData, cmdResp.result.responses) // library marker davegut.appTpLinkSmart, line 429
				logDebug(logData) // library marker davegut.appTpLinkSmart, line 430
			} else { // library marker davegut.appTpLinkSmart, line 431
				logData << [status: "errorInCmdResp"] // library marker davegut.appTpLinkSmart, line 432
				logWarn(logData) // library marker davegut.appTpLinkSmart, line 433
			} // library marker davegut.appTpLinkSmart, line 434
		} catch (err) { // library marker davegut.appTpLinkSmart, line 435
			logData << [status: "deviceDataParseError", error: err, dataLength: resp.data.length()] // library marker davegut.appTpLinkSmart, line 436
			logWarn(logData) // library marker davegut.appTpLinkSmart, line 437
		} // library marker davegut.appTpLinkSmart, line 438
	} else { // library marker davegut.appTpLinkSmart, line 439
		logData << [status: "httpFailure", data: resp.properties] // library marker davegut.appTpLinkSmart, line 440
		logWarn(logData) // library marker davegut.appTpLinkSmart, line 441
	} // library marker davegut.appTpLinkSmart, line 442
} // library marker davegut.appTpLinkSmart, line 443

//	===== get Smart AES1 Protocol Data ===== // library marker davegut.appTpLinkSmart, line 445
def aes1Handshake(baseUrl, devData) { // library marker davegut.appTpLinkSmart, line 446
	Map reqData = [baseUrl: baseUrl, devData: devData] // library marker davegut.appTpLinkSmart, line 447
	Map cmdBody = [method: "login", // library marker davegut.appTpLinkSmart, line 448
				   params: [hashed: true,  // library marker davegut.appTpLinkSmart, line 449
							password: encPasswordVac, // library marker davegut.appTpLinkSmart, line 450
							username: userName]] // library marker davegut.appTpLinkSmart, line 451
	Map reqParams = [uri: baseUrl, // library marker davegut.appTpLinkSmart, line 452
					 ignoreSSLIssues: true, // library marker davegut.appTpLinkSmart, line 453
					 body: cmdBody, // library marker davegut.appTpLinkSmart, line 454
					 contentType: "application/json", // library marker davegut.appTpLinkSmart, line 455
					 requestContentType: "application/json", // library marker davegut.appTpLinkSmart, line 456
					 timeout: 10] // library marker davegut.appTpLinkSmart, line 457
	asynchttpPost("parseAes1Login", reqParams, [data: reqData]) // library marker davegut.appTpLinkSmart, line 458
} // library marker davegut.appTpLinkSmart, line 459

def parseAes1Login(resp, data) { // library marker davegut.appTpLinkSmart, line 461
	Map logData = [method: "parseAes1Login", oldToken: token] // library marker davegut.appTpLinkSmart, line 462
	if (resp.status == 200 && resp.json != null) { // library marker davegut.appTpLinkSmart, line 463
		logData << [status: "OK"] // library marker davegut.appTpLinkSmart, line 464
		logData << [token: resp.json.result.token] // library marker davegut.appTpLinkSmart, line 465
		sendAes1DataCmd(resp.json.result.token, data) // library marker davegut.appTpLinkSmart, line 466
		logDebug(logData) // library marker davegut.appTpLinkSmart, line 467
	} else { // library marker davegut.appTpLinkSmart, line 468
		logData << [respStatus: "ERROR in HTTP response", resp: resp.properties] // library marker davegut.appTpLinkSmart, line 469
		logWarn(logData) // library marker davegut.appTpLinkSmart, line 470
	} // library marker davegut.appTpLinkSmart, line 471
} // library marker davegut.appTpLinkSmart, line 472

def sendAes1DataCmd(token, data) { // library marker davegut.appTpLinkSmart, line 474
	Map devData = data.data.devData // library marker davegut.appTpLinkSmart, line 475
	Map reqParams = [uri: "${data.data.baseUrl}/?token=${token}", // library marker davegut.appTpLinkSmart, line 476
					 body: getDataCmd(), // library marker davegut.appTpLinkSmart, line 477
					 contentType: "application/json", // library marker davegut.appTpLinkSmart, line 478
					 requestContentType: "application/json", // library marker davegut.appTpLinkSmart, line 479
					 ignoreSSLIssues: true, // library marker davegut.appTpLinkSmart, line 480
					 timeout: 10] // library marker davegut.appTpLinkSmart, line 481
	asynchttpPost("parseAes1Resp", reqParams, [data: devData]) // library marker davegut.appTpLinkSmart, line 482
} // library marker davegut.appTpLinkSmart, line 483

def parseAes1Resp(resp, devData) { // library marker davegut.appTpLinkSmart, line 485
	Map logData = [parseMethod: "parseAes1Resp"] // library marker davegut.appTpLinkSmart, line 486
	try { // library marker davegut.appTpLinkSmart, line 487
		Map cmdResp = resp.json // library marker davegut.appTpLinkSmart, line 488
		logData << [status: "OK", cmdResp: cmdResp] // library marker davegut.appTpLinkSmart, line 489
			if (cmdResp.error_code == 0) { // library marker davegut.appTpLinkSmart, line 490
				addToDevices(devData.data, cmdResp.result.responses) // library marker davegut.appTpLinkSmart, line 491
				logDebug(logData) // library marker davegut.appTpLinkSmart, line 492
			} else { // library marker davegut.appTpLinkSmart, line 493
				logData << [status: "errorInCmdResp"] // library marker davegut.appTpLinkSmart, line 494
				logWarn(logData) // library marker davegut.appTpLinkSmart, line 495
			} // library marker davegut.appTpLinkSmart, line 496
	} catch (err) { // library marker davegut.appTpLinkSmart, line 497
		logData << [status: "deviceDataParseError", error: err, dataLength: resp.data.length()] // library marker davegut.appTpLinkSmart, line 498
		logWarn(logData) // library marker davegut.appTpLinkSmart, line 499
	} // library marker davegut.appTpLinkSmart, line 500
	return parseData	 // library marker davegut.appTpLinkSmart, line 501
} // library marker davegut.appTpLinkSmart, line 502

def tpLinkCheckForDevices(timeout = 3) { // library marker davegut.appTpLinkSmart, line 504
	Map logData = [method: "tpLinkCheckForDevices"] // library marker davegut.appTpLinkSmart, line 505
	def checked = true // library marker davegut.appTpLinkSmart, line 506
	if (state.tpLinkChecked == true) { // library marker davegut.appTpLinkSmart, line 507
		checked = false // library marker davegut.appTpLinkSmart, line 508
		logData << [status: "noCheck", reason: "Completed within last 10 minutes"] // library marker davegut.appTpLinkSmart, line 509
	} else { // library marker davegut.appTpLinkSmart, line 510
		def findData = findTpLinkDevices("parseTpLinkCheck", timeout) // library marker davegut.appTpLinkSmart, line 511
		logData << [status: "checking"] // library marker davegut.appTpLinkSmart, line 512
		pauseExecution(5000) // library marker davegut.appTpLinkSmart, line 513
	} // library marker davegut.appTpLinkSmart, line 514
	logDebug(logData) // library marker davegut.appTpLinkSmart, line 515
	return checked // library marker davegut.appTpLinkSmart, line 516
} // library marker davegut.appTpLinkSmart, line 517

def resetTpLinkChecked() { state.tpLinkChecked = false } // library marker davegut.appTpLinkSmart, line 519

def parseTpLinkCheck(response) { // library marker davegut.appTpLinkSmart, line 521
	List discData = [] // library marker davegut.appTpLinkSmart, line 522
	if (response instanceof Map) { // library marker davegut.appTpLinkSmart, line 523
		Map devdata = getDiscData(response) // library marker davegut.appTpLinkSmart, line 524
		if (devData.status != "INVALID") { // library marker davegut.appTpLinkSmart, line 525
			discData << devData // library marker davegut.appTpLinkSmart, line 526
		} // library marker davegut.appTpLinkSmart, line 527
	} else { // library marker davegut.appTpLinkSmart, line 528
		response.each { // library marker davegut.appTpLinkSmart, line 529
			Map devData = getDiscData(it) // library marker davegut.appTpLinkSmart, line 530
			if (devData.status == "OK") { // library marker davegut.appTpLinkSmart, line 531
				discData << devData // library marker davegut.appTpLinkSmart, line 532
			} // library marker davegut.appTpLinkSmart, line 533
		} // library marker davegut.appTpLinkSmart, line 534
	} // library marker davegut.appTpLinkSmart, line 535
	atomicState.finding = false // library marker davegut.appTpLinkSmart, line 536
	updateTpLinkDevices(discData) // library marker davegut.appTpLinkSmart, line 537
} // library marker davegut.appTpLinkSmart, line 538

def updateTpLinkDevices(discData) { // library marker davegut.appTpLinkSmart, line 540
	Map logData = [method: "updateTpLinkDevices"] // library marker davegut.appTpLinkSmart, line 541
	state.tpLinkChecked = true // library marker davegut.appTpLinkSmart, line 542
	runIn(570, resetTpLinkChecked) // library marker davegut.appTpLinkSmart, line 543
	List children = getChildDevices() // library marker davegut.appTpLinkSmart, line 544
	children.each { childDev -> // library marker davegut.appTpLinkSmart, line 545
		Map childData = [:] // library marker davegut.appTpLinkSmart, line 546
		def dni = childDev.deviceNetworkId // library marker davegut.appTpLinkSmart, line 547
		def connected = "false" // library marker davegut.appTpLinkSmart, line 548
		Map devData = discData.find{ it.dni == dni } // library marker davegut.appTpLinkSmart, line 549
		if (childDev.getDataValue("baseUrl")) { // library marker davegut.appTpLinkSmart, line 550
			if (devData != null) { // library marker davegut.appTpLinkSmart, line 551
				if (childDev.getDataValue("baseUrl") == devData.baseUrl && // library marker davegut.appTpLinkSmart, line 552
				    childDev.getDataValue("protocol") == devData.protocol) { // library marker davegut.appTpLinkSmart, line 553
					childData << [status: "noChanges"] // library marker davegut.appTpLinkSmart, line 554
				} else { // library marker davegut.appTpLinkSmart, line 555
					childDev.updateDataValue("baseUrl", devData.baseUrl) // library marker davegut.appTpLinkSmart, line 556
					childDev.updateDataValue("protocol", devData.protocol) // library marker davegut.appTpLinkSmart, line 557
					childData << ["baseUrl": devData.baseUrl, // library marker davegut.appTpLinkSmart, line 558
								  "protocol": devData.protocol, // library marker davegut.appTpLinkSmart, line 559
								  "connected": "true"] // library marker davegut.appTpLinkSmart, line 560
				} // library marker davegut.appTpLinkSmart, line 561
			} else { // library marker davegut.appTpLinkSmart, line 562
				Map warnData = [method: "updateTpLinkDevices", device: childDev, // library marker davegut.appTpLinkSmart, line 563
								connected: "false", reason: "not Discovered By App"] // library marker davegut.appTpLinkSmart, line 564
				logWarn(warnData) // library marker davegut.appTpLinkSmart, line 565
			} // library marker davegut.appTpLinkSmart, line 566
			pauseExecution(500) // library marker davegut.appTpLinkSmart, line 567
		} // library marker davegut.appTpLinkSmart, line 568
		logData << ["${childDev}": childData] // library marker davegut.appTpLinkSmart, line 569
	} // library marker davegut.appTpLinkSmart, line 570
	logDebug(logData) // library marker davegut.appTpLinkSmart, line 571
} // library marker davegut.appTpLinkSmart, line 572

// ~~~~~ end include (252) davegut.appTpLinkSmart ~~~~~

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
			logData << parseVacAesData(resp) // library marker davegut.tpLinkComms, line 44
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
	asynchttpPost("parseAesHandshake", reqParams, [data: reqData]) // library marker davegut.tpLinkCrypto, line 27
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
			asynchttpPost("parseAesLogin", reqParams, [data: reqData]) // library marker davegut.tpLinkCrypto, line 73
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
						setCommsError(200) // library marker davegut.tpLinkCrypto, line 100
						logDebug(logData) // library marker davegut.tpLinkCrypto, line 101
					} else { // library marker davegut.tpLinkCrypto, line 102
						logData << [respStatus: "ERROR code in cmdResp",  // library marker davegut.tpLinkCrypto, line 103
									error_code: cmdResp.error_code, // library marker davegut.tpLinkCrypto, line 104
									check: "cryptoArray, credentials", data: cmdResp] // library marker davegut.tpLinkCrypto, line 105
						logInfo(logData) // library marker davegut.tpLinkCrypto, line 106
					} // library marker davegut.tpLinkCrypto, line 107
				} catch (err) { // library marker davegut.tpLinkCrypto, line 108
					logData << [respStatus: "ERROR parsing respJson", respJson: resp.json, // library marker davegut.tpLinkCrypto, line 109
								error: err] // library marker davegut.tpLinkCrypto, line 110
					logInfo(logData) // library marker davegut.tpLinkCrypto, line 111
				} // library marker davegut.tpLinkCrypto, line 112
			} else { // library marker davegut.tpLinkCrypto, line 113
				logData << [respStatus: "ERROR code in resp.json", errorCode: resp.json.error_code, // library marker davegut.tpLinkCrypto, line 114
							respJson: resp.json] // library marker davegut.tpLinkCrypto, line 115
				logInfo(logData) // library marker davegut.tpLinkCrypto, line 116
			} // library marker davegut.tpLinkCrypto, line 117
		} else { // library marker davegut.tpLinkCrypto, line 118
			logData << [respStatus: "ERROR in HTTP response", respStatus: resp.status, data: resp.properties] // library marker davegut.tpLinkCrypto, line 119
			logInfo(logData) // library marker davegut.tpLinkCrypto, line 120
		} // library marker davegut.tpLinkCrypto, line 121
	} else { // library marker davegut.tpLinkCrypto, line 122
		//	Code used in application only. // library marker davegut.tpLinkCrypto, line 123
		getAesToken(resp, data.data) // library marker davegut.tpLinkCrypto, line 124
	} // library marker davegut.tpLinkCrypto, line 125
} // library marker davegut.tpLinkCrypto, line 126

//	===== KLAP Handshake ===== // library marker davegut.tpLinkCrypto, line 128
def klapHandshake(baseUrl = getDataValue("baseUrl"), localHash = parent.localHash, devData = null) { // library marker davegut.tpLinkCrypto, line 129
	byte[] localSeed = new byte[16] // library marker davegut.tpLinkCrypto, line 130
	new Random().nextBytes(localSeed) // library marker davegut.tpLinkCrypto, line 131
	Map reqData = [localSeed: localSeed, baseUrl: baseUrl, localHash: localHash, devData:devData] // library marker davegut.tpLinkCrypto, line 132
	Map reqParams = [uri: "${baseUrl}/handshake1", // library marker davegut.tpLinkCrypto, line 133
					 body: localSeed, // library marker davegut.tpLinkCrypto, line 134
					 contentType: "application/octet-stream", // library marker davegut.tpLinkCrypto, line 135
					 requestContentType: "application/octet-stream", // library marker davegut.tpLinkCrypto, line 136
					 timeout:10] // library marker davegut.tpLinkCrypto, line 137
	asynchttpPost("parseKlapHandshake", reqParams, [data: reqData]) // library marker davegut.tpLinkCrypto, line 138
} // library marker davegut.tpLinkCrypto, line 139

def parseKlapHandshake(resp, data) { // library marker davegut.tpLinkCrypto, line 141
	Map logData = [method: "parseKlapHandshake"] // library marker davegut.tpLinkCrypto, line 142
	if (resp.status == 200 && resp.data != null) { // library marker davegut.tpLinkCrypto, line 143
		try { // library marker davegut.tpLinkCrypto, line 144
			Map reqData = [devData: data.data.devData, baseUrl: data.data.baseUrl] // library marker davegut.tpLinkCrypto, line 145
			byte[] localSeed = data.data.localSeed // library marker davegut.tpLinkCrypto, line 146
			byte[] seedData = resp.data.decodeBase64() // library marker davegut.tpLinkCrypto, line 147
			byte[] remoteSeed = seedData[0 .. 15] // library marker davegut.tpLinkCrypto, line 148
			byte[] serverHash = seedData[16 .. 47] // library marker davegut.tpLinkCrypto, line 149
			byte[] localHash = data.data.localHash.decodeBase64() // library marker davegut.tpLinkCrypto, line 150
			byte[] authHash = [localSeed, remoteSeed, localHash].flatten() // library marker davegut.tpLinkCrypto, line 151
			byte[] localAuthHash = mdEncode("SHA-256", authHash) // library marker davegut.tpLinkCrypto, line 152
			if (localAuthHash == serverHash) { // library marker davegut.tpLinkCrypto, line 153
				//	cookie // library marker davegut.tpLinkCrypto, line 154
				def cookieHeader = resp.headers["Set-Cookie"].toString() // library marker davegut.tpLinkCrypto, line 155
				def cookie = cookieHeader.substring(cookieHeader.indexOf(":") +1, cookieHeader.indexOf(";")) // library marker davegut.tpLinkCrypto, line 156
				//	seqNo and encIv // library marker davegut.tpLinkCrypto, line 157
				byte[] payload = ["iv".getBytes(), localSeed, remoteSeed, localHash].flatten() // library marker davegut.tpLinkCrypto, line 158
				byte[] fullIv = mdEncode("SHA-256", payload) // library marker davegut.tpLinkCrypto, line 159
				byte[] byteSeqNo = fullIv[-4..-1] // library marker davegut.tpLinkCrypto, line 160

				int seqNo = byteArrayToInteger(byteSeqNo) // library marker davegut.tpLinkCrypto, line 162
				atomicState.seqNo = seqNo // library marker davegut.tpLinkCrypto, line 163

				//	encKey // library marker davegut.tpLinkCrypto, line 165
				payload = ["lsk".getBytes(), localSeed, remoteSeed, localHash].flatten() // library marker davegut.tpLinkCrypto, line 166
				byte[] encKey = mdEncode("SHA-256", payload)[0..15] // library marker davegut.tpLinkCrypto, line 167
				//	encSig // library marker davegut.tpLinkCrypto, line 168
				payload = ["ldk".getBytes(), localSeed, remoteSeed, localHash].flatten() // library marker davegut.tpLinkCrypto, line 169
				byte[] encSig = mdEncode("SHA-256", payload)[0..27] // library marker davegut.tpLinkCrypto, line 170
				if (device) { // library marker davegut.tpLinkCrypto, line 171
					device.updateSetting("cookie",[type:"password", value: cookie])  // library marker davegut.tpLinkCrypto, line 172
					device.updateSetting("encKey",[type:"password", value: encKey])  // library marker davegut.tpLinkCrypto, line 173
					device.updateSetting("encIv",[type:"password", value: fullIv[0..11]])  // library marker davegut.tpLinkCrypto, line 174
					device.updateSetting("encSig",[type:"password", value: encSig])  // library marker davegut.tpLinkCrypto, line 175
				} else { // library marker davegut.tpLinkCrypto, line 176
					reqData << [cookie: cookie, seqNo: seqNo, encIv: fullIv[0..11],  // library marker davegut.tpLinkCrypto, line 177
								encSig: encSig, encKey: encKey] // library marker davegut.tpLinkCrypto, line 178
				} // library marker davegut.tpLinkCrypto, line 179
				byte[] loginHash = [remoteSeed, localSeed, localHash].flatten() // library marker davegut.tpLinkCrypto, line 180
				byte[] body = mdEncode("SHA-256", loginHash) // library marker davegut.tpLinkCrypto, line 181
				Map reqParams = [uri: "${data.data.baseUrl}/handshake2", // library marker davegut.tpLinkCrypto, line 182
								 body: body, // library marker davegut.tpLinkCrypto, line 183
								 timeout:10, // library marker davegut.tpLinkCrypto, line 184
								 headers: ["Cookie": cookie], // library marker davegut.tpLinkCrypto, line 185
								 contentType: "application/octet-stream", // library marker davegut.tpLinkCrypto, line 186
								 requestContentType: "application/octet-stream"] // library marker davegut.tpLinkCrypto, line 187
				asynchttpPost("parseKlapHandshake2", reqParams, [data: reqData]) // library marker davegut.tpLinkCrypto, line 188
			} else { // library marker davegut.tpLinkCrypto, line 189
				logData << [respStatus: "ERROR: localAuthHash != serverHash", // library marker davegut.tpLinkCrypto, line 190
							action: "<b>Check credentials and try again</b>"] // library marker davegut.tpLinkCrypto, line 191
				logWarn(logData) // library marker davegut.tpLinkCrypto, line 192
			} // library marker davegut.tpLinkCrypto, line 193
		} catch (err) { // library marker davegut.tpLinkCrypto, line 194
			logData << [respStatus: "ERROR parsing 200 response", resp: resp.properties, error: err] // library marker davegut.tpLinkCrypto, line 195
			logData << [action: "<b>Try Configure command</b>"] // library marker davegut.tpLinkCrypto, line 196
			logWarn(logData) // library marker davegut.tpLinkCrypto, line 197
		} // library marker davegut.tpLinkCrypto, line 198
	} else { // library marker davegut.tpLinkCrypto, line 199
		logData << [respStatus: resp.status, message: resp.errorMessage] // library marker davegut.tpLinkCrypto, line 200
		logData << [action: "<b>Try Configure command</b>"] // library marker davegut.tpLinkCrypto, line 201
		logWarn(logData) // library marker davegut.tpLinkCrypto, line 202
	} // library marker davegut.tpLinkCrypto, line 203
} // library marker davegut.tpLinkCrypto, line 204

def parseKlapHandshake2(resp, data) { // library marker davegut.tpLinkCrypto, line 206
	Map logData = [method: "parseKlapHandshake2"] // library marker davegut.tpLinkCrypto, line 207
	if (resp.status == 200 && resp.data == null) { // library marker davegut.tpLinkCrypto, line 208
		logData << [respStatus: "Login OK"] // library marker davegut.tpLinkCrypto, line 209
		setCommsError(200) // library marker davegut.tpLinkCrypto, line 210
		logDebug(logData) // library marker davegut.tpLinkCrypto, line 211
	} else { // library marker davegut.tpLinkCrypto, line 212
		logData << [respStatus: "LOGIN FAILED", reason: "ERROR in HTTP response", // library marker davegut.tpLinkCrypto, line 213
					resp: resp.properties] // library marker davegut.tpLinkCrypto, line 214
		logInfo(logData) // library marker davegut.tpLinkCrypto, line 215
	} // library marker davegut.tpLinkCrypto, line 216
	if (!device) { sendKlapDataCmd(logData, data) } // library marker davegut.tpLinkCrypto, line 217
} // library marker davegut.tpLinkCrypto, line 218

//	===== Comms Support ===== // library marker davegut.tpLinkCrypto, line 220
def getKlapParams(cmdBody) { // library marker davegut.tpLinkCrypto, line 221
	Map reqParams = [timeout: 10, headers: ["Cookie": cookie]] // library marker davegut.tpLinkCrypto, line 222
	int seqNo = state.seqNo + 1 // library marker davegut.tpLinkCrypto, line 223
	state.seqNo = seqNo // library marker davegut.tpLinkCrypto, line 224
	byte[] encKey = new JsonSlurper().parseText(encKey) // library marker davegut.tpLinkCrypto, line 225
	byte[] encIv = new JsonSlurper().parseText(encIv) // library marker davegut.tpLinkCrypto, line 226
	byte[] encSig = new JsonSlurper().parseText(encSig) // library marker davegut.tpLinkCrypto, line 227
	String cmdBodyJson = new groovy.json.JsonBuilder(cmdBody).toString() // library marker davegut.tpLinkCrypto, line 228

	Map encryptedData = klapEncrypt(cmdBodyJson.getBytes(), encKey, encIv, // library marker davegut.tpLinkCrypto, line 230
									encSig, seqNo) // library marker davegut.tpLinkCrypto, line 231
	reqParams << [uri: "${getDataValue("baseUrl")}/request?seq=${seqNo}", // library marker davegut.tpLinkCrypto, line 232
				  body: encryptedData.cipherData, // library marker davegut.tpLinkCrypto, line 233
				  contentType: "application/octet-stream", // library marker davegut.tpLinkCrypto, line 234
				  requestContentType: "application/octet-stream"] // library marker davegut.tpLinkCrypto, line 235
	return reqParams // library marker davegut.tpLinkCrypto, line 236
} // library marker davegut.tpLinkCrypto, line 237

def getAesParams(cmdBody) { // library marker davegut.tpLinkCrypto, line 239
	byte[] encKey = new JsonSlurper().parseText(encKey) // library marker davegut.tpLinkCrypto, line 240
	byte[] encIv = new JsonSlurper().parseText(encIv) // library marker davegut.tpLinkCrypto, line 241
	def cmdStr = JsonOutput.toJson(cmdBody).toString() // library marker davegut.tpLinkCrypto, line 242
	Map reqBody = [method: "securePassthrough", // library marker davegut.tpLinkCrypto, line 243
				   params: [request: aesEncrypt(cmdStr, encKey, encIv)]] // library marker davegut.tpLinkCrypto, line 244
	Map reqParams = [uri: "${getDataValue("baseUrl")}?token=${token}", // library marker davegut.tpLinkCrypto, line 245
					 body: new groovy.json.JsonBuilder(reqBody).toString(), // library marker davegut.tpLinkCrypto, line 246
					 contentType: "application/json", // library marker davegut.tpLinkCrypto, line 247
					 requestContentType: "application/json", // library marker davegut.tpLinkCrypto, line 248
					 timeout: 10, // library marker davegut.tpLinkCrypto, line 249
					 headers: ["Cookie": cookie]] // library marker davegut.tpLinkCrypto, line 250
	return reqParams // library marker davegut.tpLinkCrypto, line 251
} // library marker davegut.tpLinkCrypto, line 252

def parseKlapData(resp) { // library marker davegut.tpLinkCrypto, line 254
	Map parseData = [parseMethod: "parseKlapData"] // library marker davegut.tpLinkCrypto, line 255
	try { // library marker davegut.tpLinkCrypto, line 256
		byte[] encKey = new JsonSlurper().parseText(encKey) // library marker davegut.tpLinkCrypto, line 257
		byte[] encIv = new JsonSlurper().parseText(encIv) // library marker davegut.tpLinkCrypto, line 258
		int seqNo = state.seqNo // library marker davegut.tpLinkCrypto, line 259
		byte[] cipherResponse = resp.data.decodeBase64()[32..-1] // library marker davegut.tpLinkCrypto, line 260
		Map cmdResp =  new JsonSlurper().parseText(klapDecrypt(cipherResponse, encKey, // library marker davegut.tpLinkCrypto, line 261
														   encIv, seqNo)) // library marker davegut.tpLinkCrypto, line 262
		parseData << [cryptoStatus: "OK", cmdResp: cmdResp] // library marker davegut.tpLinkCrypto, line 263
	} catch (err) { // library marker davegut.tpLinkCrypto, line 264
		parseData << [cryptoStatus: "decryptDataError", error: err] // library marker davegut.tpLinkCrypto, line 265
	} // library marker davegut.tpLinkCrypto, line 266
	return parseData // library marker davegut.tpLinkCrypto, line 267
} // library marker davegut.tpLinkCrypto, line 268

def parseAesData(resp) { // library marker davegut.tpLinkCrypto, line 270
	Map parseData = [parseMethod: "parseAesData"] // library marker davegut.tpLinkCrypto, line 271
	try { // library marker davegut.tpLinkCrypto, line 272
		byte[] encKey = new JsonSlurper().parseText(encKey) // library marker davegut.tpLinkCrypto, line 273
		byte[] encIv = new JsonSlurper().parseText(encIv) // library marker davegut.tpLinkCrypto, line 274
		Map cmdResp = new JsonSlurper().parseText(aesDecrypt(resp.json.result.response, // library marker davegut.tpLinkCrypto, line 275
														 encKey, encIv)) // library marker davegut.tpLinkCrypto, line 276
		parseData << [cryptoStatus: "OK", cmdResp: cmdResp] // library marker davegut.tpLinkCrypto, line 277
	} catch (err) { // library marker davegut.tpLinkCrypto, line 278
		parseData << [cryptoStatus: "decryptDataError", error: err, dataLength: resp.data.length()] // library marker davegut.tpLinkCrypto, line 279
	} // library marker davegut.tpLinkCrypto, line 280
	return parseData // library marker davegut.tpLinkCrypto, line 281
} // library marker davegut.tpLinkCrypto, line 282

//	===== Crypto Methods ===== // library marker davegut.tpLinkCrypto, line 284
def klapEncrypt(byte[] request, encKey, encIv, encSig, seqNo) { // library marker davegut.tpLinkCrypto, line 285
	byte[] encSeqNo = integerToByteArray(seqNo) // library marker davegut.tpLinkCrypto, line 286
	byte[] ivEnc = [encIv, encSeqNo].flatten() // library marker davegut.tpLinkCrypto, line 287
	def cipher = Cipher.getInstance("AES/CBC/PKCS5Padding") // library marker davegut.tpLinkCrypto, line 288
	SecretKeySpec key = new SecretKeySpec(encKey, "AES") // library marker davegut.tpLinkCrypto, line 289
	IvParameterSpec iv = new IvParameterSpec(ivEnc) // library marker davegut.tpLinkCrypto, line 290
	cipher.init(Cipher.ENCRYPT_MODE, key, iv) // library marker davegut.tpLinkCrypto, line 291
	byte[] cipherRequest = cipher.doFinal(request) // library marker davegut.tpLinkCrypto, line 292

	byte[] payload = [encSig, encSeqNo, cipherRequest].flatten() // library marker davegut.tpLinkCrypto, line 294
	byte[] signature = mdEncode("SHA-256", payload) // library marker davegut.tpLinkCrypto, line 295
	cipherRequest = [signature, cipherRequest].flatten() // library marker davegut.tpLinkCrypto, line 296
	return [cipherData: cipherRequest, seqNumber: seqNo] // library marker davegut.tpLinkCrypto, line 297
} // library marker davegut.tpLinkCrypto, line 298

def klapDecrypt(cipherResponse, encKey, encIv, seqNo) { // library marker davegut.tpLinkCrypto, line 300
	byte[] encSeqNo = integerToByteArray(seqNo) // library marker davegut.tpLinkCrypto, line 301
	byte[] ivEnc = [encIv, encSeqNo].flatten() // library marker davegut.tpLinkCrypto, line 302
	def cipher = Cipher.getInstance("AES/CBC/PKCS5Padding") // library marker davegut.tpLinkCrypto, line 303
    SecretKeySpec key = new SecretKeySpec(encKey, "AES") // library marker davegut.tpLinkCrypto, line 304
	IvParameterSpec iv = new IvParameterSpec(ivEnc) // library marker davegut.tpLinkCrypto, line 305
    cipher.init(Cipher.DECRYPT_MODE, key, iv) // library marker davegut.tpLinkCrypto, line 306
	byte[] byteResponse = cipher.doFinal(cipherResponse) // library marker davegut.tpLinkCrypto, line 307
	return new String(byteResponse, "UTF-8") // library marker davegut.tpLinkCrypto, line 308
} // library marker davegut.tpLinkCrypto, line 309

def aesEncrypt(request, encKey, encIv) { // library marker davegut.tpLinkCrypto, line 311
	def cipher = Cipher.getInstance("AES/CBC/PKCS5Padding") // library marker davegut.tpLinkCrypto, line 312
	SecretKeySpec key = new SecretKeySpec(encKey, "AES") // library marker davegut.tpLinkCrypto, line 313
	IvParameterSpec iv = new IvParameterSpec(encIv) // library marker davegut.tpLinkCrypto, line 314
	cipher.init(Cipher.ENCRYPT_MODE, key, iv) // library marker davegut.tpLinkCrypto, line 315
	String result = cipher.doFinal(request.getBytes("UTF-8")).encodeBase64().toString() // library marker davegut.tpLinkCrypto, line 316
	return result.replace("\r\n","") // library marker davegut.tpLinkCrypto, line 317
} // library marker davegut.tpLinkCrypto, line 318

def aesDecrypt(cipherResponse, encKey, encIv) { // library marker davegut.tpLinkCrypto, line 320
    byte[] decodedBytes = cipherResponse.decodeBase64() // library marker davegut.tpLinkCrypto, line 321
	def cipher = Cipher.getInstance("AES/CBC/PKCS5Padding") // library marker davegut.tpLinkCrypto, line 322
    SecretKeySpec key = new SecretKeySpec(encKey, "AES") // library marker davegut.tpLinkCrypto, line 323
	IvParameterSpec iv = new IvParameterSpec(encIv) // library marker davegut.tpLinkCrypto, line 324
    cipher.init(Cipher.DECRYPT_MODE, key, iv) // library marker davegut.tpLinkCrypto, line 325
	return new String(cipher.doFinal(decodedBytes), "UTF-8") // library marker davegut.tpLinkCrypto, line 326
} // library marker davegut.tpLinkCrypto, line 327

//	===== Encoding Methods ===== // library marker davegut.tpLinkCrypto, line 329
def mdEncode(hashMethod, byte[] data) { // library marker davegut.tpLinkCrypto, line 330
	MessageDigest md = MessageDigest.getInstance(hashMethod) // library marker davegut.tpLinkCrypto, line 331
	md.update(data) // library marker davegut.tpLinkCrypto, line 332
	return md.digest() // library marker davegut.tpLinkCrypto, line 333
} // library marker davegut.tpLinkCrypto, line 334

String encodeUtf8(String message) { // library marker davegut.tpLinkCrypto, line 336
	byte[] arr = message.getBytes("UTF8") // library marker davegut.tpLinkCrypto, line 337
	return new String(arr) // library marker davegut.tpLinkCrypto, line 338
} // library marker davegut.tpLinkCrypto, line 339

int byteArrayToInteger(byte[] byteArr) { // library marker davegut.tpLinkCrypto, line 341
	int arrayASInteger // library marker davegut.tpLinkCrypto, line 342
	try { // library marker davegut.tpLinkCrypto, line 343
		arrayAsInteger = ((byteArr[0] & 0xFF) << 24) + ((byteArr[1] & 0xFF) << 16) + // library marker davegut.tpLinkCrypto, line 344
			((byteArr[2] & 0xFF) << 8) + (byteArr[3] & 0xFF) // library marker davegut.tpLinkCrypto, line 345
	} catch (error) { // library marker davegut.tpLinkCrypto, line 346
		Map errLog = [byteArr: byteArr, ERROR: error] // library marker davegut.tpLinkCrypto, line 347
		logWarn("byteArrayToInteger: ${errLog}") // library marker davegut.tpLinkCrypto, line 348
	} // library marker davegut.tpLinkCrypto, line 349
	return arrayAsInteger // library marker davegut.tpLinkCrypto, line 350
} // library marker davegut.tpLinkCrypto, line 351

byte[] integerToByteArray(value) { // library marker davegut.tpLinkCrypto, line 353
	String hexValue = hubitat.helper.HexUtils.integerToHexString(value, 4) // library marker davegut.tpLinkCrypto, line 354
	byte[] byteValue = hubitat.helper.HexUtils.hexStringToByteArray(hexValue) // library marker davegut.tpLinkCrypto, line 355
	return byteValue // library marker davegut.tpLinkCrypto, line 356
} // library marker davegut.tpLinkCrypto, line 357

def getRsaKey() { // library marker davegut.tpLinkCrypto, line 359
	return [public: "MIGfMA0GCSqGSIb3DQEBAQUAA4GNADCBiQKBgQDGr/mHBK8aqx7UAS+g+TuAvE3J2DdwsqRn9MmAkjPGNon1ZlwM6nLQHfJHebdohyVqkNWaCECGXnftnlC8CM2c/RujvCrStRA0lVD+jixO9QJ9PcYTa07Z1FuEze7Q5OIa6pEoPxomrjxzVlUWLDXt901qCdn3/zRZpBdpXzVZtQIDAQAB", // library marker davegut.tpLinkCrypto, line 360
			private: "MIICeAIBADANBgkqhkiG9w0BAQEFAASCAmIwggJeAgEAAoGBAMav+YcErxqrHtQBL6D5O4C8TcnYN3CypGf0yYCSM8Y2ifVmXAzqctAd8kd5t2iHJWqQ1ZoIQIZed+2eULwIzZz9G6O8KtK1EDSVUP6OLE71An09xhNrTtnUW4TN7tDk4hrqkSg/GiauPHNWVRYsNe33TWoJ2ff/NFmkF2lfNVm1AgMBAAECgYEAocxCHmKBGe2KAEkq+SKdAxvVGO77TsobOhDMWug0Q1C8jduaUGZHsxT/7JbA9d1AagSh/XqE2Sdq8FUBF+7vSFzozBHyGkrX1iKURpQFEQM2j9JgUCucEavnxvCqDYpscyNRAgqz9jdh+BjEMcKAG7o68bOw41ZC+JyYR41xSe0CQQD1os71NcZiMVqYcBud6fTYFHZz3HBNcbzOk+RpIHyi8aF3zIqPKIAh2pO4s7vJgrMZTc2wkIe0ZnUrm0oaC//jAkEAzxIPW1mWd3+KE3gpgyX0cFkZsDmlIbWojUIbyz8NgeUglr+BczARG4ITrTV4fxkGwNI4EZxBT8vXDSIXJ8NDhwJBAIiKndx0rfg7Uw7VkqRvPqk2hrnU2aBTDw8N6rP9WQsCoi0DyCnX65Hl/KN5VXOocYIpW6NAVA8VvSAmTES6Ut0CQQCX20jD13mPfUsHaDIZafZPhiheoofFpvFLVtYHQeBoCF7T7vHCRdfl8oj3l6UcoH/hXMmdsJf9KyI1EXElyf91AkAvLfmAS2UvUnhX4qyFioitjxwWawSnf+CewN8LDbH7m5JVXJEh3hqp+aLHg1EaW4wJtkoKLCF+DeVIgbSvOLJw"] // library marker davegut.tpLinkCrypto, line 361
} // library marker davegut.tpLinkCrypto, line 362

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
def klap1Handshake(baseUrl = getDataValue("baseUrl"), localHash1 = parent.localHash1, devData = null) { // library marker davegut.tpLinkTransKlap1, line 18
	byte[] localSeed = new byte[16] // library marker davegut.tpLinkTransKlap1, line 19
	new Random().nextBytes(localSeed) // library marker davegut.tpLinkTransKlap1, line 20
	Map reqData = [localSeed: localSeed, baseUrl: baseUrl, localHash1: localHash1, devData:devData] // library marker davegut.tpLinkTransKlap1, line 21
	Map reqParams = [uri: "${baseUrl}/handshake1", // library marker davegut.tpLinkTransKlap1, line 22
					 body: localSeed, // library marker davegut.tpLinkTransKlap1, line 23
					 contentType: "application/octet-stream", // library marker davegut.tpLinkTransKlap1, line 24
					 requestContentType: "application/octet-stream", // library marker davegut.tpLinkTransKlap1, line 25
					 ignoreSSLIssues: true, // library marker davegut.tpLinkTransKlap1, line 26
					 timeout:10] // library marker davegut.tpLinkTransKlap1, line 27
///////////////////////////////	Added try around httpPost) // library marker davegut.tpLinkTransKlap1, line 28
try { // library marker davegut.tpLinkTransKlap1, line 29
	asynchttpPost("parseklap1Handshake", reqParams, [data: reqData]) // library marker davegut.tpLinkTransKlap1, line 30
} catch (error) { // library marker davegut.tpLinkTransKlap1, line 31
reqData << [ERROR: error, errorreqParams: reqParams] // library marker davegut.tpLinkTransKlap1, line 32
logWarn(reqData) // library marker davegut.tpLinkTransKlap1, line 33
} // library marker davegut.tpLinkTransKlap1, line 34
///////////////////////////////	Added try around httpPost) // library marker davegut.tpLinkTransKlap1, line 35
} // library marker davegut.tpLinkTransKlap1, line 36

def parseklap1Handshake(resp, data) { // library marker davegut.tpLinkTransKlap1, line 38
	Map logData = [method: "parseKlay1Handshake"] // library marker davegut.tpLinkTransKlap1, line 39
///////////////////////////// // library marker davegut.tpLinkTransKlap1, line 40
Map testData = [DATA: "methodInput", data: data, resp: resp.properties] // library marker davegut.tpLinkTransKlap1, line 41
log.trace "<b>${testData}</b>" // library marker davegut.tpLinkTransKlap1, line 42
///////////////////////// // library marker davegut.tpLinkTransKlap1, line 43
	if (resp.status == 200 && resp.data != null) { // library marker davegut.tpLinkTransKlap1, line 44
		try { // library marker davegut.tpLinkTransKlap1, line 45
			Map reqData = [devData: data.data.devData, baseUrl: data.data.baseUrl] // library marker davegut.tpLinkTransKlap1, line 46
			byte[] localSeed = data.data.localSeed // library marker davegut.tpLinkTransKlap1, line 47
			byte[] seedData = resp.data.decodeBase64() // library marker davegut.tpLinkTransKlap1, line 48
/////////////////////// // library marker davegut.tpLinkTransKlap1, line 49
		if (seedData.length() != 48) { // library marker davegut.tpLinkTransKlap1, line 50
			//	Check return length.  If not 48 bytes, Abort handshake with error message // library marker davegut.tpLinkTransKlap1, line 51
			logData << [ERROR: "invalid return from device", seedDataLen: seeData.length(), resp: resp.properties] // library marker davegut.tpLinkTransKlap1, line 52
			logWarn(logData) // library marker davegut.tpLinkTransKlap1, line 53
			return // library marker davegut.tpLinkTransKlap1, line 54
		} // library marker davegut.tpLinkTransKlap1, line 55
/////////////////////// // library marker davegut.tpLinkTransKlap1, line 56
			byte[] remoteSeed = seedData[0 .. 15] // library marker davegut.tpLinkTransKlap1, line 57
			byte[] serverHash = seedData[16 .. 47] // library marker davegut.tpLinkTransKlap1, line 58
			byte[] localHash1 = data.data.localHash1.decodeBase64() // library marker davegut.tpLinkTransKlap1, line 59
			byte[] authHashByte = [localSeed, localHash1].flatten() // library marker davegut.tpLinkTransKlap1, line 60
			byte[] authHash = mdEncode("SHA-256", authHashByte) // library marker davegut.tpLinkTransKlap1, line 61
///////////////// // library marker davegut.tpLinkTransKlap1, line 62
testData = [DATA: "checkHashData", serverHash: serverHash, authHash: authHash] // library marker davegut.tpLinkTransKlap1, line 63
log.warn testData // library marker davegut.tpLinkTransKlap1, line 64
///////////////// // library marker davegut.tpLinkTransKlap1, line 65
			if (authHash == serverHash) { // library marker davegut.tpLinkTransKlap1, line 66
				//	cookie	//	Not used on Klap1? // library marker davegut.tpLinkTransKlap1, line 67
/////////////////////////////// // library marker davegut.tpLinkTransKlap1, line 68
//				def cookieHeader = resp.headers["Set-Cookie"] // library marker davegut.tpLinkTransKlap1, line 69
				def cookieHeader = resp.headers["Set-Cookie"].toString() // library marker davegut.tpLinkTransKlap1, line 70
				def cookie = null // library marker davegut.tpLinkTransKlap1, line 71
				if (cookieHeader != null) { // library marker davegut.tpLinkTransKlap1, line 72
					cookie = cookieHeader.substring(cookieHeader.indexOf(":") +1, cookieHeader.indexOf(";")) // library marker davegut.tpLinkTransKlap1, line 73
				} // library marker davegut.tpLinkTransKlap1, line 74
/////////////////// // library marker davegut.tpLinkTransKlap1, line 75
				//	seqNo and encIv // library marker davegut.tpLinkTransKlap1, line 76
				byte[] payload = ["iv".getBytes(), localSeed, remoteSeed, localHash].flatten() // library marker davegut.tpLinkTransKlap1, line 77
				byte[] fullIv = mdEncode("SHA-256", payload) // library marker davegut.tpLinkTransKlap1, line 78
				byte[] byteSeqNo = fullIv[-4..-1] // library marker davegut.tpLinkTransKlap1, line 79

				int seqNo = byteArrayToInteger(byteSeqNo) // library marker davegut.tpLinkTransKlap1, line 81
				atomicState.seqNo = seqNo // library marker davegut.tpLinkTransKlap1, line 82

				//	encKey // library marker davegut.tpLinkTransKlap1, line 84
				payload = ["lsk".getBytes(), localSeed, remoteSeed, localHash].flatten() // library marker davegut.tpLinkTransKlap1, line 85
				byte[] encKey = mdEncode("SHA-256", payload)[0..15] // library marker davegut.tpLinkTransKlap1, line 86
				//	encSig // library marker davegut.tpLinkTransKlap1, line 87
				payload = ["ldk".getBytes(), localSeed, remoteSeed, localHash].flatten() // library marker davegut.tpLinkTransKlap1, line 88
				byte[] encSig = mdEncode("SHA-256", payload)[0..27] // library marker davegut.tpLinkTransKlap1, line 89
				if (device) { // library marker davegut.tpLinkTransKlap1, line 90
					device.updateSetting("cookie",[type:"password", value: cookie])  // library marker davegut.tpLinkTransKlap1, line 91
					device.updateSetting("encKey",[type:"password", value: encKey])  // library marker davegut.tpLinkTransKlap1, line 92
					device.updateSetting("encIv",[type:"password", value: fullIv[0..11]])  // library marker davegut.tpLinkTransKlap1, line 93
					device.updateSetting("encSig",[type:"password", value: encSig])  // library marker davegut.tpLinkTransKlap1, line 94
				} else { // library marker davegut.tpLinkTransKlap1, line 95
					reqData << [cookie: cookie, seqNo: seqNo, encIv: fullIv[0..11],  // library marker davegut.tpLinkTransKlap1, line 96
								encSig: encSig, encKey: encKey] // library marker davegut.tpLinkTransKlap1, line 97
//					reqData << [seqNo: seqNo, encIv: fullIv[0..11],  // library marker davegut.tpLinkTransKlap1, line 98
//								encSig: encSig, encKey: encKey] // library marker davegut.tpLinkTransKlap1, line 99
				} // library marker davegut.tpLinkTransKlap1, line 100
				byte[] loginHash = [remoteSeed, localHash1].flatten() // library marker davegut.tpLinkTransKlap1, line 101
				byte[] body = mdEncode("SHA-256", loginHash) // library marker davegut.tpLinkTransKlap1, line 102
				Map reqParams = [uri: "${data.data.baseUrl}/handshake2", // library marker davegut.tpLinkTransKlap1, line 103
								 body: body, // library marker davegut.tpLinkTransKlap1, line 104
								 ignoreSSLIssues: true, // library marker davegut.tpLinkTransKlap1, line 105
								 timeout:10, // library marker davegut.tpLinkTransKlap1, line 106
///////////////// // library marker davegut.tpLinkTransKlap1, line 107
//								 headers: ["Cookie": cookie], // library marker davegut.tpLinkTransKlap1, line 108
///////////////// // library marker davegut.tpLinkTransKlap1, line 109
								 contentType: "application/octet-stream", // library marker davegut.tpLinkTransKlap1, line 110
								 requestContentType: "application/octet-stream"] // library marker davegut.tpLinkTransKlap1, line 111
///////////////// // library marker davegut.tpLinkTransKlap1, line 112
				if (cookie != null ) { reqParams << [headers: ["Cookie": cookie]] } // library marker davegut.tpLinkTransKlap1, line 113
///////////////// // library marker davegut.tpLinkTransKlap1, line 114
				asynchttpPost("parseklap1Handshake2", reqParams, [data: reqData]) // library marker davegut.tpLinkTransKlap1, line 115
/////////////////////////// // library marker davegut.tpLinkTransKlap1, line 116
logTrace "<b>${logData}</b>" // library marker davegut.tpLinkTransKlap1, line 117
///////////////////////// // library marker davegut.tpLinkTransKlap1, line 118
			} else { // library marker davegut.tpLinkTransKlap1, line 119
				logData << [respStatus: "ERROR: localAuthHash != serverHash", // library marker davegut.tpLinkTransKlap1, line 120
							action: "<b>Check credentials and try again</b>"] // library marker davegut.tpLinkTransKlap1, line 121
				logWarn(logData) // library marker davegut.tpLinkTransKlap1, line 122
			} // library marker davegut.tpLinkTransKlap1, line 123
		} catch (err) { // library marker davegut.tpLinkTransKlap1, line 124
			logData << [respStatus: "ERROR parsing 200 response", resp: resp.properties, error: err] // library marker davegut.tpLinkTransKlap1, line 125
			logData << [action: "<b>Try Configure command</b>"] // library marker davegut.tpLinkTransKlap1, line 126
/////////////////////////// // library marker davegut.tpLinkTransKlap1, line 127
log.warn "<b>${logData}</b>" // library marker davegut.tpLinkTransKlap1, line 128
//			logWarn(logData) // library marker davegut.tpLinkTransKlap1, line 129
///////////////////////// // library marker davegut.tpLinkTransKlap1, line 130
		} // library marker davegut.tpLinkTransKlap1, line 131
	} else { // library marker davegut.tpLinkTransKlap1, line 132
		logData << [respStatus: resp.status, message: resp.errorMessage] // library marker davegut.tpLinkTransKlap1, line 133
		logData << [action: "<b>Try Configure command</b>"] // library marker davegut.tpLinkTransKlap1, line 134
/////////////////////////// // library marker davegut.tpLinkTransKlap1, line 135
		log.warn "<b>${logData}</b>" // library marker davegut.tpLinkTransKlap1, line 136
//		logWarn(logData) // library marker davegut.tpLinkTransKlap1, line 137
///////////////////////// // library marker davegut.tpLinkTransKlap1, line 138
	} // library marker davegut.tpLinkTransKlap1, line 139
} // library marker davegut.tpLinkTransKlap1, line 140

def parseklap1Handshake2(resp, data) { // library marker davegut.tpLinkTransKlap1, line 142
	Map logData = [method: "parseklap1Handshake2"] // library marker davegut.tpLinkTransKlap1, line 143
	if (resp.status == 200 && resp.data == null) { // library marker davegut.tpLinkTransKlap1, line 144
		logData << [respStatus: "Login OK"] // library marker davegut.tpLinkTransKlap1, line 145
		setCommsError(200) // library marker davegut.tpLinkTransKlap1, line 146
		logDebug(logData) // library marker davegut.tpLinkTransKlap1, line 147
	} else { // library marker davegut.tpLinkTransKlap1, line 148
		logData << [respStatus: "LOGIN FAILED", reason: "ERROR in HTTP response", // library marker davegut.tpLinkTransKlap1, line 149
					resp: resp.properties] // library marker davegut.tpLinkTransKlap1, line 150
		logInfo(logData) // library marker davegut.tpLinkTransKlap1, line 151
	} // library marker davegut.tpLinkTransKlap1, line 152
	if (!device) { sendklap1DataCmd(logData, data) } // library marker davegut.tpLinkTransKlap1, line 153
} // library marker davegut.tpLinkTransKlap1, line 154

//	===== Comms Support ===== // library marker davegut.tpLinkTransKlap1, line 156
def getklap1Params(cmdBody) { // library marker davegut.tpLinkTransKlap1, line 157
	Map reqParams = [timeout: 10, headers: ["Cookie": cookie]] // library marker davegut.tpLinkTransKlap1, line 158
	int seqNo = state.seqNo + 1 // library marker davegut.tpLinkTransKlap1, line 159
	state.seqNo = seqNo // library marker davegut.tpLinkTransKlap1, line 160
	byte[] encKey = new JsonSlurper().parseText(encKey) // library marker davegut.tpLinkTransKlap1, line 161
	byte[] encIv = new JsonSlurper().parseText(encIv) // library marker davegut.tpLinkTransKlap1, line 162
	byte[] encSig = new JsonSlurper().parseText(encSig) // library marker davegut.tpLinkTransKlap1, line 163
	String cmdBodyJson = new groovy.json.JsonBuilder(cmdBody).toString() // library marker davegut.tpLinkTransKlap1, line 164

	Map encryptedData = klap1Encrypt(cmdBodyJson.getBytes(), encKey, encIv, // library marker davegut.tpLinkTransKlap1, line 166
									encSig, seqNo) // library marker davegut.tpLinkTransKlap1, line 167
	reqParams << [uri: "${getDataValue("baseUrl")}/request?seq=${seqNo}", // library marker davegut.tpLinkTransKlap1, line 168
				  ignoreSSLIssues: true, // library marker davegut.tpLinkTransKlap1, line 169
				  body: encryptedData.cipherData, // library marker davegut.tpLinkTransKlap1, line 170
				  contentType: "application/octet-stream", // library marker davegut.tpLinkTransKlap1, line 171
				  requestContentType: "application/octet-stream"] // library marker davegut.tpLinkTransKlap1, line 172
	return reqParams // library marker davegut.tpLinkTransKlap1, line 173
} // library marker davegut.tpLinkTransKlap1, line 174

def parseklap1Data(resp) { // library marker davegut.tpLinkTransKlap1, line 176
	Map parseData = [parseMethod: "parseklap1Data"] // library marker davegut.tpLinkTransKlap1, line 177
	try { // library marker davegut.tpLinkTransKlap1, line 178
		byte[] encKey = new JsonSlurper().parseText(encKey) // library marker davegut.tpLinkTransKlap1, line 179
		byte[] encIv = new JsonSlurper().parseText(encIv) // library marker davegut.tpLinkTransKlap1, line 180
		int seqNo = state.seqNo // library marker davegut.tpLinkTransKlap1, line 181
		byte[] cipherResponse = resp.data.decodeBase64()[32..-1] // library marker davegut.tpLinkTransKlap1, line 182
		Map cmdResp =  new JsonSlurper().parseText(klap1Decrypt(cipherResponse, encKey, // library marker davegut.tpLinkTransKlap1, line 183
														   encIv, seqNo)) // library marker davegut.tpLinkTransKlap1, line 184
		parseData << [cryptoStatus: "OK", cmdResp: cmdResp] // library marker davegut.tpLinkTransKlap1, line 185
	} catch (err) { // library marker davegut.tpLinkTransKlap1, line 186
		parseData << [cryptoStatus: "decryptDataError", error: err] // library marker davegut.tpLinkTransKlap1, line 187
	} // library marker davegut.tpLinkTransKlap1, line 188
	return parseData // library marker davegut.tpLinkTransKlap1, line 189
} // library marker davegut.tpLinkTransKlap1, line 190

//	===== Crypto Methods ===== // library marker davegut.tpLinkTransKlap1, line 192
def klap1Encrypt(byte[] request, encKey, encIv, encSig, seqNo) { // library marker davegut.tpLinkTransKlap1, line 193
	byte[] encSeqNo = integerToByteArray(seqNo) // library marker davegut.tpLinkTransKlap1, line 194
	byte[] ivEnc = [encIv, encSeqNo].flatten() // library marker davegut.tpLinkTransKlap1, line 195
	def cipher = Cipher.getInstance("AES/CBC/PKCS5Padding") // library marker davegut.tpLinkTransKlap1, line 196
	SecretKeySpec key = new SecretKeySpec(encKey, "AES") // library marker davegut.tpLinkTransKlap1, line 197
	IvParameterSpec iv = new IvParameterSpec(ivEnc) // library marker davegut.tpLinkTransKlap1, line 198
	cipher.init(Cipher.ENCRYPT_MODE, key, iv) // library marker davegut.tpLinkTransKlap1, line 199
	byte[] cipherRequest = cipher.doFinal(request) // library marker davegut.tpLinkTransKlap1, line 200

	byte[] payload = [encSig, encSeqNo, cipherRequest].flatten() // library marker davegut.tpLinkTransKlap1, line 202
	byte[] signature = mdEncode("SHA-256", payload) // library marker davegut.tpLinkTransKlap1, line 203
	cipherRequest = [signature, cipherRequest].flatten() // library marker davegut.tpLinkTransKlap1, line 204
	return [cipherData: cipherRequest, seqNumber: seqNo] // library marker davegut.tpLinkTransKlap1, line 205
} // library marker davegut.tpLinkTransKlap1, line 206

def klap1Decrypt(cipherResponse, encKey, encIv, seqNo) { // library marker davegut.tpLinkTransKlap1, line 208
	byte[] encSeqNo = integerToByteArray(seqNo) // library marker davegut.tpLinkTransKlap1, line 209
	byte[] ivEnc = [encIv, encSeqNo].flatten() // library marker davegut.tpLinkTransKlap1, line 210
	def cipher = Cipher.getInstance("AES/CBC/PKCS5Padding") // library marker davegut.tpLinkTransKlap1, line 211
    SecretKeySpec key = new SecretKeySpec(encKey, "AES") // library marker davegut.tpLinkTransKlap1, line 212
	IvParameterSpec iv = new IvParameterSpec(ivEnc) // library marker davegut.tpLinkTransKlap1, line 213
    cipher.init(Cipher.DECRYPT_MODE, key, iv) // library marker davegut.tpLinkTransKlap1, line 214
	byte[] byteResponse = cipher.doFinal(cipherResponse) // library marker davegut.tpLinkTransKlap1, line 215
	return new String(byteResponse, "UTF-8") // library marker davegut.tpLinkTransKlap1, line 216
} // library marker davegut.tpLinkTransKlap1, line 217

//	===== Misc routines that are general (apply to all) // library marker davegut.tpLinkTransKlap1, line 219
//	Send asyncPost - generates final reqParams and sends the data // library marker davegut.tpLinkTransKlap1, line 220
//	data is a map: [body:body, uri: uri, cookie: cookie, data: data] // library marker davegut.tpLinkTransKlap1, line 221



// ~~~~~ end include (263) davegut.tpLinkTransKlap1 ~~~~~
