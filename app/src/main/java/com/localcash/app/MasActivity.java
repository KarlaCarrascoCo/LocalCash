package com.localcash.app;

import android.content.Intent;
import android.os.Bundle;
import androidx.appcompat.app.AppCompatActivity;

/** Accesos a módulos offline complementarios. */
public final class MasActivity extends AppCompatActivity {
    @Override protected void onCreate(Bundle state) {
        super.onCreate(state); setContentView(R.layout.activity_mas);
        findViewById(R.id.button_more_contacts).setOnClickListener(v -> startActivity(new Intent(this, ContactosActivity.class)));
        findViewById(R.id.button_more_shared).setOnClickListener(v -> startActivity(new Intent(this, GastosCompartidosActivity.class)));
        findViewById(R.id.button_more_map).setOnClickListener(v -> startActivity(new Intent(this, MapActivity.class)));
    }
}
