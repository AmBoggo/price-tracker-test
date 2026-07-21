package com.pricetracker.worker;

import android.content.Context;
import android.content.Intent;

import androidx.annotation.NonNull;
import androidx.work.Worker;
import androidx.work.WorkerParameters;

/**
 * Worker que inicia o ScrapingService (Foreground Service com WebView)
 * para atualizar preços automaticamente em segundo plano.
 */
public class PriceCheckWorker extends Worker {

    public PriceCheckWorker(@NonNull Context context, @NonNull WorkerParameters params) {
        super(context, params);
    }

    @NonNull
    @Override
    public Result doWork() {
        Intent intent = new Intent(getApplicationContext(), ScrapingService.class);
        getApplicationContext().startForegroundService(intent);
        return Result.success();
    }
}