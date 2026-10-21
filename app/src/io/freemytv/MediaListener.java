package io.freemytv;

import android.service.notification.NotificationListenerService;

/**
 * Empty on purpose: Android lets an app see and control the media sessions playing (QuickPanelActivity's "now playing")
 * only while it has notification access, granted to this service (adb: cmd notification allow_listener
 * io.freemytv/io.freemytv.MediaListener). No notification is read, stored or sent anywhere.
 */
public class MediaListener extends NotificationListenerService {
}
