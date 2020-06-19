package com.dmarc.cordovacall;

import org.apache.cordova.CallbackContext;
import org.apache.cordova.PluginResult;
import org.json.JSONObject;
import org.json.JSONException;
import android.content.Intent;
import android.content.Context;
import android.graphics.drawable.Icon;
import android.os.Bundle;
import android.telecom.Connection;
import android.telecom.ConnectionRequest;
import android.telecom.ConnectionService;
import android.telecom.DisconnectCause;
import android.telecom.PhoneAccountHandle;
import android.telecom.StatusHints;
import android.telecom.TelecomManager;
import android.telephony.PhoneNumberUtils;
import android.os.Handler;
import android.net.Uri;
import java.util.ArrayList;
import android.util.Log;
import android.widget.Toast;

public class MyConnectionService extends ConnectionService {

    private static String TAG = "MyConnectionService";
    private static Connection conn;

    public static Connection getConnection() {
        return conn;
    }

    public static void deinitConnection() {
        conn = null;
    }

    public static void notifyCordovaCall(String handler, PluginResult result) {
        ArrayList<CallbackContext> callbackContexts = CordovaCall.getCallbackContexts().get(handler);
        if (callbackContexts == null) {
            return;
        }

        for (final CallbackContext callbackContext : callbackContexts) {
            CordovaCall.getCordova().getThreadPool().execute(new Runnable() {
                public void run() {
                    result.setKeepCallback(true);
                    callbackContext.sendPluginResult(result);
                }
            });
        }
    }

    public static String getMessage(Context context, String name) {
        Integer identifier = context.getResources().getIdentifier(name, "string", context.getPackageName());
        return context.getString(identifier);
    }

    private void setCallerName(Connection connection, String name) {
        connection.setAddress(Uri.parse(name), TelecomManager.PRESENTATION_ALLOWED);
        if (!PhoneNumberUtils.isGlobalPhoneNumber(name)) {
            connection.setCallerDisplayName(name, TelecomManager.PRESENTATION_ALLOWED);
        }
    }

    @Override
    public Connection onCreateIncomingConnection(final PhoneAccountHandle connectionManagerPhoneAccount, final ConnectionRequest request) {
        final Connection connection = new Connection() {
            @Override
            public void onAnswer() {
                this.setActive();
                this.setAudioModeIsVoip(true);
                Intent intent = new Intent(CordovaCall.getCordova().getActivity().getApplicationContext(), CordovaCall.getCordova().getActivity().getClass());
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_SINGLE_TOP);
                CordovaCall.getCordova().getActivity().getApplicationContext().startActivity(intent);
                ArrayList<CallbackContext> callbackContexts = CordovaCall.getCallbackContexts().get("answer");
                for (final CallbackContext callbackContext : callbackContexts) {
                    CordovaCall.getCordova().getThreadPool().execute(new Runnable() {
                        public void run() {
                            PluginResult result = new PluginResult(PluginResult.Status.OK, "answer event called successfully");
                            result.setKeepCallback(true);
                            callbackContext.sendPluginResult(result);
                        }
                    });
                }
            }

            @Override
            public void onReject() {
                DisconnectCause cause = new DisconnectCause(DisconnectCause.REJECTED);
                this.setDisconnected(cause);
                this.destroy();
                conn = null;
                ArrayList<CallbackContext> callbackContexts = CordovaCall.getCallbackContexts().get("reject");
                for (final CallbackContext callbackContext : callbackContexts) {
                    CordovaCall.getCordova().getThreadPool().execute(new Runnable() {
                        public void run() {
                            JSONObject info = new JSONObject();
                            try {
                                info.put("reason", request.getExtras().getBoolean("isBusy", false) ? "busy" : "decline");
                                info.put("message", "reject event called successfully");
                            } catch (JSONException e) {

                            }
                            PluginResult result = new PluginResult(PluginResult.Status.OK, info);
                            result.setKeepCallback(true);
                            callbackContext.sendPluginResult(result);
                        }
                    });
                }
            }

            @Override
            public void onAbort() {
                super.onAbort();
            }

            @Override
            public void onPlayDtmfTone(char c) {
                super.onPlayDtmfTone(c);

                MyConnectionService.notifyCordovaCall("DTMF", new PluginResult(PluginResult.Status.OK, String.valueOf(c)));
            }

            @Override
            public void onDisconnect() {
                DisconnectCause cause = new DisconnectCause(DisconnectCause.LOCAL);
                this.setDisconnected(cause);
                this.destroy();
                conn = null;
                ArrayList<CallbackContext> callbackContexts = CordovaCall.getCallbackContexts().get("hangup");
                for (final CallbackContext callbackContext : callbackContexts) {
                    CordovaCall.getCordova().getThreadPool().execute(new Runnable() {
                        public void run() {
                            PluginResult result = new PluginResult(PluginResult.Status.OK, "hangup event called successfully");
                            result.setKeepCallback(true);
                            callbackContext.sendPluginResult(result);
                        }
                    });
                }
            }
        };

