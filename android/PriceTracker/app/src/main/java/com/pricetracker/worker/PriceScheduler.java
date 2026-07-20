package com.pricetracker.worker;

import android.content.Context;

import androidx.work.ExistingPeriodicWorkPolicy;
import androidx.work.PeriodicWorkRequest;
import androidx.work.WorkManager;

import java.util.concurrent.TimeUnit;

/**
 * Agenda verificações de preço 3x ao dia (8h, 14h, 20h).
 * Na verdade, WorkManager periodic não suporta horários exatos,
 * mas aproxima com intervalos de 8 horas.
 */
public class PriceScheduler {

    private static final String WORK_NAME = "price_check_periodic";

    public static void agendar(Context context) {
        // 8h inicial + repetir a cada 8h = ~3x/dia
        PeriodicWorkRequest request = new PeriodicWorkRequest.Builder(
            PriceCheckWorker.class,
            8, TimeUnit.HOURS,
            PeriodicWorkRequest.MIN_PERIODIC_INTERVAL_MILLIS, TimeUnit.MILLISECONDS
        )
            .setInitialDelay(calcularDelayInicial(), TimeUnit.HOURS)
            .build();

        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            WORK_NAME,
            ExistingPeriodicWorkPolicy.KEEP,
            request
        );
    }

    /**
     * Calcula delay inicial até as próximas 8h, 14h ou 20h.
     */
    private static long calcularDelayInicial() {
        java.util.Calendar now = java.util.Calendar.getInstance();
        int hora = now.get(java.util.Calendar.HOUR_OF_DAY);

        if (hora < 8) return 8 - hora;      // até 8h
        if (hora < 14) return 14 - hora;     // até 14h
        if (hora < 20) return 20 - hora;     // até 20h
        return 24 - hora + 8;                // até 8h do dia seguinte
    }
}