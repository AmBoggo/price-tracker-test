package com.pricetracker.ui;

import android.os.Bundle;
import android.view.View;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.pricetracker.R;
import com.pricetracker.api.ApiService;
import com.pricetracker.api.RetrofitClient;
import com.pricetracker.model.Produto;
import com.pricetracker.model.ProdutoCreate;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class AddProductActivity extends AppCompatActivity {

    private EditText etUrl, etMeta;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_add_product);

        etUrl = findViewById(R.id.etUrl);
        etMeta = findViewById(R.id.etMeta);
    }

    public void onVoltarClick(View view) {
        finish();
    }

    public void onAdicionarClick(View view) {
        String url = etUrl.getText().toString().trim();
        if (url.isEmpty() || !url.startsWith("http")) {
            Toast.makeText(this, "Cole um link válido", Toast.LENGTH_SHORT).show();
            return;
        }

        Double meta = null;
        String metaStr = etMeta.getText().toString().trim();
        if (!metaStr.isEmpty()) {
            try {
                meta = Double.parseDouble(metaStr.replace(",", "."));
            } catch (NumberFormatException e) {
                Toast.makeText(this, "Meta inválida", Toast.LENGTH_SHORT).show();
                return;
            }
        }

        TextView btn = (TextView) view;
        btn.setText("Adicionando...");
        btn.setEnabled(false);

        ApiService api = RetrofitClient.getService();
        ProdutoCreate body = new ProdutoCreate(url, meta);
        api.addProduto(body).enqueue(new Callback<Produto>() {
            @Override
            public void onResponse(Call<Produto> call, Response<Produto> response) {
                btn.setText("Adicionar");
                btn.setEnabled(true);
                if (response.isSuccessful()) {
                    Toast.makeText(AddProductActivity.this, "Produto adicionado!", Toast.LENGTH_SHORT).show();
                    finish();
                } else if (response.code() == 409) {
                    Toast.makeText(AddProductActivity.this, "Produto já cadastrado", Toast.LENGTH_SHORT).show();
                } else {
                    Toast.makeText(AddProductActivity.this, "Erro: " + response.code(), Toast.LENGTH_SHORT).show();
                }
            }

            @Override
            public void onFailure(Call<Produto> call, Throwable t) {
                btn.setText("Adicionar");
                btn.setEnabled(true);
                Toast.makeText(AddProductActivity.this, "Sem conexão: " + t.getMessage(), Toast.LENGTH_LONG).show();
            }
        });
    }
}