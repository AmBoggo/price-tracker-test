package com.pricetracker.worker;

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.os.Build;

import androidx.annotation.NonNull;
import androidx.core.app.NotificationCompat;
import androidx.work.Worker;
import androidx.work.WorkerParameters;

import com.pricetracker.ui.MainActivity;

/** Worker simples: notifica o usuário para abrir o app e atualizar preços. */
public class PriceCheckWorker extends Worker {

    private static final String CHANNEL_ID = "price_reminders";

    public PriceCheckWorker(@NonNull Context context, @NonNull WorkerParameters params) {
        super(context, params);
    }

    @NonNull
    @Override
    public Result doWork() {
        criarCanal();
        notificar();
        return Result.success();
    }

    private void criarCanal() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                CHANNEL_ID, "PriceTracker Reminders",
                NotificationManager.IMPORTANCE_DEFAULT
            );
            channel.setDescription("Lembretes para verificar preços");
            NotificationManager manager = getApplicationContext()
                    .getSystemService(NotificationManager.class);
            if (manager != null) manager.createNotificationChannel(channel);
        }
    }

    private void notificar() {
        Intent intent = new Intent(getApplicationContext(), MainActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        PendingIntent pi = PendingIntent.getActivity(
            getApplicationContext(), 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT |
            (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M ? PendingIntent.FLAG_IMMUTABLE : 0)
        );

        NotificationCompat.Builder b = new NotificationCompat.Builder(
            getApplicationContext(), CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle("🛒 PriceTracker")
            .setContentText("Abra o app para atualizar os preços dos seus produtos")
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)
            .setContentIntent(pi);

        NotificationManager mgr = getApplicationContext()
                .getSystemService(NotificationManager.class);
        if (mgr != null) mgr.notify(1001, b.build());
    }
}