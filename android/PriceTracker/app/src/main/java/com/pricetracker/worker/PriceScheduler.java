package com.pricetracker.worker;

import android.content.Context;

import androidx.work.ExistingPeriodicWorkPolicy;
import androidx.work.PeriodicWorkRequest;
import androidx.work.WorkManager;

import java.util.concurrent.TimeUnit;

public class PriceScheduler {

    private static final String WORK_NAME = "price_check_periodic";

    public static void agendar(Context context) {
        // Agora: verificar a cada 1 hora (para teste)
        PeriodicWorkRequest request = new PeriodicWorkRequest.Builder(
            PriceCheckWorker.class,
            1, TimeUnit.HOURS
        )
            .setInitialDelay(1, TimeUnit.MINUTES) // começa em 1 minuto
            .build();

        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            WORK_NAME,
            ExistingPeriodicWorkPolicy.REPLACE, // substitui agendamento antigo
            request
        );
    }
}