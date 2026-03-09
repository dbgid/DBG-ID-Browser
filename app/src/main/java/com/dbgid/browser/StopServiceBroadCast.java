package com.dbgid.browser;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

public class StopServiceBroadCast extends BroadcastReceiver {

    @Override
    public void onReceive(Context context, Intent intent) {
        String action = intent != null && intent.getExtras() != null
                ? intent.getExtras().getString("action", "exit")
                : "exit";
        if ("exit".equals(action)) {
            Intent service = new Intent(context, MainService.class);
            context.stopService(service);
        }
    }
}
