package me.tatocaster.nearbyconnection;

import com.google.android.gms.nearby.messages.Message;
import com.google.gson.Gson;

import java.nio.charset.Charset;

/**
 * Created by tatocaster on 2/24/17.
 */

public class DeviceMessage {
    private static final Gson gson = new Gson();

    private final String mUUID;
    private final String mMessageBody;


    public static Message newNearbyMessage(String instanceId, String messageBody) {
        DeviceMessage deviceMessage = new DeviceMessage(instanceId, messageBody);
        return new Message(gson.toJson(deviceMessage).getBytes(Charset.forName("UTF-8")));
    }


    public static DeviceMessage fromNearbyMessage(Message message) {
        String nearbyMessageString = new String(message.getContent()).trim();
        return gson.fromJson((new String(nearbyMessageString.getBytes(Charset.forName("UTF-8")))), DeviceMessage.class);
    }

    private DeviceMessage(String uuid, String messageBody) {
        mUUID = uuid;
        mMessageBody = messageBody;
        // TODO(developer): add other fields that must be included in the Nearby Message payload.
    }

    protected String getMessageBody() {
        return mMessageBody;
    }
}
