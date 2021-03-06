package com.devstepbcn.wifi;

import com.facebook.react.uimanager.*;
import com.facebook.react.bridge.*;
import com.facebook.systrace.Systrace;
import com.facebook.systrace.SystraceMessage;
import com.facebook.react.LifecycleState;
import com.facebook.react.ReactInstanceManager;
import com.facebook.react.ReactRootView;
import com.facebook.react.modules.core.DefaultHardwareBackBtnHandler;
import com.facebook.react.modules.core.DeviceEventManagerModule;
import com.facebook.react.shell.MainReactPackage;
import com.facebook.soloader.SoLoader;

import android.net.wifi.ScanResult;
import android.net.wifi.WifiManager;
import android.net.wifi.WifiConfiguration;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.net.Network;
import android.net.wifi.WifiInfo;
import android.content.Context;
import android.os.Bundle;
import android.widget.Toast;
import android.util.Log;

import java.util.List;
import java.lang.Thread;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

public class AndroidWifiModule extends ReactContextBaseJavaModule {

	//WifiManager Instance
	WifiManager wifi;
	ConnectivityManager connMan;
	//Constructor
	public AndroidWifiModule(ReactApplicationContext reactContext) {
		super(reactContext);
		wifi = (WifiManager)reactContext.getSystemService(Context.WIFI_SERVICE);
		connMan = (ConnectivityManager) reactContext.getSystemService(Context.CONNECTIVITY_SERVICE);
	}

	//Name for module register to use:
	@Override
	public String getName() {
		return "AndroidWifiModule";
	}

	//Method to load wifi list into string via Callback. Returns a stringified JSONArray
	@ReactMethod
	public void loadWifiList(Callback successCallback, Callback errorCallback) {
		try {
			List < ScanResult > results = wifi.getScanResults();
			JSONArray wifiArray = new JSONArray();

			for (ScanResult result: results) {
				JSONObject wifiObject = new JSONObject();
				if(!result.SSID.equals("")){
					try {
                                            wifiObject.put("SSID", result.SSID);
                                            wifiObject.put("BSSID", result.BSSID);
                                            wifiObject.put("capabilities", result.capabilities);
                                            wifiObject.put("frequency", result.frequency);
                                            wifiObject.put("level", result.level);
                                            wifiObject.put("timestamp", result.timestamp);
                                            //Other fields not added
                                            //wifiObject.put("operatorFriendlyName", result.operatorFriendlyName);
                                            //wifiObject.put("venueName", result.venueName);
                                            //wifiObject.put("centerFreq0", result.centerFreq0);
                                            //wifiObject.put("centerFreq1", result.centerFreq1);
                                            //wifiObject.put("channelWidth", result.channelWidth);
					} catch (JSONException e) {
                                            errorCallback.invoke(e.getMessage());
					}
					wifiArray.put(wifiObject);
				}
			}
			successCallback.invoke(wifiArray.toString());
		} catch (IllegalViewOperationException e) {
			errorCallback.invoke(e.getMessage());
		}
	}

	//Method to refresh wifi scan
	@ReactMethod
	public void doWifiScan() {
		wifi.startScan();
	}

	//Method to check if wifi is enabled
	@ReactMethod
	public void isEnabled(Callback isEnabled) {
		isEnabled.invoke(wifi.isWifiEnabled());
	}

	//Method to connect/disconnect wifi service
	@ReactMethod
	public void setEnabled(Boolean enabled) {
		wifi.setWifiEnabled(enabled);
	}

	//Send the ssid and password of a Wifi network into this to connect to the network.
	//Example:  wifi.findAndConnect(ssid, password);
	//After 10 seconds, a post telling you whether you are connected will pop up.
	//Callback returns true if ssid is in the range
	@ReactMethod
	public void findAndConnect(String ssid, String password, Callback ssidFound) {
		// Log.v("WDD", "SSID - " + ssid);
		List < ScanResult > results = wifi.getScanResults();
		boolean connected = false;
		for (ScanResult result: results) {
			String resultString = "" + result.SSID;
			if (ssid.equals(resultString)) {
				connected = connectTo(result, password, ssid);
			}
		}
		ssidFound.invoke(connected);
	}

