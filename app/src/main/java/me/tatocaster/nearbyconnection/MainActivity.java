package me.tatocaster.nearbyconnection;

import android.app.Activity;
import android.content.Intent;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.os.Bundle;
import android.support.annotation.IntDef;
import android.support.annotation.NonNull;
import android.support.v7.app.AlertDialog;
import android.text.method.ScrollingMovementMethod;
import android.util.Log;
import android.view.View;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.nearby.Nearby;
import com.google.android.gms.nearby.connection.AppIdentifier;
import com.google.android.gms.nearby.connection.AppMetadata;
import com.google.android.gms.nearby.connection.Connections;
import com.google.android.gms.nearby.connection.ConnectionsStatusCodes;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.util.ArrayList;
import java.util.List;

import butterknife.BindView;
import butterknife.ButterKnife;
import butterknife.OnClick;

/**
 * Main class for the Nearby Connections demo application.  This implements both ends of a two-
 * sided connection where one device advertises and the other discovers. Once the devices are
 * connected, they can send messages to each other.
 */
public class MainActivity extends Activity implements
        GoogleApiClient.ConnectionCallbacks,
        GoogleApiClient.OnConnectionFailedListener,
        Connections.MessageListener {

    private static final String TAG = "MainActivity";

    Connections.ConnectionRequestListener mConnectionRequestListener = new Connections.ConnectionRequestListener() {
        @Override
        public void onConnectionRequest(String s, String s1, byte[] bytes) {
            MainActivity.this.onConnectionRequest(s, s1, bytes)
            ;
        }
    };

    Connections.EndpointDiscoveryListener mEndpointDiscoveryListener = new Connections.EndpointDiscoveryListener() {
        @Override
        public void onEndpointFound(String s, String s1, String s2) {
            MainActivity.this.onEndpointFound(s, s1, s2);
        }

        @Override
        public void onEndpointLost(String s) {
            MainActivity.this.onEndpointLost(s);
        }
    };

    /**
     * Timeouts (in millis) for startAdvertising and startDiscovery.  At the end of these time
     * intervals the app will silently stop advertising or discovering.
     * <p>
     * To set advertising or discovery to run indefinitely, use 0L where timeouts are required.
     */
    private static final long TIMEOUT_ADVERTISE = 1000L * 30L;
    private static final long TIMEOUT_DISCOVER = 1000L * 30L;

    /**
     * Possible states for this application:
     * IDLE - GoogleApiClient not yet connected, can't do anything.
     * READY - GoogleApiClient connected, ready to use Nearby Connections API.
     * ADVERTISING - advertising for peers to connect.
     * DISCOVERING - looking for a peer that is advertising.
     * CONNECTED - found a peer.
     */
    @Retention(RetentionPolicy.CLASS)
    @IntDef({STATE_IDLE, STATE_READY, STATE_ADVERTISING, STATE_DISCOVERING, STATE_CONNECTED})
    public @interface NearbyConnectionState {
    }

    private static final int STATE_IDLE = 1023;
    private static final int STATE_READY = 1024;
    private static final int STATE_ADVERTISING = 1025;
    private static final int STATE_DISCOVERING = 1026;
    private static final int STATE_CONNECTED = 1027;

    /**
     * GoogleApiClient for connecting to the Nearby Connections API
     **/
    private GoogleApiClient mGoogleApiClient;

    /**
     * Views and Dialogs
     **/
    private AlertDialog mConnectionRequestDialog;
    private MyListDialog mMyListDialog;

    /**
     * The current state of the application
     **/
    @NearbyConnectionState
    private int mState = STATE_IDLE;

    @BindView(R.id.edittext_message)
    public EditText mMessageText;

    @BindView(R.id.debug_text)
    TextView mDebugInfo;

    /**
     * The endpoint ID of the connected peer, used for messaging
     **/
    private String mOtherEndpointId;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        ButterKnife.bind(this);


        // Debug text view
        mDebugInfo.setMovementMethod(new ScrollingMovementMethod());

        mGoogleApiClient = new GoogleApiClient.Builder(this)
                .addConnectionCallbacks(this)
                .addOnConnectionFailedListener(this)
                .addApi(Nearby.CONNECTIONS_API)
                .build();
    }

    @Override
    public void onStart() {
        super.onStart();
        Log.d(TAG, "onStart");
        mGoogleApiClient.connect();
    }

    @Override
    public void onStop() {
        super.onStop();
        Log.d(TAG, "onStop");

        // Disconnect the Google API client and stop any ongoing discovery or advertising. When the
        // GoogleAPIClient is disconnected, any connected peers will get an onDisconnected callback.
        if (mGoogleApiClient != null) {
            mGoogleApiClient.disconnect();
        }
    }

    public void openNearbyMessages(View v) {
        startActivity(new Intent(this, NearbyMessagesActivity.class));
    }

    /**
     * Check if the device is connected (or connecting) to a WiFi network.
     *
     * @return true if connected or connecting, false otherwise.
     */
    private boolean isConnectedToNetwork() {
        ConnectivityManager connManager = (ConnectivityManager) getSystemService(CONNECTIVITY_SERVICE);
        NetworkInfo info = connManager.getNetworkInfo(ConnectivityManager.TYPE_WIFI);

        return (info != null && info.isConnectedOrConnecting());
    }

    /**
     * Begin advertising for Nearby Connections, if possible.
     */
    private void startAdvertising() {
        debugLog("startAdvertising");
        if (!isConnectedToNetwork()) {
            debugLog("startAdvertising: not connected to WiFi network.");
            return;
        }

        // Advertising with an AppIdentifer lets other devices on the network discover
        // this application and prompt the user to install the application.
        List<AppIdentifier> appIdentifierList = new ArrayList<>();
        appIdentifierList.add(new AppIdentifier(getPackageName()));
        AppMetadata appMetadata = new AppMetadata(appIdentifierList);

        // Advertise for Nearby Connections. This will broadcast the service id defined in
        // AndroidManifest.xml. By passing 'null' for the name, the Nearby Connections API
        // will construct a default name based on device model such as 'LGE Nexus 5'.
        String name = null;
        Nearby.Connections.startAdvertising(mGoogleApiClient, name, appMetadata, TIMEOUT_ADVERTISE, mConnectionRequestListener)
                .setResultCallback(result -> {
                    Log.d(TAG, "startAdvertising:onResult:" + result);
                    if (result.getStatus().isSuccess()) {
                        debugLog("startAdvertising:onResult: SUCCESS");

                        updateViewVisibility(STATE_ADVERTISING);
                    } else {
                        debugLog("startAdvertising:onResult: FAILURE ");

                        // If the user hits 'Advertise' multiple times in the timeout window,
                        // the error will be STATUS_ALREADY_ADVERTISING
                        int statusCode = result.getStatus().getStatusCode();
                        if (statusCode == ConnectionsStatusCodes.STATUS_ALREADY_ADVERTISING) {
                            debugLog("STATUS_ALREADY_ADVERTISING");
                        } else {
                            updateViewVisibility(STATE_READY);
                        }
                    }
                });
    }

    /**
     * Begin discovering devices advertising Nearby Connections, if possible.
     */
    private void startDiscovery() {
        debugLog("startDiscovery");
        if (!isConnectedToNetwork()) {
            debugLog("startDiscovery: not connected to WiFi network.");
            return;
        }

        // Discover nearby apps that are advertising with the required service ID.
        String serviceId = getString(R.string.service_id);
        Nearby.Connections.startDiscovery(mGoogleApiClient, serviceId, TIMEOUT_DISCOVER, mEndpointDiscoveryListener)
                .setResultCallback(status -> {
                    if (status.isSuccess()) {
                        debugLog("startDiscovery:onResult: SUCCESS");

                        updateViewVisibility(STATE_DISCOVERING);
                    } else {
                        debugLog("startDiscovery:onResult: FAILURE");

                        // If the user hits 'Discover' multiple times in the timeout window,
                        // the error will be STATUS_ALREADY_DISCOVERING
                        int statusCode = status.getStatusCode();
                        if (statusCode == ConnectionsStatusCodes.STATUS_ALREADY_DISCOVERING) {
                            debugLog("STATUS_ALREADY_DISCOVERING");
                        } else {
                            updateViewVisibility(STATE_READY);
                        }
                    }
                });
    }

    /**
     * Send a reliable message to the connected peer. Takes the contents of the EditText and
     * sends the message as a byte[].
     */
    private void sendMessage() {
        // Sends a reliable message, which is guaranteed to be delivered eventually and to respect
        // message ordering from sender to receiver. Nearby.Connections.sendUnreliableMessage
        // should be used for high-frequency messages where guaranteed delivery is not required, such
        // as showing one player's cursor location to another. Unreliable messages are often
        // delivered faster than reliable messages.
        String msg = mMessageText.getText().toString();
        Nearby.Connections.sendReliableMessage(mGoogleApiClient, mOtherEndpointId, msg.getBytes());

        mMessageText.setText(null);
    }

    /**
     * Send a connection request to a given endpoint.
     *
     * @param endpointId   the endpointId to which you want to connect.
     * @param endpointName the name of the endpoint to which you want to connect. Not required to
     *                     make the connection, but used to display after success or failure.
     */
    private void connectTo(String endpointId, final String endpointName) {
        debugLog("connectTo:" + endpointId + ":" + endpointName);

        // Send a connection request to a remote endpoint. By passing 'null' for the name,
        // the Nearby Connections API will construct a default name based on device model
        // such as 'LGE Nexus 5'.
        String myName = null;
        byte[] myPayload = null;
        Nearby.Connections.sendConnectionRequest(mGoogleApiClient, myName, endpointId, myPayload,
                (endpointId1, status, bytes) -> {
                    Log.d(TAG, "onConnectionResponse:" + endpointId1 + ":" + status);
                    if (status.isSuccess()) {
                        debugLog("onConnectionResponse: " + endpointName + " SUCCESS");
                        Toast.makeText(MainActivity.this, "Connected to " + endpointName, Toast.LENGTH_SHORT).show();

                        mOtherEndpointId = endpointId1;
                        updateViewVisibility(STATE_CONNECTED);
                    } else {
                        debugLog("onConnectionResponse: " + endpointName + " FAILURE");
                    }
                }, this);
    }


    public void onConnectionRequest(final String endpointId, String endpointName, byte[] payload) {
        debugLog("onConnectionRequest:" + endpointId + ":" + endpointName);

        // This device is advertising and has received a connection request. Show a dialog asking
        // the user if they would like to connect and accept or reject the request accordingly.
        mConnectionRequestDialog = new AlertDialog.Builder(this)
                .setTitle("Connection Request")
                .setMessage("Do you want to connect to " + endpointName + "?")
                .setCancelable(false)
                .setPositiveButton("Connect", (dialog, which) -> {
                    byte[] payload1 = null;
                    Nearby.Connections.acceptConnectionRequest(mGoogleApiClient, endpointId, payload1, MainActivity.this)
                            .setResultCallback(status -> {
                                if (status.isSuccess()) {
                                    debugLog("acceptConnectionRequest: SUCCESS");

                                    mOtherEndpointId = endpointId;
                                    updateViewVisibility(STATE_CONNECTED);
                                } else {
                                    debugLog("acceptConnectionRequest: FAILURE");
                                }
                            });
                })
                .setNegativeButton("No", (dialog, which) -> Nearby.Connections.rejectConnectionRequest(mGoogleApiClient, endpointId)).create();

        mConnectionRequestDialog.show();
    }

    @Override
    public void onMessageReceived(String endpointId, byte[] payload, boolean isReliable) {
        // A message has been received from a remote endpoint.
        debugLog("onMessageReceived:" + endpointId + ":" + new String(payload));
    }

    @Override
    public void onDisconnected(String endpointId) {
        debugLog("onDisconnected:" + endpointId);

        updateViewVisibility(STATE_READY);
    }


    public void onEndpointFound(final String endpointId, String serviceId, final String endpointName) {
        Log.d(TAG, "onEndpointFound:" + endpointId + ":" + endpointName);

        // This device is discovering endpoints and has located an advertiser. Display a dialog to
        // the user asking if they want to connect, and send a connection request if they do.
        if (mMyListDialog == null) {
            // Configure the AlertDialog that the MyListDialog wraps
            AlertDialog.Builder builder = new AlertDialog.Builder(this)
                    .setTitle("Endpoint(s) Found")
                    .setCancelable(true)
                    .setNegativeButton("Cancel", (dialog, which) -> mMyListDialog.dismiss());

            // Create the MyListDialog with a listener
            mMyListDialog = new MyListDialog(this, builder, (dialog, which) -> {
                String selectedEndpointName = mMyListDialog.getItemKey(which);
                String selectedEndpointId = mMyListDialog.getItemValue(which);

                MainActivity.this.connectTo(selectedEndpointId, selectedEndpointName);
                mMyListDialog.dismiss();
            });
        }

        mMyListDialog.addItem(endpointName, endpointId);
        mMyListDialog.show();
    }


    public void onEndpointLost(String endpointId) {
        debugLog("onEndpointLost:" + endpointId);

        // An endpoint that was previously available for connection is no longer. It may have
        // stopped advertising, gone out of range, or lost connectivity. Dismiss any dialog that
        // was offering a connection.
        if (mMyListDialog != null) {
            mMyListDialog.removeItemByValue(endpointId);
        }
    }

    @Override
    public void onConnected(Bundle bundle) {
        debugLog("onConnected");
        updateViewVisibility(STATE_READY);
    }

    @Override
    public void onConnectionSuspended(int i) {
        debugLog("onConnectionSuspended: " + i);
        updateViewVisibility(STATE_IDLE);

        // Try to re-connect
        mGoogleApiClient.reconnect();
    }

    @Override
    public void onConnectionFailed(@NonNull ConnectionResult connectionResult) {
        debugLog("onConnectionFailed: " + connectionResult);
        updateViewVisibility(STATE_IDLE);
    }

    @OnClick({R.id.button_advertise, R.id.button_discover, R.id.button_send})
    public void onButtonClick(View v) {
        switch (v.getId()) {
            case R.id.button_advertise:
                startAdvertising();
                break;
            case R.id.button_discover:
                startDiscovery();
                break;
            case R.id.button_send:
                sendMessage();
                break;
        }
    }

    /**
     * Change the application state and update the visibility on on-screen views '
     * based on the new state of the application.
     *
     * @param newState the state to move to (should be NearbyConnectionState)
     */
    private void updateViewVisibility(@NearbyConnectionState int newState) {
        mState = newState;
        switch (mState) {
            case STATE_IDLE:
                // The GoogleAPIClient is not connected, we can't yet start advertising or
                // discovery so hide all buttons
                ButterKnife.findById(this, R.id.layout_nearby_buttons).setVisibility(View.GONE);
                ButterKnife.findById(this, R.id.layout_message).setVisibility(View.GONE);
                break;
            case STATE_READY:
                // The GoogleAPIClient is connected, we can begin advertising or discovery.
                ButterKnife.findById(this, R.id.layout_nearby_buttons).setVisibility(View.VISIBLE);
                ButterKnife.findById(this, R.id.layout_message).setVisibility(View.GONE);
                break;
            case STATE_ADVERTISING:
                break;
            case STATE_DISCOVERING:
                break;
            case STATE_CONNECTED:
                // We are connected to another device via the Connections API, so we can
                // show the message UI.
                ButterKnife.findById(this, R.id.layout_nearby_buttons).setVisibility(View.VISIBLE);
                ButterKnife.findById(this, R.id.layout_message).setVisibility(View.VISIBLE);
                break;
        }
    }

    /**
     * Print a message to the DEBUG LogCat as well as to the on-screen debug panel.
     *
     * @param msg the message to print and display.
     */
    private void debugLog(String msg) {
        Log.d(TAG, msg);
        mDebugInfo.append("\n" + msg);
    }
}