/*
 * 
 */

/**
* @class ZaStatus 
* @contructor ZaStatus
* @param app
* @author Greg Solovyev
**/
ZaStatus = function() {
	ZaItem.call(this,"ZaStatus");
	this._init();	
}

ZaStatus.prototype = new ZaItem;
ZaStatus.prototype.constructor = ZaStatus;
ZaItem.loadMethods["ZaStatus"] = new Array();
ZaItem.initMethods["ZaStatus"] = new Array();

ZaStatus.A_server = "server";
ZaStatus.A_service = "service";
ZaStatus.A_timestamp = "t";
ZaStatus.PRFX_Server = "status_server";
ZaStatus.PRFX_Service = "status_service";
ZaStatus.PRFX_Time = "status_time";
ZaStatus.PRFX_Status = "status_status";
ZaStatus.SVC_SPELL = "spell";
ZaStatus.SVC_LOGGER = "logger";
ZaStatus.SVC_MTA = "mta";
ZaStatus.SVC_LDAP = "ldap";
ZaStatus.SVC_MEMCACHED = "memcached";
ZaStatus.SVC_MAILBOX = "mailbox";
ZaStatus.SVC_CONVERTD = "convertd";
ZaStatus.SVC_IMAPPROXY = "imapproxy";
ZaStatus.SVC_STATS = "stats";
ZaStatus.SVC_SNMP = "snmp";
ZaStatus.SVC_AS = "antispam";
ZaStatus.SVC_AV = "antivirus";


ZaStatus.prototype.load = function (by, val) {
	ZaItem.prototype.load.call(this,by,val,true, false);
}

ZaStatus.loadMethod = 
function() {
	try {
		var logHost = ZaApp.getInstance().getGlobalConfig().attrs[ZaServer.A_zimbraLogHostname];		
		//if zimbraLogHostname is set
		if (logHost) {
			var soapDoc = AjxSoapDoc.create("GetServiceStatusRequest", ZaZimbraAdmin.URN, null);
			var command = new ZmCsfeCommand();
			var params = new Object();
			params.noAuthToken = true;
			params.soapDoc = soapDoc;	
			var resp = command.invoke(params).Body.GetServiceStatusResponse;			
			this.initFromJS(resp);
		}	
	} catch (ex) {
			ZaApp.getInstance().getStatusViewController()._handleException(ex, "ZaStatus.loadMethod", null, false);		
	}	
}

ZaItem.loadMethods["ZaStatus"].push(ZaStatus.loadMethod);

ZaStatus.initMethod = function () {
	this.serverMap = new Object();
	this.statusVector = new AjxVector();
	this.id = Dwt.getNextId();
}
ZaItem.initMethods["ZaStatus"].push(ZaStatus.initMethod);

ZaStatus.prototype.initFromJS =
function(obj) {
    var tzId = obj.timezone[0].id;
    if(obj.status && obj.status instanceof Array) {
		var statusArray = obj.status;
        var cnt = statusArray.length;
        var formatter = AjxDateFormat.getDateTimeInstance(AjxDateFormat.MEDIUM, AjxDateFormat.SHORT);
        for(var i=0; i < cnt; i++) {
        	var serverName = statusArray[i].server;
            if(!this.serverMap[serverName]) {
            	this.serverMap[serverName] = new Object();
                this.serverMap[serverName].name = serverName;
                this.serverMap[serverName].id = Dwt.getNextId();
                this.serverMap[serverName].serviceMap = null;
                this.serverMap[serverName].status = 1;
                this.statusVector.add(this.serverMap[serverName]);
            }
            var serviceName = statusArray[i].service;
            if(serviceName) {
            	if(!this.serverMap[serverName].serviceMap)
                  	this.serverMap[serverName].serviceMap = new Object();
                var seconds = Number(statusArray[i].t);
                var millis = seconds*1000;                   
                var gmtSeconds = seconds - AjxTimezone.getOffset(AjxTimezone.DEFAULT_RULE)*60;                                
                var serverSeconds = gmtSeconds+AjxTimezone.getOffset(tzId,(new Date(millis)))*60;                                
                this.serverMap[serverName].serviceMap[serviceName] = new Object();
                this.serverMap[serverName].serviceMap[serviceName].status = statusArray[i]._content;
                this.serverMap[serverName].serviceMap[serviceName].timestamp = millis;
                var serverMillis = serverSeconds*1000;
                this.serverMap[serverName].serviceMap[serviceName].time = formatter.format(new Date(serverMillis));
                //this.serverMap[serverName].serviceMap[serviceName].time = formatter.format(new Date(Number(statusArray[i].t)*1000));
                if(this.serverMap[serverName].serviceMap[serviceName].status != 1) {
                	this.serverMap[serverName].status = 0;
                }
            }
		}
    }
}

ZaStatus.prototype.getStatusVector = 
function() {
	return this.statusVector;
}

ZaStatus.compare = function (a,b) {
	return (a.serverName < b.serverName)? -1: ((a.serverName > b.serverName)? 1: 0);
};