	@ReactMethod
	public void reconnect(String ssid, Callback success) {
		boolean connect = false;
		List<WifiConfiguration> networks = wifi.getConfiguredNetworks();
		if (networks != null) {
			for (WifiConfiguration network : networks) {
				if (ssid.equals(deQuotifySsid(network.SSID))) {
					connect = wifi.enableNetwork(network.networkId, false);
				}
			}
			success.invoke(connect);
		}
	}

	//Use this method to check if the device is currently connected to Wifi.
	@ReactMethod
	public void connectionStatus(Callback connectionStatusResult) {
		ConnectivityManager connManager = (ConnectivityManager) getReactApplicationContext().getSystemService(Context.CONNECTIVITY_SERVICE);
		NetworkInfo mWifi = connManager.getNetworkInfo(ConnectivityManager.TYPE_WIFI);
		if (mWifi.isConnected()) {
			connectionStatusResult.invoke(true);
		} else {
			connectionStatusResult.invoke(false);
		}
	}

	//Method to connect to WIFI Network
	public Boolean connectTo(ScanResult result, String password, String ssid) {
		//Make new configuration
		// Log.v("WDD", "SSID - " + ssid);
		WifiConfiguration conf = new WifiConfiguration();
		conf.priority = 999999;
		conf.SSID = "\"" + ssid + "\"";
		String Capabilities = result.capabilities;
		if (Capabilities.contains("WPA2")) {
			conf.preSharedKey = "\"" + password + "\"";
		} else if (Capabilities.contains("WPA")) {
			conf.preSharedKey = "\"" + password + "\"";
		} else if (Capabilities.contains("WEP")) {
			conf.wepKeys[0] = "\"" + password + "\"";
			conf.wepTxKeyIndex = 0;
			conf.allowedKeyManagement.set(WifiConfiguration.KeyMgmt.NONE);
			conf.allowedGroupCiphers.set(WifiConfiguration.GroupCipher.WEP40);
		} else {
			conf.allowedKeyManagement.set(WifiConfiguration.KeyMgmt.NONE);
		}
		//Remove the existing configuration for this netwrok
		List<WifiConfiguration> mWifiConfigList = wifi.getConfiguredNetworks();
		//String comparableSSID = ('"' + ssid + '"'); //Add quotes because wifiConfig.SSID has them
		int updateNetwork = -1;
		if (mWifiConfigList != null) {
			for(WifiConfiguration wifiConfig : mWifiConfigList){

				if(wifiConfig.SSID.equals(conf.SSID)){
					if (!wifi.removeNetwork(conf.networkId)) {
						updateNetwork = wifiConfig.networkId;
						//updateNetwork = conf.networkId;
					}
					// conf.networkId = wifiConfig.networkId;
					// updateNetwork = wifi.updateNetwork(conf);
				}
			}
		}
    // If network not already in configured networks add new network
		if ( updateNetwork == -1 ) {
    	updateNetwork = wifi.addNetwork(conf);
		};

    if ( updateNetwork == -1 ) {
			Log.v("WDD", "FAILING OUT - COULD NOT ADD NETWORK");
			return false;
    }

    boolean disconnect = wifi.disconnect();

		if ( !disconnect ) {
			Log.v("WDD", "FAILING OUT - COULD NOT DISCONNECT");
			return false;
		};

		boolean enableNetwork = wifi.enableNetwork(updateNetwork, true);
		if ( !enableNetwork ) {
			Log.v("WDD", "FAILING OUT - COULD NOT ENABLE NETWORK");
			return false;
		};
		return true;
	}

	//Disconnect current Wifi.
	@ReactMethod
	public void disconnect() {
		wifi.disconnect();
	}

	@ReactMethod
	public void findAndRemove(String ssid, Callback success) {
		boolean remove = false;
		Log.v("WDD", "Network to remove: " + ssid);
		List<WifiConfiguration> networks = wifi.getConfiguredNetworks();
		if (networks != null) {
      for (WifiConfiguration network : networks) {
        if (ssid.equals(deQuotifySsid(network.SSID))) {
          Log.v("WDD", "Requesting removal of network...");
          remove = wifi.removeNetwork(network.networkId);
        }
      }
		  success.invoke(remove);
    }
    else {
      success.invoke();
    }
	}

