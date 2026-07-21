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

import com.pricetracker.api.RetrofitClient;
import com.pricetracker.model.Produto;
import com.pricetracker.ui.MainActivity;

import java.io.IOException;
import java.util.List;

import retrofit2.Response;

/**
 * Worker que verifica se há produtos sem preço e notifica.
 * O scraping real acontece quando o usuário abre o app (MainActivity).
 */
public class PriceCheckWorker extends Worker {

    private static final String CHANNEL_ID = "price_updates";

    public PriceCheckWorker(@NonNull Context context, @NonNull WorkerParameters params) {
        super(context, params);
    }

    @NonNull
    @Override
    public Result doWork() {
        try {
            // Consulta a API pra ver quantos produtos precisam de atualização
            Response<List<Produto>> response = RetrofitClient.getService()
                .listarProdutos().execute();

            if (response.isSuccessful() && response.body() != null) {
                int pendentes = 0;
                for (Produto p : response.body()) {
                    if (p.precoAtual == null) pendentes++;
                }

                criarCanal();
                if (pendentes > 0) {
                    notificar(pendentes, response.body().size());
                }
                // Se não tem pendentes, não notifica (silencioso)
            }

            return Result.success();
        } catch (IOException e) {
            return Result.retry();
        }
    }

    private void criarCanal() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                CHANNEL_ID, "Atualização de Preços",
                NotificationManager.IMPORTANCE_DEFAULT
            );
            channel.setDescription("Notificações de atualização de preços");
            NotificationManager mgr = getApplicationContext()
                .getSystemService(NotificationManager.class);
            if (mgr != null) mgr.createNotificationChannel(channel);
        }
    }

    private void notificar(int pendentes, int total) {
        Intent intent = new Intent(getApplicationContext(), MainActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        intent.putExtra("verificar_precos", true);
        
        int flags = PendingIntent.FLAG_UPDATE_CURRENT;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            flags |= PendingIntent.FLAG_IMMUTABLE;
        }
        PendingIntent pi = PendingIntent.getActivity(
            getApplicationContext(), 0, intent, flags
        );

        NotificationCompat.Builder b = new NotificationCompat.Builder(
            getApplicationContext(), CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle("🛒 PriceTracker")
            .setContentText(pendentes + " de " + total + " produtos precisam de verificação")
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)
            .setContentIntent(pi);

        NotificationManager mgr = getApplicationContext()
            .getSystemService(NotificationManager.class);
        if (mgr != null) mgr.notify(1001, b.build());
    }
}