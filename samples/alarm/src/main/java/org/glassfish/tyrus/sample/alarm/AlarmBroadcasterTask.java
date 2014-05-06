package org.glassfish.tyrus.sample.alarm;

import java.io.IOException;
import java.util.TimerTask;

import javax.websocket.Session;

/**
 * @author Ondrej Kosatka (ondrej.kosatka at oracle.com)
 */
public class AlarmBroadcasterTask extends TimerTask {

    private final AlarmClientHolder alarmClientHolder;

    public AlarmBroadcasterTask(AlarmClientHolder alarmClientHolder) {
        this.alarmClientHolder = alarmClientHolder;
    }

    @Override
    public void run() {
        for (Session client : alarmClientHolder.getClients()) {
            try {
                client.getBasicRemote().sendText("tick");
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

}