	//This method will return current ssid
	@ReactMethod
	public void getSSID(final Callback callback) {
		WifiInfo info = wifi.getConnectionInfo();

		// This value should be wrapped in double quotes, so we need to unwrap it.
		String ssid = info.getSSID();
		if (ssid.startsWith("\"") && ssid.endsWith("\"")) {
			ssid = ssid.substring(1, ssid.length() - 1);
		}

		callback.invoke(ssid);
	}

	//This method will return the basic service set identifier (BSSID) of the current access point
	@ReactMethod
	public void getBSSID(final Callback callback) {
		WifiInfo info = wifi.getConnectionInfo();

		String bssid = info.getBSSID();

		callback.invoke(bssid.toUpperCase());
	}

	//This method will return current wifi signal strength
	@ReactMethod
	public void getCurrentSignalStrength(final Callback callback) {
		int linkSpeed = wifi.getConnectionInfo().getRssi();
		callback.invoke(linkSpeed);
	}
	//This method will return current IP
	@ReactMethod
	public void getIP(final Callback callback) {
		WifiInfo info = wifi.getConnectionInfo();
		String stringip=longToIP(info.getIpAddress());
		callback.invoke(stringip);
	}

	public static String longToIP(int longIp){
		StringBuffer sb = new StringBuffer("");
		String[] strip=new String[4];
		strip[3]=String.valueOf((longIp >>> 24));
		strip[2]=String.valueOf((longIp & 0x00FFFFFF) >>> 16);
		strip[1]=String.valueOf((longIp & 0x0000FFFF) >>> 8);
		strip[0]=String.valueOf((longIp & 0x000000FF));
		sb.append(strip[0]);
		sb.append(".");
		sb.append(strip[1]);
		sb.append(".");
		sb.append(strip[2]);
		sb.append(".");
		sb.append(strip[3]);
		return sb.toString();
	}
	// @TargetApi(VERSION_CODES.MARSHMELLOW)
	@ReactMethod
	private void bindAppToSoftAp(String ssid) {
			Log.v("WDD", "Trying to bind to: " + ssid);
			Network softAp = null;
			for (Network network : connMan.getAllNetworks()) {
					Log.v("WDD","Inspecting network:  " + network);
					NetworkInfo networkInfo = connMan.getNetworkInfo(network);
					if (networkInfo != null) {
						Log.v("WDD","Inspecting network info:  " + networkInfo);
						String dequotifiedNetworkExtraSsid = deQuotifySsid(networkInfo.getExtraInfo());
						String dequotifiedTargetSsid = deQuotifySsid(ssid);
						Log.v("WDD","Network extra info: '" + dequotifiedNetworkExtraSsid + "'");
						Log.v("WDD","And the SSID we were to connect to: '" + dequotifiedTargetSsid + "'");
						if (dequotifiedTargetSsid.equalsIgnoreCase(dequotifiedNetworkExtraSsid)) {
								softAp = network;
								break;
						}
					}
			}

			if (softAp == null) {
					connMan.bindProcessToNetwork(null);
			}
			else {
				connMan.bindProcessToNetwork(softAp);
			}
	}


	private static boolean isEmpty(final CharSequence cs) {
			return cs == null || cs.length() == 0;
	}

	public static String removeStart(final String str, final String remove) {
			 if (isEmpty(str) || isEmpty(remove)) {
					 return str;
			 }
			 if (str.startsWith(remove)){
					 return str.substring(remove.length());
			 }
			 return str;
	 }


	 public static String removeEnd(final String str, final String remove) {
			 if (isEmpty(str) || isEmpty(remove)) {
					 return str;
			 }
			 if (str.endsWith(remove)) {
					 return str.substring(0, str.length() - remove.length());
			 }
			 return str;
}

	public static String deQuotifySsid(String SSID) {
			if (SSID == null) {
					return null;
			}
			String quoteMark = "\"";
			SSID = removeStart(SSID, quoteMark);
			SSID = removeEnd(SSID, quoteMark);
			return SSID;
	}

}
