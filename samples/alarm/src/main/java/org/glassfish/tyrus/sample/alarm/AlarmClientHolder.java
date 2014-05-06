package org.glassfish.tyrus.sample.alarm;

import java.util.HashSet;
import java.util.Set;
import java.util.Timer;

import javax.websocket.Session;

/**
 * @author Ondrej Kosatka (ondrej.kosatka at oracle.com)
 */
public class AlarmClientHolder {

    private final Set<Session> clients = new HashSet<Session>();
    private Timer timer;

    public AlarmClientHolder() {
    }

    public synchronized boolean addClient(Session client) {
        if (timer == null) {
            timer = new Timer();
            timer.schedule(new AlarmBroadcasterTask(this), 0, 5000);
        }
        return clients.add(client);
    }

    public synchronized boolean removeClient(Session session) {
        return clients.remove(session);
    }

    public Set<Session> getClients() {
        return clients;
    }
}