        this.setCallerName(connection, request.getExtras().getString("from"));

        Icon icon = CordovaCall.getIcon();
        if(icon != null) {
            StatusHints statusHints = new StatusHints((CharSequence)"", icon, new Bundle());
            connection.setStatusHints(statusHints);
        }
        conn = connection;
        ArrayList<CallbackContext> callbackContexts = CordovaCall.getCallbackContexts().get("receiveCall");
        for (final CallbackContext callbackContext : callbackContexts) {
            CordovaCall.getCordova().getThreadPool().execute(new Runnable() {
                public void run() {
                    PluginResult result = new PluginResult(PluginResult.Status.OK, "receiveCall event called successfully");
                    result.setKeepCallback(true);
                    callbackContext.sendPluginResult(result);
                }
            });
        }
        return connection;
    }

    @Override
    public Connection onCreateOutgoingConnection(PhoneAccountHandle connectionManagerPhoneAccount, ConnectionRequest request) {
        final Context context = this.getApplicationContext();

        if (conn != null) {
            String message = MyConnectionService.getMessage(context, "cordova_call_already_calling");
            Toast.makeText(context, message, Toast.LENGTH_SHORT).show();
            return null;
        }

        final Connection connection = new Connection() {
            @Override
            public void onAnswer() {
                super.onAnswer();
                this.setAudioModeIsVoip(true);
            }

            @Override
            public void onReject() {
                super.onReject();
            }

            @Override
            public void onAbort() {
                super.onAbort();
            }

            @Override
            public void onPlayDtmfTone(char c) {
                super.onPlayDtmfTone(c);

                MyConnectionService.notifyCordovaCall("DTMF", new PluginResult(PluginResult.Status.OK, String.valueOf(c)));
            }

            @Override
            public void onDisconnect() {
                DisconnectCause cause = new DisconnectCause(DisconnectCause.LOCAL);
                this.setDisconnected(cause);
                this.destroy();
                conn = null;
                ArrayList<CallbackContext> callbackContexts = CordovaCall.getCallbackContexts().get("hangup");
                if (callbackContexts == null) {
                    return;
                }

                for (final CallbackContext callbackContext : callbackContexts) {
                    CordovaCall.getCordova().getThreadPool().execute(new Runnable() {
                        public void run() {
                            PluginResult result = new PluginResult(PluginResult.Status.OK, "hangup event called successfully");
                            result.setKeepCallback(true);
                            callbackContext.sendPluginResult(result);
                        }
                    });
                }
            }

            @Override
            public void onStateChanged(int state) {
              if(state == Connection.STATE_DIALING) {
                final Connection self = this;
                final Handler handler = new Handler();
                handler.postDelayed(new Runnable() {
                    @Override
                    public void run() {
                        if (CordovaCall.getCordova() == null) {
                            String message = MyConnectionService.getMessage(context, "cordova_call_app_not_running");
                            Toast.makeText(context, message, Toast.LENGTH_SHORT).show();
                            self.onDisconnect();
                            return;
                        }

                        Intent intent = new Intent(CordovaCall.getCordova().getActivity().getApplicationContext(), CordovaCall.getCordova().getActivity().getClass());
                        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_SINGLE_TOP);
                        CordovaCall.getCordova().getActivity().getApplicationContext().startActivity(intent);
                    }
                }, 500);
              }
            }
        };

        this.setCallerName(connection, request.getExtras().getString("to"));

        Icon icon = CordovaCall.getIcon();
        if(icon != null) {
            StatusHints statusHints = new StatusHints((CharSequence)"", icon, new Bundle());
            connection.setStatusHints(statusHints);
        }
        connection.setDialing();
        connection.setAudioModeIsVoip(true);
        conn = connection;
        ArrayList<CallbackContext> callbackContexts = CordovaCall.getCallbackContexts().get("sendCall");
        if(callbackContexts != null) {
            for (final CallbackContext callbackContext : callbackContexts) {
                CordovaCall.getCordova().getThreadPool().execute(new Runnable() {
                    public void run() {
                        JSONObject info = new JSONObject();
                        try {
                            info.put("callId", request.getAddress().getSchemeSpecificPart());
                            info.put("message", "sendCall event called successfully");
                        } catch (JSONException e) {

                        }
                        PluginResult result = new PluginResult(PluginResult.Status.OK, info);
                        result.setKeepCallback(true);
                        callbackContext.sendPluginResult(result);
                    }
                });
            }
        }
        return connection;
    }
}
